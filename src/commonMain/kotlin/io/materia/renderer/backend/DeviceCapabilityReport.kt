package io.materia.renderer.backend

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Runtime snapshot of detected GPU/device capabilities.
 * Generated during backend negotiation to determine backend compatibility.
 *
 * @property deviceId Vendor + product identifier
 * @property driverVersion Driver version string
 * @property osBuild Operating system version
 * @property featureFlags Map of features to their support status
 * @property preferredBackend Recommended backend based on capabilities
 * @property limitations Human-readable list of capability blockers
 * @property timestamp When this report was generated
 */
@Serializable
data class DeviceCapabilityReport(
    val deviceId: String,
    val driverVersion: String,
    val osBuild: String,
    val featureFlags: Map<BackendFeature, FeatureStatus>,
    val preferredBackend: BackendId?,
    val limitations: List<String> = emptyList(),
    val timestamp: String
) {
    init {
        require(deviceId.isNotBlank()) { "Device ID cannot be blank" }
        require(featureFlags.isNotEmpty()) { "Feature flags cannot be empty" }

        // Validate preferred backend only if all required features are supported
        if (preferredBackend != null) {
            val allRequiredSupported =
                RenderingBackendProfile.REQUIRED_PARITY_FEATURES.all { feature ->
                    featureFlags[feature] == FeatureStatus.SUPPORTED
                }
            require(allRequiredSupported) {
                "Preferred backend set but not all required features are SUPPORTED. " +
                        "Required features: ${RenderingBackendProfile.REQUIRED_PARITY_FEATURES}, " +
                        "Feature flags: $featureFlags"
            }
        }
    }

    /**
     * Check if this device meets minimum requirements for a specific backend profile.
     */
    fun meetsRequirements(profile: RenderingBackendProfile): Boolean {
        return profile.supportedFeatures.all { feature ->
            featureFlags[feature] == FeatureStatus.SUPPORTED
        }
    }

    /**
     * Generate a human-readable summary of capabilities.
     */
    fun summary(): String = buildString {
        appendLine("Device: $deviceId")
        appendLine("Driver: $driverVersion")
        appendLine("OS: $osBuild")
        appendLine("Preferred Backend: ${preferredBackend ?: "None"}")
        if (limitations.isNotEmpty()) {
            appendLine("Limitations:")
            limitations.forEach { appendLine("  - $it") }
        }
    }
}
