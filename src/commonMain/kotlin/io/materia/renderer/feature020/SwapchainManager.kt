package io.materia.renderer.feature020

/**
 * Swapchain manager for presenting rendered frames.
 * Feature 020 - Production-Ready Renderer
 *
 * Provides cross-platform swapchain management for acquiring images,
 * presenting to screen, and handling window resize.
 */
interface SwapchainManager {
    /**
     * Acquire next swapchain image for rendering.
     *
     * Blocks until image available (vsync).
     *
     * @return Swapchain image ready for rendering
     * @throws SwapchainException if acquire fails
     */
    fun acquireNextImage(): SwapchainImage

    /**
     * Present rendered image to screen.
     *
     * @param image Swapchain image to present
     * @throws SwapchainException if present fails
     */
    fun presentImage(image: SwapchainImage)

    /**
     * Recreate swapchain on window resize.
     *
     * @param width New width in pixels (> 0)
     * @param height New height in pixels (> 0)
     * @throws IllegalArgumentException if width or height <= 0
     */
    fun recreateSwapchain(width: Int, height: Int)

    /**
     * Get current swapchain extent.
     *
     * @return Pair of (width, height) in pixels
     */
    fun getExtent(): Pair<Int, Int>
}

/**
 * Swapchain image for rendering.
 */
data class SwapchainImage(
    val handle: Any?,
    val index: Int,
    val ready: Boolean
) {
    fun isReady(): Boolean = ready
}

/**
 * Exception thrown when swapchain acquire/present fails.
 */
class SwapchainException(message: String) : Exception(message)
