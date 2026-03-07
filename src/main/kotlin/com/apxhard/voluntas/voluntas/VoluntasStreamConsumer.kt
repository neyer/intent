package com.apxhard.voluntas.voluntas

import com.apxhard.voluntas.CommandResult
import voluntas.v1.Relationship

/**
 * Consumes raw Voluntas Relationships and applies them to the model.
 */
interface VoluntasStreamConsumer {
    fun consume(relationship: Relationship): CommandResult
}
