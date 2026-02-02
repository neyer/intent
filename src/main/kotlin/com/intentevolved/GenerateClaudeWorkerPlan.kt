package com.intentevolved

import com.intentevolved.com.intentevolved.IntentServiceImpl

/**
 * Generates the Claude worker implementation plan as an intent stream file.
 */
fun main() {
    val service = IntentServiceImpl.new("Build a Claude worker that executes intents using the Claude API")

    // Level 1: Major components
    val cliArgs = service.addIntent("Parse command-line arguments for server address and intent ID", parentId = 0)
    val grpcClient = service.addIntent("Set up gRPC client to connect to the intent server", parentId = 0)
    val claudeClient = service.addIntent("Set up Claude API client for LLM interactions", parentId = 0)
    val intentFetcher = service.addIntent("Implement intent and sub-intent fetching logic", parentId = 0)
    val timestampTracker = service.addIntent("Implement timestamp tracking for intent start/finish using AddField and SetFieldValue", parentId = 0)
    val intentExecutor = service.addIntent("Implement intent execution logic using Claude API", parentId = 0)
    val orchestrator = service.addIntent("Implement main orchestration loop to execute sub-intents in order", parentId = 0)

    // Level 2: CLI argument parsing details
    service.addIntent("Define required arguments: --server <address:port> and --intent <intent-id>", parentId = cliArgs.id())
    service.addIntent("Use kotlinx-cli or args4j for argument parsing", parentId = cliArgs.id())
    service.addIntent("Validate that server address is in host:port format", parentId = cliArgs.id())
    service.addIntent("Validate that intent ID is a positive integer", parentId = cliArgs.id())
    service.addIntent("Print usage help if arguments are missing or invalid", parentId = cliArgs.id())

    // Level 2: gRPC client setup details
    service.addIntent("Create ClaudeWorkerGrpcClient class that wraps ManagedChannel", parentId = grpcClient.id())
    service.addIntent("Implement connect(address: String) method that creates channel to server", parentId = grpcClient.id())
    service.addIntent("Create stub for IntentService to call SubmitOp, GetIntent, GetFocalScope", parentId = grpcClient.id())
    service.addIntent("Implement submitOp() wrapper methods for AddField and SetFieldValue operations", parentId = grpcClient.id())
    service.addIntent("Implement getIntent(id: Long) method that returns Intent data", parentId = grpcClient.id())
    service.addIntent("Implement getFocalScope(id: Long) method to retrieve intent with children", parentId = grpcClient.id())
    service.addIntent("Handle gRPC connection errors with appropriate error messages", parentId = grpcClient.id())
    service.addIntent("Implement close() method to cleanly shut down the channel", parentId = grpcClient.id())

    // Level 2: Claude API client setup details
    service.addIntent("Add Anthropic SDK dependency to build.gradle.kts", parentId = claudeClient.id())
    service.addIntent("Create ClaudeApiClient class that wraps the Anthropic SDK", parentId = claudeClient.id())
    service.addIntent("Read API key from ANTHROPIC_API_KEY environment variable", parentId = claudeClient.id())
    service.addIntent("Implement executeIntent(intentText: String, context: String) method that sends prompt to Claude", parentId = claudeClient.id())
    service.addIntent("Configure model selection (claude-3-opus, claude-3-sonnet, etc.) via optional argument", parentId = claudeClient.id())
    service.addIntent("Handle rate limiting and API errors with retry logic", parentId = claudeClient.id())
    service.addIntent("Return structured response with success/failure status and output text", parentId = claudeClient.id())

    // Level 2: Intent fetching logic details
    service.addIntent("Implement fetchIntentTree(intentId: Long) that retrieves the target intent and all descendants", parentId = intentFetcher.id())
    service.addIntent("Use GetFocalScope RPC to get immediate children of an intent", parentId = intentFetcher.id())
    service.addIntent("Recursively fetch children to build complete sub-intent tree", parentId = intentFetcher.id())
    service.addIntent("Flatten tree into ordered list for sequential execution (depth-first or breadth-first)", parentId = intentFetcher.id())
    service.addIntent("Filter out already-completed intents (where done=true)", parentId = intentFetcher.id())
    service.addIntent("Sort sub-intents by their ID to ensure consistent execution order", parentId = intentFetcher.id())

    // Level 2: Timestamp tracking details
    service.addIntent("Create TimestampTracker class to manage start/finish time fields", parentId = timestampTracker.id())
    service.addIntent("Implement markIntentStarted(intentId: Long) method", parentId = timestampTracker.id())
    service.addIntent("In markIntentStarted: call AddField to add 'started_at' field with FIELD_TYPE_TIMESTAMP", parentId = timestampTracker.id())
    service.addIntent("In markIntentStarted: call SetFieldValue to set 'started_at' to current epoch nanos", parentId = timestampTracker.id())
    service.addIntent("Implement markIntentFinished(intentId: Long) method", parentId = timestampTracker.id())
    service.addIntent("In markIntentFinished: call AddField to add 'finished_at' field with FIELD_TYPE_TIMESTAMP", parentId = timestampTracker.id())
    service.addIntent("In markIntentFinished: call SetFieldValue to set 'finished_at' to current epoch nanos", parentId = timestampTracker.id())
    service.addIntent("In markIntentFinished: call AddField to add 'done' field with FIELD_TYPE_BOOL if not present", parentId = timestampTracker.id())
    service.addIntent("In markIntentFinished: call SetFieldValue to set 'done' to true", parentId = timestampTracker.id())
    service.addIntent("Handle case where fields already exist (skip AddField, just SetFieldValue)", parentId = timestampTracker.id())

    // Level 2: Intent execution details
    service.addIntent("Create IntentExecutor class that uses ClaudeApiClient to perform intents", parentId = intentExecutor.id())
    service.addIntent("Build prompt from intent text and any relevant context (parent intent text, sibling results)", parentId = intentExecutor.id())
    service.addIntent("Include system prompt explaining the task context and expected output format", parentId = intentExecutor.id())
    service.addIntent("Send prompt to Claude API and capture response", parentId = intentExecutor.id())
    service.addIntent("Optionally store execution result as a field on the intent (e.g., 'result' field)", parentId = intentExecutor.id())
    service.addIntent("Handle execution failures gracefully and report errors", parentId = intentExecutor.id())
    service.addIntent("Log execution progress to console for visibility", parentId = intentExecutor.id())

    // Level 2: Main orchestration loop details
    service.addIntent("Create ClaudeWorker main class with main() entry point", parentId = orchestrator.id())
    service.addIntent("Parse command-line arguments to get server address and intent ID", parentId = orchestrator.id())
    service.addIntent("Initialize gRPC client and connect to intent server", parentId = orchestrator.id())
    service.addIntent("Initialize Claude API client", parentId = orchestrator.id())
    service.addIntent("Fetch the target intent and build ordered list of sub-intents", parentId = orchestrator.id())
    service.addIntent("For each sub-intent in order: mark started, execute with Claude, mark finished", parentId = orchestrator.id())
    service.addIntent("Skip intents that are already marked done=true", parentId = orchestrator.id())
    service.addIntent("Print summary of executed intents on completion", parentId = orchestrator.id())
    service.addIntent("Handle interrupts (SIGINT) gracefully, marking current intent as incomplete", parentId = orchestrator.id())
    service.addIntent("Close gRPC connection on exit", parentId = orchestrator.id())

    // Write to file
    service.writeToFile("claude_worker.pb")
    println("Generated claude_worker.pb")
}
