package com.intentevolved.com.intentevolved.voluntas

import com.intentevolved.com.intentevolved.FocalScope
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentService
import com.intentevolved.com.intentevolved.server.IntentWebServer
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import voluntas.v1.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class VoluntasRuntime(
    private val port: Int,
    private val service: VoluntasIntentService,
    private val fileName: String,
    private val webPort: Int? = null,
    private val modulesDir: String? = null
) {
    // this enforces that there's exactly one thread messing with the service
    private val stateDispatcher: CloseableCoroutineDispatcher = newSingleThreadContext("intent-state")

    private val intentServiceGrpc = VoluntasIntentServiceGrpcImpl(service, fileName, stateDispatcher)
    private val voluntasServiceGrpc = VoluntasServiceGrpcImpl(service, fileName, stateDispatcher)

    private val server: Server = ServerBuilder
        .forPort(port)
        .addService(intentServiceGrpc)
        .addService(voluntasServiceGrpc)
        .addService(ProtoReflectionService.newInstance())
        .build()

    private var webServer: IntentWebServer? = null

    fun start() {
        if (modulesDir != null) {
            loadModules(modulesDir)
        }

        server.start()
        println("Voluntas gRPC server started on port $port")

        if (webPort != null) {
            println("Voluntas web server started on port $port")
            val ws = IntentWebServer(webPort, service, service, stateDispatcher) {
                service.writeToFile(fileName)
            }
            ws.start()
            webServer = ws
            val broadcastCallback: suspend () -> Unit = { ws.broadcastAll() }
            intentServiceGrpc.onMutation = broadcastCallback
            voluntasServiceGrpc.onMutation = broadcastCallback
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            println("Shutting down Voluntas server...")
            stop()
        })
    }

    private fun loadModules(dir: String) {
        val modulesDirectory = File(dir)
        if (!modulesDirectory.isDirectory) {
            println("Modules directory '$dir' not found, skipping module loading")
            return
        }
        val pbFiles = modulesDirectory.listFiles { f -> f.extension == "pb" } ?: return
        if (pbFiles.isEmpty()) {
            println("No .pb module files found in '$dir'")
            return
        }

        val loader = ModuleLoader(service)
        for (file in pbFiles.sortedBy { it.name }) {
            println("Loading module: ${file.name}")
            val module = VoluntasModule.fromFile(file.absolutePath)
            val manifest = loader.loadModule(module)
            println("  Module '${module.rootText}': ${manifest.newlyCreated.size} created, ${manifest.alreadyExisted.size} existing")
        }

        service.writeToFile(fileName)
        println("Modules loaded, stream saved")
    }

    fun stop() {
        webServer?.stop()
        server.shutdown()
        stateDispatcher.close()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val port = args.getOrNull(0)?.toIntOrNull() ?: 50051
            val fileName = args.getOrNull(1) ?: "voluntas_current.pb"
            val webPort = args.getOrNull(2)?.toIntOrNull()
            val modulesDir = args.getOrNull(3)

            val service = try {
                println("Loading voluntas stream from $fileName")
                VoluntasIntentService.fromFile(fileName)
            } catch (e: IllegalArgumentException) {
                println("File not found, creating new voluntas stream")
                VoluntasIntentService.new("Voluntas Server Root")
            }

            val server = VoluntasRuntime(port, service, fileName, webPort, modulesDir)
            server.start()
            server.blockUntilShutdown()
        }
    }
}

