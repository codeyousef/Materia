package io.materia.telemetry

import io.materia.renderer.backend.BackendId
import io.materia.renderer.backend.DeviceCapabilityReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Persistent record of initialization, device loss, and denial events.
 * Emitted to telemetry pipeline for diagnostics and reproduction.
 *
 * @property logId Unique identifier for this log entry
 * @property eventType Type of diagnostic event
 * @property backendId Backend associated with this event
 * @property capabilityReport Device capability snapshot at event time
 * @property telemetryPayload Complete telemetry payload matching contract schema
 * @property sessionId Anonymized session identifier (SHA-256 hash)
 * @property timestamp ISO-8601 timestamp
 */
@Serializable
data class BackendDiagnosticsLog(
    val logId: String,
    val eventType: DiagnosticEventType,
    val backendId: BackendId?,
    val capabilityReportId: String? = null,
    val telemetryPayload: TelemetryPayload,
    val sessionId: String,
    val timestamp: String
) {
    init {
        require(logId.isNotBlank()) { "Log ID cannot be blank" }
        require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
        require(sessionId.length == 64) { "Session ID must be SHA-256 hash (64 chars), got ${sessionId.length}" }

        // DENIED events must have capability report and limitations
        if (eventType == DiagnosticEventType.DENIED) {
            require(telemetryPayload.limitations?.isNotEmpty() == true) {
                "DENIED events must include limitations in telemetry payload"
            }
        }

        // Performance metrics required for INITIALIZED and PERFORMANCE_DEGRADED
        if (eventType in listOf(
                DiagnosticEventType.INITIALIZED,
                DiagnosticEventType.PERFORMANCE_DEGRADED
            )
        ) {
            require(telemetryPayload.performance != null) {
                "$eventType events must include performance metrics in telemetry payload"
            }
        }
    }

    /**
     * Generate a human-readable summary of this diagnostic log.
     */
    fun summary(): String = buildString {
        appendLine("Log ID: $logId")
        appendLine("Event: $eventType")
        appendLine("Backend: ${backendId ?: "N/A"}")
        appendLine("Session: ${sessionId.take(8)}...")
        appendLine("Timestamp: $timestamp")

        telemetryPayload.limitations?.let { limitations ->
            if (limitations.isNotEmpty()) {
                appendLine("Limitations:")
                limitations.forEach { appendLine("  - $it") }
            }
        }
    }
}

/**
 * Types of diagnostic events.
 */
enum class DiagnosticEventType {
    INITIALIZED,
    DENIED,
    DEVICE_LOST,
    PERFORMANCE_DEGRADED
}

/**
 * Telemetry payload structure matching contract schema.
 */
@Serializable
data class TelemetryPayload(
    val eventId: String,
    val eventType: String,
    val backendId: String?,
    val device: DeviceInfo?,
    val driverVersion: String?,
    val osBuild: String?,
    val featureFlags: Map<String, String>?,
    val performance: PerformanceMetrics?,
    val sessionId: String,
    val callStack: List<String>,
    val limitations: List<String>?,
    val timestamp: String
) {
    init {
        require(callStack.size >= 3) {
            "Call stack must contain at least 3 frames, got ${callStack.size}"
        }
    }
}

/**
 * Device information in telemetry payload.
 */
@Serializable
data class DeviceInfo(
    val vendorId: String,
    val productId: String
)

/**
 * Performance metrics in telemetry payload.
 */
@Serializable
data class PerformanceMetrics(
    val initMs: Long,
    val avgFps: Double,
    val minFps: Double
)
