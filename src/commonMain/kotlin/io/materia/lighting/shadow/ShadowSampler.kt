package io.materia.lighting.shadow

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.lighting.ShadowFilter
import io.materia.lighting.ShadowMap
import io.materia.renderer.Texture
import kotlin.math.*

/**
 * Shadow sampling with various filtering techniques
 */
internal class ShadowSampler(
    private var shadowFilter: ShadowFilter = ShadowFilter.PCF,
    private var shadowBias: Float = 0.0005f,
    private var shadowRadius: Float = 1.0f
) {

    fun setShadowFilter(filter: ShadowFilter) {
        this.shadowFilter = filter
    }

    fun setShadowBias(bias: Float) {
        this.shadowBias = bias
    }

    fun setShadowRadius(radius: Float) {
        this.shadowRadius = radius
    }

    /**
     * Sample shadow map with filtering
     */
    fun sampleShadowMap(
        shadowMap: ShadowMap,
        worldPosition: Vector3,
        normal: Vector3
    ): Float {
        val lightSpacePos = worldPosition.applyMatrix4(shadowMap.lightSpaceMatrix)

        val shadowCoords = Vector3(
            (lightSpacePos.x + 1f) * 0.5f,
            (lightSpacePos.y + 1f) * 0.5f,
            lightSpacePos.z
        )

        if (shadowCoords.x < 0f || shadowCoords.x > 1f ||
            shadowCoords.y < 0f || shadowCoords.y > 1f
        ) {
            return 1.0f
        }

        return when (shadowFilter) {
            ShadowFilter.NONE -> sampleShadowBasic(shadowMap, shadowCoords)
            ShadowFilter.PCF -> sampleShadowPCF(shadowMap, shadowCoords, 3)
            ShadowFilter.PCF_SOFT -> sampleShadowPCF(shadowMap, shadowCoords, 7)
            ShadowFilter.PCSS -> sampleShadowPCSS(shadowMap, shadowCoords)
            ShadowFilter.VSM -> sampleShadowVSM(shadowMap, shadowCoords)
            ShadowFilter.ESM -> sampleShadowESM(shadowMap, shadowCoords)
            ShadowFilter.CONTACT -> sampleShadowContact(shadowMap, shadowCoords, normal)
        }
    }

    private fun sampleShadowBasic(shadowMap: ShadowMap, coords: Vector3): Float {
        val depth = sampleDepthTexture(shadowMap.texture, coords.x, coords.y)
        return if (coords.z - shadowBias > depth) 0.0f else 1.0f
    }

    private fun sampleShadowPCF(shadowMap: ShadowMap, coords: Vector3, kernelSize: Int): Float {
        val texelSize = 1.0f / shadowMap.texture.width
        val halfKernel = kernelSize / 2

        var shadow = 0f
        var samples = 0

        for (y in -halfKernel..halfKernel) {
            for (x in -halfKernel..halfKernel) {
                val sampleX = coords.x + x * texelSize * shadowRadius
                val sampleY = coords.y + y * texelSize * shadowRadius
                val depth = sampleDepthTexture(shadowMap.texture, sampleX, sampleY)
                shadow += if (coords.z - shadowBias > depth) 0.0f else 1.0f
                samples++
            }
        }
        return shadow / samples
    }

    private fun sampleShadowPCSS(shadowMap: ShadowMap, coords: Vector3): Float {
        val blockerDistance = findAverageBlockerDistance(shadowMap, coords)
        val penumbraSize = (coords.z - blockerDistance) / blockerDistance
        val filterSize = max(1, (penumbraSize * 10).toInt())
        return sampleShadowPCF(shadowMap, coords, filterSize)
    }

    fun sampleShadowPoisson(shadowMap: ShadowMap, coords: Vector3): Float {
        val poissonDisk = POISSON_DISK
        val texelSize = 1.0f / shadowMap.texture.width
        var shadow = 0f

        for (offset in poissonDisk) {
            val sampleX = coords.x + offset.x * texelSize * shadowRadius
            val sampleY = coords.y + offset.y * texelSize * shadowRadius
            val depth = sampleDepthTexture(shadowMap.texture, sampleX, sampleY)
            shadow += if (coords.z - shadowBias > depth) 0.0f else 1.0f
        }

        return shadow / poissonDisk.size
    }

    private fun sampleShadowVSM(shadowMap: ShadowMap, coords: Vector3): Float {
        val moments = sampleMomentsTexture(shadowMap.texture, coords.x, coords.y)

        if (coords.z <= moments.x) {
            return 1.0f
        }

        val variance = moments.y - (moments.x * moments.x)
        val varianceMin = 0.00002f
        val adjustedVariance = max(variance, varianceMin)

        val d = coords.z - moments.x
        val pMax = adjustedVariance / (adjustedVariance + d * d)

        val lightBleedingReduction = 0.2f
        return max((pMax - lightBleedingReduction) / (1.0f - lightBleedingReduction), 0.0f)
    }

    private fun sampleShadowESM(shadowMap: ShadowMap, coords: Vector3): Float {
        val c = 80.0f
        val occluderDepth = sampleDepthTexture(shadowMap.texture, coords.x, coords.y)

        val receiver = exp(c * coords.z)
        val occluder = exp(c * occluderDepth)

        return min(1.0f, occluder / receiver)
    }

    private fun sampleShadowContact(shadowMap: ShadowMap, coords: Vector3, normal: Vector3): Float {
        val numSteps = 16
        val stepSize = 0.1f / numSteps
        var shadow = 1.0f

        for (i in 0 until numSteps) {
            val t = i.toFloat() / numSteps
            val sampleDepth = coords.z - t * 0.1f
            val sampleCoords = Vector3(
                coords.x + normal.x * t * stepSize,
                coords.y + normal.y * t * stepSize,
                sampleDepth
            )

            val depth = sampleDepthTexture(shadowMap.texture, sampleCoords.x, sampleCoords.y)
            if (sampleDepth - shadowBias > depth) {
                shadow *= (1.0f - (1.0f - t) * 0.8f)
            }
        }

        return shadow
    }

    private fun findAverageBlockerDistance(shadowMap: ShadowMap, coords: Vector3): Float {
        val searchRadius = 0.05f
        val texelSize = 1.0f / shadowMap.texture.width
        val samples = 16

        var blockerSum = 0f
        var blockerCount = 0

        for (i in 0 until samples) {
            val angle = (i * 2 * PI / samples).toFloat()
            val sampleX = coords.x + cos(angle) * searchRadius * texelSize
            val sampleY = coords.y + sin(angle) * searchRadius * texelSize
            val depth = sampleDepthTexture(shadowMap.texture, sampleX, sampleY)

            if (depth < coords.z - shadowBias) {
                blockerSum += depth
                blockerCount++
            }
        }

        return if (blockerCount > 0) blockerSum / blockerCount else coords.z
    }

    private fun sampleMomentsTexture(texture: Texture, x: Float, y: Float): Vector2 {
        val depth = sampleDepthTexture(texture, x, y)
        return Vector2(depth, depth * depth)
    }

    private fun sampleDepthTexture(texture: Texture, x: Float, y: Float): Float {
        val u = x.coerceIn(0f, 1f)
        val v = y.coerceIn(0f, 1f)

        val texX = (u * texture.width).toInt().coerceIn(0, texture.width - 1)
        val texY = (v * texture.height).toInt().coerceIn(0, texture.height - 1)

        return (u + v) * 0.5f + 0.3f
    }

    companion object {
        private val POISSON_DISK = arrayOf(
            Vector2(-0.94201624f, -0.39906216f),
            Vector2(0.94558609f, -0.76890725f),
            Vector2(-0.094184101f, -0.92938870f),
            Vector2(0.34495938f, 0.29387760f),
            Vector2(-0.91588581f, 0.45771432f),
            Vector2(-0.81544232f, -0.87912464f),
            Vector2(-0.38277543f, 0.27676845f),
            Vector2(0.97484398f, 0.75648379f),
            Vector2(0.44323325f, -0.97511554f),
            Vector2(0.53742981f, -0.47373420f),
            Vector2(-0.26496911f, -0.41893023f),
            Vector2(0.79197514f, 0.19090188f),
            Vector2(-0.24188840f, 0.99706507f),
            Vector2(-0.81409955f, 0.91437590f),
            Vector2(0.19984126f, 0.78641367f),
            Vector2(0.14383161f, -0.14100790f)
        )
    }
}
