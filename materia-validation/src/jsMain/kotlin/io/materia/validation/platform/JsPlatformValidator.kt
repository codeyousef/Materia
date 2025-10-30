package io.materia.validation.platform

/**
 * JavaScript platform-specific validation utilities.
 *
 * Provides helpers for validating JavaScript/Browser-specific functionality:
 * - WebGPU/WebGL API availability
 * - Browser environment detection
 * - Node.js runtime validation
 * - JavaScript engine capabilities
 */
object JsPlatformValidator {

    /**
     * Validates that the JavaScript environment supports required WebGPU/WebGL features.
     */
    fun validateWebGpuSupport(): Boolean {
        // Check if running in browser vs Node.js
        val isBrowser = js("typeof window !== 'undefined'") as Boolean

        if (!isBrowser) {
            // Node.js environment - WebGPU not applicable
            return true
        }

        // Check for WebGPU support
        val hasWebGPU = js("typeof navigator !== 'undefined' && 'gpu' in navigator") as Boolean

        // Check for WebGL fallback
        val hasWebGL = js(
            """
            (function() {
                try {
                    var canvas = document.createElement('canvas');
                    return !!(canvas.getContext('webgl') || canvas.getContext('experimental-webgl'));
                } catch(e) {
                    return false;
                }
            })()
        """
        ) as Boolean

        return hasWebGPU || hasWebGL
    }

    /**
     * Detects the JavaScript engine being used.
     */
    fun detectJavaScriptEngine(): String {
        return when {
            js("typeof process !== 'undefined' && process.versions && process.versions.v8") as Boolean -> "V8 (Node.js)"
            js("typeof navigator !== 'undefined' && navigator.userAgent") as Boolean -> {
                val userAgent = js("navigator.userAgent") as String
                when {
                    userAgent.contains("Chrome") -> "V8 (Chrome)"
                    userAgent.contains("Firefox") -> "SpiderMonkey"
                    userAgent.contains("Safari") -> "JavaScriptCore"
                    else -> "Unknown Browser Engine"
                }
            }

            else -> "Unknown JS Engine"
        }
    }

    /**
     * Checks if WebAssembly is supported.
     */
    fun hasWebAssemblySupport(): Boolean {
        return js("typeof WebAssembly === 'object'") as Boolean
    }

    /**
     * Checks if SharedArrayBuffer is supported (required for multithreading).
     */
    fun hasSharedArrayBufferSupport(): Boolean {
        return js("typeof SharedArrayBuffer === 'function'") as Boolean
    }
}