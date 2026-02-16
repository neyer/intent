package com.intentevolved.plans

import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import voluntas.v1.FieldType
import java.io.File

/**
 * Generates the "Software Development Module" — a reusable schema defining
 * requirement, system, and implementation types with their fields.
 */
fun main() {
    val service = VoluntasIntentService.new("Software Development Module")
    val moduleRoot = 0L  // the root intent IS the module entity

    // Define "requirement" type with module root as participant
    val requirementType = service.defineType("requirement", moduleRoot)
    service.addField(requirementType, "name", FieldType.FIELD_TYPE_STRING, required = true, description = "The name of the requirement")
    service.addField(requirementType, "description", FieldType.FIELD_TYPE_STRING, required = false, description = "Detailed description of the requirement")

    // Define "system" type
    val systemType = service.defineType("system", moduleRoot)
    service.addField(systemType, "name", FieldType.FIELD_TYPE_STRING, required = true, description = "The name of the system")
    service.addField(systemType, "fulfills", FieldType.FIELD_TYPE_INTENT_REF, required = false, description = "The requirement this system fulfills")

    // Define "implementation" type
    val implementationType = service.defineType("implementation", moduleRoot)
    service.addField(implementationType, "name", FieldType.FIELD_TYPE_STRING, required = true, description = "The name of the implementation")
    service.addField(implementationType, "implements", FieldType.FIELD_TYPE_INTENT_REF, required = false, description = "The system this implementation implements")

    // Write to modules directory
    File("../modules").mkdirs()
    service.writeToFile("modules/software_dev.pb")
    println("Generated modules/software_dev.pb")
}
