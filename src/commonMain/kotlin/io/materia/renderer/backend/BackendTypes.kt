package io.materia.renderer.backend

/**
 * Supported rendering backend identifiers.
 */
enum class BackendId {
    WEBGPU,
    VULKAN
}

/**
 * Backend feature capabilities for parity tracking.
 */
enum class BackendFeature {
    COMPUTE,
    RAY_TRACING,
    XR_SURFACE
}

/**
 * Feature support status.
 */
enum class FeatureStatus {
    SUPPORTED,
    MISSING,
    EMULATED
}

/**
 * Reason for backend selection.
 */
enum class SelectionReason {
    PREFERRED,
    FALLBACK,
    FAILED
}
