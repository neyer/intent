package com.apxhard.voluntas.server

import com.apxhard.voluntas.IntentStateProvider
import com.apxhard.voluntas.IntentStreamConsumer
import com.apxhard.voluntas.terminal.CommandExecutor
import com.apxhard.voluntas.terminal.DynamicMacroCommand
import com.apxhard.voluntas.terminal.ProvideUserTokenCommand
import com.apxhard.voluntas.voluntas.AuthGate
import io.ktor.server.websocket.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebSessionState(
    val sessionId: String,
    val executor: CommandExecutor,
    var focalIntent: Long = 0L,
    var username: String? = null,
    var authToken: String? = null
)

class SessionManager(
    private val consumer: IntentStreamConsumer,
    private val stateProvider: IntentStateProvider,
    private val commandAnnotations: List<Pair<String, Long>> = emptyList(),
    private val authGate: AuthGate? = null,
    private val writeFileName: String? = null
) {
    private val sessions = ConcurrentHashMap<String, WebSessionState>()
    private val connections = ConcurrentHashMap<String, WebSocketServerSession>()

    fun getOrCreate(sessionId: String?): WebSessionState {
        if (sessionId != null) {
            sessions[sessionId]?.let { return it }
        }
        val id = sessionId ?: UUID.randomUUID().toString()
        lateinit var session: WebSessionState
        session = WebSessionState(sessionId = id, executor = CommandExecutor(
            consumer = if (authGate != null)
                authGate.authenticatingConsumer(consumer) { session.username to session.authToken }
            else
                consumer,
            stateProvider = stateProvider,
            writeFileName = writeFileName
        ))
        for ((keyword, macroId) in commandAnnotations) {
            if (keyword == "provide-user-token") {
                session.executor.registerCommand(ProvideUserTokenCommand(
                    macroEntityId = macroId,
                    onCredentialsSet = { u, t -> session.username = u; session.authToken = t },
                    onCredentialsCleared = { session.username = null; session.authToken = null }
                ))
            } else {
                session.executor.registerCommand(DynamicMacroCommand(keyword, macroId))
            }
        }
        sessions[id] = session
        return session
    }

    fun get(sessionId: String): WebSessionState? = sessions[sessionId]

    fun registerConnection(sessionId: String, wsSession: WebSocketServerSession) {
        connections[sessionId] = wsSession
    }

    fun removeConnection(sessionId: String) {
        connections.remove(sessionId)
        sessions.remove(sessionId)
    }

    fun connectedSessions(): List<Pair<WebSessionState, WebSocketServerSession>> {
        return connections.mapNotNull { (sessionId, ws) ->
            sessions[sessionId]?.let { it to ws }
        }
    }
}
