/**
 * WebGPUCanvasConfig - Configuration and state management for WebGPU canvas
 * 
 * Provides configuration options and state management for WebGPU canvas initialization.
 * The actual WebGPU initialization is platform-specific (JS only), but this module
 * provides the common configuration and state management.
 * 
 * Usage:
 * ```kotlin
 * val config = WebGPUCanvasConfig(
 *     options = WebGPUCanvasOptions(
 *         alphaMode = AlphaMode.PREMULTIPLIED,
 *         powerPreference = PowerPreference.HIGH_PERFORMANCE
 *     )
 * )
 * 
 * config.onResize = { width, height ->
 *     effect.updateUniforms {
 *         set("resolution", width.toFloat(), height.toFloat())
 *     }
 * }
 * 
 * // Platform-specific initialization would use these config values
 * ```
 */
package io.materia.effects

/**
 * Alpha compositing mode for the canvas
 */
enum class AlphaMode {
    /** Canvas is fully opaque, alpha channel is ignored */
    OPAQUE,
    /** Standard alpha blending with premultiplied alpha */
    PREMULTIPLIED
}

/**
 * GPU power preference for adapter selection
 */
enum class PowerPreference {
    /** Prefer high-performance discrete GPU */
    HIGH_PERFORMANCE,
    /** Prefer low-power integrated GPU (better for battery) */
    LOW_POWER
}

/**
 * Common texture formats
 */
enum class TextureFormat(val value: String) {
    /** Standard BGRA format (preferred on most platforms) */
    BGRA8_UNORM("bgra8unorm"),
    /** Standard RGBA format */
    RGBA8_UNORM("rgba8unorm"),
    /** RGBA with sRGB encoding */
    RGBA8_UNORM_SRGB("rgba8unorm-srgb"),
    /** BGRA with sRGB encoding */
    BGRA8_UNORM_SRGB("bgra8unorm-srgb")
}

/**
 * Configuration options for WebGPU canvas
 */
data class WebGPUCanvasOptions(
    /** Alpha compositing mode */
    val alphaMode: AlphaMode = AlphaMode.PREMULTIPLIED,
    /** GPU power preference for adapter selection */
    val powerPreference: PowerPreference = PowerPreference.HIGH_PERFORMANCE,
    /** Whether to automatically handle canvas resize */
    val handleResize: Boolean = true,
    /** Whether to respect device pixel ratio for high-DPI displays */
    val respectDevicePixelRatio: Boolean = true,
    /** Preferred texture format (null = use navigator.gpu.getPreferredCanvasFormat()) */
    val preferredFormat: TextureFormat? = null
)

/**
 * Current state of the canvas
 */
data class CanvasState(
    /** Logical width in CSS pixels */
    val width: Int,
    /** Logical height in CSS pixels */
    val height: Int,
    /** Device pixel ratio (1.0 for standard displays, 2.0 for retina, etc.) */
    val devicePixelRatio: Float = 1.0f
) {
    /** Physical width in actual pixels */
    val physicalWidth: Int
        get() = (width * devicePixelRatio).toInt()
    
    /** Physical height in actual pixels */
    val physicalHeight: Int
        get() = (height * devicePixelRatio).toInt()
    
    /** Aspect ratio (width / height) */
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

/**
 * Result of WebGPU initialization
 */
sealed class InitResult {
    abstract val isSuccess: Boolean
    abstract val errorMessage: String?
    
    /** Initialization succeeded */
    object Success : InitResult() {
        override val isSuccess = true
        override val errorMessage: String? = null
    }
    
    /** WebGPU is not supported in the current environment */
    object WebGPUNotSupported : InitResult() {
        override val isSuccess = false
        override val errorMessage = "WebGPU is not supported in this browser"
    }
    
    /** Could not find a suitable GPU adapter */
    object AdapterNotFound : InitResult() {
        override val isSuccess = false
        override val errorMessage = "WebGPU adapter not found"
    }
    
    /** Device creation failed */
    data class DeviceCreationFailed(val reason: String) : InitResult() {
        override val isSuccess = false
        override val errorMessage = "Device creation failed: $reason"
    }
    
    /** Context configuration failed */
    data class ContextConfigFailed(val reason: String) : InitResult() {
        override val isSuccess = false
        override val errorMessage = "Context configuration failed: $reason"
    }
}

/**
 * Configuration and state manager for WebGPU canvas
 * 
 * This class manages the configuration options and tracks the current
 * canvas state. Actual WebGPU initialization is platform-specific.
 */
class WebGPUCanvasConfig(
    /** Configuration options */
    val options: WebGPUCanvasOptions = WebGPUCanvasOptions()
) {
    /** Current canvas state */
    var state: CanvasState = CanvasState(0, 0, 1.0f)
        private set
    
    /** Callback when canvas size changes */
    var onResize: ((width: Int, height: Int) -> Unit)? = null
    
    /**
     * Update the canvas state
     * Triggers onResize callback if size actually changed
     */
    fun updateState(width: Int, height: Int, devicePixelRatio: Float) {
        val newState = CanvasState(width, height, devicePixelRatio)
        
        // Check if physical size changed
        val sizeChanged = state.physicalWidth != newState.physicalWidth ||
                         state.physicalHeight != newState.physicalHeight
        
        state = newState
        
        if (sizeChanged) {
            onResize?.invoke(width, height)
        }
    }
    
    /**
     * Get physical dimensions as a pair
     */
    fun physicalSize(): Pair<Int, Int> = state.physicalWidth to state.physicalHeight
    
    /**
     * Get logical dimensions as a pair
     */
    fun logicalSize(): Pair<Int, Int> = state.width to state.height
}

/**
 * Builder for WebGPUCanvasOptions using DSL
 */
class WebGPUCanvasOptionsBuilder {
    var alphaMode: AlphaMode = AlphaMode.PREMULTIPLIED
    var powerPreference: PowerPreference = PowerPreference.HIGH_PERFORMANCE
    var handleResize: Boolean = true
    var respectDevicePixelRatio: Boolean = true
    var preferredFormat: TextureFormat? = null
    
    internal fun build(): WebGPUCanvasOptions = WebGPUCanvasOptions(
        alphaMode = alphaMode,
        powerPreference = powerPreference,
        handleResize = handleResize,
        respectDevicePixelRatio = respectDevicePixelRatio,
        preferredFormat = preferredFormat
    )
}

/**
 * DSL function to create WebGPUCanvasOptions
 */
fun webGPUCanvasOptions(block: WebGPUCanvasOptionsBuilder.() -> Unit): WebGPUCanvasOptions {
    val builder = WebGPUCanvasOptionsBuilder()
    builder.block()
    return builder.build()
}
