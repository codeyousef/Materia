/**
 * T015-T016: WebGPUSwapchain Implementation
 * Feature: 020-go-from-mvp
 *
 * WebGPU swapchain management for presenting rendered frames.
 */

package io.materia.renderer.webgpu

import io.materia.renderer.feature020.SwapchainException
import io.materia.renderer.feature020.SwapchainImage

/**
 * WebGPU swapchain manager implementation.
 *
 * Manages canvas context configuration and texture presentation.
 * Note: WebGPU auto-presents, so presentImage() is essentially a no-op.
 *
 * @property context WebGPU canvas context
 * @property device WebGPU device
 */
class WebGPUSwapchain(
    private val context: dynamic, // GPUCanvasContext
    private val device: dynamic   // GPUDevice
) : io.materia.renderer.feature020.SwapchainManager {

    private var currentWidth: Int = 800
    private var currentHeight: Int = 600
    private var configured: Boolean = false

    init {
        // Configure with initial dimensions
        configure(currentWidth, currentHeight)
    }

    /**
     * Configure canvas context with device and format.
     *
     * @param width Canvas width in pixels
     * @param height Canvas height in pixels
     */
    private fun configure(width: Int, height: Int) {
        try {
            val config = js(
                """({
                device: device,
                format: 'bgra8unorm',
                usage: GPUTextureUsage.RENDER_ATTACHMENT,
                alphaMode: 'opaque'
            })"""
            )

            context.configure(config)

            currentWidth = width
            currentHeight = height
            configured = true
        } catch (e: Exception) {
            throw SwapchainException("Failed to configure swapchain: ${e.message}")
        } catch (e: Throwable) {
            throw SwapchainException("Failed to configure swapchain: ${e.message}")
        }
    }

    /**
     * Acquire next swapchain image for rendering.
     *
     * @return Swapchain image ready for rendering
     * @throws SwapchainException if acquire fails
     */
    override fun acquireNextImage(): SwapchainImage {
        if (!configured) {
            throw SwapchainException("Swapchain not configured")
        }

        return try {
            // Get current texture from canvas context
            val texture = context.getCurrentTexture()

            if (texture == null || texture == undefined) {
                throw SwapchainException("Failed to acquire swapchain image: getCurrentTexture() returned null")
            }

            // Create texture view for rendering
            val textureView = texture.createView()

            if (textureView == null || textureView == undefined) {
                throw SwapchainException("Failed to create texture view")
            }

            SwapchainImage(
                handle = textureView,
                index = 0, // WebGPU doesn't expose image index
                ready = true
            )
        } catch (e: SwapchainException) {
            throw e
        } catch (e: Exception) {
            throw SwapchainException("Failed to acquire swapchain image: ${e.message}")
        } catch (e: Throwable) {
            throw SwapchainException("Failed to acquire swapchain image: ${e.message}")
        }
    }

    /**
     * Present rendered image to screen.
     *
     * Note: WebGPU auto-presents after command buffer submission,
     * so this is essentially a no-op for WebGPU.
     *
     * @param image Swapchain image to present
     * @throws SwapchainException if present fails
     */
    override fun presentImage(image: SwapchainImage) {
        if (!image.isReady()) {
            throw SwapchainException("Swapchain image not ready for presentation")
        }

        // WebGPU auto-presents after command buffer submission
        // No explicit present call needed
    }

    /**
     * Recreate swapchain on window resize.
     *
     * For WebGPU, this just reconfigures the canvas context.
     *
     * @param width New width in pixels (> 0)
     * @param height New height in pixels (> 0)
     * @throws IllegalArgumentException if width or height <= 0
     */
    override fun recreateSwapchain(width: Int, height: Int) {
        if (width <= 0) {
            throw IllegalArgumentException("width must be > 0, got $width")
        }
        if (height <= 0) {
            throw IllegalArgumentException("height must be > 0, got $height")
        }

        // Unconfigure old context
        if (configured) {
            try {
                context.unconfigure()
            } catch (e: Exception) {
                // Ignore errors on unconfigure
            } catch (e: Throwable) {
                // Ignore errors on unconfigure
            }
        }

        // Reconfigure with new dimensions
        configure(width, height)
    }

    /**
     * Get current swapchain extent.
     *
     * @return Pair of (width, height) in pixels
     */
    override fun getExtent(): Pair<Int, Int> {
        return Pair(currentWidth, currentHeight)
    }

    /**
     * Cleanup swapchain resources.
     */
    fun dispose() {
        if (configured) {
            try {
                context.unconfigure()
                configured = false
            } catch (e: Exception) {
                // Ignore errors on cleanup
            } catch (e: Throwable) {
                // Ignore errors on cleanup
            }
        }
    }
}
