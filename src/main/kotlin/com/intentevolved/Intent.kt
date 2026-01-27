package com.intentevolved.com.intentevolved

interface Intent {

    fun text(): String

    fun id(): Long

    fun createdTimestamp(): Long?

    fun lastUpdatedTimestamp(): Long?

    fun parent(): Intent?

    fun children(): List<Intent>
}