/**
 * T016: RendererInitializationException Hierarchy
 * Feature: 019-we-should-not
 *
 * Typed exceptions for fail-fast error handling (FR-022, FR-024).
 */

package io.materia.renderer

/**
 * Base exception for renderer initialization failures.
 *
 * All renderer initialization errors are subclasses of this sealed class,
 * enabling exhaustive when-expression handling.
 *
 * Feature Requirements:
 * - FR-022: Fail-fast on initialization errors with detailed diagnostics
 * - FR-024: Detect capabilities before rendering attempts
 *
 * Usage:
 * ```kotlin
 * try {
 *     val renderer = RendererFactory.create(surface).getOrThrow()
 * } catch (e: RendererInitializationException.NoGraphicsSupportException) {
 *     Logger.error("Graphics not supported: ${e.message}")
 *     showErrorDialog(e.message)
 * } catch (e: RendererInitializationException) {
 *     Logger.error("Renderer init failed: ${e.message}")
 * }
 * ```
 */
sealed class RendererInitializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * No supported graphics API found on the platform.
     *
     * Thrown when:
     * - JVM: Vulkan not available (missing driver, unsupported GPU)
     * - JS: Neither WebGPU nor WebGL available (very old browser)
     *
     * @property platform Platform name ("JVM", "JS", "Native")
     * @property availableBackends List of detected backends (empty if none)
     * @property requiredFeatures List of required GPU features (e.g., "Vulkan 1.1+", "WebGPU")
     *
     * Example message:
     * ```
     * No supported graphics API found on JVM.
     * Available backends: []
     * Required features: [Vulkan 1.1+]
     * Troubleshooting: Ensure GPU drivers are installed and up to date.
     * ```
     */
    class NoGraphicsSupportException(
        val platform: String,
        val availableBackends: List<BackendType>,
        val requiredFeatures: List<String>
    ) : RendererInitializationException(
        """
        No supported graphics API found on $platform.
        Available backends: $availableBackends
        Required features: $requiredFeatures
        Troubleshooting: Ensure GPU drivers are installed and up to date.
        """.trimIndent()
    )

    /**
     * Failed to request GPU adapter (WebGPU) or physical device (Vulkan).
     *
     * Thrown when:
     * - WebGPU: navigator.gpu.requestAdapter() returns null
     * - Vulkan: vkEnumeratePhysicalDevices() finds no suitable devices
     *
     * @property backend Backend that failed (WEBGPU or VULKAN)
     * @property reason Human-readable reason for failure
     *
     * Example message:
     * ```
     * Failed to request WEBGPU adapter: No compatible GPU found
     * ```
     */
    class AdapterRequestFailedException(
        val backend: BackendType,
        val reason: String
    ) : RendererInitializationException(
        "Failed to request $backend adapter: $reason"
    )

    /**
     * Failed to create logical device from adapter/physical device.
     *
     * Thrown when:
     * - WebGPU: adapter.requestDevice() fails (missing features, limits exceeded)
     * - Vulkan: vkCreateDevice() fails (missing extensions, queue families)
     *
     * @property backend Backend that failed (WEBGPU or VULKAN)
     * @property adapterInfo Adapter/device name and info
     * @property reason Human-readable reason for failure
     *
     * Example message:
     * ```
     * Failed to create VULKAN device.
     * Adapter: NVIDIA GeForce RTX 3080 (Vulkan 1.3.0)
     * Reason: Required extension VK_KHR_swapchain not supported
     * ```
     */
    class DeviceCreationFailedException(
        val backend: BackendType,
        val adapterInfo: String,
        val reason: String
    ) : RendererInitializationException(
        "Failed to create $backend device.\nAdapter: $adapterInfo\nReason: $reason"
    )

    /**
     * Failed to create render surface or swapchain.
     *
     * Thrown when:
     * - WebGPU: context.configure() fails (unsupported format, invalid dimensions)
     * - Vulkan: vkCreateSwapchainKHR() fails (surface lost, out of memory)
     *
     * @property backend Backend that failed
     * @property surfaceType Surface type description (e.g., "HTMLCanvasElement", "GLFW Window")
     *
     * Example message:
     * ```
     * Failed to create WEBGPU surface for HTMLCanvasElement
     * ```
     */
    class SurfaceCreationFailedException(
        val backend: BackendType,
        val surfaceType: String
    ) : RendererInitializationException(
        "Failed to create $backend surface for $surfaceType"
    )

    /**
     * Shader compilation failed during renderer initialization.
     *
     * Thrown when:
     * - WGSL shader fails to compile (WebGPU)
     * - SPIR-V shader fails validation (Vulkan)
     * - GLSL shader fails compilation (WebGL fallback)
     *
     * @property shaderName Name of shader that failed (e.g., "basic.wgsl", "pbr.vert")
     * @property errors List of compilation error messages
     *
     * Example message:
     * ```
     * Shader compilation failed: basic.wgsl
     * Errors:
     * - Line 10: Unknown identifier 'unifrm' (did you mean 'uniform'?)
     * - Line 15: Type mismatch: expected vec3, got vec4
     * ```
     */
    class ShaderCompilationException(
        val shaderName: String,
        val errors: List<String>
    ) : RendererInitializationException(
        "Shader compilation failed: $shaderName\nErrors:\n${errors.joinToString("\n")}"
    )
}

/**
 * Type alias for Result with RendererInitializationException.
 *
 * Used in RendererFactory.create() return type.
 */
typealias RendererResult<T> = io.materia.core.Result<T>

/**
 * Type alias for RendererError used in Renderer.initialize().
 *
 * Note: Renderer.initialize() uses RendererError (runtime errors during init),
 * while RendererFactory.create() uses RendererInitializationException (factory-level errors).
 * They are semantically equivalent for Feature 019 core implementation.
 */
typealias RendererError = RendererInitializationException
