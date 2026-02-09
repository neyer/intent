package com.intentevolved.com.intentevolved.voluntas

import com.intentevolved.com.intentevolved.*
import voluntas.v1.FieldType
import voluntas.v1.Literal
import voluntas.v1.Op
import voluntas.v1.Relationship
import voluntas.v1.Stream
import voluntas.v1.SetFieldValue
import voluntas.v1.SubmitOpRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant

/**
 * IntentService implementation backed by Voluntas Relationships and Literals.
 *
 * All intent operations are translated into Relationships whose participants[0]
 * identifies the relationship type (DEFINES_TYPE, DEFINES_FIELD, INSTANTIATES,
 * SETS_FIELD, ADDS_PARTICIPANT).
 */
class VoluntasIntentService private constructor(
// Some relationship between existing intents.
    private val streamId: String
) : IntentService, IntentStateProvider, IntentStreamConsumer, VoluntasStreamConsumer {

    companion object {
        private fun currentEpochNanos(): Long {
            val now = Instant.now()
            return now.epochSecond * 1_000_000_000L + now.nano.toLong()
        }

        private val stringToFieldType = mapOf(
            "STRING" to FieldType.FIELD_TYPE_STRING,
            "INT32" to FieldType.FIELD_TYPE_INT32,
            "INT64" to FieldType.FIELD_TYPE_INT64,
            "FLOAT" to FieldType.FIELD_TYPE_FLOAT,
            "DOUBLE" to FieldType.FIELD_TYPE_DOUBLE,
            "BOOL" to FieldType.FIELD_TYPE_BOOL,
            "TIMESTAMP" to FieldType.FIELD_TYPE_TIMESTAMP,
            "INTENT_REF" to FieldType.FIELD_TYPE_INTENT_REF,
        )
        private val fieldTypeToStringMap = stringToFieldType.entries.associate { (k, v) -> v to k }

        fun fieldTypeFromString(s: String?): FieldType =
            stringToFieldType[s] ?: FieldType.FIELD_TYPE_UNSPECIFIED

        fun fieldTypeToString(ft: FieldType): String =
            fieldTypeToStringMap[ft] ?: "UNSPECIFIED"

        /**
         * Create a new service with a root intent, emitting bootstrap relationships.
         */
        fun new(rootIntent: String): VoluntasIntentService {
            val service = VoluntasIntentService(streamId = "intent-stream")
            service.emitBootstrap(rootIntent)
            return service
        }

        /**
         * Load from a serialized Voluntas Stream file, replaying all ops.
         */
        fun fromFile(fileName: String): VoluntasIntentService {
            val file = File(fileName)
            if (!file.exists()) throw IllegalArgumentException("No such file $fileName")
            val stream = FileInputStream(file).use { Stream.parseFrom(it) }
            val service = VoluntasIntentService(streamId = stream.streamId)
            service.replayStream(stream)
            return service
        }
    }

    val literalStore = LiteralStore()
    private val ops = mutableListOf<Op>()
    private val byId = mutableMapOf<Long, Intent>()
    private val childrenById = mutableMapOf<Long, MutableList<Long>>().withDefault { mutableListOf() }
    private var nextEntityId = VoluntasIds.FIRST_USER_ENTITY

    // --- Bootstrap ---

    private fun emitBootstrap(rootIntent: String) {
        // 1. Entity 7 = "string intent" type
        //    Relationship(id=7, participants=[DEFINES_TYPE])
        emitRelationship(Relationship.newBuilder()
            .setId(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(VoluntasIds.DEFINES_TYPE)
            .build())

        // 2. Entity 8 = text field of string intent type
        //    Relationship(id=8, participants=[DEFINES_FIELD, STRING_INTENT_TYPE, "text", STRING])
        val textLit = literalStore.getOrCreate("text")
        val stringTypeLit = literalStore.getOrCreate("STRING")
        emitRelationship(Relationship.newBuilder()
            .setId(8L)
            .addParticipants(VoluntasIds.DEFINES_FIELD)
            .addParticipants(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(textLit)
            .addParticipants(stringTypeLit)
            .build())

        // 3. Entity 0 = root intent (instantiation of string_intent_type, no parent)
        //    Relationship(id=0, participants=[INSTANTIATES, STRING_INTENT_TYPE, rootTextLit])
        val rootTextLit = literalStore.getOrCreate(rootIntent)
        emitRelationship(Relationship.newBuilder()
            .setId(VoluntasIds.ROOT_INTENT)
            .addParticipants(VoluntasIds.INSTANTIATES)
            .addParticipants(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(rootTextLit)
            .build())
    }

    // --- Emit & interpret ---

    private fun emitRelationship(rel: Relationship) {
        val timestamp = currentEpochNanos()
        // Emit literals needed by participants that are literal IDs
        val literalOps = mutableListOf<Op>()
        for (pid in rel.participantsList) {
            if (VoluntasIds.isLiteral(pid)) {
                val lit = literalStore.getById(pid)
                if (lit != null) {
                    literalOps.add(Op.newBuilder()
                        .setTimestamp(timestamp)
                        .setLiteral(lit)
                        .build())
                }
            }
        }
        // Append literal ops then relationship op
        ops.addAll(literalOps)
        val relOp = Op.newBuilder()
            .setTimestamp(timestamp)
            .setRelationship(rel)
            .build()
        ops.add(relOp)

        interpretRelationship(rel, timestamp)
    }

    private fun interpretRelationship(rel: Relationship, timestamp: Long?) {
        val participants = rel.participantsList
        if (participants.isEmpty()) return

        when (participants[0]) {
            VoluntasIds.DEFINES_TYPE      -> handleDefinesType(rel)
            VoluntasIds.DEFINES_FIELD     -> handleDefinesField(rel, timestamp)
            VoluntasIds.INSTANTIATES      -> handleInstantiates(rel, timestamp)
            VoluntasIds.SETS_FIELD        -> handleSetsField(rel, timestamp)
            VoluntasIds.ADDS_PARTICIPANT  -> handleAddsParticipant(rel, timestamp)
        }
    }

    private fun handleDefinesType(rel: Relationship) {
        // Entity rel.id is now a type. We create a meta-intent for it.
        val id = rel.id.toLong()
        if (!byId.containsKey(id)) {
            byId[id] = IntentImpl(
                text = "Type:${id}",
                id = id,
                stateProvider = this,
                isMeta = true
            )
        }
        trackEntityId(id)
    }

    private fun handleDefinesField(rel: Relationship, timestamp: Long?) {
        // participants: [DEFINES_FIELD, targetEntity, nameLit, fieldTypeLit, (requiredLit), (descriptionLit)]
        val id = rel.id.toLong()
        val participants = rel.participantsList

        if (!byId.containsKey(id)) {
            val desc = if (participants.size >= 3) {
                val nameLit = literalStore.getString(participants[2])
                "DefinesField:${nameLit ?: participants[2]}"
            } else "DefinesField:$id"
            val targetId = if (participants.size >= 2) participants[1] else null
            byId[id] = IntentImpl(
                text = desc,
                id = id,
                participantIds = if (targetId != null) mutableListOf(targetId) else mutableListOf(),
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = true
            )
        }

        // Register the field on the target entity
        if (participants.size >= 4) {
            val targetId = participants[1]
            val fieldName = literalStore.getString(participants[2]) ?: return
            val fieldTypeStr = literalStore.getString(participants[3])
            val fieldType = fieldTypeFromString(fieldTypeStr)
            val required = if (participants.size >= 5) {
                val lit = literalStore.getById(participants[4])
                lit?.hasBoolVal() == true && lit.boolVal
            } else false
            val description = if (participants.size >= 6) {
                literalStore.getString(participants[5])
            } else null

            val target = byId[targetId] as? IntentImpl
            target?.addField(fieldName, FieldDetails(fieldType, required, description))
        }

        trackEntityId(id)
    }

    private fun handleInstantiates(rel: Relationship, timestamp: Long?) {
        val participants = rel.participantsList
        val typeId = if (participants.size >= 2) participants[1] else return
        val entityId = rel.id.toLong()

        if (typeId == VoluntasIds.STRING_INTENT_TYPE) {
            // This is a string intent instantiation
            val textLitId = if (participants.size >= 3) participants[2] else null
            val parentEntityId = if (participants.size >= 4) participants[3] else null
            val text = if (textLitId != null) literalStore.getString(textLitId) ?: "" else ""

            val intent = IntentImpl(
                text = text,
                id = entityId,
                participantIds = if (parentEntityId != null) mutableListOf(parentEntityId) else mutableListOf(),
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = false
            )
            byId[entityId] = intent

            if (parentEntityId != null) {
                linkChild(parentEntityId, entityId)
            }
        } else {
            // Generic instantiation → meta intent
            byId[entityId] = IntentImpl(
                text = "Instance:${entityId} of type:${typeId}",
                id = entityId,
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = true
            )
        }
        trackEntityId(entityId)
    }

    private fun handleSetsField(rel: Relationship, timestamp: Long?) {
        // participants: [SETS_FIELD, entityId, fieldNameLit, valueLit]
        val participants = rel.participantsList
        if (participants.size < 4) return

        val targetId = participants[1]
        val fieldNameLitId = participants[2]
        val valueLitId = participants[3]
        val fieldName = literalStore.getString(fieldNameLitId) ?: return
        val existing = byId[targetId] as? IntentImpl ?: return

        when (fieldName) {
            "text" -> {
                val newText = literalStore.getString(valueLitId) ?: return
                val updated = IntentImpl(
                    text = newText,
                    id = targetId,
                    participantIds = existing.participantIds.toMutableList(),
                    stateProvider = this,
                    createdTimestamp = existing.createdTimestamp(),
                    lastUpdatedTimestamp = timestamp,
                    fields = existing.fields().toMutableMap(),
                    values = existing.fieldValues().toMutableMap(),
                    isMeta = existing.isMeta()
                )
                byId[targetId] = updated
            }
            "parent" -> {
                val newParentId = valueLitId // For parent, the value is an entity ID, not a literal
                val oldParentId = existing.participantIds.firstOrNull()

                // Remove from old parent
                if (oldParentId != null) {
                    unlinkChild(oldParentId, targetId)
                }

                // Add to new parent
                linkChild(newParentId, targetId)

                // Replace the first participant (primary parent)
                val newParticipants = existing.participantIds.toMutableList()
                if (newParticipants.isNotEmpty()) {
                    newParticipants[0] = newParentId
                } else {
                    newParticipants.add(newParentId)
                }

                val updated = IntentImpl(
                    text = existing.text(),
                    id = targetId,
                    participantIds = newParticipants,
                    stateProvider = this,
                    createdTimestamp = existing.createdTimestamp(),
                    lastUpdatedTimestamp = timestamp,
                    fields = existing.fields().toMutableMap(),
                    values = existing.fieldValues().toMutableMap(),
                    isMeta = existing.isMeta()
                )
                byId[targetId] = updated
            }
            else -> {
                // Generic field set — resolve value from literal store
                val lit = literalStore.getById(valueLitId)
                if (lit != null) {
                    val value: Any = when {
                        lit.hasStringVal() -> lit.stringVal
                        lit.hasIntVal()    -> lit.intVal
                        lit.hasDoubleVal() -> lit.doubleVal
                        lit.hasBoolVal()   -> lit.boolVal
                        lit.hasBytesVal()  -> lit.bytesVal.toByteArray()
                        else -> return
                    }
                    existing.setFieldValue(fieldName, value)
                }
            }
        }

        // Create meta-intent for the sets_field relationship itself
        val relId = rel.id.toLong()
        if (!byId.containsKey(relId)) {
            byId[relId] = IntentImpl(
                text = "SetsField:$fieldName on $targetId",
                id = relId,
                participantIds = mutableListOf(targetId),
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = true
            )
        }
        trackEntityId(relId)
    }

    /**
     * Handle ADDS_PARTICIPANT relationship.
     * participants: [ADDS_PARTICIPANT, targetEntityId, participantToAddId, (indexLit)]
     */
    private fun handleAddsParticipant(rel: Relationship, timestamp: Long?) {
        val participants = rel.participantsList
        if (participants.size < 3) return

        val targetId = participants[1]
        val participantToAdd = participants[2]
        val existing = byId[targetId] as? IntentImpl ?: return

        val index: Int? = if (participants.size >= 4) {
            val indexLitId = participants[3]
            val lit = literalStore.getById(indexLitId)
            lit?.intVal?.toInt()
        } else null

        existing.addParticipant(participantToAdd, index)

        // Update childrenById: the new participant now has targetId as a child
        linkChild(participantToAdd, targetId)

        // Create meta-intent for the relationship itself
        val relId = rel.id.toLong()
        if (!byId.containsKey(relId)) {
            byId[relId] = IntentImpl(
                text = "AddsParticipant:$participantToAdd to $targetId",
                id = relId,
                participantIds = mutableListOf(targetId),
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = true
            )
        }
        trackEntityId(relId)
    }

    // --- childrenById helpers ---

    private fun linkChild(parentId: Long, childId: Long) {
        val childList = childrenById.getValue(parentId)
        if (!childList.contains(childId)) {
            childList.add(childId)
        }
        childrenById[parentId] = childList
    }

    private fun unlinkChild(parentId: Long, childId: Long) {
        childrenById[parentId]?.remove(childId)
    }

    private fun trackEntityId(id: Long) {
        if (id >= nextEntityId) {
            nextEntityId = id + 1
        }
    }

    // --- Replay ---

    private fun replayStream(stream: Stream) {
        for (op in stream.opsList) {
            when {
                op.hasLiteral() -> literalStore.register(op.literal)
                op.hasRelationship() -> {
                    // Just re-add the op to our list and interpret
                    ops.add(op)
                    val ts = if (op.timestamp != 0L) op.timestamp else null
                    interpretRelationship(op.relationship, ts)
                }
            }
        }
    }

    // --- IntentService implementation ---

    override fun addIntent(text: String, parentId: Long): Intent {
        val id = nextEntityId++
        val textLitId = literalStore.getOrCreate(text)
        val builder = Relationship.newBuilder()
            .setId(id)
            .addParticipants(VoluntasIds.INSTANTIATES)
            .addParticipants(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(textLitId)
            .addParticipants(parentId)

        emitRelationship(builder.build())
        return byId[id]!!
    }

    override fun edit(id: Long, newText: String) {
        byId[id] ?: throw IllegalArgumentException("No intent with id $id")

        val relId = nextEntityId++
        val fieldNameLit = literalStore.getOrCreate("text")
        val newTextLit = literalStore.getOrCreate(newText)

        emitRelationship(Relationship.newBuilder()
            .setId(relId)
            .addParticipants(VoluntasIds.SETS_FIELD)
            .addParticipants(id)
            .addParticipants(fieldNameLit)
            .addParticipants(newTextLit)
            .build())
    }

    override fun moveParent(id: Long, newParentId: Long) {
        byId[id] ?: throw IllegalArgumentException("No intent with id $id")
        byId[newParentId] ?: throw IllegalArgumentException("No intent with id $newParentId")

        val relId = nextEntityId++
        val fieldNameLit = literalStore.getOrCreate("parent")

        // For parent, the value participant is the entity ID directly, not a literal
        emitRelationship(Relationship.newBuilder()
            .setId(relId)
            .addParticipants(VoluntasIds.SETS_FIELD)
            .addParticipants(id)
            .addParticipants(fieldNameLit)
            .addParticipants(newParentId)
            .build())
    }

    fun addField(entityId: Long, fieldName: String, fieldType: FieldType,
                 required: Boolean = false, description: String? = null) {
        byId[entityId] ?: throw IllegalArgumentException("No entity with id $entityId")

        val relId = nextEntityId++
        val fieldNameLit = literalStore.getOrCreate(fieldName)
        val fieldTypeLit = literalStore.getOrCreate(fieldTypeToString(fieldType))

        val builder = Relationship.newBuilder()
            .setId(relId)
            .addParticipants(VoluntasIds.DEFINES_FIELD)
            .addParticipants(entityId)
            .addParticipants(fieldNameLit)
            .addParticipants(fieldTypeLit)

        if (required || description != null) {
            builder.addParticipants(literalStore.getOrCreate(required))
        }
        if (description != null) {
            builder.addParticipants(literalStore.getOrCreate(description))
        }

        emitRelationship(builder.build())
    }

    fun setFieldValue(intentId: Long, fieldName: String, value: Any) {
        byId[intentId] ?: throw IllegalArgumentException("No intent with id $intentId")

        val relId = nextEntityId++
        val fieldNameLit = literalStore.getOrCreate(fieldName)
        val valueLit = literalStore.getOrCreate(value)

        emitRelationship(Relationship.newBuilder()
            .setId(relId)
            .addParticipants(VoluntasIds.SETS_FIELD)
            .addParticipants(intentId)
            .addParticipants(fieldNameLit)
            .addParticipants(valueLit)
            .build())
    }

    /**
     * Add a participant to an intent.
     *
     * @param intentId the intent gaining a new participant
     * @param participantId the ID of the participant to add
     * @param index optional index where to insert; defaults to the end of the list
     */
    fun addParticipant(intentId: Long, participantId: Long, index: Int? = null) {
        byId[intentId] ?: throw IllegalArgumentException("No intent with id $intentId")

        val relId = nextEntityId++
        val builder = Relationship.newBuilder()
            .setId(relId)
            .addParticipants(VoluntasIds.ADDS_PARTICIPANT)
            .addParticipants(intentId)
            .addParticipants(participantId)

        if (index != null) {
            val indexLit = literalStore.getOrCreate(index.toLong())
            builder.addParticipants(indexLit)
        }

        emitRelationship(builder.build())
    }

    override fun getById(id: Long): Intent? = byId[id]

    override fun getFocalScope(id: Long): FocalScope {
        val intent = byId[id]!!
        val childIds = childrenById[id] ?: emptyList()
        val children = childIds.mapNotNull { byId[it] }

        return FocalScope(
            focus = intent,
            ancestry = intent.getAncestry(),
            children = children
        )
    }

    override fun getAll(): List<Intent> = byId.values.filter { !it.isMeta() }

    override fun writeToFile(fileName: String) {
        val streamBuilder = Stream.newBuilder()
            .setStreamId(streamId)
        streamBuilder.addAllOps(ops)
        val file = File(fileName)
        FileOutputStream(file).use { output ->
            streamBuilder.build().writeTo(output)
        }
    }

    // --- IntentStreamConsumer implementation ---

    override fun consume(request: SubmitOpRequest): CommandResult {
        return when (request.payloadCase) {
            SubmitOpRequest.PayloadCase.CREATE_INTENT -> {
                val create = request.createIntent
                val parentId = if (create.hasParentId()) create.parentId else 0L
                val intent = addIntent(create.text, parentId)
                CommandResult("added intent ${intent.id()}", id = intent.id())
            }
            SubmitOpRequest.PayloadCase.UPDATE_INTENT -> {
                val update = request.updateIntent
                edit(update.id, update.newText)
                CommandResult("updated intent ${update.id}")
            }
            SubmitOpRequest.PayloadCase.UPDATE_INTENT_PARENT -> {
                val move = request.updateIntentParent
                moveParent(move.id, move.parentId)
                CommandResult("moved intent ${move.id} to parent ${move.parentId}")
            }
            SubmitOpRequest.PayloadCase.ADD_FIELD -> {
                val af = request.addField
                val required = if (af.hasRequired()) af.required else false
                val description = if (af.hasDescription()) af.description else null
                addField(af.intentId, af.fieldName, af.fieldType, required, description)
                CommandResult("added field '${af.fieldName}' to intent ${af.intentId}")
            }
            SubmitOpRequest.PayloadCase.SET_FIELD_VALUE -> {
                val sfv = request.setFieldValue
                val value: Any = when (sfv.valueCase) {
                    SetFieldValue.ValueCase.STRING_VALUE -> sfv.stringValue
                    SetFieldValue.ValueCase.INT32_VALUE -> sfv.int32Value
                    SetFieldValue.ValueCase.INT64_VALUE -> sfv.int64Value
                    SetFieldValue.ValueCase.FLOAT_VALUE -> sfv.floatValue
                    SetFieldValue.ValueCase.DOUBLE_VALUE -> sfv.doubleValue
                    SetFieldValue.ValueCase.BOOL_VALUE -> sfv.boolValue
                    SetFieldValue.ValueCase.TIMESTAMP_VALUE -> sfv.timestampValue
                    SetFieldValue.ValueCase.INTENT_REF_VALUE -> sfv.intentRefValue
                    SetFieldValue.ValueCase.VALUE_NOT_SET, null ->
                        throw IllegalArgumentException("SetFieldValue has no value set")
                }
                setFieldValue(sfv.intentId, sfv.fieldName, value)
                CommandResult("set field '${sfv.fieldName}' on intent ${sfv.intentId}")
            }
            SubmitOpRequest.PayloadCase.PAYLOAD_NOT_SET ->
                throw IllegalArgumentException("Request has no payload")
        }
    }

    // --- VoluntasStreamConsumer implementation ---

    override fun consume(relationship: Relationship): CommandResult {
        val timestamp = currentEpochNanos()
        val relOp = Op.newBuilder()
            .setTimestamp(timestamp)
            .setRelationship(relationship)
            .build()
        ops.add(relOp)
        interpretRelationship(relationship, timestamp)

        val participants = relationship.participantsList
        val desc = when {
            participants.isNotEmpty() && participants[0] == VoluntasIds.INSTANTIATES ->
                "created entity ${relationship.id}"
            participants.isNotEmpty() && participants[0] == VoluntasIds.SETS_FIELD ->
                "set field on entity ${if (participants.size >= 2) participants[1] else "?"}"
            participants.isNotEmpty() && participants[0] == VoluntasIds.DEFINES_FIELD ->
                "defined field on entity ${if (participants.size >= 2) participants[1] else "?"}"
            participants.isNotEmpty() && participants[0] == VoluntasIds.DEFINES_TYPE ->
                "defined type ${relationship.id}"
            participants.isNotEmpty() && participants[0] == VoluntasIds.ADDS_PARTICIPANT ->
                "added participant to entity ${if (participants.size >= 2) participants[1] else "?"}"
            else -> "applied relationship ${relationship.id}"
        }
        return CommandResult(desc)
    }
}
