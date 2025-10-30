/**
 * T014: Renderer Interface (Feature 019-we-should-not)
 *
 * Unified renderer interface for WebGPU/Vulkan/WebGL backends.
 * Refactored from previous WebGL-only implementation to support multiplatform.
 */

package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.scene.Scene

/**
 * Unified renderer interface across platforms.
 *
 * Platform implementations:
 * - JVM: VulkanRenderer (primary) via LWJGL 3.3.6
 * - JS: WebGPURenderer (primary) with WebGLRenderer fallback
 *
 * Usage:
 * ```kotlin
 * val renderer = RendererFactory.create(surface).getOrThrow()
 * renderer.initialize(RendererConfig()).getOrThrow()
 * renderer.render(scene, camera)
 * renderer.dispose()
 * ```
 *
 * @property backend Active graphics backend (WEBGPU, VULKAN, or WEBGL)
 * @property capabilities Hardware and API capabilities (FR-024)
 * @property stats Current frame performance metrics (FR-019)
 *
 * Feature Requirements:
 * - FR-001: WebGPU primary for JavaScript/browser
 * - FR-002: Vulkan primary for JVM/Native
 * - FR-011: Unified renderer interface
 * - FR-019: 60 FPS target, 30 FPS minimum
 * - FR-020: Visual parity across backends
 * - FR-022: Fail-fast on initialization errors
 * - FR-024: Capability detection before rendering
 */
expect interface Renderer {
    val backend: BackendType
    val capabilities: RendererCapabilities
    val stats: RenderStats

    /**
     * Initialize the renderer with specified configuration.
     *
     * @param config Renderer configuration (backend preference, validation, vsync, MSAA, etc.)
     * @return Success or RendererError on failure
     *
     * FR-022: Fail-fast on initialization errors with detailed diagnostics
     * FR-024: Detect and validate capabilities before rendering
     *
     * Initialization process:
     * 1. Detect available backends (FR-004)
     * 2. Select backend (preferredBackend or auto-detect)
     * 3. Create graphics context (Device+Queue for WebGPU, VkInstance for Vulkan)
     * 4. Query capabilities (maxTextureSize, MSAA samples, extensions)
     * 5. Initialize render pipeline and resources
     *
     * Throws: RendererInitializationException on failure
     */
    suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit>

    /**
     * Render a scene from the camera's perspective.
     *
     * @param scene Scene graph to render
     * @param camera Camera defining view and projection
     *
     * FR-019: Must maintain 60 FPS target, 30 FPS minimum acceptable
     * FR-020: Visual parity across backends (same scene produces same output)
     *
     * Rendering process:
     * 1. Update camera matrices (view, projection)
     * 2. Frustum culling (skip off-screen objects)
     * 3. Sort render queue (opaque front-to-back, transparent back-to-front)
     * 4. Bind shader pipeline
     * 5. Bind geometry (VBO/IBO)
     * 6. Issue draw calls
     * 7. Update stats (FPS, triangles, draw calls)
     */
    fun render(scene: Scene, camera: Camera)

    /**
     * Resize render targets to new dimensions.
     *
     * @param width New width in pixels (must be > 0)
     * @param height New height in pixels (must be > 0)
     *
     * Called on window/canvas resize events.
     * Recreates swapchain (WebGPU) or framebuffer (Vulkan/WebGL).
     */
    fun resize(width: Int, height: Int)

    /**
     * Dispose GPU resources and clean up.
     *
     * Must be called before application exit to prevent resource leaks.
     * Renderer is unusable after dispose().
     *
     * Cleanup process:
     * 1. Wait for GPU to finish pending work
     * 2. Destroy render pipelines
     * 3. Free GPU buffers (VBO, IBO, UBO)
     * 4. Destroy textures
     * 5. Destroy device/context
     */
    fun dispose()

    // Note: Use the 'stats' property instead of getStats() to avoid JVM signature clash
}

/*
 * NOTE: Previous implementation (pre-Feature-019) included these additional properties:
 * - renderTarget, autoClear, autoClearColor, autoClearDepth, autoClearStencil
 * - clearColor, clearAlpha, shadowMap, toneMapping, toneMappingExposure
 * - outputColorSpace, physicallyCorrectLights
 * - setSize, setPixelRatio, setViewport, getViewport, setScissorTest, setScissor
 * - clear, clearColorBuffer, clearDepth, clearStencil, resetState
 * - compile, forceContextLoss, isContextLost, resetStats
 *
 * These will be restored in platform implementations (VulkanRenderer, WebGPURenderer)
 * after Feature 019 core refactoring is complete. They are currently deferred to
 * maintain focus on the WebGPU/Vulkan primary backend architecture.
 *
 * See git history for full Three.js-compatible API.
 */

/*
 * NOTE: The following types from pre-Feature-019 implementation have been moved:
 * - RenderTarget: To be implemented in Phase 2-13 (Advanced Features)
 * - ShadowMapSettings, ShadowMapType: Deferred to lighting system (Phase 2-13)
 * - ToneMapping, ColorSpace: Deferred to post-processing (Phase 2-13)
 * - RenderStats: Now in RenderStats.kt (T013)
 * - MemoryStats: Merged into RenderStats (textureMemory, bufferMemory fields)
 * - RendererResult: Replaced with Kotlin stdlib Result<T, E>
 * - RendererException: Now RendererError hierarchy (T016)
 * - RendererConfig: Now in RendererConfig.kt (T013) with Feature 019 requirements
 * - PowerPreference: Now in RendererConfig.kt with HIGH_PERFORMANCE/LOW_POWER only
 * - Precision: Deferred to shader system (Phase 3.7)
 * - RendererFactory: Now expect object in T017
 * - RendererUtils: Utility functions deferred to platform implementations
 * - Extension functions: Deferred to platform implementations
 *
 * See git history for full Three.js-compatible API before Feature 019 refactoring.
 */