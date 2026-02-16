package com.intentevolved.com.intentevolved.server

import com.intentevolved.com.intentevolved.IntentStateProvider
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import com.intentevolved.com.intentevolved.terminal.CommandExecutor
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
    private val stateProvider: IntentStateProvider
) {
    private val sessions = ConcurrentHashMap<String, WebSessionState>()
    private val connections = ConcurrentHashMap<String, WebSocketServerSession>()

    fun getOrCreate(sessionId: String?): WebSessionState {
        if (sessionId != null) {
            sessions[sessionId]?.let { return it }
        }
        val id = sessionId ?: UUID.randomUUID().toString()
        val session = WebSessionState(
            sessionId = id,
            executor = CommandExecutor(consumer, stateProvider, null)
        )
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
