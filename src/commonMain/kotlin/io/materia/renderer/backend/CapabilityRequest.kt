package io.materia.renderer.backend

import kotlinx.serialization.Serializable

/**
 * Request structure for device capability detection.
 */
@Serializable
data class CapabilityRequest(
    val requestedFeatures: Set<BackendFeature> = setOf(
        BackendFeature.COMPUTE,
        BackendFeature.RAY_TRACING,
        BackendFeature.XR_SURFACE
    ),
    val preferredBackend: BackendId? = null,
    val includeDebugInfo: Boolean = false
)
