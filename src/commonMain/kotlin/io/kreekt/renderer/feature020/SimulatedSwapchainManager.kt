package io.kreekt.renderer.feature020

/**
 * Simple in-memory [SwapchainManager] implementation used for unit tests
 * and platforms where a full GPU swapchain is not required.
 */
class SimulatedSwapchainManager(
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : SwapchainManager {

    init {
        require(initialWidth > 0) { "Initial swapchain width must be > 0, got $initialWidth" }
        require(initialHeight > 0) { "Initial swapchain height must be > 0, got $initialHeight" }
    }

    private var width: Int = initialWidth
    private var height: Int = initialHeight
    private var nextImageIndex: Int = 0
    private var lastAcquired: SwapchainImage? = null

    override fun acquireNextImage(): SwapchainImage {
        val image = SwapchainImage(
            handle = "swapchain-image-${nextImageIndex}",
            index = nextImageIndex,
            ready = true
        )
        lastAcquired = image
        nextImageIndex = (nextImageIndex + 1) % Int.MAX_VALUE
        return image
    }

    override fun presentImage(image: SwapchainImage) {
        val acquired = lastAcquired
            ?: throw SwapchainException("No swapchain image acquired before presentImage()")

        if (acquired.index != image.index) {
            throw SwapchainException("Presented image index ${image.index} does not match last acquired index ${acquired.index}")
        }
        if (!image.isReady()) {
            throw SwapchainException("Cannot present swapchain image that is not ready")
        }
        // Present succeeds - reset acquired image reference.
        lastAcquired = null
    }

    override fun recreateSwapchain(width: Int, height: Int) {
        require(width > 0) { "Swapchain width must be greater than 0, got $width" }
        require(height > 0) { "Swapchain height must be greater than 0, got $height" }

        this.width = width
        this.height = height
        nextImageIndex = 0
        lastAcquired = null
    }

    override fun getExtent(): Pair<Int, Int> = width to height
}
