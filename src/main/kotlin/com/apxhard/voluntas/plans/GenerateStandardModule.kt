package com.apxhard.voluntas.plans

import com.apxhard.voluntas.voluntas.buildModule
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

        // "undelete" — sets deleted=false on the focused intent
        mutationCommand("undelete") {
            defineField("deleted", bool)
            setField("deleted", false)
        }

        // Builtin commands (hardcoded in CommandExecutor) documented in the intent tree
        builtinCommand("add", "text,id")
        builtinCommand("focus", "id")
        builtinCommand("move", "id")
        builtinCommand("do", "id")
        builtinCommand("add-parent", "id")
        builtinCommand("remove-parent", "id")
        builtinCommands("up", "update", "write", "import", "delete", "write-no-garbage")
    }

    File("modules").mkdirs()
    service.writeToFile("modules/standard.pb")
    println("Generated modules/standard.pb")
}
