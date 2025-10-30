package io.materia.tools.api

import io.materia.tools.api.config.configureDatabase
import io.materia.tools.api.config.configureSecurity
import io.materia.tools.api.config.configureSerialization
import io.materia.tools.api.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Materia Tools API Server
 *
 * Provides REST API and WebSocket endpoints for all development tools:
 * - Scene Editor API
 * - Material Editor API
 * - Animation Editor API
 * - Performance Profiler API
 * - Testing Framework API
 * - Documentation API
 * - CI/CD Pipeline API
 */

private val logger = LoggerFactory.getLogger("ToolServer")

fun main() {
    // Configure server startup
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"

    logger.info("Starting Materia Tools API Server...")
    logger.info("Host: $host, Port: $port")

    embeddedServer(
        factory = Netty,
        host = host,
        port = port,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // Configure core functionality
    configureSecurity()
    configureSerialization()
    configureHTTP()
    configureMonitoring()
    configureRouting()
    configureWebSockets()

    // Initialize database
    runBlocking {
        configureDatabase()
    }

    logger.info("Materia Tools API Server started successfully")
}

/**
 * Application lifecycle management
 */
class ToolServerLifecycle {
    private val logger = LoggerFactory.getLogger(ToolServerLifecycle::class.java)

    fun onServerStart() {
        logger.info("Server startup initiated")
        // Initialize tools, load configurations, etc.
    }

    fun onServerStop() {
        logger.info("Server shutdown initiated")
        // Cleanup resources, save state, etc.
    }

    fun onApplicationReady() {
        logger.info("Application ready to serve requests")
        // Final initialization steps
    }
}