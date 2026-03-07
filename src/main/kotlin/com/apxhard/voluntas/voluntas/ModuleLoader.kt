package com.apxhard.voluntas.voluntas

import com.apxhard.voluntas.FieldDetails
import voluntas.v1.Relationship

class ModuleConflictException(message: String) : RuntimeException(message)

data class ModuleManifest(
    val moduleId: String,
    val moduleEntityId: Long,
    val idMapping: Map<Long, Long>,
    val newlyCreated: Set<Long>,
    val alreadyExisted: Set<Long>
)

/**
 * Loads a [VoluntasModule] into a main [VoluntasIntentService], merging types and fields.
 *
 * Types are matched by name. If a matching type already exists with identical fields,
 * it is reused. If it exists with different fields, a [ModuleConflictException] is thrown.
 * If it doesn't exist, it is created with remapped IDs.
 */
class ModuleLoader(private val service: VoluntasIntentService) {

    fun loadModule(module: VoluntasModule): ModuleManifest {
        val entityIdMap = mutableMapOf<Long, Long>()   // module entity ID -> main stream entity ID
        val literalIdMap = mutableMapOf<Long, Long>()   // module literal ID -> main stream literal ID
        val newlyCreated = mutableSetOf<Long>()
        val alreadyExisted = mutableSetOf<Long>()
        val matchedModuleEntityIds = mutableSetOf<Long>()

        // 1. Build literal mapping
        for (op in module.stream.opsList) {
            if (op.hasLiteral()) {
                val lit = op.literal
                val value: Any = when {
                    lit.hasStringVal() -> lit.stringVal
                    lit.hasIntVal()    -> lit.intVal
                    lit.hasDoubleVal() -> lit.doubleVal
                    lit.hasBoolVal()   -> lit.boolVal
                    lit.hasBytesVal()  -> lit.bytesVal.toByteArray()
                    else -> continue
                }
                val mainLitId = service.literalStore.getOrCreate(value)
                literalIdMap[lit.id] = mainLitId
            }
        }

        // 2. Match or create module root (entity 0 in module stream) — placed under META_ROOT
        val existingRoot = service.getAll().find { it.text() == module.rootText }
        if (existingRoot != null) {
            entityIdMap[VoluntasIds.ROOT_INTENT] = existingRoot.id()
            alreadyExisted.add(existingRoot.id())
        } else {
            val newRoot = service.addIntent(module.rootText, VoluntasIds.META_ROOT)
            entityIdMap[VoluntasIds.ROOT_INTENT] = newRoot.id()
            newlyCreated.add(newRoot.id())
        }
        val moduleMainId = entityIdMap[VoluntasIds.ROOT_INTENT]!!

        // 3. Match types by name
        val allMainEntities = service.getAllEntities()
        for ((moduleTypeId, typeDef) in module.typeEntities) {
            val existingType = allMainEntities.values.find { it.text() == typeDef.name }
            if (existingType != null) {
                // Verify fields are identical
                verifyFieldsMatch(typeDef.name, typeDef.fields, existingType.fields())
                entityIdMap[moduleTypeId] = existingType.id()
                alreadyExisted.add(existingType.id())
                matchedModuleEntityIds.add(moduleTypeId)
            }
            // else: will be created during replay
        }

        // 3.5 Match macros by name
        for (op in module.stream.opsList) {
            if (!op.hasRelationship()) continue
            val rel = op.relationship
            val participants = rel.participantsList
            if (participants.isEmpty() || participants[0] != VoluntasIds.DEFINES_MACRO) continue
            val moduleEntityId = rel.id.toLong()
            if (moduleEntityId < VoluntasIds.FIRST_USER_ENTITY) continue

            val nameLitId = if (participants.size >= 2) participants[1] else continue
            val macroName = module.moduleService.literalStore.getString(nameLitId) ?: continue

            val existing = service.getMacroByName(macroName)
            if (existing != null) {
                entityIdMap[moduleEntityId] = existing.first
                alreadyExisted.add(existing.first)
                matchedModuleEntityIds.add(moduleEntityId)
            }
        }

        // 4. Replay module stream ops, skipping bootstrap and already-matched entities
        for (op in module.stream.opsList) {
            if (!op.hasRelationship()) continue
            val rel = op.relationship
            val moduleEntityId = rel.id.toLong()
            val participants = rel.participantsList

            // Skip bootstrap entities (IDs 0-999 in module stream)
            if (moduleEntityId < VoluntasIds.FIRST_USER_ENTITY && moduleEntityId != VoluntasIds.ROOT_INTENT) continue

            // Skip module root — already handled
            if (moduleEntityId == VoluntasIds.ROOT_INTENT) continue

            // Determine the relationship type
            val relType = if (participants.isNotEmpty()) participants[0] else continue

            // Skip ops for already-matched entities
            if (isOpForMatchedEntity(relType, moduleEntityId, participants, matchedModuleEntityIds, entityIdMap)) continue

            // For name node INSTANTIATES: match by path if it already exists in the main stream.
            if (relType == VoluntasIds.INSTANTIATES && participants.size >= 4 &&
                participants[1] == VoluntasIds.NAME_TYPE) {
                val parentNameNodeModuleId = participants[3]
                val mainParentId = entityIdMap[parentNameNodeModuleId]
                    ?: if (parentNameNodeModuleId < VoluntasIds.FIRST_USER_ENTITY) parentNameNodeModuleId else null
                val segment = module.moduleService.literalStore.getString(participants[2])
                if (mainParentId != null && !segment.isNullOrEmpty()) {
                    val parentPath = service.getNameNodePath(mainParentId) ?: ""
                    val path = "$parentPath/$segment"
                    val existingNodeId = service.getNameNodeByPath(path)
                    if (existingNodeId != null) {
                        entityIdMap[moduleEntityId] = existingNodeId
                        alreadyExisted.add(existingNodeId)
                        matchedModuleEntityIds.add(moduleEntityId)
                        continue
                    }
                }
            }

            // For non-name INSTANTIATES where both type and parent are already mapped,
            // look for an existing instance rather than creating a new one.
            if (relType == VoluntasIds.INSTANTIATES && participants.size >= 3 &&
                participants[1] != VoluntasIds.NAME_TYPE) {
                val mappedTypeId = entityIdMap[participants[1]]
                    ?: if (participants[1] < VoluntasIds.FIRST_USER_ENTITY) participants[1] else null
                val mappedParentId = entityIdMap[participants[2]]
                    ?: if (participants[2] < VoluntasIds.FIRST_USER_ENTITY) participants[2] else null
                if (mappedTypeId != null && mappedParentId != null) {
                    val existingInstance = service.getInstanceOfTypeWithParent(mappedTypeId, mappedParentId)
                    if (existingInstance != null) {
                        entityIdMap[moduleEntityId] = existingInstance
                        alreadyExisted.add(existingInstance)
                        matchedModuleEntityIds.add(moduleEntityId)
                        continue
                    }
                }
            }

            // Allocate new ID if needed
            if (!entityIdMap.containsKey(moduleEntityId)) {
                entityIdMap[moduleEntityId] = service.allocateEntityId()
            }
            val mainEntityId = entityIdMap[moduleEntityId]!!

            // Remap participants and emit
            val remappedRel = remapRelationship(rel, mainEntityId, relType, participants,
                entityIdMap, literalIdMap, moduleMainId)

            service.emitOp(remappedRel)
            newlyCreated.add(mainEntityId)
        }

        return ModuleManifest(
            moduleId = module.stream.streamId,
            moduleEntityId = moduleMainId,
            idMapping = entityIdMap.toMap(),
            newlyCreated = newlyCreated,
            alreadyExisted = alreadyExisted
        )
    }

