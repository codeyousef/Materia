package io.materia.renderer

import io.materia.texture.CubeTexture

/**
 * Render target for cube texture rendering.
 * Used by CubeCamera for environment mapping and reflections.
 *
 * @property width Width of each cube face
 * @property height Height of each cube face
 * @property cubeTexture The cube texture for 6-face rendering
 */
class CubeRenderTarget(
    override var width: Int,
    override var height: Int,
    override val depthBuffer: Boolean = true,
    override val stencilBuffer: Boolean = false
) : RenderTarget {

    /**
     * The cube texture containing all 6 faces
     */
    val cubeTexture: CubeTexture = CubeTexture(size = width)

    /**
     * Main texture reference (returns cube texture)
     */
    override val texture: Texture? = cubeTexture

    /**
     * Depth texture (optional)
     */
    override val depthTexture: Texture? = null

    /**
     * Active cube face index (0-5 for +X, -X, +Y, -Y, +Z, -Z)
     */
    var activeCubeFace: Int = 0
        set(value) {
            require(value in 0..5) { "Cube face index must be 0-5, got $value" }
            field = value
        }

    override fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun dispose() {
        cubeTexture.dispose()
    }

    companion object {
        /** Face names for debugging */
        val FACE_NAMES = listOf("+X", "-X", "+Y", "-Y", "+Z", "-Z")
    }
}
