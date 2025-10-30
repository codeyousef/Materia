package io.materia.lighting.shadow

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.lighting.*
import io.materia.renderer.Texture
import io.materia.renderer.CubeTexture
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureFilter

/**
 * Shadow quality levels
 */
enum class ShadowQuality {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA
}

/**
 * Light frustum for shadow calculations
 */
data class LightFrustum(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
    val near: Float,
    val far: Float
)

/**
 * Shadow map implementation
 */
internal data class ShadowMapImpl(
    override val texture: Texture,
    override val lightSpaceMatrix: Matrix4,
    override val near: Float,
    override val far: Float,
    override val bias: Float
) : ShadowMap

/**
 * Shadow cascade implementation
 */
internal data class ShadowCascadeImpl(
    override val texture: Texture,
    override val projectionViewMatrix: Matrix4,
    override val splitDistance: Float
) : ShadowCascade

/**
 * Cascaded shadow map implementation
 */
internal data class CascadedShadowMapImpl(
    override val cascades: List<ShadowCascade>,
    val splitDistances: FloatArray,
    val texture: Texture,
    val lightSpaceMatrix: Matrix4,
    val near: Float,
    val far: Float,
    val bias: Float
) : CascadedShadowMap {

    fun getCascadeForDepth(depth: Float): Int {
        for (i in 0 until splitDistances.size - 1) {
            if (depth >= splitDistances[i] && depth < splitDistances[i + 1]) {
                return i
            }
        }
        return splitDistances.size - 2
    }

    fun getCascadeMatrix(index: Int): Matrix4 {
        return if (index < cascades.size) cascades[index].projectionViewMatrix else Matrix4.identity()
    }
}

/**
 * Cube shadow map implementation
 */
internal data class CubeShadowMapImpl(
    val textures: Array<Texture>,
    val lightPosition: Vector3,
    override val near: Float,
    override val far: Float
) : CubeShadowMap {
    override val cubeTexture: CubeTexture = CubeTextureImpl(
        size = 256,
        format = TextureFormat.RGBA8,
        filter = TextureFilter.LINEAR
    )

    override val matrices: Array<Matrix4> = Array(6) { Matrix4.identity() }
}

/**
 * Simple cube texture implementation
 */
private data class CubeTextureImpl(
    override val size: Int,
    val format: TextureFormat,
    val filter: TextureFilter
) : CubeTexture {
    override val id: Int = (kotlin.random.Random.nextFloat() * 10000).toInt()
    override var needsUpdate: Boolean = true
    override val width: Int = size
    override val height: Int = size

    override fun dispose() {
        // Dispose implementation
    }
}
