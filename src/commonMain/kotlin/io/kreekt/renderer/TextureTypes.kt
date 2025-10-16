/**
 * Texture types and classes for the renderer
 */
package io.kreekt.renderer

import io.kreekt.core.math.*
import io.kreekt.renderer.Texture as BaseTexture
import io.kreekt.renderer.TextureFilter
import io.kreekt.renderer.TextureFormat
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * 2D Texture implementation
 */
class Texture2D(
    override val width: Int,
    override val height: Int,
    val format: TextureFormat = TextureFormat.RGBA8,
    val filter: TextureFilter = TextureFilter.LINEAR,
    val generateMipmaps: Boolean = false,
    textureName: String = "Texture2D"
) : BaseTexture {
    override val id: Int = generateId()
    val name: String = textureName
    override var needsUpdate: Boolean = true

    private var data: FloatArray? = null

    fun setData(data: FloatArray) {
        this.data = data
        needsUpdate = true
    }

    override fun dispose() {
        data = null
    }

    private companion object {
        private val nextId: AtomicInt = atomic(0)
        private fun generateId(): Int = nextId.incrementAndGet()
    }
}

/**
 * Cube texture for environment mapping
 */
class CubeTextureImpl(
    override val size: Int,
    val format: TextureFormat = TextureFormat.RGBA8,
    val filter: TextureFilter = TextureFilter.LINEAR,
    val generateMipmaps: Boolean = false,
    textureName: String = "CubeTexture"
) : io.kreekt.renderer.CubeTexture {
    override val id: Int = generateId()
    val name: String = textureName
    override var needsUpdate: Boolean = true

    override val width: Int get() = size
    override val height: Int get() = size

    private val faces = Array(6) { mutableMapOf<Int, FloatArray>() }

    fun setData(data: FloatArray) {
        // Set data for all faces
        val faceSize = size * size * 4
        for (i in 0..5) {
            faces[i][0] = data.sliceArray(i * faceSize until (i + 1) * faceSize)
        }
        needsUpdate = true
    }

    fun setFaceData(face: CubeFace, data: FloatArray, mip: Int = 0) {
        faces[face.ordinal][mip] = data
        needsUpdate = true
    }

    fun getFaceData(face: CubeFace, mip: Int = 0): FloatArray? = faces[face.ordinal][mip]

    fun maxMipLevel(): Int = faces.maxOfOrNull { entry -> entry.keys.maxOrNull() ?: 0 } ?: 0

    override fun dispose() {
        for (i in faces.indices) {
            faces[i].clear()
        }
    }

    private companion object {
        private val nextId: AtomicInt = atomic(0)
        private fun generateId(): Int = nextId.incrementAndGet()
    }
}

/**
 * Cube face enumeration
 */

/**
 * Extension to set face data on CubeTextureImpl using integer face index
 */
fun CubeTextureImpl.setFaceDataByIndex(face: Int, data: FloatArray, mip: Int = 0) {
    setFaceData(CubeFace.values()[face], data, mip)
}

enum class CubeFace {
    POSITIVE_X,
    NEGATIVE_X,
    POSITIVE_Y,
    NEGATIVE_Y,
    POSITIVE_Z,
    NEGATIVE_Z
}

// TextureFormat enum is defined in RendererCapabilities.kt

/**
 * Texture filtering modes
 */
enum class TextureFilter {
    NEAREST,
    LINEAR,
    NEAREST_MIPMAP_NEAREST,
    LINEAR_MIPMAP_NEAREST,
    NEAREST_MIPMAP_LINEAR,
    LINEAR_MIPMAP_LINEAR
}

/**
 * Texture wrapping modes
 */
enum class TextureWrap {
    REPEAT,
    CLAMP_TO_EDGE,
    MIRRORED_REPEAT
}
