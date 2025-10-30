/**
 * SurfaceFactory - Platform-agnostic surface creation
 *
 * Provides a unified API for creating render surfaces across all platforms
 * without requiring applications to import platform-specific surface types.
 *
 * Usage in application code:
 * ```kotlin
 * // JS
 * val canvas = document.getElementById("canvas") as HTMLCanvasElement
 * val surface = SurfaceFactory.create(canvas)
 *
 * // JVM
 * val window = glfwCreateWindow(...)
 * val surface = SurfaceFactory.create(window)
 *
 * // Both platforms use the same code
 * val renderer = RendererFactory.create(surface)
 * ```
 */

package io.materia.renderer

/**
 * Platform-agnostic surface factory.
 *
 * Creates the appropriate RenderSurface implementation for each platform:
 * - JVM: Creates VulkanSurface from GLFW window handle
 * - JS: Creates WebGPUSurface from HTMLCanvasElement
 *
 * This allows application code to remain platform-agnostic and avoid
 * importing platform-specific surface types.
 */
expect object SurfaceFactory {
    /**
     * Create a render surface from a platform-specific window/canvas handle.
     *
     * @param handle Platform-specific handle:
     *   - JVM: Long (GLFW window pointer)
     *   - JS: HTMLCanvasElement
     * @return Platform-appropriate RenderSurface implementation
     * @throws IllegalArgumentException if handle type is invalid for platform
     */
    fun create(handle: Any): RenderSurface
}
