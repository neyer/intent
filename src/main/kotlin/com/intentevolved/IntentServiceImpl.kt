package com.intentevolved.com.intentevolved

import com.intentevolved.IntentStream
import com.intentevolved.Op
import java.io.File
import java.io.FileOutputStream

class IntentServiceImpl : IntentService {

    private val streamBuilder = IntentStream.newBuilder()
    private var nextId = 0L

    private val byId = mutableMapOf<Long, Intent>()

    override fun addIntent(text: String): Intent {

        val op = Op.newBuilder()
        // create the item on the stream
        // we may end up putting this into a separate one
        // probably could do this in a derived class,
        // so the base class just handles the state updates in memory
        // the derived class  also writes out a new stream
        val createBuilder = op.createIntentBuilder
        createBuilder.setText(text)
        createBuilder.setId(nextId)
        op.setCreateIntent(createBuilder.build())
        streamBuilder.addOps(op)

        val thisOne = IntentImpl(
            text=text,
            id =nextId,
            serviceImpl = this
        )
        // store it in the map and update the counter
        byId[nextId] = thisOne
        ++nextId

        return thisOne

    }

    override fun getById(id: Long): Intent? {
        return byId[id]
    }

    override fun edit(id: Long, newText: String) {
        byId[id] ?: throw IllegalArgumentException("No intent with id $id")
        val newOne = IntentImpl(
            text=newText,
            id=id,
            serviceImpl=this
        )
        byId[id] = newOne
    }

    override fun getAll(): List<Intent>  = byId.values.toList()


    fun writeToFile(fileName: String) {
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
    private val parentId: Long? = null,
    private val serviceImpl: IntentServiceImpl
) : Intent {
    override fun text() = text
    override fun id() = id
    override fun parent() = if (parentId == null) null else serviceImpl.getById(parentId)
    override fun children(): List<Intent> = listOf()

}