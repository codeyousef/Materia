package io.materia.renderer.backend

import kotlinx.serialization.Serializable

/**
 * Surface configuration for backend initialization.
 */
@Serializable
data class SurfaceConfig(
    val width: Int,
    val height: Int,
    val colorFormat: ColorFormat = ColorFormat.RGBA16F,
    val depthFormat: DepthFormat = DepthFormat.DEPTH24_STENCIL8,
    val presentMode: PresentMode = PresentMode.FIFO,
    val isXRSurface: Boolean = false
)

enum class ColorFormat {
    RGBA16F,
    BGRA8_UNORM
}

enum class DepthFormat {
    DEPTH24_STENCIL8,
    DEPTH32_FLOAT
}

enum class PresentMode {
    FIFO,
    MAILBOX,
    IMMEDIATE
}
