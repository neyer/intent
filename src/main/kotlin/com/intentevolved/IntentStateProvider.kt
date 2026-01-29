package com.intentevolved.com.intentevolved

/**
 * Read-only access to the current intent state.
 *
 * Used by the input layer (e.g. focus/up commands and UI) to query state
 * without performing mutations. Mutations go through [IntentStreamConsumer].
 */
interface IntentStateProvider {

    /** Returns the intent with the given id, or null if none. */
    fun getById(id: Long): Intent?

    /**
     * Returns the focal scope for the given intent id (focus, ancestry, children).
     * @throws NullPointerException if no intent exists for the id
     */
    fun getFocalScope(id: Long): FocalScope
}
