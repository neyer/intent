package com.intentevolved.com.intentevolved

/**
 * Result wrapper for commands / ops flowing from the input handler to the
 * intent stream consumer. Carries a user-facing message and an optional
 * new focal intent id.
 */
data class CommandResult(
    val message: String,
    val newFocalIntent: Long? = null // null means no change
)

