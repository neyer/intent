package com.intentevolved.com.intentevolved.voluntas

import com.intentevolved.com.intentevolved.FieldDetails
import voluntas.v1.Stream
import java.io.File
import java.io.FileInputStream

/**
 * Represents a module — a reusable schema definition packaged as a Voluntas stream.
 *
 * A module defines types, fields, and relationships that can be merged into a main stream.
 * The module's root intent (entity 0) serves as the module's identity.
 */
class VoluntasModule(val stream: Stream) {

    /** The module's internal replayed state. */
    internal val moduleService: VoluntasIntentService = VoluntasIntentService.fromStream(stream)

    /** The module's root intent text (entity 0 in the module stream). */
    val rootText: String = moduleService.getById(VoluntasIds.ROOT_INTENT)?.text()
        ?: throw IllegalStateException("Module stream has no root intent (entity 0)")

    /** Type definitions this module declares, keyed by module-local entity ID. */
    internal val typeEntities: Map<Long, ModuleTypeDefinition> = extractTypeDefinitions()

    data class ModuleTypeDefinition(
        val moduleEntityId: Long,
        val name: String,
        val fields: Map<String, FieldDetails>
    )

    private fun extractTypeDefinitions(): Map<Long, ModuleTypeDefinition> {
        val result = mutableMapOf<Long, ModuleTypeDefinition>()
        for ((id, entity) in moduleService.getAllEntities()) {
            if (!entity.isMeta()) continue
            if (id < VoluntasIds.FIRST_USER_ENTITY) continue

            val text = entity.text()
            // Skip meta entities that are field definitions, sets-field ops, or adds-participant ops
            if (text.startsWith("DefinesField:") ||
                text.startsWith("SetsField:") ||
                text.startsWith("AddsParticipant:") ||
                text.startsWith("Instance:")) continue

            // This is a type entity — collect its field definitions
            result[id] = ModuleTypeDefinition(
                moduleEntityId = id,
                name = text,
                fields = entity.fields().toMap()
            )
        }
        return result
    }

    companion object {
        fun fromFile(fileName: String): VoluntasModule {
            val file = File(fileName)
            if (!file.exists()) throw IllegalArgumentException("No such module file: $fileName")
            val stream = FileInputStream(file).use { Stream.parseFrom(it) }
            return VoluntasModule(stream)
        }
    }
}
