package com.intentevolved.worker

/**
 * Claude Worker - Executes intents using the Claude API.
 *
 * Usage: ClaudeWorker --server <host:port> --intent <intent-id>
 */

// Intent 8: Define required arguments: --server <address:port> and --intent <intent-id>
data class WorkerArgs(
    val serverAddress: String,  // host:port format
    val intentId: Long
)

/**
 * Intent 51: Main entry point for ClaudeWorker.
 * Intent 59: Handle interrupts gracefully.
 */
fun main(args: Array<String>) {
    // Intent 52: Parse command-line arguments
    val workerArgs = parseArgs(args) ?: run {
        printUsage()
        System.exit(1)
        return
    }

    println("Claude Worker starting...")
    println("Connecting to ${workerArgs.serverAddress} to execute intent ${workerArgs.intentId}")

    // Intent 53: Initialize gRPC client and connect
    val grpcClient = try {
        WorkerGrpcClient.connect(workerArgs.serverAddress)
    } catch (e: Exception) {
        System.err.println("Failed to connect to server: ${e.message}")
        System.exit(1)
        return
    }

    // Intent 59: Handle interrupts gracefully
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nShutting down...")
        grpcClient.close()
    })

    try {
        // Intent 54: Initialize Claude API client
        val claudeClient = try {
            ClaudeApiClient.fromEnvironment()
        } catch (e: Exception) {
            System.err.println("Failed to initialize Claude client: ${e.message}")
            System.exit(1)
            return
        }

        val timestampTracker = TimestampTracker(grpcClient)
        val intentFetcher = IntentFetcher(grpcClient)
        val intentExecutor = IntentExecutor(claudeClient, grpcClient, timestampTracker)

        // Intent 55: Fetch the target intent and build ordered list of sub-intents
        println("Fetching intent tree for intent ${workerArgs.intentId}...")
        val allIntents = intentFetcher.fetchIntentTree(workerArgs.intentId)

        // Intent 57: Skip intents that are already marked done=true
        val pendingIntents = intentFetcher.filterIncomplete(allIntents)

        if (pendingIntents.isEmpty()) {
            println("All intents are already complete!")
            return
        }

        println("Found ${pendingIntents.size} pending intents (${allIntents.size - pendingIntents.size} already done)")

        // Build parent context map
        val contextMap = allIntents.associate { it.id to it.text }

        // Intent 56: For each sub-intent in order: mark started, execute with Claude, mark finished
        println("\nExecuting intents...")
        val summary = intentExecutor.executeAll(pendingIntents, contextMap)

        // Intent 58: Print summary of executed intents on completion
        println("\n=== Execution Summary ===")
        println("Completed: ${summary.successCount}")
        println("Failed: ${summary.failCount}")
        println("Total tokens: ${summary.totalInputTokens} input + ${summary.totalOutputTokens} output")

        // Estimate cost (approximate pricing)
        val inputCost = summary.totalInputTokens * 0.003 / 1000  // $3/MTok for Sonnet input
        val outputCost = summary.totalOutputTokens * 0.015 / 1000  // $15/MTok for Sonnet output
        println("Estimated cost: $${String.format("%.4f", inputCost + outputCost)}")

    } finally {
        // Intent 60: Close gRPC connection on exit
        grpcClient.close()
    }
}

// Intent 9: Use manual argument parsing (simple approach, no external dependency)
// Intent 10: Validate that server address is in host:port format
// Intent 11: Validate that intent ID is a positive integer
fun parseArgs(args: Array<String>): WorkerArgs? {
    var serverAddress: String? = null
    var intentId: Long? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--server", "-s" -> {
                if (i + 1 >= args.size) return null
                serverAddress = args[++i]
            }
            "--intent", "-i" -> {
                if (i + 1 >= args.size) return null
                val idStr = args[++i]
                intentId = idStr.toLongOrNull()
                // Intent 11: Validate that intent ID is a positive integer
                if (intentId == null || intentId < 0) {
                    System.err.println("Error: Intent ID must be a positive integer, got: $idStr")
                    return null
                }
            }
            "--help", "-h" -> return null
            else -> {
                System.err.println("Unknown argument: ${args[i]}")
                return null
            }
        }
        i++
    }

    // Intent 10: Validate that server address is in host:port format
    if (serverAddress != null) {
        val parts = serverAddress.split(":")
        if (parts.size != 2 || parts[1].toIntOrNull() == null) {
            System.err.println("Error: Server address must be in host:port format, got: $serverAddress")
            return null
        }
    }

    if (serverAddress == null || intentId == null) {
        return null
    }

    return WorkerArgs(serverAddress, intentId)
}

// Intent 12: Print usage help if arguments are missing or invalid
fun printUsage() {
    println("""
        Claude Worker - Executes intents using the Claude API

        Usage: ClaudeWorker --server <host:port> --intent <intent-id>

        Required arguments:
          --server, -s <host:port>   Address of the intent server (e.g., localhost:50051)
          --intent, -i <id>          ID of the intent to execute

        Optional arguments:
          --help, -h                 Show this help message

        Environment variables:
          ANTHROPIC_API_KEY          API key for Claude API (required)

        Example:
          ClaudeWorker --server localhost:50051 --intent 1
    """.trimIndent())
}
