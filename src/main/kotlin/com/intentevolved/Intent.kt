package com.intentevolved.com.intentevolved

import com.intentevolved.FieldType

data class FieldDetails(
    val fieldType: FieldType,
    val required: Boolean = false,
    val description: String? = null
)

interface Intent {

    fun text(): String

    fun id(): Long

    fun createdTimestamp(): Long?

    fun lastUpdatedTimestamp(): Long?

    fun parent(): Intent?

    fun children(): List<Intent>

    fun fields(): Map<String, FieldDetails>

    fun fieldValues(): Map<String, Any>

    /**
     * Returns true if this intent represents a meta-operation (like UpdateIntentText, AddField, etc.)
     * Returns false for regular intents created via CreateIntent.
     */
    fun isMeta(): Boolean
}