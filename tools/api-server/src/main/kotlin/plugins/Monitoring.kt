package io.materia.tools.api.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }

        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            val requestTime = call.attributes.getOrNull(CallLogging.CallKey)?.timestamp
            val duration = requestTime?.let { System.currentTimeMillis() - it } ?: 0

            "$status: $httpMethod ${call.request.path()} in ${duration}ms - $userAgent"
        }
    }

    // Health check endpoint
    routing {
        get("/health") {
            call.respond(HealthStatus(
                status = "healthy",
                timestamp = System.currentTimeMillis(),
                version = environment.config.propertyOrNull("version")?.getString() ?: "unknown",
                uptime = ManagementFactory.getRuntimeMXBean().uptime,
                tools = getToolsStatus()
            ))
        }

        get("/metrics") {
            call.respond(ServerMetrics(
                memoryUsage = getMemoryUsage(),
                cpuUsage = getCpuUsage(),
                activeConnections = getActiveConnections(),
                requestCount = getRequestCount(),
                errorRate = getErrorRate()
            ))
        }
    }
}

@Serializable
data class HealthStatus(
    val status: String,
    val timestamp: Long,
    val version: String,
    val uptime: Long,
    val tools: Map<String, String>
)

@Serializable
data class ServerMetrics(
    val memoryUsage: MemoryInfo,
    val cpuUsage: Double,
    val activeConnections: Int,
    val requestCount: Long,
    val errorRate: Double
)

@Serializable
data class MemoryInfo(
    val used: Long,
    val free: Long,
    val total: Long,
    val max: Long
)

private fun getToolsStatus(): Map<String, String> {
    return mapOf(
        "scene-editor" to "active",
        "material-editor" to "active",
        "animation-editor" to "active",
        "performance-profiler" to "active",
        "testing-framework" to "active",
        "documentation" to "active"
    )
}

private fun getMemoryUsage(): MemoryInfo {
    val runtime = Runtime.getRuntime()
    return MemoryInfo(
        used = runtime.totalMemory() - runtime.freeMemory(),
        free = runtime.freeMemory(),
        total = runtime.totalMemory(),
        max = runtime.maxMemory()
    )
}

private fun getCpuUsage(): Double {
    // Returns simulated CPU usage for demo purposes
    return kotlin.random.Random.nextDouble(0.0, 100.0)
}

private fun getActiveConnections(): Int {
    // Would track actual WebSocket connections
    return 0
}

private fun getRequestCount(): Long {
    // Would track actual request metrics
    return 0L
}

private fun getErrorRate(): Double {
    // Would calculate actual error rate
    return 0.0
}

import java.lang.management.ManagementFactory