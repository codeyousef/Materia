/**
 * Additional IBL (Image-Based Lighting) types
 * Note: Main types are in IBLProcessor.kt
 */
package io.materia.lighting

import io.materia.core.math.Vector3
import io.materia.core.math.Color

/**
 * IBL exception types
 */
sealed class IBLException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HDRLoadingFailed(url: String, cause: Throwable) :
        IBLException("Failed to load HDR from $url", cause)

    class CubemapGenerationFailed(message: String, cause: Throwable) :
        IBLException(message, cause)

    class ProcessingFailed(message: String, cause: Throwable) :
        IBLException(message, cause)
}

/**
 * Capture result for light probes
 */
sealed class CaptureResult<out T> {
    data class Success<T>(val value: T) : CaptureResult<T>()
    data class Error(val message: String) : CaptureResult<Nothing>()
}

/**
 * Light probe capture exception
 */
class CaptureUpgradeFailed(message: String) : Exception(message)

/**
 * Spherical Harmonics order constants
 */
object SHOrder {
    const val SH_L0 = 0
    const val SH_L1 = 1
    const val SH_L2 = 2
}