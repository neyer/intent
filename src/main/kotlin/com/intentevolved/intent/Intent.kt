package com.intentevolved.com.intentevolved.intent

interface Intent {

    fun text(): String

    fun parent(): Intent?

    fun children(): List<Intent>
}
