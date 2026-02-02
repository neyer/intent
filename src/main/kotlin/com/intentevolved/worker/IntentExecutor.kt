package com.intentevolved.worker

import com.intentevolved.IntentProto

/**
 * Intent 6: Implement intent execution logic using Claude API.
 * Intent 44: IntentExecutor class that uses ClaudeApiClient to perform intents.
 */
class IntentExecutor(
    private val claudeClient: ClaudeApiClient,
    private val grpcClient: WorkerGrpcClient,
    private val timestampTracker: TimestampTracker
) {

    /**
     * Execute an intent using Claude API.
     * Intent 45: Build prompt from intent text and context.
     * Intent 47: Send prompt to Claude API and capture response.
     */
    fun executeIntent(intent: IntentProto, parentContext: String = ""): ExecutionResult {
        // Intent 50: Log execution progress
        println("  Executing: ${intent.text}")

        // Intent 45: Build context from parent intent text
        val context = if (parentContext.isNotEmpty()) {
            "Parent task: $parentContext"
        } else {
            ""
        }

        // Intent 46: System prompt is handled in ClaudeApiClient
        // Intent 47: Send to Claude and capture response
        val result = claudeClient.executeIntent(intent.text, context)

        // Intent 48: Store execution result as a field
        if (result.success) {
            storeResult(intent.id, result)
        }

        // Intent 49: Handle execution failures
        if (!result.success) {
            System.err.println("  Failed: ${result.output}")
        } else {
            // Intent 50: Log success
            println("  Done (${result.inputTokens}+${result.outputTokens} tokens)")
        }

        return result
    }

    /**
     * Intent 48: Store execution result as a field on the intent.
     */
    private fun storeResult(intentId: Long, result: ExecutionResult) {
        // Store token usage
        timestampTracker.recordTokenUsage(intentId, result.inputTokens, result.outputTokens)
    }

    /**
     * Execute all intents in the given list, marking progress as we go.
     */
    fun executeAll(intents: List<IntentProto>, parentContextMap: Map<Long, String> = emptyMap()): Summary {
        var successCount = 0
        var failCount = 0
        var totalInputTokens = 0L
        var totalOutputTokens = 0L

        for (intent in intents) {
            // Mark as started
            timestampTracker.markIntentStarted(intent.id)

            // Get parent context
            val parentContext = parentContextMap[intent.parentId] ?: ""

            // Execute
            val result = executeIntent(intent, parentContext)

            if (result.success) {
                successCount++
                totalInputTokens += result.inputTokens
                totalOutputTokens += result.outputTokens

                // Mark as finished
                timestampTracker.markIntentFinished(intent.id)
            } else {
                failCount++
                // Don't mark as done if failed
            }
        }

        return Summary(successCount, failCount, totalInputTokens, totalOutputTokens)
    }

    data class Summary(
        val successCount: Int,
        val failCount: Int,
        val totalInputTokens: Long,
        val totalOutputTokens: Long
    )
}
