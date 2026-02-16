package com.intentevolved.plans

import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService

/**
 * Generates the web server implementation plan as an intent stream file.
 */
fun main() {
    val service = VoluntasIntentService.new("Add a web interface to the Intent Server")

    // Level 1: Major components
    val webServer = service.addIntent("Set up web server infrastructure using Ktor", parentId = 0)
    val frontend = service.addIntent("Create HTML/CSS/JS frontend", parentId = 0)
    val api = service.addIntent("Implement API layer for web client communication", parentId = 0)
    val sessionMgmt = service.addIntent("Add session management for per-user focal intent tracking", parentId = 0)
    val integration = service.addIntent("Integrate InputHandler for command processing", parentId = 0)
    val realtime = service.addIntent("Implement real-time updates via WebSocket", parentId = 0)

    // Level 2: Web server infrastructure details
    service.addIntent("Add Ktor dependencies to build.gradle.kts (ktor-server-netty, ktor-server-html-builder, ktor-server-websockets)", parentId = webServer.id())
    service.addIntent("Create WebServer.kt in server package with Ktor application setup", parentId = webServer.id())
    service.addIntent("Configure routing for static files, API endpoints, and WebSocket", parentId = webServer.id())
    service.addIntent("Add command line option to IntentServer to enable web interface on configurable port (default 8080)", parentId = webServer.id())
    service.addIntent("Ensure web server and gRPC server can run concurrently", parentId = webServer.id())

    // Level 2: Frontend details
    service.addIntent("Create index.html with basic layout: command input at top, result line below, intent tree below that", parentId = frontend.id())
    service.addIntent("Style with CSS: monospace font, dark theme to match terminal aesthetic, indentation for tree hierarchy", parentId = frontend.id())
    service.addIntent("Create main.js with: WebSocket connection management, command submission, tree rendering", parentId = frontend.id())
    service.addIntent("Implement intent tree renderer that shows ancestry, focus, and children with proper indentation", parentId = frontend.id())
    service.addIntent("Display field values indented below each intent, matching terminal display format", parentId = frontend.id())
    service.addIntent("Add keyboard handling: Enter to submit command, maintain focus on input box", parentId = frontend.id())

    // Level 2: API layer details
    service.addIntent("Create WebSessionState class to hold: session ID, focal intent, InputHandler instance", parentId = api.id())
    service.addIntent("Implement REST endpoint POST /api/command to submit commands (returns JSON with result and updated tree)", parentId = api.id())
    service.addIntent("Implement REST endpoint GET /api/scope/{intentId} to fetch focal scope as JSON", parentId = api.id())
    service.addIntent("Implement REST endpoint GET /api/intent/{id} to fetch single intent as JSON", parentId = api.id())
    service.addIntent("Create JSON serialization for Intent, FocalScope, and command results", parentId = api.id())

    // Level 2: Session management details
    service.addIntent("Generate unique session ID for each web client connection", parentId = sessionMgmt.id())
    service.addIntent("Store WebSessionState in concurrent map keyed by session ID", parentId = sessionMgmt.id())
    service.addIntent("Initialize new sessions with focal intent = 0 (root)", parentId = sessionMgmt.id())
    service.addIntent("Pass session ID via cookie or WebSocket connection parameter", parentId = sessionMgmt.id())
    service.addIntent("Implement session timeout and cleanup for inactive sessions", parentId = sessionMgmt.id())

    // Level 2: InputHandler integration details
    service.addIntent("Create WebIntentStreamConsumer that wraps IntentServiceImpl and delegates consume() calls", parentId = integration.id())
    service.addIntent("Create WebIntentStateProvider that wraps IntentServiceImpl for getById() and getFocalScope()", parentId = integration.id())
    service.addIntent("Instantiate one InputHandler per session, sharing the underlying IntentServiceImpl", parentId = integration.id())
    service.addIntent("Route web commands through InputHandler.handleCommand() method (extract from handleEnter)", parentId = integration.id())
    service.addIntent("Refactor InputHandler to expose a processCommand(command: String) method that returns result without requiring KeyStroke", parentId = integration.id())

    // Level 2: Real-time updates details
    service.addIntent("Set up WebSocket endpoint at /ws for bidirectional communication", parentId = realtime.id())
    service.addIntent("Send initial focal scope to client on WebSocket connection", parentId = realtime.id())
    service.addIntent("Broadcast tree updates to all connected clients when intents change", parentId = realtime.id())
    service.addIntent("Implement WebSocket message types: COMMAND (client->server), RESULT (server->client), TREE_UPDATE (server->client)", parentId = realtime.id())
    service.addIntent("Handle WebSocket disconnection gracefully, clean up session state", parentId = realtime.id())

    // Write to file
    service.writeToFile("web_server_plan.pb")
    println("Generated web_server_plan.pb")
}
