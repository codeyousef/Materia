package io.materia.performance

import io.materia.core.platform.currentTimeMillis
import kotlinx.browser.window
import kotlin.js.Date

/**
 * JavaScript implementation of performance monitoring functions
 */

actual fun detectHardware(): HardwareInfo {
    return HardwareInfo(
        platform = Platform.WEB,
        cpuModel = "Unknown Browser CPU",
        cpuCores = (window.navigator.hardwareConcurrency ?: 4).toInt(),
        cpuFrequency = 2.0f, // Default fallback
        totalMemory = 1024L * 1024L * 1024L, // 1GB default
        gpuModel = "Unknown Browser GPU",
        displayResolution = Resolution(
            window.screen.width,
            window.screen.height
        )
    )
}

actual fun profileGPU(): GPUCapabilities {
    return GPUCapabilities(
        vendor = "Unknown",
        renderer = "WebGL Renderer",
        vramSize = 256L * 1024L * 1024L, // 256MB default
        computeUnits = 8,
        maxTextureSize = 4096,
        maxTextureUnits = 16,
        supportsComputeShaders = false, // Basic WebGL support
        supportsGeometryShaders = false,
        supportsTessellation = false,
        supportsRayTracing = false,
        supportsBindlessTextures = false,
        supportsMeshShaders = false
    )
}

actual fun getCurrentVRAMUsage(): Float {
    // Return estimated usage in MB
    return 64.0f
}

actual fun measureCPUTime(): Float {
    return Date.now().toFloat()
}

actual fun measureGPUTime(): Float {
    // GPU timing is limited in browsers, fallback to CPU timing
    return measureCPUTime()
}

actual fun countDrawCalls(): Int = 0 // Not easily accessible in browser

actual fun countTriangles(): Int = 0 // Not easily accessible in browser

actual fun getSystemTemperature(): Float = 25.0f // Default room temperature

actual fun getSystemPowerUsage(): Float = 0.0f // Not accessible in browser

actual fun getMemoryUsage(): Float = 0.0f // Limited access in browser

actual fun getAvailableSystemMemory(): Long = 1024L * 1024L * 1024L // 1GB default