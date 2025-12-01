package io.materia.texture

import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap

/**
 * DepthTexture - Texture for depth buffer storage
 * T084 - Depth texture for shadow maps and post-processing
 *
 * Special texture type for storing depth values, commonly used for shadow mapping,
 * screen-space effects, and deferred rendering.
 */
class DepthTexture(
    override val width: Int,
    override val height: Int,
    val depthFormat: DepthTextureFormat = DepthTextureFormat.DEPTH24
) : Texture() {

    init {
        name = "DepthTexture"
        // Depth textures have specific settings
        // Map depth format to texture format
        // Note: The base TextureFormat enum serves as the storage container.
        // The actual GPU depth format is determined by the depthFormat field
        // and configured by the renderer during texture resource creation.
        this.format =
            TextureFormat.RGBA8  // Temporary; renderer uses depthFormat for actual GPU format

        this.type = when (depthFormat) {
            DepthTextureFormat.DEPTH16 -> TextureType.UNSIGNED_SHORT
            DepthTextureFormat.DEPTH24 -> TextureType.UNSIGNED_INT
            DepthTextureFormat.DEPTH32F -> TextureType.FLOAT
            DepthTextureFormat.DEPTH24_STENCIL8 -> TextureType.UNSIGNED_INT
            DepthTextureFormat.DEPTH32F_STENCIL8 -> TextureType.FLOAT
        }

        // Depth textures typically use specific filtering
        this.magFilter = TextureFilter.NEAREST
        this.minFilter = TextureFilter.NEAREST

        // Depth textures don't use mipmaps
        this.generateMipmaps = false

        // Clamp to edges for depth textures
        this.wrapS = TextureWrap.CLAMP_TO_EDGE
        this.wrapT = TextureWrap.CLAMP_TO_EDGE
    }

    /**
     * Check if this depth format includes stencil buffer
     */
    fun hasStencil(): Boolean {
        return depthFormat == DepthTextureFormat.DEPTH24_STENCIL8 ||
                depthFormat == DepthTextureFormat.DEPTH32F_STENCIL8
    }

    /**
     * Check if this is a floating-point depth format
     */
    fun isFloatingPoint(): Boolean {
        return depthFormat == DepthTextureFormat.DEPTH32F ||
                depthFormat == DepthTextureFormat.DEPTH32F_STENCIL8
    }

    /**
     * Get the bit depth of the depth component
     */
    fun getDepthBits(): Int {
        return when (depthFormat) {
            DepthTextureFormat.DEPTH16 -> 16
            DepthTextureFormat.DEPTH24 -> 24
            DepthTextureFormat.DEPTH32F -> 32
            DepthTextureFormat.DEPTH24_STENCIL8 -> 24
            DepthTextureFormat.DEPTH32F_STENCIL8 -> 32
        }
    }

    /**
     * Set depth comparison mode for shadow mapping
     */
    fun setCompareMode(enabled: Boolean, compareFunc: DepthCompareFunc = DepthCompareFunc.LEQUAL) {
        if (enabled) {
            this.magFilter = TextureFilter.LINEAR  // Enable PCF filtering
            this.minFilter = TextureFilter.LINEAR
        }
        // Platform-specific implementation would set texture compare mode
    }

    override fun clone(): DepthTexture = DepthTexture(width, height, depthFormat).apply {
        copy(this@DepthTexture)
    }
}

/**
 * Depth texture formats
 */
enum class DepthTextureFormat {
    DEPTH16,             // 16-bit depth
    DEPTH24,             // 24-bit depth
    DEPTH32F,            // 32-bit floating point depth
    DEPTH24_STENCIL8,    // 24-bit depth + 8-bit stencil
    DEPTH32F_STENCIL8    // 32-bit float depth + 8-bit stencil
}

/**
 * Depth comparison functions for shadow mapping
 */
enum class DepthCompareFunc {
    NEVER,
    LESS,
    EQUAL,
    LEQUAL,
    GREATER,
    NOTEQUAL,
    GEQUAL,
    ALWAYS
}

/**
 * Helper object for creating depth textures for common use cases
 */
object DepthTextureFactory {

    /**
     * Create a depth texture for shadow mapping
     */
    fun createShadowMapTexture(size: Int = 1024, highPrecision: Boolean = false): DepthTexture {
        val format = if (highPrecision) {
            DepthTextureFormat.DEPTH32F
        } else {
            DepthTextureFormat.DEPTH24
        }

        return DepthTexture(size, size, format).apply {
            name = "ShadowMap"
            // Shadow maps need special setup
            setCompareMode(true, DepthCompareFunc.LEQUAL)
        }
    }

    /**
     * Create a depth texture for deferred rendering G-buffer
     */
    fun createGBufferDepthTexture(
        width: Int,
        height: Int,
        includeStencil: Boolean = false
    ): DepthTexture {
        val format = if (includeStencil) {
            DepthTextureFormat.DEPTH24_STENCIL8
        } else {
            DepthTextureFormat.DEPTH24
        }

        return DepthTexture(width, height, format).apply {
            name = "GBufferDepth"
        }
    }

    /**
     * Create a high-precision depth texture for logarithmic depth buffer
     */
    fun createLogDepthTexture(width: Int, height: Int): DepthTexture {
        return DepthTexture(width, height, DepthTextureFormat.DEPTH32F).apply {
            name = "LogDepth"
        }
    }
}