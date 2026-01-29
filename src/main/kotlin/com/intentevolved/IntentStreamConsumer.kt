package com.intentevolved.com.intentevolved

import com.intentevolved.Op

/**
 * Consumes intent stream operations.
 *
 * Implementations are responsible for:
 *  - validating ops
 *  - assigning ids / timestamps where required
 *  - mutating internal state based on the op payload
 *  - returning a user-facing result message and any state updates
 */
interface IntentStreamConsumer {

    /**
     * Apply a single op to the underlying intent stream / model.
     *
     * Implementations may throw IllegalArgumentException for invalid ops
     * (e.g. unknown ids). Callers can translate those into user messages.
     */
    fun consume(op: Op): CommandResult
}

