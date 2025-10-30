/**
 * T017: RendererFactory Object
 * Feature: 019-we-should-not
 *
 * Platform-specific renderer factory with automatic backend detection.
 */

package io.materia.renderer

/**
 * Platform-specific renderer factory.
 *
 * Automatically detects and creates the appropriate renderer for the platform:
 * - JVM: VulkanRenderer (primary)
 * - JS: WebGPURenderer (primary) with WebGLRenderer fallback
 *
 * Usage:
 * ```kotlin
 * // Detect available backends
 * val backends = RendererFactory.detectAvailableBackends()
 * println("Available: $backends") // [VULKAN] on JVM, [WEBGPU, WEBGL] on modern JS
 *
 * // Create renderer with auto-detection
 * val renderer = RendererFactory.create(surface).getOrElse { error ->
 *     when (error) {
 *         is RendererInitializationException.NoGraphicsSupportException -> {
 *             showErrorDialog("Graphics not supported: ${error.message}")
 *         }
 *         else -> Logger.error("Renderer init failed", error)
 *     }
 *     exitProcess(1)
 * }
 *
 * // Create with specific backend preference
 * val config = RendererConfig(preferredBackend = BackendType.WEBGPU)
 * val renderer = RendererFactory.create(surface, config).getOrThrow()
 * ```
 *
 * Platform implementations:
 * - JVM (jvmMain): Detects Vulkan, creates VulkanRenderer
 * - JS (jsMain): Detects WebGPU/WebGL, creates WebGPURenderer or WebGLRenderer
 *
 * Feature Requirements:
 * - FR-001: WebGPU primary for JavaScript/browser
 * - FR-002: Vulkan primary for JVM/Native
 * - FR-003: WebGL fallback only (JS only)
 * - FR-004: Automatic backend detection
 * - FR-022: Fail-fast on initialization errors
 */
expect object RendererFactory {
    /**
     * Create a renderer for the given surface.
     *
     * @param surface Platform-specific render surface (JvmRenderSurface or JsRenderSurface)
     * @param config Renderer configuration (backend preference, validation, vsync, MSAA, etc.)
     * @return Result with Renderer or RendererInitializationException on failure
     *
     * Initialization process:
     * 1. Detect available backends (FR-004)
     * 2. Select backend:
     *    - If config.preferredBackend specified and available, use it
     *    - Otherwise, use platform primary (Vulkan for JVM, WebGPU for JS)
     *    - JS only: If primary fails, try WebGL fallback (FR-003)
     * 3. Create platform renderer (VulkanRenderer or WebGPURenderer/WebGLRenderer)
     * 4. Initialize renderer with config
     * 5. Return success or throw RendererInitializationException (FR-022)
     *
     * Platform behavior:
     * - JVM: Try Vulkan → Fail if unavailable (no fallback per FR-002)
     * - JS: Try WebGPU → Try WebGL fallback → Fail if both unavailable
     *
     * Throws: RendererInitializationException.NoGraphicsSupportException if no backend available
     * Throws: RendererInitializationException.AdapterRequestFailedException if adapter request fails
     * Throws: RendererInitializationException.DeviceCreationFailedException if device creation fails
     * Throws: RendererInitializationException.SurfaceCreationFailedException if surface creation fails
     * Throws: RendererInitializationException.ShaderCompilationException if shader compilation fails
     */
    suspend fun create(
        surface: RenderSurface,
        config: RendererConfig = RendererConfig()
    ): io.materia.core.Result<Renderer>

    /**
     * Detect available graphics backends on this platform.
     *
     * @return List of available BackendType (non-empty on supported platforms)
     *
     * Platform returns:
     * - JVM: [VULKAN] if Vulkan driver available, [] otherwise
     * - JS: [WEBGPU, WEBGL] on modern browsers, [WEBGL] on older browsers, [] on ancient browsers
     *
     * Used for:
     * - Pre-flight capability checks (FR-024)
     * - User settings UI (show available backends)
     * - Error diagnostics (show what's available vs required)
     *
     * Example:
     * ```kotlin
     * val backends = RendererFactory.detectAvailableBackends()
     * if (BackendType.VULKAN !in backends) {
     *     showWarning("Vulkan not available, please update GPU drivers")
     * }
     * ```
     */
    fun detectAvailableBackends(): List<BackendType>
}
