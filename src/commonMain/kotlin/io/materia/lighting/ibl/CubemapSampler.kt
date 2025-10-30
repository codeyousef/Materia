/**
 * Cubemap sampling utilities for IBL processing
 */
package io.materia.lighting.ibl

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.renderer.CubeTexture
import kotlin.math.*

/**
 * Handles cubemap sampling and coordinate conversions
 */
internal object CubemapSampler {

    /**
     * Sample cubemap at given direction
     */
    fun sampleCubemap(cubemap: CubeTexture, direction: Vector3): Color {
        val absX = abs(direction.x)
        val absY = abs(direction.y)
        val absZ = abs(direction.z)

        val (faceIndex, u, v) = when {
            absX >= absY && absX >= absZ -> {
                val invX = if (absX > 0.000001f) 1f / absX else 0f
                if (direction.x > 0) {
                    Triple(0, -direction.z * invX, -direction.y * invX)
                } else {
                    Triple(1, direction.z * invX, -direction.y * invX)
                }
            }

            absY >= absX && absY >= absZ -> {
                val invY = if (absY > 0.000001f) 1f / absY else 0f
                if (direction.y > 0) {
                    Triple(2, direction.x * invY, direction.z * invY)
                } else {
                    Triple(3, direction.x * invY, -direction.z * invY)
                }
            }

            else -> {
                val invZ = if (absZ > 0.000001f) 1f / absZ else 0f
                if (direction.z > 0) {
                    Triple(4, direction.x * invZ, -direction.y * invZ)
                } else {
                    Triple(5, -direction.x * invZ, -direction.y * invZ)
                }
            }
        }

        val s = (u + 1f) * 0.5f
        val t = (v + 1f) * 0.5f

        return (cubemap as? io.materia.texture.CubeTexture)?.sampleFace(faceIndex, s, t)
            ?.let { result ->
                Color(result.x, result.y, result.z)
            } ?: Color.WHITE
    }

    /**
     * Sample cubemap with LOD level for mipmapping
     */
    fun sampleCubemapLOD(cubemap: CubeTexture, direction: Vector3, lod: Float): Color {
        val mipLevel = lod.toInt()
        val mipFraction = lod - mipLevel

        val color1 = sampleCubemap(cubemap, direction)

        if (mipFraction > 0.01f && mipLevel < (cubemap as? io.materia.texture.CubeTexture)?.getMipLevelCount()
                .let { it ?: 1 } - 1
        ) {
            val color2 = sampleCubemap(cubemap, direction)
            return Color(
                color1.r * (1f - mipFraction) + color2.r * mipFraction,
                color1.g * (1f - mipFraction) + color2.g * mipFraction,
                color1.b * (1f - mipFraction) + color2.b * mipFraction,
                color1.a
            )
        }

        return color1
    }

    /**
     * Convert cube face UV to direction
     */
    fun cubeFaceUVToDirection(face: Int, u: Float, v: Float): Vector3 {
        val dir = when (face) {
            0 -> Vector3(1f, -v, -u)
            1 -> Vector3(-1f, -v, u)
            2 -> Vector3(u, 1f, v)
            3 -> Vector3(u, -1f, -v)
            4 -> Vector3(u, -v, 1f)
            5 -> Vector3(-u, -v, -1f)
            else -> Vector3(0f, 0f, 1f)
        }

        val dirLength = dir.length()
        return if (dirLength > 0.001f) {
            dir.normalize()
        } else {
            // Fallback to forward direction if degenerate
            Vector3(0f, 0f, 1f)
        }
    }

    /**
     * Convert cube face and spherical coordinates to direction
     */
    fun cubeFaceToDirection(face: Int, theta: Float, phi: Float): Vector3 {
        val x = sin(theta) * cos(phi)
        val y = cos(theta)
        val z = sin(theta) * sin(phi)

        val dir = when (face) {
            0 -> Vector3(1f, -y, -x)
            1 -> Vector3(-1f, -y, x)
            2 -> Vector3(x, 1f, y)
            3 -> Vector3(x, -1f, -y)
            4 -> Vector3(x, -y, 1f)
            5 -> Vector3(-x, -y, -1f)
            else -> Vector3(0f, 0f, 1f)
        }

        val dirLength = dir.length()
        return if (dirLength > 0.001f) {
            dir.normalize()
        } else {
            // Fallback to forward direction if degenerate
            Vector3(0f, 0f, 1f)
        }
    }
}
