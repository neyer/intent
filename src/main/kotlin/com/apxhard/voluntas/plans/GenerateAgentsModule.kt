package com.apxhard.voluntas.plans

import com.apxhard.voluntas.voluntas.buildModule
import java.io.File

/**
 * Generates the "agents" module using the Module DSL.
 *
 * Defines the 'prompt' command type used to record every Claude Code prompt
 * as a typed intent in the tree. The hook in .claude/settings.json calls
 * add-prompt-intent.sh on UserPromptSubmit, which invokes the 'agents/add-prompt'
 * macro via gRPC.
 */
fun main() {
    val service = buildModule("agents") {
        command("prompt")
    }

    File("modules").mkdirs()
    service.writeToFile("modules/agents.pb")
    println("Generated modules/agents.pb")
}
