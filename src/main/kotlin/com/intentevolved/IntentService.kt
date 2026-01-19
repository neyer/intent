package com.intentevolved.com.intentevolved



// the intent service is a kotlin-layer interface to the intent stream
interface IntentService {

    fun addIntent(text: String, parentId: Long): Intent

    fun getById(id: Long): Intent?

    fun edit(id: Long, newText: String)

    fun moveParent(id: Long, newParentId: Long)

    // probably won't be in here for long
    fun getAll(): List<Intent>

    // gets intent objects directly relevant to this one
    // - ancestry path
    // - immediate children
    fun getFocalScope(id: Long): FocalScope

    fun writeToFile(fileName: String)
}