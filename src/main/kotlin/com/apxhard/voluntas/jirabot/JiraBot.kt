package com.apxhard.voluntas.jirabot

import com.apxhard.voluntas.worker.WorkerGrpcClient
import com.google.gson.GsonBuilder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val gson = GsonBuilder().setPrettyPrinting().create()
private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun main(args: Array<String>) {
    // Parse flags
    val flags = args.toMutableList()
    val watchMode = flags.remove("--watch")
    val listFields = flags.remove("--list-fields")

    if (flags.firstOrNull() == "--init") {
        writeExampleConfig()
        return
    }

    val configPath = flags.firstOrNull() ?: "jira-bot.json"
    val configFile = File(configPath)
    if (!configFile.exists()) {
        System.err.println("Config file not found: $configPath")
        System.err.println("Run with --init to generate an example config.")
        System.exit(1)
        return
    }

    val config = try {
        gson.fromJson(configFile.readText(), JiraConfig::class.java)
    } catch (e: Exception) {
        System.err.println("Failed to parse $configPath: ${e.message}")
        System.exit(1)
        return
    }

    val errors = config.validate()
    if (errors.isNotEmpty()) {
        errors.forEach { System.err.println("Config error: $it") }
        System.exit(1)
        return
    }

    println("=== Jira Bot ===")
    println("Intent server : ${config.serverAddress}")
    println("Jira project  : ${config.jiraBaseUrl}/projects/${config.jiraProjectKey}")
    println("Max depth     : ${config.maxIssueDepth} (deeper items become comments)")
    println("Skip done     : ${config.skipDone}")
    if (watchMode) println("Watch mode    : polling every ${config.pollIntervalSeconds}s")
    println()

    // Connect to Jira
    val jira = JiraClient(config)
    print("Connecting to Jira...")
    val displayName = try {
        jira.testConnection()
    } catch (e: Exception) {
        println(" FAILED: ${e.message}")
        System.exit(1)
        return
    }
    println(" OK (authenticated as $displayName)")

    if (listFields) {
        println("\nCustom fields in your Jira instance:")
        jira.printCustomFields()
        println("\nSet 'epicLinkField' in your config to the ID of the Epic Link field (e.g. customfield_10014)")
        return
    }

    // Connect to intent server
    val voluntasUser  = config.voluntasUser.ifBlank { System.getenv("VOLUNTAS_USER") ?: "" }
    val voluntasToken = config.voluntasToken.ifBlank { System.getenv("VOLUNTAS_TOKEN") ?: "" }
    print("Connecting to intent server ${config.serverAddress}...")
    val grpc = try {
        WorkerGrpcClient.connect(config.serverAddress, voluntasUser, voluntasToken)
    } catch (e: Exception) {
        println(" FAILED: ${e.message}")
        System.exit(1)
        return
    }
    println(" OK")

    println()

    Runtime.getRuntime().addShutdownHook(Thread {
        grpc.close()
        println("\nShutdown")
    })

    val walker = TreeWalker(grpc)
    val engine = SyncEngine(config, jira, walker, grpc)

    if (watchMode) {
        runWatchLoop(engine, config.pollIntervalSeconds)
    } else {
        val result = runSync(engine)
        println()
        println("=== Done: $result ===")
    }

    grpc.close()
}

private fun runSync(engine: SyncEngine): SyncResult {
    return try {
        engine.syncOnce()
    } catch (e: Exception) {
        System.err.println("Sync failed: ${e.message}")
        throw e
    }
}

private fun runWatchLoop(engine: SyncEngine, intervalSeconds: Int) {
    println("Watching for changes. Press Ctrl+C to stop.\n")
    while (true) {
        val ts = LocalDateTime.now().format(timeFmt)
        print("[$ts] Checking for new intents...")

        val result = try {
            engine.syncOnce()
        } catch (e: Exception) {
            println(" error: ${e.message}")
            sleepSeconds(intervalSeconds)
            continue
        }

        if (result.hasChanges) {
            println(" $result")
        } else {
            println(" none")
        }

        sleepSeconds(intervalSeconds)
    }
}

private fun sleepSeconds(n: Int) {
    try {
        Thread.sleep(n * 1000L)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun writeExampleConfig() {
    val example = JiraConfig(
        serverAddress    = "localhost:50051",
        voluntasUser     = "root",
        voluntasToken    = "root",
        jiraBaseUrl      = "https://your-org.atlassian.net",
        jiraUserEmail    = "you@example.com",
        jiraApiToken     = "your-jira-api-token",
        jiraProjectKey   = "PROJ",
        maxIssueDepth    = 3,
        depthToIssueType = mapOf("1" to "Epic", "2" to "Story", "3" to "Sub-task"),
        parentLinkField  = "parent",
        epicLinkField    = null,   // set to e.g. "customfield_10014" for classic projects
        rootIntentId     = 0,
        skipDone         = false,
        pollIntervalSeconds = 30
    )
    val path = "jira-bot.json.example"
    File(path).writeText(gson.toJson(example))
    println("Wrote example config to $path")
    println()
    println("Fill in your credentials and rename to jira-bot.json, then run:")
    println()
    println("  # One-shot sync:")
    println("  ./gradlew runJiraBot")
    println()
    println("  # Watch mode — re-syncs every pollIntervalSeconds:")
    println("  ./gradlew runJiraBot --args='jira-bot.json --watch'")
    println()
    println("Notes:")
    println("  - Get your API token at: https://id.atlassian.com/manage-profile/security/api-tokens")
    println("  - parentLinkField: \"parent\" for team-managed projects (default).")
    println("    For classic projects linking Stories to Epics, try \"customfield_10014\".")
    println("  - Intents deeper than maxIssueDepth become comments on their nearest")
    println("    ancestor issue. Sync state is stored as fields on each intent in the stream.")
    println("  - pollIntervalSeconds: how often to poll in watch mode (default 30).")
}
