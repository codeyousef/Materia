package io.materia.renderer.backend

import io.materia.datetime.currentTimeMillis
import io.materia.renderer.backend.FeatureParityMatrix
import io.materia.telemetry.BackendDiagnosticsLog
import io.materia.telemetry.DiagnosticEventType
import io.materia.telemetry.TelemetryPayload
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Backend negotiation orchestrator for detecting, selecting, and initializing rendering backends.
 * Uses expect/actual pattern for platform-specific implementations.
 */
interface BackendNegotiator {
    /**
     * Detect device capabilities and populate telemetry fields.
     * @return DeviceCapabilityReport with complete device info
     */
    suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport

    /**
     * Select the best backend based on device capabilities and available profiles.
     * @return BackendSelection indicating chosen backend or failure
     */
    suspend fun selectBackend(
        report: DeviceCapabilityReport,
        profiles: List<RenderingBackendProfile>
    ): BackendSelection

    /**
     * Initialize the selected backend and create rendering surface.
     * @return BackendHandle on success, throws on failure
     */
    suspend fun initializeBackend(
        selection: BackendSelection,
        surface: SurfaceConfig
    ): BackendHandle
}

/**
 * Result of backend selection process.
 */
@Serializable
data class BackendSelection(
    val backendId: BackendId?,
    val reason: SelectionReason,
    val parityMatrix: FeatureParityMatrix?,
    val errorMessage: String? = null
) {
    val isSuccessful: Boolean get() = reason != SelectionReason.FAILED
}

/**
 * Handle to an initialized backend and its rendering surface.
 */
@Serializable
data class BackendHandle(
    val backendId: BackendId,
    val surfaceDescriptor: RenderSurfaceDescriptor,
    val diagnosticsLogId: String,
    val initializationTimeMs: Long
)

/**
 * Shared orchestration logic for backend negotiation.
 * Platform-specific implementations extend this with actual device probing.
 */
abstract class AbstractBackendNegotiator : BackendNegotiator {

    /**
     * Validate that backend selection meets parity requirements.
     */
    protected fun validateParityRequirements(
        profile: RenderingBackendProfile,
        report: DeviceCapabilityReport
    ): Boolean {
        return profile.supportedFeatures.all { feature ->
            report.featureFlags[feature] == FeatureStatus.SUPPORTED
        }
    }

    /**
     * Create a fail-fast denial selection when no backend qualifies.
     */
    protected fun createDenialSelection(
        report: DeviceCapabilityReport,
        profiles: List<RenderingBackendProfile>
    ): BackendSelection {
        val missingFeatures = RenderingBackendProfile.REQUIRED_PARITY_FEATURES.filter { feature ->
            report.featureFlags[feature] != FeatureStatus.SUPPORTED
        }

        val errorMessage = buildString {
            appendLine("Backend negotiation failed: No qualifying backend found")
            appendLine("Device: ${report.deviceId}")
            appendLine("Driver: ${report.driverVersion}")
            appendLine("OS: ${report.osBuild}")
            if (missingFeatures.isNotEmpty()) {
                appendLine("Missing required features:")
                missingFeatures.forEach { appendLine("  - $it") }
            }
            if (report.limitations.isNotEmpty()) {
                appendLine("Limitations:")
                report.limitations.forEach { appendLine("  - $it") }
            }
        }

        return BackendSelection(
            backendId = null,
            reason = SelectionReason.FAILED,
            parityMatrix = null,
            errorMessage = errorMessage
        )
    }

    /**
     * Select backend from available profiles based on priority and capability match.
     */
    override suspend fun selectBackend(
        report: DeviceCapabilityReport,
        profiles: List<RenderingBackendProfile>
    ): BackendSelection {
        // Sort by fallback priority (lower = higher priority)
        val sortedProfiles = profiles.sortedBy { it.fallbackPriority }

        // Find first profile that meets all requirements
        for (profile in sortedProfiles) {
            if (validateParityRequirements(profile, report)) {
                val isPref = profile.backendId == report.preferredBackend
                return BackendSelection(
                    backendId = profile.backendId,
                    reason = if (isPref) SelectionReason.PREFERRED else SelectionReason.FALLBACK,
                    parityMatrix = null // Will be populated by parity evaluator
                )
            }
        }

        // No qualifying backend found
        return createDenialSelection(report, profiles)
    }

    /**
     * Initialize backend with timeout enforcement.
     */
    override suspend fun initializeBackend(
        selection: BackendSelection,
        surface: SurfaceConfig
    ): BackendHandle {
        if (!selection.isSuccessful) {
            throw BackendInitializationException(
                "Cannot initialize backend: ${selection.errorMessage}"
            )
        }

        val backendId = selection.backendId!!
        val startTime = currentTimeMillis()

        try {
            // Use a reasonable default timeout if not specified
            val timeoutMs = 5000L

            val result = withTimeout(timeoutMs) {
                performPlatformInitialization(backendId, surface)
            }

            val endTime = currentTimeMillis()
            val elapsedMs = endTime - startTime

            return BackendHandle(
                backendId = backendId,
                surfaceDescriptor = result,
                diagnosticsLogId = generateDiagnosticsLogId(),
                initializationTimeMs = elapsedMs
            )
        } catch (e: Exception) {
            val endTime = currentTimeMillis()
            val elapsedMs = endTime - startTime

            throw BackendInitializationException(
                "Backend initialization failed after ${elapsedMs}ms: ${e.message}",
                e
            )
        }
    }

    /**
     * Platform-specific initialization implementation.
     * Must be overridden by each platform's actual implementation.
     */
    protected abstract suspend fun performPlatformInitialization(
        backendId: BackendId,
        surface: SurfaceConfig
    ): RenderSurfaceDescriptor

    /**
     * Generate unique diagnostics log ID.
     */
    protected fun generateDiagnosticsLogId(): String {
        return "diag-${currentTimeMillis()}-${(0..999).random()}"
    }
}

/**
 * Exception thrown when backend initialization fails.
 */
class BackendInitializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Platform factory for creating backend negotiators.
 */
expect fun createBackendNegotiator(): BackendNegotiator
