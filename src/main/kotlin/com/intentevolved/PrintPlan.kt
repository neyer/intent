package com.intentevolved

import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService

fun main(args: Array<String>) {
    val fileName = args.getOrNull(0) ?: "web_server_plan.pb"
    val service = VoluntasIntentService.fromFile(fileName)

    println("=== ${service.getById(0)?.text()} ===\n")

    fun printIntent(id: Long, indent: Int = 0) {
        val intent = service.getById(id) ?: return
        val prefix = "  ".repeat(indent)
        println("$prefix${intent.id()}. ${intent.text()}")

        val scope = service.getFocalScope(id)
        scope.children.filter { !it.isMeta() }.forEach { child ->
            printIntent(child.id(), indent + 1)
        }
    }

    val rootScope = service.getFocalScope(0)
    rootScope.children.filter { !it.isMeta() }.forEach { child ->
        printIntent(child.id(), 0)
    }
}
