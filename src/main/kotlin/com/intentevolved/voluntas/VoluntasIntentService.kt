package com.intentevolved.com.intentevolved.voluntas

import com.intentevolved.com.intentevolved.*
import voluntas.v1.Literal
import voluntas.v1.Op
import voluntas.v1.Relationship
import voluntas.v1.Stream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant

/**
 * IntentService implementation backed by Voluntas Relationships and Literals.
 *
 * All intent operations are translated into Relationships whose participants[0]
 * identifies the relationship type (DEFINES_TYPE, DEFINES_FIELD, INSTANTIATES,
 * SETS_FIELD).
 */
class VoluntasIntentService private constructor(
    private val streamId: String
) : IntentService, IntentStateProvider, VoluntasStreamConsumer {

    companion object {
        private fun currentEpochNanos(): Long {
            val now = Instant.now()
            return now.epochSecond * 1_000_000_000L + now.nano.toLong()
        }

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

        // 3. Entity 9 = parent field of string intent type
        //    Relationship(id=9, participants=[DEFINES_FIELD, STRING_INTENT_TYPE, "parent", INTENT_REF])
        val parentLit = literalStore.getOrCreate("parent")
        val intentRefLit = literalStore.getOrCreate("INTENT_REF")
        emitRelationship(Relationship.newBuilder()
            .setId(9L)
            .addParticipants(VoluntasIds.DEFINES_FIELD)
            .addParticipants(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(parentLit)
            .addParticipants(intentRefLit)
            .build())

        // 4. Entity 0 = root intent (instantiation of string_intent_type, no parent)
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
            VoluntasIds.DEFINES_TYPE  -> handleDefinesType(rel)
            VoluntasIds.DEFINES_FIELD -> handleDefinesField(rel, timestamp)
            VoluntasIds.INSTANTIATES  -> handleInstantiates(rel, timestamp)
            VoluntasIds.SETS_FIELD    -> handleSetsField(rel, timestamp)
        }
    }

    private fun handleDefinesType(rel: Relationship) {
        // Entity rel.id is now a type. We create a meta-intent for it.
        val id = rel.id.toLong()
        if (!byId.containsKey(id)) {
            byId[id] = IntentImpl(
                text = "Type:${id}",
                id = id,
                parentId = null,
                stateProvider = this,
                isMeta = true
            )
        }
        trackEntityId(id)
    }

    private fun handleDefinesField(rel: Relationship, timestamp: Long?) {
        // participants: [DEFINES_FIELD, typeEntity, nameLit, fieldTypeLit, ...]
        val id = rel.id.toLong()
        if (!byId.containsKey(id)) {
            val participants = rel.participantsList
            val desc = if (participants.size >= 3) {
                val nameLit = literalStore.getString(participants[2])
                "DefinesField:${nameLit ?: participants[2]}"
            } else "DefinesField:$id"
            byId[id] = IntentImpl(
                text = desc,
                id = id,
                parentId = if (participants.size >= 2) participants[1].toLong() else null,
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = true
            )
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
                parentId = parentEntityId?.toLong(),
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = false
            )
            byId[entityId] = intent

            if (parentEntityId != null) {
                val childList = childrenById.getValue(parentEntityId)
                childList.add(entityId)
                childrenById[parentEntityId] = childList
            }
        } else {
            // Generic instantiation → meta intent
            byId[entityId] = IntentImpl(
                text = "Instance:${entityId} of type:${typeId}",
                id = entityId,
                parentId = null,
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
                    parentId = existing.parentId,
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
                val oldParentId = existing.parentId

                // Remove from old parent
                if (oldParentId != null) {
                    childrenById[oldParentId]?.remove(targetId)
                }

                // Add to new parent
                val childList = childrenById.getValue(newParentId)
                childList.add(targetId)
                childrenById[newParentId] = childList

                val updated = IntentImpl(
                    text = existing.text(),
                    id = targetId,
                    parentId = newParentId,
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
                parentId = targetId,
                stateProvider = this,
                createdTimestamp = timestamp,
                isMeta = true
            )
        }
        trackEntityId(relId)
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
            else -> "applied relationship ${relationship.id}"
        }
        return CommandResult(desc)
    }
}
