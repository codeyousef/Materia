/**
 * JVM SurfaceFactory implementation
 * Creates VulkanSurface from GLFW window handle
 */

package io.materia.renderer

import io.materia.renderer.vulkan.VulkanSurface

/**
 * JVM implementation of SurfaceFactory.
 * Creates VulkanSurface from GLFW window handle.
 */
actual object SurfaceFactory {
    /**
     * Create VulkanSurface from GLFW window handle.
     *
     * @param handle Long (GLFW window pointer from glfwCreateWindow)
     * @return VulkanSurface ready for Vulkan renderer
     * @throws IllegalArgumentException if handle is not a Long
     */
    actual fun create(handle: Any): RenderSurface {
        return when (handle) {
            is Long -> VulkanSurface(handle)
            else -> throw IllegalArgumentException(
                "JVM SurfaceFactory.create() expects Long (GLFW window handle), got ${handle::class.simpleName}"
            )
        }
    }
}
