package com.apxhard.voluntas.server

import com.apxhard.voluntas.IntentStateProvider
import com.apxhard.voluntas.IntentStreamConsumer
import com.apxhard.voluntas.terminal.CommandExecutor
import com.apxhard.voluntas.terminal.DynamicMacroCommand
import io.ktor.server.websocket.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebSessionState(
    val sessionId: String,
    val executor: CommandExecutor,
    var focalIntent: Long = 0L
)

class SessionManager(
    private val consumer: IntentStreamConsumer,
    private val stateProvider: IntentStateProvider,
    private val commandAnnotations: List<Pair<String, Long>> = emptyList()
) {
    private val sessions = ConcurrentHashMap<String, WebSessionState>()
    private val connections = ConcurrentHashMap<String, WebSocketServerSession>()

    fun getOrCreate(sessionId: String?): WebSessionState {
        if (sessionId != null) {
            sessions[sessionId]?.let { return it }
        }
        val id = sessionId ?: UUID.randomUUID().toString()
        val executor = CommandExecutor(consumer, stateProvider, null)
        for ((keyword, macroId) in commandAnnotations) {
            executor.registerCommand(DynamicMacroCommand(keyword, macroId))
        }
        val session = WebSessionState(sessionId = id, executor = executor)
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
