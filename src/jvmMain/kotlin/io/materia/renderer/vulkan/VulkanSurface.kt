/**
 * T019: VulkanSurface Implementation
 * Feature: 019-we-should-not
 *
 * Vulkan surface wrapper for GLFW windows.
 */

package io.materia.renderer.vulkan

import io.materia.renderer.RenderSurface
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VkInstance

/**
 * Vulkan surface implementation wrapping a GLFW window.
 *
 * Usage:
 * ```kotlin
 * // Create GLFW window
 * val window = glfwCreateWindow(800, 600, "My App", NULL, NULL)
 *
 * // Wrap in VulkanSurface
 * val surface = VulkanSurface(window)
 *
 * // Create VkSurfaceKHR after VkInstance is created
 * surface.createSurface(vkInstance)
 *
 * // Use with RendererFactory
 * val renderer = RendererFactory.create(surface).getOrThrow()
 * ```
 *
 * @property windowHandle GLFW window handle (Long pointer)
 */
class VulkanSurface(
    private val windowHandle: Long
) : RenderSurface {

    private var surfaceHandle: Long = 0L // VkSurfaceKHR
    private var vkInstance: VkInstance? = null

    /**
     * Surface width in pixels.
     */
    override val width: Int
        get() = MemoryStack.stackPush().use { stack ->
            val pWidth = stack.callocInt(1)
            val pHeight = stack.callocInt(1)
            glfwGetWindowSize(windowHandle, pWidth, pHeight)
            pWidth.get(0)
        }

    /**
     * Surface height in pixels.
     */
    override val height: Int
        get() = MemoryStack.stackPush().use { stack ->
            val pWidth = stack.callocInt(1)
            val pHeight = stack.callocInt(1)
            glfwGetWindowSize(windowHandle, pWidth, pHeight)
            pHeight.get(0)
        }

    /**
     * Get GLFW window handle.
     *
     * @return GLFW window handle as Long
     */
    override fun getHandle(): Any = windowHandle

    /**
     * Get framebuffer size (respects DPI scaling).
     *
     * @return Pair of (width, height) in pixels
     */
    fun getFramebufferSize(): Pair<Int, Int> {
        return MemoryStack.stackPush().use { stack ->
            val pWidth = stack.callocInt(1)
            val pHeight = stack.callocInt(1)
            glfwGetFramebufferSize(windowHandle, pWidth, pHeight)
            Pair(pWidth.get(0), pHeight.get(0))
        }
    }

    /**
     * Check if window should close.
     *
     * @return true if window close requested
     */
    fun shouldClose(): Boolean {
        return glfwWindowShouldClose(windowHandle)
    }

    /**
     * Poll GLFW events.
     *
     * Should be called once per frame.
     */
    fun pollEvents() {
        glfwPollEvents()
    }

    /**
     * Create Vulkan surface from GLFW window.
     * T019: Required for SwapchainManager initialization.
     *
     * @param instance Vulkan instance
     * @return VkSurfaceKHR handle
     */
    fun createSurface(instance: VkInstance): Long {
        if (surfaceHandle != 0L) {
            return surfaceHandle // Already created
        }

        return MemoryStack.stackPush().use { stack ->
            val pSurface = stack.mallocLong(1)
            val result = glfwCreateWindowSurface(instance, windowHandle, null, pSurface)
            if (result != org.lwjgl.vulkan.VK12.VK_SUCCESS) {
                throw RuntimeException("Failed to create window surface: VkResult=$result")
            }
            surfaceHandle = pSurface.get(0)
            vkInstance = instance
            surfaceHandle
        }
    }

    /**
     * Get VkSurfaceKHR handle.
     * T019: Used by VulkanRenderer to initialize SwapchainManager.
     *
     * @return VkSurfaceKHR handle
     * @throws IllegalStateException if surface not created yet
     */
    fun getSurfaceHandle(): Long {
        if (surfaceHandle == 0L) {
            throw IllegalStateException("Surface not created. Call createSurface() first.")
        }
        return surfaceHandle
    }

    /**
     * Destroy Vulkan surface.
     * Should be called during cleanup.
     */
    fun destroySurface() {
        val instance = vkInstance
        if (surfaceHandle != 0L && instance != null) {
            org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR(instance, surfaceHandle, null)
            surfaceHandle = 0L
            vkInstance = null
        }
    }
}
