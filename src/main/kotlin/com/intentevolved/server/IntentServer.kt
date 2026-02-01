package com.intentevolved.server

import com.intentevolved.*
import com.intentevolved.com.intentevolved.FocalScope
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentServiceImpl
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import kotlinx.coroutines.runBlocking

class IntentServer(
    private val port: Int,
    private val service: IntentServiceImpl,
    private val fileName: String
) {
    private val server: Server = ServerBuilder
        .forPort(port)
        .addService(IntentServiceGrpcImpl(service, fileName))
        .addService(ProtoReflectionService.newInstance())
        .build()

    fun start() {
        server.start()
        println("Intent server started on port $port")
        Runtime.getRuntime().addShutdownHook(Thread {
            println("Shutting down Intent server...")
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
            val fileName = args.getOrNull(1) ?: "current.pb"

            val service = try {
                IntentServiceImpl.fromFile(fileName)
            } catch (e: IllegalArgumentException) {
                println("File not found, creating new intent stream")
                IntentServiceImpl.new("Intent Server Root")
            }

            val server = IntentServer(port, service, fileName)
            server.start()
            server.blockUntilShutdown()
        }
    }
}

class IntentServiceGrpcImpl(
    private val service: IntentServiceImpl,
    private val fileName: String
) : IntentServiceGrpcKt.IntentServiceCoroutineImplBase() {

    override suspend fun submitOp(request: SubmitOpRequest): SubmitOpResponse {
        val op = buildOpFromRequest(request)

        return try {
            val result = (service as IntentStreamConsumer).consume(op)
            // Extract the id from the result message (format: "added intent X" or similar)
            val id = extractIdFromResult(result.message, op)

            // Auto-save after each operation
            service.writeToFile(fileName)

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

    private fun buildOpFromRequest(request: SubmitOpRequest): Op {
        val builder = Op.newBuilder()

        when (request.payloadCase) {
            SubmitOpRequest.PayloadCase.CREATE_INTENT ->
                builder.setCreateIntent(request.createIntent)
            SubmitOpRequest.PayloadCase.UPDATE_INTENT ->
                builder.setUpdateIntent(request.updateIntent)
            SubmitOpRequest.PayloadCase.DELETE_INTENT ->
                builder.setDeleteIntent(request.deleteIntent)
            SubmitOpRequest.PayloadCase.FULFILL_INTENT ->
                builder.setFulfillIntent(request.fulfillIntent)
            SubmitOpRequest.PayloadCase.UPDATE_INTENT_PARENT ->
                builder.setUpdateIntentParent(request.updateIntentParent)
            SubmitOpRequest.PayloadCase.ADD_FIELD ->
                builder.setAddField(request.addField)
            SubmitOpRequest.PayloadCase.SET_FIELD_VALUE ->
                builder.setSetFieldValue(request.setFieldValue)
            SubmitOpRequest.PayloadCase.PAYLOAD_NOT_SET ->
                throw IllegalArgumentException("Request has no payload")
        }

        return builder.build()
    }

    private fun extractIdFromResult(message: String, op: Op): Long {
        // Try to extract id from messages like "added intent 5" or "updated intent 3"
        val regex = Regex("intent (\\d+)")
        val match = regex.find(message)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun intentToProto(intent: Intent): IntentProto {
        val builder = IntentProto.newBuilder()
            .setId(intent.id())
            .setText(intent.text())
            .setIsMeta(intent.isMeta())

        intent.parent()?.let { builder.setParentId(it.id()) }
        intent.createdTimestamp()?.let { builder.setCreatedTimestamp(it) }
        intent.lastUpdatedTimestamp()?.let { builder.setLastUpdatedTimestamp(it) }

        // Add fields
        intent.fields().forEach { (name, details) ->
            builder.putFields(name, FieldDetailsProto.newBuilder()
                .setFieldType(details.fieldType)
                .setRequired(details.required)
                .apply { details.description?.let { setDescription(it) } }
                .build())
        }

        // Add field values
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
}
