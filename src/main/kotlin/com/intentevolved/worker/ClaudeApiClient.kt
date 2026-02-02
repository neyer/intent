package com.intentevolved.worker

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Intent 22: Claude API client wrapper using Java HTTP client.
 * Intent 21: No external SDK needed - using built-in Java HTTP client.
 */
class ClaudeApiClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514"
) {
    private val httpClient = HttpClient.newHttpClient()
    private val apiUrl = "https://api.anthropic.com/v1/messages"

    // Track token usage for cost reporting
    var totalInputTokens: Long = 0
        private set
    var totalOutputTokens: Long = 0
        private set

    companion object {
        /**
         * Intent 23: Create client from ANTHROPIC_API_KEY environment variable.
         * Intent 25: Support optional model selection.
         */
        fun fromEnvironment(model: String? = null): ClaudeApiClient {
            val apiKey = System.getenv("ANTHROPIC_API_KEY")
                ?: throw IllegalStateException("ANTHROPIC_API_KEY environment variable not set")
            return ClaudeApiClient(apiKey, model ?: "claude-sonnet-4-20250514")
        }
    }

    /**
     * Intent 24: Execute an intent by sending it to Claude.
     * Intent 27: Returns structured response with success/failure and output.
     */
    fun executeIntent(intentText: String, context: String = ""): ExecutionResult {
        val systemPrompt = """You are an AI assistant helping to execute software development tasks.
You will be given a task description and should provide a clear, concise response about how to accomplish it,
or if it's a simple task, just confirm it's done with a brief explanation.
Be direct and technical. Focus on the actual implementation."""

        val userMessage = buildString {
            if (context.isNotEmpty()) {
                append("Context:\n$context\n\n")
            }
            append("Task: $intentText")
        }

        val requestBody = """
            {
                "model": "$model",
                "max_tokens": 4096,
                "system": ${escapeJson(systemPrompt)},
                "messages": [
                    {"role": "user", "content": ${escapeJson(userMessage)}}
                ]
            }
        """.trimIndent()

        // Intent 26: Handle rate limiting and errors with retry
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 429) {
                    // Rate limited - wait and retry
                    val waitTime = (attempt + 1) * 5000L
                    System.err.println("Rate limited, waiting ${waitTime}ms before retry...")
                    Thread.sleep(waitTime)
                    return@repeat
                }

                if (response.statusCode() != 200) {
                    return ExecutionResult(
                        success = false,
                        output = "API error: ${response.statusCode()} - ${response.body()}",
                        inputTokens = 0,
                        outputTokens = 0
                    )
                }

                // Parse response (simple JSON parsing without external library)
                val body = response.body()
                val content = extractJsonField(body, "text") ?: "No content in response"
                val inputTokens = extractJsonNumber(body, "input_tokens")
                val outputTokens = extractJsonNumber(body, "output_tokens")

                totalInputTokens += inputTokens
                totalOutputTokens += outputTokens

                return ExecutionResult(
                    success = true,
                    output = content,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens
                )
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    Thread.sleep((attempt + 1) * 1000L)
                }
            }
        }

        return ExecutionResult(
            success = false,
            output = "Failed after retries: ${lastException?.message}",
            inputTokens = 0,
            outputTokens = 0
        )
    }

    private fun escapeJson(s: String): String {
        return "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    private fun extractJsonField(json: String, field: String): String? {
        // Simple extraction - looks for "field": "value" pattern
        val pattern = """"$field"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
    }

    private fun extractJsonNumber(json: String, field: String): Long {
        val pattern = """"$field"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

/**
 * Intent 27: Structured response with success/failure status and output.
 */
data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val inputTokens: Long,
    val outputTokens: Long
)
