package com.intentevolved.plans

import com.intentevolved.com.intentevolved.voluntas.buildModule
import java.io.File

/**
 * Generates the "standard" module using the Module DSL.
 *
 * Run this once to regenerate modules/standard.pb whenever the standard module changes.
 */
fun main() {
    val service = buildModule("standard") {
        // "note <text>" — creates a visible freeform note under the current focus
        command("note")

        // Builtin commands (hardcoded in CommandExecutor) documented in the intent tree
        builtinCommands("add", "focus", "up", "update", "move", "do", "write", "import")
    }

    File("modules").mkdirs()
    service.writeToFile("modules/standard.pb")
    println("Generated modules/standard.pb")
}
