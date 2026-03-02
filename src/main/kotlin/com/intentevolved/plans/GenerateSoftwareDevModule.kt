package com.intentevolved.plans

import com.intentevolved.com.intentevolved.voluntas.buildModule
import java.io.File

/**
 * Generates the "Software Development Module" using the Module DSL.
 *
 * Defines requirement, system, and implementation types with their fields.
 */
fun main() {
    val service = buildModule("Software Development Module") {
        type("requirement") {
            field("name", string, required = true, description = "The name of the requirement")
            field("description", string, description = "Detailed description of the requirement")
        }

        type("system") {
            field("name", string, required = true, description = "The name of the system")
            field("fulfills", intentRef, description = "The requirement this system fulfills")
        }

        type("implementation") {
            field("name", string, required = true, description = "The name of the implementation")
            field("implements", intentRef, description = "The system this implementation implements")
        }
    }

    File("modules").mkdirs()
    service.writeToFile("modules/software_dev.pb")
    println("Generated modules/software_dev.pb")
}
