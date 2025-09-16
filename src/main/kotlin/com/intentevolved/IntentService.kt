package com.intentevolved.com.intentevolved



// the intent service is a kotlin-layer interface to the intent stream
interface IntentService {
    fun addIntent(text: String): Intent

    fun getById(id: Long): Intent?
}