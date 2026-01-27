package com.intentevolved.com.intentevolved

import com.intentevolved.CreateIntent
import com.intentevolved.Header
import com.intentevolved.IntentStream
import com.intentevolved.IntentStream.Builder as IntentStreamBuilder
import com.intentevolved.Op
import com.intentevolved.UpdateIntentText
import com.intentevolved.UpdateIntentParent
import com.intentevolved.DeleteIntent
import com.intentevolved.FulfillIntent
import java.time.Instant
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class IntentServiceImpl private constructor(
    private val streamBuilder: IntentStreamBuilder
) : IntentService {
    companion object {
        private fun currentEpochNanos(): Long {
            val now = Instant.now()
            return now.epochSecond * 1_000_000_000L + now.nano.toLong()
        }

        fun fromFile(fileName: String): IntentServiceImpl {
            val streamBuilder = streamBuilderFromFile(fileName)
            val service = IntentServiceImpl(streamBuilder)
            service.replayOps()
            return service
        }

        fun new(rootIntent: String): IntentServiceImpl {
            val streamBuilder = IntentStream.newBuilder()
            streamBuilder.headerBuilder.setRootIntent(rootIntent)
            return IntentServiceImpl(streamBuilder)
        }

        fun streamBuilderFromFile(fileName: String): IntentStreamBuilder {
            val file = File(fileName)
            return if (file.exists()) {
                FileInputStream(file).use { input ->
                    val parsed = IntentStream.parseFrom(input)
                    val builder = parsed.toBuilder()

                    // Ensure all create_intent ops have a timestamp_epoch_nanos set.
                    // For any existing ops without a timestamp, assign the current time.
                    for (i in 0 until builder.opsCount) {
                        val op = builder.getOps(i)
                        if (op.hasCreateIntent() && !op.hasTimestampEpochNanos()) {
                            val updatedOp = op.toBuilder()
                                .setTimestampEpochNanos(currentEpochNanos())
                                .build()
                            builder.setOps(i, updatedOp)
                        }
                    }

                    builder
                }
            } else {
                throw IllegalArgumentException("No such file $fileName")
            }
        }
    }

    private var nextId = 1L
    private val byId: MutableMap<Long, Intent> = mutableMapOf()
    private val childrenById = mutableMapOf<Long, MutableList<Long>>().withDefault { mutableListOf() }

    init {
        // we need to add an 'intent' object for the root intent
        val rootIntentObj = IntentImpl(
            text = streamBuilder.header.rootIntent,
            id = 0,
            parentId = null,
            serviceImpl = this
        )
        byId[0] = rootIntentObj
    }

    private fun replayOps() {
        val stream = streamBuilder.build()
        stream.opsList.forEach { op ->
            when {
                op.hasCreateIntent() -> handleCreateIntent(op.createIntent, op.timestampEpochNanos)
                op.hasUpdateIntent() -> handleUpdateIntent(op.updateIntent, op.timestampEpochNanos)
                op.hasUpdateIntentParent() -> handleUpdateIntentParent(op.updateIntentParent, op.timestampEpochNanos)
                op.hasDeleteIntent() -> handleDeleteIntent(op.deleteIntent)
                op.hasFulfillIntent() -> handleFulfillIntent(op.fulfillIntent)
            }
        }
    }

    private fun handleCreateIntent(create: CreateIntent, timestamp: Long?) {
        val intent = IntentImpl(
            text = create.text,
            id = create.id,
            parentId = if (create.hasParentId()) create.parentId else null,
            serviceImpl = this,
            createdTimestamp = timestamp,
        )
        byId[create.id] = intent
        if (intent.parentId != null) {
            // link this child to its parent
            val childList = childrenById.getValue(intent.parentId)
            childList.add(create.id)
            childrenById[intent.parentId] = childList
        }


        if (create.id >= nextId) {
            nextId = create.id + 1
        }
    }

    private fun handleUpdateIntent(update: UpdateIntentText, timestamp: Long?) {
        val existing = byId[update.id] ?: return
        val updated = IntentImpl(
            text = update.newText,
            id = update.id,
            parentId = (existing as IntentImpl).parentId,
            serviceImpl = this,
            createdTimestamp = existing.createdTimestamp(),
            lastUpdatedTimestamp = timestamp
        )
        byId[update.id] = updated
    }

    private fun handleUpdateIntentParent(update: UpdateIntentParent, timestamp: Long?) {
        val existing = byId[update.id] ?: return
        val oldParentId = (existing as IntentImpl).parentId
        
        // Remove from old parent's children list
        if (oldParentId != null) {
            childrenById[oldParentId]?.remove(update.id)
        }
        
        // Add to new parent's children list
        val newParentId = update.parentId
        val childList = childrenById.getValue(newParentId)
        childList.add(update.id)
        childrenById[newParentId] = childList
        
        // Update the intent with new parent
        val updated = IntentImpl(
            text = existing.text(),
            id = update.id,
            parentId = newParentId,
            createdTimestamp = existing.createdTimestamp(),
            lastUpdatedTimestamp = timestamp,
            serviceImpl = this
        )
        byId[update.id] = updated
    }

    private fun handleDeleteIntent(delete: DeleteIntent) {
        val existing = byId[delete.id]!!
        byId.remove(delete.id)
        if (existing.parent() != null) {
            childrenById[existing.parent()!!.id()]!!.remove(existing.id())
        }
    }

    private fun handleFulfillIntent(fulfill: FulfillIntent) {
        // TODO: Handle fulfillment logic when you implement it
    }

    override fun addIntent(text: String, parentId: Long): Intent {
        val createIntent = CreateIntent.newBuilder()
            .setId(nextId)
            .setText(text)
            .setParentId(parentId)
            .build()

        val timestamp = currentEpochNanos()
        val op = Op.newBuilder()
            .setCreateIntent(createIntent)
            .setTimestampEpochNanos(timestamp)
            .build()

        streamBuilder.addOps(op)
        handleCreateIntent(createIntent, timestamp)

        return byId[createIntent.id]!!
    }

    override fun edit(id: Long, newText: String) {
        byId[id] ?: throw IllegalArgumentException("No intent with id $id")

        val updateIntent = UpdateIntentText.newBuilder()
            .setId(id)
            .setNewText(newText)
            .build()

        val timestamp = currentEpochNanos()

        val op = Op.newBuilder()
            .setUpdateIntent(updateIntent)
            .setTimestampEpochNanos(timestamp)
            .build()

        streamBuilder.addOps(op)
        handleUpdateIntent(updateIntent, timestamp)
    }

    override fun moveParent(id: Long, newParentId: Long) {
        byId[id] ?: throw IllegalArgumentException("No intent with id $id")
        byId[newParentId] ?: throw IllegalArgumentException("No intent with id $newParentId")

        val updateIntentParent = UpdateIntentParent.newBuilder()
            .setId(id)
            .setParentId(newParentId)
            .build()


        val timestamp = currentEpochNanos()
        val op = Op.newBuilder()
            .setUpdateIntentParent(updateIntentParent)
            .setTimestampEpochNanos(timestamp)
            .build()

        streamBuilder.addOps(op)
        handleUpdateIntentParent(updateIntentParent, timestamp)
    }

    override fun getById(id: Long): Intent? = byId[id]

    override fun getFocalScope(id: Long): FocalScope {
        val intent = byId[id]!!
        // Add immediate children
        val childIds = childrenById[id] ?: emptyList()
        val children = (childIds.mapNotNull { childId -> byId[childId] })
        
        return FocalScope(
            focus = intent,
            ancestry = intent.getAncestry(),
            children = children
        )
    }

    override fun getAll(): List<Intent> = byId.values.toList()

    override fun writeToFile(fileName: String) {
        val stream = streamBuilder.build()
        val file = File(fileName)
        FileOutputStream(file).use { output ->
            stream.writeTo(output)
        }
    }

    fun print() {
        println(streamBuilder.build().toString())
    }
}

class IntentImpl(
    private val text: String,
    private val id: Long,
    internal val parentId: Long? = null,
    private val serviceImpl: IntentServiceImpl,
    private val createdTimestamp: Long? = null,
    private val lastUpdatedTimestamp: Long? = null
) : Intent {
    override fun text() = text
    override fun id() = id
    override fun parent() = if (parentId == null) null else serviceImpl.getById(parentId)
    override fun children(): List<Intent> = listOf()
    override fun createdTimestamp() = createdTimestamp
    override fun lastUpdatedTimestamp() = lastUpdatedTimestamp
}