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
}