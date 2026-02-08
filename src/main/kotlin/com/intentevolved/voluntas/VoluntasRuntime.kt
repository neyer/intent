package com.intentevolved.com.intentevolved.voluntas

import com.intentevolved.*
import com.intentevolved.com.intentevolved.FocalScope
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentService
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import voluntas.v1.*

class VoluntasRuntime(
    private val port: Int,
    private val service: VoluntasIntentService,
    private val fileName: String
) {
    private val server: Server = ServerBuilder
        .forPort(port)
        .addService(VoluntasIntentServiceGrpcImpl(service, fileName))
        .addService(VoluntasServiceGrpcImpl(service, fileName))
        .addService(ProtoReflectionService.newInstance())
        .build()

    fun start() {
        server.start()
        println("Voluntas server started on port $port")
        Runtime.getRuntime().addShutdownHook(Thread {
            println("Shutting down Voluntas server...")
            stop()
        })
    }

    fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val port = args.getOrNull(0)?.toIntOrNull() ?: 50051
            val fileName = args.getOrNull(1) ?: "voluntas_current.pb"

            val service = try {
                println("Loading voluntas stream from $fileName")
                VoluntasIntentService.fromFile(fileName)
            } catch (e: IllegalArgumentException) {
                println("File not found, creating new voluntas stream")
                VoluntasIntentService.new("Voluntas Server Root")
            }

            val server = VoluntasRuntime(port, service, fileName)
            server.start()
            server.blockUntilShutdown()
        }
    }
}

/**
 * gRPC impl for the IntentService endpoints, backed by VoluntasIntentService.
 *
 * Translates intent.proto SubmitOp requests to IntentService method calls
 * rather than going through IntentStreamConsumer.consume(op).
 */
class VoluntasIntentServiceGrpcImpl(
    private val service: VoluntasIntentService,
    private val fileName: String
) : IntentServiceGrpcKt.IntentServiceCoroutineImplBase() {

    override suspend fun submitOp(request: SubmitOpRequest): SubmitOpResponse {
        return try {
            val (message, id) = when (request.payloadCase) {
                SubmitOpRequest.PayloadCase.CREATE_INTENT -> {
                    val create = request.createIntent
                    val parentId = if (create.hasParentId()) create.parentId else 0L
                    val intent = service.addIntent(create.text, parentId)
                    "added intent ${intent.id()}" to intent.id()
                }
                SubmitOpRequest.PayloadCase.UPDATE_INTENT -> {
                    val update = request.updateIntent
                    service.edit(update.id, update.newText)
                    "updated intent ${update.id}" to update.id
                }
                SubmitOpRequest.PayloadCase.UPDATE_INTENT_PARENT -> {
                    val move = request.updateIntentParent
                    service.moveParent(move.id, move.parentId)
                    "moved intent ${move.id} to parent ${move.parentId}" to move.id
                }
                SubmitOpRequest.PayloadCase.ADD_FIELD -> {
                    val af = request.addField
                    val required = if (af.hasRequired()) af.required else false
                    val description = if (af.hasDescription()) af.description else null
                    service.addField(af.intentId, af.fieldName, af.fieldType, required, description)
                    "added field '${af.fieldName}' to intent ${af.intentId}" to af.intentId
                }
                SubmitOpRequest.PayloadCase.SET_FIELD_VALUE -> {
                    val sfv = request.setFieldValue
                    val value: Any = when (sfv.valueCase) {
                        SetFieldValue.ValueCase.STRING_VALUE -> sfv.stringValue
                        SetFieldValue.ValueCase.INT32_VALUE -> sfv.int32Value
                        SetFieldValue.ValueCase.INT64_VALUE -> sfv.int64Value
                        SetFieldValue.ValueCase.FLOAT_VALUE -> sfv.floatValue
                        SetFieldValue.ValueCase.DOUBLE_VALUE -> sfv.doubleValue
                        SetFieldValue.ValueCase.BOOL_VALUE -> sfv.boolValue
                        SetFieldValue.ValueCase.TIMESTAMP_VALUE -> sfv.timestampValue
                        SetFieldValue.ValueCase.INTENT_REF_VALUE -> sfv.intentRefValue
                        SetFieldValue.ValueCase.VALUE_NOT_SET, null ->
                            throw IllegalArgumentException("SetFieldValue has no value set")
                    }
                    service.setFieldValue(sfv.intentId, sfv.fieldName, value)
                    "set field '${sfv.fieldName}' on intent ${sfv.intentId}" to sfv.intentId
                }
                SubmitOpRequest.PayloadCase.PAYLOAD_NOT_SET ->
                    throw IllegalArgumentException("Request has no payload")
            }

            service.writeToFile(fileName)

            SubmitOpResponse.newBuilder()
                .setSuccess(true)
                .setMessage(message)
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

    override suspend fun getIntent(request: GetIntentRequest): GetIntentResponse {
        val intent = service.getById(request.id)

        return if (intent != null) {
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

    override suspend fun getFocalScope(request: GetFocalScopeRequest): GetFocalScopeResponse {
        return try {
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

/**
 * gRPC impl for the Voluntas-native service.
 * Accepts raw Relationships and Literals.
 */
class VoluntasServiceGrpcImpl(
    private val service: VoluntasIntentService,
    private val fileName: String
) : VoluntasServiceGrpcKt.VoluntasServiceCoroutineImplBase() {

    override suspend fun submitRelationship(request: SubmitRelationshipRequest): SubmitRelationshipResponse {
        return try {
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

    override suspend fun submitLiteral(request: SubmitLiteralRequest): SubmitLiteralResponse {
        return try {
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

/**
 * Convert an Intent to its proto representation.
 * Duplicated from IntentServer to avoid coupling the two servers.
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
