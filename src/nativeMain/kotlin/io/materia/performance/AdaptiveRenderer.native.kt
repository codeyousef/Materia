package io.materia.performance

import io.materia.core.platform.currentTimeMillis

/**
 * Native implementation of performance monitoring functions
 */

actual fun detectHardware(): HardwareInfo {
    return HardwareInfo(
        platform = Platform.DESKTOP,
        cpuModel = "Unknown Native CPU",
        cpuCores = 4, // Default fallback
        cpuFrequency = 2.0f, // 2GHz default
        totalMemory = 1024L * 1024L * 1024L, // 1GB in bytes
        gpuModel = "Unknown Native GPU",
        displayResolution = Resolution(1920, 1080)
    )
}

actual fun profileGPU(): GPUCapabilities {
    return GPUCapabilities(
        vendor = "Unknown",
        renderer = "Native Renderer",
        vramSize = 256L * 1024L * 1024L, // 256MB in bytes
        computeUnits = 8,
        maxTextureSize = 4096,
        maxTextureUnits = 16,
        supportsComputeShaders = false,
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
    // Return current timestamp as CPU time approximation
    return currentTimeMillis().toFloat()
}

actual fun measureGPUTime(): Float {
    // GPU timing is not easily available on native platforms
    // Fallback to CPU timing
    return measureCPUTime()
}

actual fun countDrawCalls(): Int = 0 // Not easily accessible on native platforms

actual fun countTriangles(): Int = 0 // Not easily accessible on native platforms

actual fun getSystemTemperature(): Float = 25.0f // Default room temperature

actual fun getSystemPowerUsage(): Float = 0.0f // Not accessible on native platforms

actual fun getMemoryUsage(): Float = 0.0f // Limited access on native platforms

actual fun getAvailableSystemMemory(): Long = 1024L * 1024L * 1024L // 1GB default