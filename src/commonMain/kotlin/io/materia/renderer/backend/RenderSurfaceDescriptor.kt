package io.materia.renderer.backend

import kotlinx.serialization.Serializable

/**
 * Defines the rendering surface/swapchain configuration for an active backend.
 * Created after successful backend initialization.
 *
 * @property surfaceId Unique identifier for this surface
 * @property backendId Backend that owns this surface
 * @property width Surface width in pixels
 * @property height Surface height in pixels
 * @property colorFormat Color buffer format
 * @property depthFormat Depth/stencil buffer format
 * @property presentMode Presentation mode for frame display
 * @property isXRSurface Whether this is an XR/VR surface
 */
@Serializable
data class RenderSurfaceDescriptor(
    val surfaceId: String,
    val backendId: BackendId,
    val width: Int,
    val height: Int,
    val colorFormat: ColorFormat = ColorFormat.RGBA16F,
    val depthFormat: DepthFormat = DepthFormat.DEPTH24_STENCIL8,
    val presentMode: PresentMode = PresentMode.FIFO,
    val isXRSurface: Boolean = false
) {
    init {
        require(width > 0) { "Width must be positive, got $width" }
        require(height > 0) { "Height must be positive, got $height" }
        require(surfaceId.isNotBlank()) { "Surface ID cannot be blank" }

        // XR surfaces require XR_SURFACE support in the backend profile
        if (isXRSurface) {
            // This will be validated during backend initialization
        }
    }

    /**
     * Get the aspect ratio of this surface.
     */
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    /**
     * Get total pixel count.
     */
    val pixelCount: Int get() = width * height

    /**
     * Check if dimensions match a target configuration.
     */
    fun matchesDimensions(targetWidth: Int, targetHeight: Int): Boolean {
        return width == targetWidth && height == targetHeight
    }
}