/**
 * gRPC impl for the IntentService endpoints, backed by VoluntasIntentService.
 *
 * Delegates to service.consume(SubmitOpRequest) for all mutations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoluntasIntentServiceGrpcImpl(
    private val service: VoluntasIntentService,
    private val fileName: String,
    private val stateDispatcher: CloseableCoroutineDispatcher
) : IntentServiceGrpcKt.IntentServiceCoroutineImplBase() {

    var onMutation: (suspend () -> Unit)? = null

    override suspend fun submitOp(request: SubmitOpRequest): SubmitOpResponse {
        val response = withContext(stateDispatcher) {
            try {
                val result = service.consume(request)
                service.writeToFile(fileName)

                val id = extractIdFromResult(result.message)

                SubmitOpResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(result.message)
                    .setId(id)
                    .build()
            } catch (e: IllegalArgumentException) {
                SubmitOpResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.message ?: "Unknown error")
                    .setId(0)
                    .build()
            }
        }
        if (response.success) onMutation?.invoke()
        return response
    }

    private fun extractIdFromResult(message: String): Long {
        val regex = Regex("intent (\\d+)")
        val match = regex.find(message)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    override suspend fun getIntent(request: GetIntentRequest): GetIntentResponse {
        return withContext(stateDispatcher) {
            val intent = service.getById(request.id)

            if (intent != null) {
                GetIntentResponse.newBuilder()
                    .setFound(true)
                    .setIntent(intentToProto(intent))
                    .build()
            } else {
                GetIntentResponse.newBuilder()
                    .setFound(false)
                    .setError("No intent found with id ${request.id}")
                    .build()
            }
        }
    }

    override suspend fun getCommands(request: GetCommandsRequest): GetCommandsResponse {
        return withContext(stateDispatcher) {
            val annotations = service.getCommandAnnotations()
            GetCommandsResponse.newBuilder()
                .addAllCommands(annotations.map { (keyword, macroEntityId) ->
                    CommandInfo.newBuilder()
                        .setKeyword(keyword)
                        .setMacroEntityId(macroEntityId)
                        .build()
                })
                .build()
        }
    }

    override suspend fun getFocalScope(request: GetFocalScopeRequest): GetFocalScopeResponse {
        return withContext(stateDispatcher) {
            try {
                val scope = service.getFocalScope(request.id)
                GetFocalScopeResponse.newBuilder()
                    .setFound(true)
                    .setFocus(intentToProto(scope.focus))
                    .addAllAncestry(scope.ancestry.map { intentToProto(it) })
                    .addAllChildren(scope.children.map { intentToProto(it) })
                    .build()
            } catch (e: Exception) {
                GetFocalScopeResponse.newBuilder()
                    .setFound(false)
                    .setError("No intent found with id ${request.id}: ${e.message}")
                    .build()
            }
        }
    }
}

/**
 * gRPC impl for the Voluntas-native service.
 * Accepts raw Relationships and Literals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoluntasServiceGrpcImpl(
    private val service: VoluntasIntentService,
    private val fileName: String,
    private val stateDispatcher: CloseableCoroutineDispatcher
) : VoluntasServiceGrpcKt.VoluntasServiceCoroutineImplBase() {

    var onMutation: (suspend () -> Unit)? = null

    override suspend fun submitRelationship(request: SubmitRelationshipRequest): SubmitRelationshipResponse {
        val response = withContext(stateDispatcher) {
            try {
                val result = (service as VoluntasStreamConsumer).consume(request.relationship)
                service.writeToFile(fileName)
                SubmitRelationshipResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(result.message)
                    .build()
            } catch (e: Exception) {
                SubmitRelationshipResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.message ?: "Unknown error")
                    .build()
            }
        }
        if (response.success) onMutation?.invoke()
        return response
    }

    override suspend fun submitLiteral(request: SubmitLiteralRequest): SubmitLiteralResponse {
        return withContext(stateDispatcher) {
            try {
                service.literalStore.register(request.literal)
                service.writeToFile(fileName)
                SubmitLiteralResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("registered literal ${request.literal.id}")
                    .build()
            } catch (e: Exception) {
                SubmitLiteralResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.message ?: "Unknown error")
                    .build()
            }
        }
    }
}

/**
 * Convert an Intent to its proto representation.
 */
fun intentToProto(intent: Intent): IntentProto {
    val builder = IntentProto.newBuilder()
        .setId(intent.id())
        .setText(intent.text())
        .setIsMeta(intent.isMeta())

    intent.parent()?.let { builder.setParentId(it.id()) }
    intent.createdTimestamp()?.let { builder.setCreatedTimestamp(it) }
    intent.lastUpdatedTimestamp()?.let { builder.setLastUpdatedTimestamp(it) }
    builder.addAllParticipantIds(intent.participantIds())

    intent.fields().forEach { (name, details) ->
        builder.putFields(name, FieldDetailsProto.newBuilder()
            .setFieldType(details.fieldType)
            .setRequired(details.required)
            .apply { details.description?.let { setDescription(it) } }
            .build())
    }

    intent.fieldValues().forEach { (name, value) ->
        val valueProto = when (value) {
            is String -> FieldValueProto.newBuilder().setStringValue(value).build()
            is Int -> FieldValueProto.newBuilder().setInt32Value(value).build()
            is Long -> FieldValueProto.newBuilder().setInt64Value(value).build()
            is Float -> FieldValueProto.newBuilder().setFloatValue(value).build()
            is Double -> FieldValueProto.newBuilder().setDoubleValue(value).build()
            is Boolean -> FieldValueProto.newBuilder().setBoolValue(value).build()
            else -> FieldValueProto.newBuilder().setStringValue(value.toString()).build()
        }
        builder.putFieldValues(name, valueProto)
    }

    return builder.build()
}
