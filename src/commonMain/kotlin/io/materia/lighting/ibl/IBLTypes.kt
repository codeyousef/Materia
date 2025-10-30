/**
 * Core types and data structures for Image-Based Lighting
 */
package io.materia.lighting.ibl

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.renderer.CubeTexture
import io.materia.renderer.Texture

/**
 * Result type for IBL operations
 */
sealed class IBLResult<out T> {
    data class Success<T>(val data: T) : IBLResult<T>()
    data class Error(val message: String) : IBLResult<Nothing>()
}

/**
 * HDR Environment data
 */
data class HDREnvironment(
    val data: FloatArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HDREnvironment) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * IBL Configuration
 */
data class IBLConfig(
    val irradianceSize: Int = 32,
    val prefilterSize: Int = 128,
    val brdfLutSize: Int = 512,
    val roughnessLevels: Int = 5,
    val samples: Int = 1024
)

/**
 * IBL Environment Maps
 */
data class IBLEnvironmentMaps(
    val environment: CubeTexture,
    val irradiance: CubeTexture,
    val prefilter: CubeTexture,
    val brdfLut: Texture
)

/**
 * Spherical harmonics interface
 */
interface SphericalHarmonics {
    val coefficients: Array<Vector3>
    fun evaluate(direction: Vector3): Vector3
}

/**
 * Spherical harmonics implementation
 */
internal data class IBLSphericalHarmonics(
    override val coefficients: Array<Vector3>
) : SphericalHarmonics {

    override fun evaluate(direction: Vector3): Vector3 {
        val sh = evaluateBasis(direction)
        var result = Vector3.ZERO

        for (i in 0 until 9) {
            result = result + coefficients[i] * sh[i]
        }

        return result
    }

    private fun evaluateBasis(direction: Vector3): FloatArray {
        val x = direction.x
        val y = direction.y
        val z = direction.z

        return floatArrayOf(
            0.282095f,
            0.488603f * y,
            0.488603f * z,
            0.488603f * x,
            1.092548f * (x * y),
            1.092548f * (y * z),
            0.315392f * (3f * z * z - 1f),
            1.092548f * (x * z),
            0.546274f * (x * x - (y * y))
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IBLSphericalHarmonics) return false
        return coefficients.contentEquals(other.coefficients)
    }

    override fun hashCode(): Int {
        return coefficients.contentHashCode()
    }
}
