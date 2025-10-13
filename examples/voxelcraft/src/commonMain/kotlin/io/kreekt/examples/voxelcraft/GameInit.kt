/**
 * T030: GameInit - Centralized Renderer Initialization
 * Feature: 019-we-should-not
 *
 * Provides centralized renderer initialization with comprehensive error handling.
 */

package io.kreekt.examples.voxelcraft

import io.kreekt.renderer.*

/**
 * Initialize renderer with comprehensive error handling.
 *
 * Handles all RendererInitializationException subtypes with appropriate error messages.
 *
 * Usage:
 * ```kotlin
 * val surface = WebGPUSurface(canvas) // or VulkanSurface(window)
 * val renderer = try {
 *     initializeRenderer(surface)
 * } catch (e: RendererInitializationException) {
 *     showErrorDialog(e.message)
 *     exitProcess(1)
 * }
 * ```
 *
 * @param surface Platform-specific render surface
 * @param config Optional renderer configuration
 * @return Initialized Renderer instance
 * @throws RendererInitializationException if initialization fails
 */
suspend fun initializeRenderer(
    surface: RenderSurface,
    config: RendererConfig = RendererConfig()
): Renderer {
    return try {
        // Attempt to create renderer via RendererFactory
        when (val result = RendererFactory.create(surface, config)) {
            is io.kreekt.core.Result.Success -> result.value
            is io.kreekt.core.Result.Error -> {
                val exception = result.exception as? RendererInitializationException
                // Handle specific exception types with detailed logging
                when (exception) {
                is RendererInitializationException.NoGraphicsSupportException -> {
                    logError("âŒ Graphics Not Supported")
                    logError("   Platform: ${exception.platform}")
                    logError("   Available backends: ${exception.availableBackends}")
                    logError("   Required features: ${exception.requiredFeatures}")
                    logError("")
                    logError("Troubleshooting:")
                    logError("  1. Ensure your GPU supports Vulkan 1.1+ (JVM) or WebGPU/WebGL 2.0 (Browser)")
                    logError("  2. Update your GPU drivers to the latest version")
                    logError("  3. Check if Vulkan/WebGPU is enabled in your system")
                    throw exception
                }

                is RendererInitializationException.AdapterRequestFailedException -> {
                    logError("âŒ Failed to Request GPU Adapter")
                    logError("   Backend: ${exception.backend}")
                    logError("   Reason: ${exception.reason}")
                    logError("")
                    logError("Troubleshooting:")
                    logError("  1. Check if GPU drivers are installed")
                    logError("  2. Verify GPU is not in use by another application")
                    logError("  3. Try restarting your system")
                    throw exception
                }

                is RendererInitializationException.DeviceCreationFailedException -> {
                    logError("âŒ Failed to Create GPU Device")
                    logError("   Backend: ${exception.backend}")
                    logError("   Adapter: ${exception.adapterInfo}")
                    logError("   Reason: ${exception.reason}")
                    logError("")
                    logError("Troubleshooting:")
                    logError("  1. Update GPU drivers")
                    logError("  2. Check if GPU supports required features")
                    logError("  3. Try closing other GPU-intensive applications")
                    throw exception
                }

                is RendererInitializationException.SurfaceCreationFailedException -> {
                    logError("âŒ Failed to Create Render Surface")
                    logError("   Backend: ${exception.backend}")
                    logError("   Surface type: ${exception.surfaceType}")
                    logError("")
                    logError("Troubleshooting:")
                    logError("  1. Check if window/canvas is valid")
                    logError("  2. Verify surface dimensions are within GPU limits")
                    logError("  3. Try resizing the window")
                    throw exception
                }

                is RendererInitializationException.ShaderCompilationException -> {
                    logError("âŒ Shader Compilation Failed")
                    logError("   Shader: ${exception.shaderName}")
                    logError("   Errors:")
                    exception.errors.forEach { error ->
                        logError("     - $error")
                    }
                    logError("")
                    logError("This is likely a bug in KreeKt. Please report it with:")
                    logError("  - GPU model and driver version")
                    logError("  - Operating system")
                    logError("  - Full error log")
                    throw exception
                }
                    else -> {
                        // Unknown exception type
                        logError("âŒ Renderer initialization failed: ${result.message}")
                        throw exception ?: RuntimeException(result.message)
                    }
                }
            }
        }
    } catch (e: RendererInitializationException) {
        // Re-throw RendererInitializationException
        throw e
    } catch (e: Throwable) {
        // Catch any unexpected errors
        logError("âŒ Unexpected error during renderer initialization: ${e.message}")
        throw RendererInitializationException.DeviceCreationFailedException(
            backend = io.kreekt.renderer.BackendType.VULKAN, // Default, may not be accurate
            adapterInfo = "Unknown",
            reason = e.message ?: "Unknown error: ${e::class.simpleName}"
        )
    }
}

/**
 * Detect available graphics backends on current platform.
 *
 * @return List of available BackendType
 */
fun detectAvailableBackends() = RendererFactory.detectAvailableBackends()

