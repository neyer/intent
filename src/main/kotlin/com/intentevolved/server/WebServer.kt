package com.intentevolved.com.intentevolved.server

import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentStateProvider
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class IntentWebServer(
    private val port: Int,
    private val stateProvider: IntentStateProvider,
    private val stateDispatcher: CoroutineDispatcher
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            configureWebApp(stateProvider, stateDispatcher)
        }.start(wait = false)
        println("Web server started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        println("Web server stopped")
    }
}

fun Application.configureWebApp(
    stateProvider: IntentStateProvider,
    stateDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
) {
    install(ContentNegotiation) {
        gson()
    }
    install(WebSockets)

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/api/intent/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid intent id"))
                return@get
            }
            val intent = withContext(stateDispatcher) {
                stateProvider.getById(id)
            }
            if (intent == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Intent not found"))
                return@get
            }
            call.respond(intent.toApiMap())
        }
    }
}

private fun Intent.toApiMap(): Map<String, Any?> = mapOf(
    "id" to id(),
    "text" to text(),
    "isMeta" to isMeta(),
    "parentId" to parent()?.id(),
    "participantIds" to participantIds(),
    "createdTimestamp" to createdTimestamp(),
    "lastUpdatedTimestamp" to lastUpdatedTimestamp(),
    "fields" to fields().mapValues { (_, details) ->
        mapOf(
            "fieldType" to details.fieldType.name,
            "required" to details.required,
            "description" to details.description
        )
    },
    "fieldValues" to fieldValues()
)
