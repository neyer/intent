package com.intentevolved.com.intentevolved.voluntas

fun main(args: Array<String>) {
    val fileName = args.getOrNull(0) ?: "voluntas_current.pb"
    val service = VoluntasIntentService.fromFile(fileName)

    println("=== Voluntas Stream: $fileName ===\n")

    val all = service.getAll()
    println("Total non-meta intents: ${all.size}\n")

    for (intent in all) {
        val textDisplay = if (intent.text().isEmpty()) "<BLANK>" else intent.text()
        println("  [${intent.id()}] text='$textDisplay' isMeta=${intent.isMeta()}")
        intent.fields().forEach { (name, details) ->
            println("       field: $name (${details.fieldType})")
        }
        intent.fieldValues().forEach { (name, value) ->
            println("       value: $name = $value")
        }
    }
}
