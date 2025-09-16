package com.intentevolved.com.intentevolved

interface Intent {

    fun text(): String

    fun id(): Long

    fun parent(): Intent?

    fun children(): List<Intent>
}