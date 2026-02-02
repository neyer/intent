package com.intentevolved.worker

import com.intentevolved.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import java.util.concurrent.TimeUnit

/**
 * Intent 13: gRPC client wrapper for the Intent service.
 */
class WorkerGrpcClient private constructor(
    private val channel: ManagedChannel,
    private val stub: IntentServiceGrpc.IntentServiceBlockingStub
) {
    companion object {
        /**
         * Intent 14: Connect to the intent server at the given address.
         */
        fun connect(address: String): WorkerGrpcClient {
            val parts = address.split(":")
            val host = parts[0]
            val port = parts[1].toInt()

            val channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build()

            // Intent 15: Create stub for IntentService
            val stub = IntentServiceGrpc.newBlockingStub(channel)

            return WorkerGrpcClient(channel, stub)
        }
    }

    /**
     * Intent 17: Get an intent by its ID.
     */
    fun getIntent(id: Long): IntentProto? {
        return try {
            val response = stub.getIntent(
                GetIntentRequest.newBuilder().setId(id).build()
            )
            if (response.found) response.intent else null
        } catch (e: StatusException) {
            // Intent 19: Handle gRPC connection errors
            System.err.println("gRPC error getting intent $id: ${e.status}")
            null
        }
    }

    /**
     * Intent 18: Get focal scope for an intent (intent + ancestors + children).
     */
    fun getFocalScope(id: Long): GetFocalScopeResponse? {
        return try {
            val response = stub.getFocalScope(
                GetFocalScopeRequest.newBuilder().setId(id).build()
            )
            if (response.found) response else null
        } catch (e: StatusException) {
            // Intent 19: Handle gRPC connection errors
            System.err.println("gRPC error getting focal scope for $id: ${e.status}")
            null
        }
    }

    /**
     * Intent 16: Submit an AddField operation.
     */
    fun addField(intentId: Long, fieldName: String, fieldType: FieldType, description: String? = null): SubmitOpResponse {
        val addField = AddField.newBuilder()
            .setIntentId(intentId)
            .setFieldName(fieldName)
            .setFieldType(fieldType)
        if (description != null) {
            addField.setDescription(description)
        }

        val request = SubmitOpRequest.newBuilder()
            .setAddField(addField)
            .build()

        return try {
            stub.submitOp(request)
        } catch (e: StatusException) {
            System.err.println("gRPC error adding field: ${e.status}")
            SubmitOpResponse.newBuilder().setSuccess(false).setMessage(e.message ?: "Unknown error").build()
        }
    }

    /**
     * Intent 16: Submit a SetFieldValue operation for timestamp.
     */
    fun setTimestampField(intentId: Long, fieldName: String, timestampNanos: Long): SubmitOpResponse {
        val request = SubmitOpRequest.newBuilder()
            .setSetFieldValue(
                SetFieldValue.newBuilder()
                    .setIntentId(intentId)
                    .setFieldName(fieldName)
                    .setTimestampValue(timestampNanos)
            )
            .build()

        return try {
            stub.submitOp(request)
        } catch (e: StatusException) {
            System.err.println("gRPC error setting timestamp field: ${e.status}")
            SubmitOpResponse.newBuilder().setSuccess(false).setMessage(e.message ?: "Unknown error").build()
        }
    }

    /**
     * Intent 16: Submit a SetFieldValue operation for boolean.
     */
    fun setBoolField(intentId: Long, fieldName: String, value: Boolean): SubmitOpResponse {
        val request = SubmitOpRequest.newBuilder()
            .setSetFieldValue(
                SetFieldValue.newBuilder()
                    .setIntentId(intentId)
                    .setFieldName(fieldName)
                    .setBoolValue(value)
            )
            .build()

        return try {
            stub.submitOp(request)
        } catch (e: StatusException) {
            System.err.println("gRPC error setting bool field: ${e.status}")
            SubmitOpResponse.newBuilder().setSuccess(false).setMessage(e.message ?: "Unknown error").build()
        }
    }

    /**
     * Intent 16: Submit a SetFieldValue operation for int64.
     */
    fun setInt64Field(intentId: Long, fieldName: String, value: Long): SubmitOpResponse {
        val request = SubmitOpRequest.newBuilder()
            .setSetFieldValue(
                SetFieldValue.newBuilder()
                    .setIntentId(intentId)
                    .setFieldName(fieldName)
                    .setInt64Value(value)
            )
            .build()

        return try {
            stub.submitOp(request)
        } catch (e: StatusException) {
            System.err.println("gRPC error setting int64 field: ${e.status}")
            SubmitOpResponse.newBuilder().setSuccess(false).setMessage(e.message ?: "Unknown error").build()
        }
    }

    /**
     * Intent 16: Submit a SetFieldValue operation for string.
     */
    fun setStringField(intentId: Long, fieldName: String, value: String): SubmitOpResponse {
        val request = SubmitOpRequest.newBuilder()
            .setSetFieldValue(
                SetFieldValue.newBuilder()
                    .setIntentId(intentId)
                    .setFieldName(fieldName)
                    .setStringValue(value)
            )
            .build()

        return try {
            stub.submitOp(request)
        } catch (e: StatusException) {
            System.err.println("gRPC error setting string field: ${e.status}")
            SubmitOpResponse.newBuilder().setSuccess(false).setMessage(e.message ?: "Unknown error").build()
        }
    }

    /**
     * Intent 20: Close the gRPC channel cleanly.
     */
    fun close() {
        channel.shutdown()
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow()
            }
        } catch (e: InterruptedException) {
            channel.shutdownNow()
        }
    }
}
