/**
 * T021: RendererFactory Actual (JVM)
 * Feature: 019-we-should-not
 *
 * JVM implementation of RendererFactory using Vulkan.
 */

package io.materia.renderer

import io.materia.renderer.vulkan.VulkanRenderer
import io.materia.renderer.vulkan.VulkanSurface
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.vulkan.VK12.*

/**
 * JVM actual for RendererFactory.
 *
 * Creates VulkanRenderer for JVM platform (FR-002: Vulkan primary for JVM).
 */
actual object RendererFactory {

    /**
     * Create Vulkan renderer for JVM platform.
     *
     * Process:
     * 1. Verify Vulkan is available
     * 2. Create VulkanRenderer instance
     * 3. Initialize renderer
     * 4. Return success or error
     *
     * @param surface VulkanSurface wrapping GLFW window
     * @param config Renderer configuration
     * @return Result with Renderer or RendererInitializationException on failure
     */
    actual suspend fun create(
        surface: RenderSurface,
        config: RendererConfig
    ): io.materia.core.Result<Renderer> {
        // 1. Check if surface is valid
        if (surface !is VulkanSurface) {
            return io.materia.core.Result.Error(
                "Expected VulkanSurface, got ${surface::class.simpleName}",
                RendererInitializationException.SurfaceCreationFailedException(
                    BackendType.VULKAN,
                    "Expected VulkanSurface, got ${surface::class.simpleName}"
                )
            )
        }

        // 2. Detect available backends
        val availableBackends = detectAvailableBackends()
        if (BackendType.VULKAN !in availableBackends) {
            return io.materia.core.Result.Error(
                "Vulkan not available on platform JVM",
                RendererInitializationException.NoGraphicsSupportException(
                    platform = "JVM",
                    availableBackends = availableBackends,
                    requiredFeatures = listOf("Vulkan 1.1+")
                )
            )
        }

        // 3. Create VulkanRenderer
        return try {
            val renderer = VulkanRenderer(surface, config)

            // 4. Initialize renderer
            val initResult = renderer.initialize(config)
            if (initResult is io.materia.core.Result.Error) {
                return io.materia.core.Result.Error(
                    initResult.message,
                    initResult.exception
                )
            }

            // Log selected backend
            println("[Materia] Selected backend: Vulkan (${renderer.capabilities.deviceName})")

            io.materia.core.Result.Success(renderer)
        } catch (e: Exception) {
            io.materia.core.Result.Error(
                e.message ?: "Unknown error during renderer creation",
                RendererInitializationException.DeviceCreationFailedException(
                    BackendType.VULKAN,
                    "Unknown device",
                    e.message ?: "Unknown error during renderer creation"
                )
            )
        }
    }

    /**
     * Detect available graphics backends on JVM.
     *
     * Returns [BackendType.VULKAN] if Vulkan is available, empty list otherwise.
     *
     * @return List of available BackendType (empty if none)
     */
    actual fun detectAvailableBackends(): List<BackendType> {
        return if (isVulkanAvailable()) {
            listOf(BackendType.VULKAN)
        } else {
            emptyList()
        }
    }

    /**
     * Check if Vulkan is available on this system.
     *
     * Verifies:
     * 1. GLFW supports Vulkan
     * 2. Vulkan loader can be initialized
     * 3. At least one Vulkan instance extension is available
     *
     * @return true if Vulkan is available
     */
    private fun isVulkanAvailable(): Boolean {
        return try {
            // Check GLFW Vulkan support
            if (!GLFWVulkan.glfwVulkanSupported()) {
                return false
            }

            // Try to get Vulkan instance extensions (will fail if Vulkan loader not present)
            val ppExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()

            // If we can get extensions, Vulkan is available
            ppExtensions != null && ppExtensions.remaining() > 0
        } catch (e: Exception) {
            // Any exception means Vulkan is not available
            false
        }
    }
}
