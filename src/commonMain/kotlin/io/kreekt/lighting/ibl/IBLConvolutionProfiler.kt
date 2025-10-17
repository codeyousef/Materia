package io.kreekt.lighting.ibl

import io.kreekt.core.platform.currentTimeMillis
import kotlinx.atomicfu.atomic
import kotlin.math.max

/**
 * Lightweight profiler for the CPU-side IBL convolution pipeline. Collects
 * timing information for irradiance and prefilter passes so that renderers can
 * surface the information in performance HUDs or stats panels.
 */
object IBLConvolutionProfiler {
    private val prefilterMs = atomic(0.0)
    private val irradianceMs = atomic(0.0)
    private val prefilterSamples = atomic(0)
    private val irradianceSamples = atomic(0)
    private val prefilterSize = atomic(0)
    private val irradianceSize = atomic(0)
    private val prefilterMipCount = atomic(0)
    private val lastTimestamp = atomic(0L)

    /**
     * Records the latest prefilter pass metrics.
     */
    fun recordPrefilter(durationMs: Double, size: Int, mipCount: Int, sampleCount: Int) {
        prefilterMs.value = durationMs
        prefilterSize.value = size
        prefilterMipCount.value = max(1, mipCount)
        prefilterSamples.value = sampleCount
        lastTimestamp.value = currentTimeMillis()
    }

    /**
     * Records the latest irradiance pass metrics.
     */
    fun recordIrradiance(durationMs: Double, size: Int, sampleCount: Int) {
        irradianceMs.value = durationMs
        irradianceSize.value = size
        irradianceSamples.value = sampleCount
        lastTimestamp.value = currentTimeMillis()
    }

    /**
     * Returns the most recent convolution profile snapshot.
     */
    fun snapshot(): IBLConvolutionMetrics = IBLConvolutionMetrics(
        prefilterMs = prefilterMs.value,
        irradianceMs = irradianceMs.value,
        prefilterSamples = prefilterSamples.value,
        irradianceSamples = irradianceSamples.value,
        prefilterSize = prefilterSize.value,
        irradianceSize = irradianceSize.value,
        prefilterMipCount = prefilterMipCount.value,
        timestamp = lastTimestamp.value
    )
}

/**
 * Data carrier for the latest IBL convolution metrics.
 */
data class IBLConvolutionMetrics(
    val prefilterMs: Double,
    val irradianceMs: Double,
    val prefilterSamples: Int,
    val irradianceSamples: Int,
    val prefilterSize: Int,
    val irradianceSize: Int,
    val prefilterMipCount: Int,
    val timestamp: Long
)
