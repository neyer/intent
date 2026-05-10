package com.apxhard.voluntas.plans

import com.apxhard.voluntas.voluntas.VoluntasIds
import com.apxhard.voluntas.voluntas.buildModule
import java.io.File

/**
 * Generates the "scheduling" module using the Module DSL.
 *
 * Defines a set of meta organizational intents for time-based grouping:
 *
 *   /meta
 *   └── when
 *       ├── today
 *       └── this week
 *
 * These are plain STRING_INTENT_TYPE intents baked into the module stream so
 * they appear automatically when the module is loaded. They live under META_ROOT
 * and are therefore isMeta=true (hidden from the visible intent tree).
 *
 * Intents can be placed under 'today' or 'this week' to express scheduling intent.
 */
fun main() {
    val service = buildModule("scheduling") { }

    val whenId = service.addIntent("when", VoluntasIds.META_ROOT).id()
    service.addIntent("today", whenId)
    service.addIntent("this week", whenId)

    File("modules").mkdirs()
    service.writeToFile("modules/scheduling.pb")
    println("Generated modules/scheduling.pb")
}
