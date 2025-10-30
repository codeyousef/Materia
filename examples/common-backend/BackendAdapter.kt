package io.materia.examples.backend

import io.materia.renderer.BackendType
import io.materia.renderer.RenderSurface
import io.materia.renderer.Renderer
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import kotlinx.coroutines.delay

/**
 * Minimal backend integration helper used by examples.
 *
 * The concrete backend detection and renderer creation live in the Materia core
 * (see [RendererFactory]). This adapter keeps the examples focused on scenario
 * logic by surfacing a tiny summary model and delegating the heavy lifting to
 * the library.
 */
suspend fun createExampleBackendIntegration(config: RendererConfig): ExampleBackendResult {
    println("Backend Integration Example")
    println("  Configuration:")
    println("    Preferred backend : ${config.preferredBackend ?: "auto"}")
    println("    Power preference  : ${config.powerPreference}")
    println("    Validation        : ${config.enableValidation}")
    println("    VSync             : ${config.vsync}")
    println("    MSAA              : ${config.msaaSamples}x")

    delay(100) // Telemetry simulation for the example narrative

    val availableBackends = RendererFactory.detectAvailableBackends()
    println(
        "  Available backends : ${
            if (availableBackends.isEmpty()) "none" else availableBackends.joinToString()
        }"
    )

    val selectedBackend = selectBackend(config, availableBackends)
        ?: error("No compatible backend detected on this platform.")

    println("  Selected backend   : $selectedBackend")

    delay(150) // Simulated initialization time for storytelling

    return ExampleBackendResult(
        backendType = selectedBackend,
        availableBackends = availableBackends,
        initTimeMs = 250,
        features = featureSummaryFor(selectedBackend)
    )
}

private fun selectBackend(
    config: RendererConfig,
    available: List<BackendType>
): BackendType? {
    val preferred = config.preferredBackend
    if (preferred != null && preferred in available) {
        return preferred
    }
    return available.firstOrNull()
}

private fun featureSummaryFor(backend: BackendType): Map<String, String> = when (backend) {
    BackendType.WEBGPU -> mapOf(
        "COMPUTE" to "Native",
        "RAY_TRACING" to "Emulated",
        "XR_SURFACE" to "Planned"
    )

    BackendType.VULKAN -> mapOf(
        "COMPUTE" to "Native",
        "RAY_TRACING" to "Native",
        "XR_SURFACE" to "Native"
    )

    BackendType.WEBGL -> mapOf(
        "COMPUTE" to "Emulated",
        "RAY_TRACING" to "Missing",
        "XR_SURFACE" to "Missing"
    )
}

/**
 * Light-weight data class the examples can print or inspect.
 */
data class ExampleBackendResult(
    val backendType: BackendType,
    val availableBackends: List<BackendType>,
    val initTimeMs: Long,
    val features: Map<String, String>
)

/**
 * Create a renderer from the summarized backend decision.
 */
suspend fun createRendererFromExampleBackend(
    surface: RenderSurface,
    result: ExampleBackendResult,
    config: RendererConfig = RendererConfig(preferredBackend = result.backendType)
): Renderer {
    println("Creating renderer with ${result.backendType} backend")
    println("   Available backends : ${result.availableBackends.joinToString()}")
    println("   Feature hints      : ${result.features}")

    return RendererFactory
        .create(surface, config)
        .getOrThrow()
}

