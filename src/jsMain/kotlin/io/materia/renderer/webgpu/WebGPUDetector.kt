package io.materia.renderer.webgpu

/**
 * WebGPU availability detector.
 * FR-001: WebGPU detection.
 */
object WebGPUDetector {
    /**
     * Checks if WebGPU is available in the current browser.
     * @return true if navigator.gpu exists, false otherwise
     */
    fun isAvailable(): Boolean {
        return try {
            js("'gpu' in navigator").unsafeCast<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the GPU object from navigator if available.
     * @return GPU object or null if unavailable
     */
    fun getGPU(): GPU? {
        return try {
            if (isAvailable()) {
                js("navigator.gpu").unsafeCast<GPU>()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
