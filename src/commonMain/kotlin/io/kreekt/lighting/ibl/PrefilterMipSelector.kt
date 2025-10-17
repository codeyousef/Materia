package io.kreekt.lighting.ibl

import kotlin.math.max
import kotlin.math.min

/**
 * Maps material roughness to the appropriate mip level for the prefiltered
 * environment cubemap. Keeps the logic shared between CPU sampling paths and
 * GPU pipelines to ensure consistent results across platforms.
 *
 * Roughness values are clamped to the [0, 1] range before conversion. The
 * mapping follows the common GGX recommendation of squaring the roughness
 * value to provide more precision for low-roughness highlights, then scaling
 * against the available mip range.
 */
object PrefilterMipSelector {

    /**
     * Converts a roughness value in [0, 1] to a fractional mip level within
     * `[0, mipCount - 1]`. Returns 0 when the cubemap does not expose any mips.
     *
     * @param roughness Material roughness in [0, 1].
     * @param mipCount Total number of mip levels generated for the cube map.
     */
    fun roughnessToMipLevel(roughness: Float, mipCount: Int): Float {
        val clampedMipCount = max(1, mipCount)
        if (clampedMipCount <= 1) return 0f

        val clampedRoughness = clamp01(roughness)
        val perceptual = clampedRoughness * clampedRoughness
        val maxLevel = (clampedMipCount - 1).toFloat()
        return min(maxLevel, perceptual * maxLevel)
    }

    /**
     * Utility used by tests and GPU uniform preparation to keep the clamp
     * logic symmetric between CPU and shader code.
     */
    fun clamp01(value: Float): Float = min(1f, max(0f, value))
}
