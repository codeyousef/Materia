package io.materia.tools.api.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
}

fun Route.webSocketRoutes() {
    webSocket("/ws/tools") {
        toolsWebSocketHandler(this)
    }

    webSocket("/ws/scene-editor") {
        sceneEditorWebSocketHandler(this)
    }

    webSocket("/ws/performance") {
        performanceWebSocketHandler(this)
    }
}

/**
 * WebSocket connection manager for real-time tool communication
 */
object WebSocketManager {
    private val logger = LoggerFactory.getLogger(WebSocketManager::class.java)
    private val connections = ConcurrentHashMap<String, MutableSet<DefaultWebSocketSession>>()

    fun addConnection(channel: String, session: DefaultWebSocketSession) {
        connections.computeIfAbsent(channel) { mutableSetOf() }.add(session)
        logger.info("Added WebSocket connection to channel: $channel")
    }

    fun removeConnection(channel: String, session: DefaultWebSocketSession) {
        connections[channel]?.remove(session)
        logger.info("Removed WebSocket connection from channel: $channel")
    }

    suspend fun broadcast(channel: String, message: String) {
        connections[channel]?.forEach { session ->
            try {
                session.send(message)
            } catch (e: Exception) {
                logger.error("Failed to send message to WebSocket session", e)
                connections[channel]?.remove(session)
            }
        }
    }

    suspend fun broadcastToAll(message: String) {
        connections.values.forEach { sessions ->
            sessions.forEach { session ->
                try {
                    session.send(message)
                } catch (e: Exception) {
                    logger.error("Failed to broadcast message to WebSocket session", e)
                }
            }
        }
    }

    fun getConnectionCount(channel: String): Int {
        return connections[channel]?.size ?: 0
    }
}

private suspend fun toolsWebSocketHandler(session: DefaultWebSocketSession) {
    val logger = LoggerFactory.getLogger("ToolsWebSocket")
    WebSocketManager.addConnection("tools", session)

    try {
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    logger.debug("Received message: $text")

                    // Parse and handle tool messages
                    handleToolMessage(text)

                    // Echo back messages while proper routing is implemented
                    session.send("Echo: $text")
                }
                is Frame.Binary -> {
                    // Handle binary data (e.g., large assets, performance data)
                    logger.debug("Received binary frame of ${frame.data.size} bytes")
                }
                else -> {}
            }
        }
    } catch (e: ClosedReceiveChannelException) {
        logger.info("Tools WebSocket connection closed")
    } catch (e: Throwable) {
        logger.error("Tools WebSocket error", e)
    } finally {
        WebSocketManager.removeConnection("tools", session)
    }
}

private suspend fun sceneEditorWebSocketHandler(session: DefaultWebSocketSession) {
    val logger = LoggerFactory.getLogger("SceneEditorWebSocket")
    WebSocketManager.addConnection("scene-editor", session)

    try {
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    logger.debug("Scene editor message: $text")

                    // Handle scene editor specific messages
                    handleSceneEditorMessage(text, session)
                }
                else -> {}
            }
        }
    } catch (e: ClosedReceiveChannelException) {
        logger.info("Scene editor WebSocket connection closed")
    } catch (e: Throwable) {
        logger.error("Scene editor WebSocket error", e)
    } finally {
        WebSocketManager.removeConnection("scene-editor", session)
    }
}

private suspend fun performanceWebSocketHandler(session: DefaultWebSocketSession) {
    val logger = LoggerFactory.getLogger("PerformanceWebSocket")
    WebSocketManager.addConnection("performance", session)

    try {
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    logger.debug("Performance message: $text")

                    // Handle performance monitoring messages
                    handlePerformanceMessage(text, session)
                }
                else -> {}
            }
        }
    } catch (e: ClosedReceiveChannelException) {
        logger.info("Performance WebSocket connection closed")
    } catch (e: Throwable) {
        logger.error("Performance WebSocket error", e)
    } finally {
        WebSocketManager.removeConnection("performance", session)
    }
}

private suspend fun handleToolMessage(message: String) {
    // Parse JSON message and route to appropriate handler
    // Implementation would depend on message format
}

private suspend fun handleSceneEditorMessage(message: String, session: DefaultWebSocketSession) {
    // Handle scene editor specific operations
    // - Object manipulation
    - Scene updates
    - Real-time collaboration
}

private suspend fun handlePerformanceMessage(message: String, session: DefaultWebSocketSession) {
    // Handle performance monitoring
    // - Real-time metrics
    // - Profiling data
    // - Alerts and notifications
}