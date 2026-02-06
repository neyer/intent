package com.intentevolved.worker

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

/**
 * Claude API client that actually executes tasks using tools.
 */
class ClaudeApiClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514",
    private val workingDir: File = File(".").absoluteFile
) {
    private val httpClient = HttpClient.newHttpClient()
    private val apiUrl = "https://api.anthropic.com/v1/messages"
    private val gson = Gson()

    // Track token usage for cost reporting
    var totalInputTokens: Long = 0
        private set
    var totalOutputTokens: Long = 0
        private set

    companion object {
        fun fromEnvironment(model: String? = null): ClaudeApiClient {
            val apiKey = System.getenv("ANTHROPIC_API_KEY")
                ?: throw IllegalStateException("ANTHROPIC_API_KEY environment variable not set")
            return ClaudeApiClient(apiKey, model ?: "claude-sonnet-4-20250514")
        }
    }

    /**
     * Execute an intent by giving Claude tools to actually perform the work.
     */
    fun executeIntent(intentText: String, context: String = ""): ExecutionResult {
        var toolsUsedCount = 0
        val systemPrompt = """You are an AI assistant that executes software development tasks.
You have access to tools to read files, write files, and run bash commands.

CRITICAL: You MUST use the tools to perform actual work. DO NOT just describe what to do.

Guidelines:
- If the task says "create" or "add" a file, you MUST use write_file to create it
- If the task says "implement" or "write" code, you MUST use write_file with the actual code
- If the task says "style" something, you MUST write the actual CSS code using write_file
- Use read_file to examine existing code before modifying
- Use bash to run commands (compile, test, git, etc.)
- Every response MUST include at least one tool use (read_file, write_file, or bash)
- Be concise in your text responses, but always DO the actual work with tools

Working directory: ${workingDir.absolutePath}"""

        val userMessage = buildString {
            if (context.isNotEmpty()) {
                append("Context:\n$context\n\n")
            }
            append("Task: $intentText\n\n")
            append("You MUST complete this task by using the available tools. ")
            append("If the task mentions creating, adding, implementing, or writing anything, ")
            append("you must use write_file to actually create or modify the file on disk. ")
            append("Do not just respond with explanations - do the actual work with tools.")
        }

        val tools = """[
            {
                "name": "read_file",
                "description": "Read the contents of a file",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "Path to the file to read"}
                    },
                    "required": ["path"]
                }
            },
            {
                "name": "write_file",
                "description": "Write content to a file (creates parent directories if needed)",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "Path to the file to write"},
                        "content": {"type": "string", "description": "Content to write to the file"}
                    },
                    "required": ["path", "content"]
                }
            },
            {
                "name": "bash",
                "description": "Run a bash command and return the output",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string", "description": "The bash command to run"}
                    },
                    "required": ["command"]
                }
            },
            {
                "name": "list_files",
                "description": "List files in a directory",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "Directory path to list"},
                        "pattern": {"type": "string", "description": "Optional glob pattern to filter files"}
                    },
                    "required": ["path"]
                }
            }
        ]"""

        // Agentic loop - keep going until we get end_turn or hit max iterations
        val messages = mutableListOf<String>()
        messages.add("""{"role": "user", "content": ${escapeJson(userMessage)}}""")

        var totalIn = 0L
        var totalOut = 0L
        var finalOutput = ""
        var iterations = 0
        val maxIterations = 20

        while (iterations < maxIterations) {
            iterations++

            val messagesJson = messages.joinToString(",\n")
            val requestBody = """
                {
                    "model": "$model",
                    "max_tokens": 8192,
                    "system": ${escapeJson(systemPrompt)},
                    "tools": $tools,
                    "messages": [$messagesJson]
                }
            """.trimIndent()

            // Debug: print first request
            if (iterations == 1) {
                System.err.println("\n=== First API Request ===")
                System.err.println("System prompt length: ${systemPrompt.length}")
                System.err.println("User message: ${userMessage.take(200)}...")
                System.err.println("Tools count: ${tools.count { it == '{' }}")
            }

            val response = makeRequest(requestBody)
            if (!response.success) {
                return ExecutionResult(false, response.error ?: "Unknown error", totalIn, totalOut)
            }

            val body = response.body ?: ""
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)

            // Extract usage stats
            val usage = jsonResponse.getAsJsonObject("usage")
            totalIn += usage?.get("input_tokens")?.asLong ?: 0
            totalOut += usage?.get("output_tokens")?.asLong ?: 0

            val stopReason = jsonResponse.get("stop_reason")?.asString

            // Extract all content blocks
            val content = jsonResponse.getAsJsonArray("content")
            val toolUses = mutableListOf<ToolUse>()
            val textParts = mutableListOf<String>()

            for (item in content) {
                val obj = item.asJsonObject
                when (obj.get("type")?.asString) {
                    "text" -> {
                        textParts.add(obj.get("text")?.asString ?: "")
                    }
                    "tool_use" -> {
                        val id = obj.get("id")?.asString ?: continue
                        val name = obj.get("name")?.asString ?: continue
                        val input = obj.getAsJsonObject("input")
                        val inputMap = mutableMapOf<String, String>()
                        for ((key, value) in input.entrySet()) {
                            inputMap[key] = value.asString
                        }
                        toolUses.add(ToolUse(id, name, inputMap))
                    }
                }
            }

            val textContent = textParts.joinToString("\n")
            if (textContent.isNotEmpty()) {
                finalOutput = textContent
            }

            // If no tool use, we're done
            if (stopReason != "tool_use" || toolUses.isEmpty()) {
                break
            }

            // Add assistant's response to messages
            val contentJson = gson.toJson(content)
            messages.add("""{"role": "assistant", "content": $contentJson}""")

            // Execute tools and add results
            val toolResults = toolUses.map { tool ->
                toolsUsedCount++
                val result = executeTool(tool.name, tool.input)
                """{"type": "tool_result", "tool_use_id": "${tool.id}", "content": ${escapeJson(result)}}"""
            }
            messages.add("""{"role": "user", "content": [${toolResults.joinToString(",")}]}""")
        }

        totalInputTokens += totalIn
        totalOutputTokens += totalOut

        // Warn if no tools were used
        if (toolsUsedCount == 0) {
            System.err.println("  WARNING: No tools were used! Claude may not have done actual work.")
        }

        return ExecutionResult(
            success = true,
            output = finalOutput.ifEmpty { "Task completed" },
            inputTokens = totalIn,
            outputTokens = totalOut,
            toolsUsed = toolsUsedCount
        )
    }

    private data class ApiResponse(val success: Boolean, val body: String?, val error: String?)

    private fun makeRequest(requestBody: String): ApiResponse {
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
                    val waitTime = (attempt + 1) * 5000L
                    System.err.println("    Rate limited, waiting ${waitTime}ms...")
                    Thread.sleep(waitTime)
                    return@repeat
                }

                if (response.statusCode() != 200) {
                    return ApiResponse(false, null, "API error ${response.statusCode()}: ${response.body()}")
                }

                return ApiResponse(true, response.body(), null)
            } catch (e: Exception) {
                if (attempt < 2) Thread.sleep((attempt + 1) * 1000L)
            }
        }
        return ApiResponse(false, null, "Failed after retries")
    }

    private data class ToolUse(val id: String, val name: String, val input: Map<String, String>)

    private fun executeTool(name: String, input: Map<String, String>): String {
        return try {
            when (name) {
                "read_file" -> {
                    val path = input["path"] ?: return "Error: path required"
                    val file = resolveFile(path)
                    if (!file.exists()) "Error: File not found: $path"
                    else {
                        val content = file.readText()
                        if (content.length > 50000) content.take(50000) + "\n... (truncated)"
                        else content
                    }
                }
                "write_file" -> {
                    val path = input["path"] ?: return "Error: path required"
                    val content = input["content"] ?: return "Error: content required"
                    val file = resolveFile(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "Successfully wrote ${content.length} bytes to $path"
                }
                "bash" -> {
                    val command = input["command"] ?: return "Error: command required"
                    println("    [bash] $command")
                    val process = ProcessBuilder("bash", "-c", command)
                        .directory(workingDir)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (output.length > 20000) {
                        output.take(20000) + "\n... (truncated)\nExit code: $exitCode"
                    } else {
                        output + "\nExit code: $exitCode"
                    }
                }
                "list_files" -> {
                    val path = input["path"] ?: return "Error: path required"
                    val dir = resolveFile(path)
                    if (!dir.exists()) "Error: Directory not found: $path"
                    else if (!dir.isDirectory) "Error: Not a directory: $path"
                    else {
                        val pattern = input["pattern"]
                        val files = dir.listFiles()?.toList() ?: emptyList()
                        val filtered = if (pattern != null) {
                            val regex = pattern.replace("*", ".*").toRegex()
                            files.filter { regex.matches(it.name) }
                        } else files
                        filtered.sortedBy { it.name }
                            .joinToString("\n") { (if (it.isDirectory) "[DIR] " else "") + it.name }
                    }
                }
                else -> "Error: Unknown tool: $name"
            }
        } catch (e: Exception) {
            "Error executing $name: ${e.message}"
        }
    }

    private fun resolveFile(path: String): File {
        val f = File(path)
        return if (f.isAbsolute) f else File(workingDir, path)
    }

    private fun escapeJson(s: String): String {
        return "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

}

/**
 * Intent 27: Structured response with success/failure status and output.
 */
data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val toolsUsed: Int = 0
)
