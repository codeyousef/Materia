/**
 * T015: RenderSurface Interface (Feature 019-we-should-not)
 *
 * Platform-specific surface abstraction for rendering.
 * Refactored from previous implementation to support WebGPU/Vulkan backends.
 */

package io.materia.renderer

/**
 * Platform-specific rendering surface.
 *
 * Platform implementations:
 * - JVM: VkSurfaceKHR (Vulkan surface) via LWJGL window
 * - JS: HTMLCanvasElement (WebGPU GPUCanvasContext)
 *
 * Usage:
 * ```kotlin
 * // JVM
 * val surface = JvmRenderSurface(glfwWindow)
 *
 * // JS
 * val surface = JsRenderSurface(canvasElement)
 *
 * // Common
 * val renderer = RendererFactory.create(surface).getOrThrow()
 * ```
 *
 * @property width Surface width in pixels
 * @property height Surface height in pixels
 */
expect interface RenderSurface {
    val width: Int
    val height: Int

    /**
     * Get platform-specific surface handle.
     *
     * Platform returns:
     * - JVM: Long (VkSurfaceKHR handle) or GLFW window pointer
     * - JS: HTMLCanvasElement or GPUCanvasContext
     *
     * Used internally by platform renderers to create swapchain/context.
     */
    fun getHandle(): Any
}

/*
 * NOTE: Previous implementation (pre-Feature-019) included these additional members:
 * - devicePixelRatio: Float
 * - isValid: Boolean
 * - resize(width: Int, height: Int)
 * - present()
 * - dispose()
 *
 * These will be restored in platform implementations (JvmRenderSurface, JsRenderSurface)
 * after Feature 019 core refactoring is complete.
 *
 * See git history for full API before Feature 019 refactoring.
 */

/*
 * NOTE: The following types from pre-Feature-019 implementation have been moved:
 * - SurfaceConfig: Deferred to platform implementations
 * - SurfaceFormat, SurfaceUsage, PresentMode, AlphaMode: Deferred to swapchain management
 * - SurfaceCapabilities: Merged into RendererCapabilities
 * - SurfaceTransform: Deferred to platform-specific windowing
 * - SurfaceFactory: Replaced by RendererFactory.create(surface)
 * - SurfaceEventListener: Deferred to platform implementations
 * - SurfaceUtils: Utility functions deferred to platform implementations
 * - Extension functions: Deferred to platform implementations
 *
 * See git history for full API before Feature 019 refactoring.
 */