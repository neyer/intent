package com.intentevolved.worker

import com.intentevolved.FieldType
import com.intentevolved.IntentProto

/**
 * Intent 5: Implement timestamp tracking for intent start/finish.
 * Intent 34: TimestampTracker class to manage start/finish time fields.
 */
class TimestampTracker(private val grpcClient: WorkerGrpcClient) {

    /**
     * Intent 35: Mark an intent as started.
     * Intent 36: Add 'started' field with FIELD_TYPE_TIMESTAMP.
     * Intent 37: Set 'started' to current epoch nanos.
     * Intent 43: Handle case where fields already exist.
     */
    fun markIntentStarted(intentId: Long) {
        val timestamp = System.nanoTime()

        // Try to add field (may already exist)
        grpcClient.addField(intentId, "started", FieldType.FIELD_TYPE_TIMESTAMP, "Timestamp when work began")

        // Set the value
        grpcClient.setTimestampField(intentId, "started", timestamp)
    }

    /**
     * Intent 38: Mark an intent as finished.
     * Intent 39: Add 'finished' field with FIELD_TYPE_TIMESTAMP.
     * Intent 40: Set 'finished' to current epoch nanos.
     * Intent 41: Add 'done' field with FIELD_TYPE_BOOL if not present.
     * Intent 42: Set 'done' to true.
     * Intent 43: Handle case where fields already exist.
     */
    fun markIntentFinished(intentId: Long) {
        val timestamp = System.nanoTime()

        // Add and set finished timestamp
        grpcClient.addField(intentId, "finished", FieldType.FIELD_TYPE_TIMESTAMP, "Timestamp when work completed")
        grpcClient.setTimestampField(intentId, "finished", timestamp)

        // Add and set done flag
        grpcClient.addField(intentId, "done", FieldType.FIELD_TYPE_BOOL, "Whether this intent is complete")
        grpcClient.setBoolField(intentId, "done", true)
    }

    /**
     * Check if an intent is already marked as done.
     */
    fun isIntentDone(intent: IntentProto): Boolean {
        return intent.fieldValuesMap["done"]?.boolValue == true
    }

    /**
     * Record token usage on an intent.
     */
    fun recordTokenUsage(intentId: Long, inputTokens: Long, outputTokens: Long) {
        if (inputTokens > 0 || outputTokens > 0) {
            grpcClient.addField(intentId, "input_tokens", FieldType.FIELD_TYPE_INT64, "Claude API input tokens used")
            grpcClient.addField(intentId, "output_tokens", FieldType.FIELD_TYPE_INT64, "Claude API output tokens used")
            grpcClient.setInt64Field(intentId, "input_tokens", inputTokens)
            grpcClient.setInt64Field(intentId, "output_tokens", outputTokens)
        }
    }
}
