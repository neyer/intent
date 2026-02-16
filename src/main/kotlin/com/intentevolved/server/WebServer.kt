package com.intentevolved.com.intentevolved.server

import com.intentevolved.com.intentevolved.FocalScope
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentStateProvider
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import com.intentevolved.com.intentevolved.terminal.CommandExecutor
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class IntentWebServer(
    private val port: Int,
    private val stateProvider: IntentStateProvider,
    private val consumer: IntentStreamConsumer,
    private val stateDispatcher: CoroutineDispatcher,
    private val onMutation: suspend () -> Unit = {}
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            configureWebApp(stateProvider, consumer, stateDispatcher, onMutation)
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
    consumer: IntentStreamConsumer,
    stateDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    onMutation: suspend () -> Unit = {}
) {
    install(ContentNegotiation) {
        gson()
    }
    install(WebSockets)

    val gson = Gson()
    val sessionManager = SessionManager(consumer, stateProvider)

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // [1023] GET /api/intent/{id} - fetch single intent as JSON
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

        // [1022] GET /api/scope/{intentId} - fetch focal scope as JSON
        get("/api/scope/{intentId}") {
            val intentId = call.parameters["intentId"]?.toLongOrNull()
            if (intentId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid intent id"))
                return@get
            }
            try {
                val scope = withContext(stateDispatcher) {
                    stateProvider.getFocalScope(intentId)
                }
                call.respond(scope.toApiMap())
            } catch (e: Exception) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Intent not found: ${e.message}"))
            }
        }

        // [1021] POST /api/command - submit command, returns result + updated tree
        post("/api/command") {
            val body = call.receive<Map<String, Any>>()
            val command = body["command"] as? String
            if (command == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'command' field"))
                return@post
            }

            val sessionId = body["sessionId"] as? String
            val session = sessionManager.getOrCreate(sessionId)

            val (result, newFocalIntent) = withContext(stateDispatcher) {
                val pair = session.executor.execute(command, session.focalIntent)
                onMutation()
                pair
            }
            session.focalIntent = newFocalIntent

            val scope = withContext(stateDispatcher) {
                stateProvider.getFocalScope(session.focalIntent)
            }

            call.respond(buildCommandResponse(session.sessionId, result, session.focalIntent, scope))
        }

        // WebSocket endpoint
        webSocket("/ws") {
            val session = sessionManager.getOrCreate(null)

            // Send initial scope
            val initialScope = withContext(stateDispatcher) {
                stateProvider.getFocalScope(session.focalIntent)
            }
            send(Frame.Text(gson.toJson(buildScopeMessage(initialScope, session.focalIntent, ""))))

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val msg = gson.fromJson(text, Map::class.java)
                    val command = msg["command"] as? String ?: continue

                    val (result, newFocalIntent) = withContext(stateDispatcher) {
                        val pair = session.executor.execute(command, session.focalIntent)
                        onMutation()
                        pair
                    }
                    session.focalIntent = newFocalIntent

                    val scope = withContext(stateDispatcher) {
                        stateProvider.getFocalScope(session.focalIntent)
                    }
                    send(Frame.Text(gson.toJson(buildScopeMessage(scope, session.focalIntent, result))))
                }
            }
        }

        staticResources("/", "static")
    }
}

// --- [1024] JSON serialization ---

private fun buildCommandResponse(
    sessionId: String,
    result: String,
    focalIntent: Long,
    scope: FocalScope
): Map<String, Any?> = mapOf(
    "sessionId" to sessionId,
    "result" to result,
    "focalIntent" to focalIntent,
    "scope" to scope.toApiMap()
)

private fun buildScopeMessage(
    scope: FocalScope,
    focalIntent: Long,
    result: String
): Map<String, Any?> = mapOf(
    "type" to "scope",
    "focalIntent" to focalIntent,
    "result" to result,
    "focus" to scope.focus.toTreeMap(),
    "ancestry" to scope.ancestry.map { it.toTreeMap() },
    "children" to scope.children.map { it.toTreeMap() }
)

fun FocalScope.toApiMap(): Map<String, Any?> = mapOf(
    "focus" to focus.toApiMap(),
    "ancestry" to ancestry.map { it.toApiMap() },
    "children" to children.map { it.toApiMap() }
)

private fun Intent.toTreeMap(): Map<String, Any?> = mapOf(
    "id" to id(),
    "text" to text(),
    "createdTimestamp" to createdTimestamp(),
    "lastUpdatedTimestamp" to lastUpdatedTimestamp(),
    "fieldValues" to fieldValues()
)

fun Intent.toApiMap(): Map<String, Any?> = mapOf(
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
