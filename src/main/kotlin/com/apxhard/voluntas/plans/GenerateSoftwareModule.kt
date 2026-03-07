package com.apxhard.voluntas.plans

import com.apxhard.voluntas.voluntas.buildModule
import java.io.File

/**
 * Generates the "software" module using the Module DSL.
 *
 * Defines requirement, system, and implementation as terminal commands so they
 * appear in the visible intent tree and can be created from the terminal.
 * The requirement type also carries token-count fields written by the Claude worker.
 *
 * Workflow:
 *   requirement "Add token tracking"   → creates a top-level requirement intent
 *   focus <id>
 *   system "Token storage"             → child system intent under the requirement
 *   focus <id>
 *   implementation "Add fields to..."  → child implementation under the system
 */
fun main() {
    val service = buildModule("software") {
        command("requirement") {
            field("input_tokens", long, description = "Total Claude API input tokens used")
            field("output_tokens", long, description = "Total Claude API output tokens used")
        }

        command("system") {
            field("fulfills", intentRef, description = "The requirement this system addresses")
        }

        command("implementation") {
            field("implements", intentRef, description = "The system this implementation realizes")
        }

        command("file")
        command("class")
        command("method")
    }

    File("modules").mkdirs()
    service.writeToFile("modules/software.pb")
    println("Generated modules/software.pb")
}
