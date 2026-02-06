package com.intentevolved.com.intentevolved.voluntas

import com.intentevolved.com.intentevolved.CommandResult
import voluntas.v1.Relationship

/**
 * Consumes raw Voluntas Relationships and applies them to the model.
 */
interface VoluntasStreamConsumer {
    fun consume(relationship: Relationship): CommandResult
}