    private fun isOpForMatchedEntity(
        relType: Long,
        moduleEntityId: Long,
        participants: List<Long>,
        matchedModuleEntityIds: Set<Long>,
        entityIdMap: Map<Long, Long>
    ): Boolean {
        // If this entity itself is matched, skip DEFINES_TYPE or DEFINES_MACRO for it
        if ((relType == VoluntasIds.DEFINES_TYPE || relType == VoluntasIds.DEFINES_MACRO) &&
            matchedModuleEntityIds.contains(moduleEntityId)) return true

        // If this is a SETS_FIELD or DEFINES_FIELD targeting a matched entity, skip
        if ((relType == VoluntasIds.SETS_FIELD || relType == VoluntasIds.DEFINES_FIELD) && participants.size >= 2) {
            val targetId = participants[1]
            if (matchedModuleEntityIds.contains(targetId)) return true
        }

        // If this is a DEFINES_MACRO_OP whose macro (participants[1]) is matched, skip
        if (relType == VoluntasIds.DEFINES_MACRO_OP && participants.size >= 2) {
            if (matchedModuleEntityIds.contains(participants[1])) return true
        }

        // If this is a name-node instantiation (INSTANTIATES NAME_TYPE) whose referent is a
        // matched entity (participants[4]), skip — the name node already exists in the main stream.
        if (relType == VoluntasIds.INSTANTIATES && participants.size >= 2 &&
            participants[1] == VoluntasIds.NAME_TYPE && participants.size >= 5) {
            val referentModuleId = participants[4]
            if (matchedModuleEntityIds.contains(referentModuleId)) return true
        }

        return false
    }

    private fun remapRelationship(
        rel: Relationship,
        mainEntityId: Long,
        relType: Long,
        participants: List<Long>,
        entityIdMap: MutableMap<Long, Long>,
        literalIdMap: Map<Long, Long>,
        moduleMainId: Long
    ): Relationship {
        val builder = Relationship.newBuilder().setId(mainEntityId)

        for ((index, pid) in participants.withIndex()) {
            val remapped = when {
                index == 0 -> pid  // relationship type — identity
                VoluntasIds.isLiteral(pid) -> literalIdMap[pid] ?: pid
                pid < VoluntasIds.FIRST_USER_ENTITY && pid != VoluntasIds.ROOT_INTENT -> pid  // bootstrap entity
                entityIdMap.containsKey(pid) -> entityIdMap[pid]!!
                else -> {
                    // Allocate for referenced entity not yet seen
                    val newId = service.allocateEntityId()
                    entityIdMap[pid] = newId
                    newId
                }
            }
            builder.addParticipants(remapped)
        }

        // For DEFINES_TYPE ops, inject module entity as participant[1] if not already there
        if (relType == VoluntasIds.DEFINES_TYPE && participants.size == 1) {
            builder.addParticipants(moduleMainId)
        }

        return builder.build()
    }

    private fun verifyFieldsMatch(
        typeName: String,
        moduleFields: Map<String, FieldDetails>,
        mainFields: Map<String, FieldDetails>
    ) {
        for ((fieldName, moduleDetail) in moduleFields) {
            val mainDetail = mainFields[fieldName]
                ?: throw ModuleConflictException(
                    "Type '$typeName': module defines field '$fieldName' but it does not exist in main stream"
                )
            if (moduleDetail != mainDetail) {
                throw ModuleConflictException(
                    "Type '$typeName': field '$fieldName' differs. " +
                    "Module: $moduleDetail, Main: $mainDetail"
                )
            }
        }
    }
}
