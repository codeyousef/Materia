package io.materia.performance

import android.os.Build
import android.os.SystemClock

actual fun detectHardware(): HardwareInfo {
    return HardwareInfo(
        platform = Platform.MOBILE,
        cpuModel = Build.HARDWARE ?: "Unknown CPU",
        cpuCores = Runtime.getRuntime().availableProcessors(),
        cpuFrequency = 2.0f,
        totalMemory = Runtime.getRuntime().maxMemory(),
        gpuModel = Build.BOARD ?: "Android GPU",
        displayResolution = Resolution(1080, 1920)
    )
}

actual fun profileGPU(): GPUCapabilities {
    return GPUCapabilities(
        vendor = Build.MANUFACTURER ?: "Unknown",
        renderer = Build.MODEL ?: "Android Device",
        vramSize = 512L * 1024L * 1024L,
        computeUnits = 4,
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

actual fun getCurrentVRAMUsage(): Float = 0f

actual fun measureCPUTime(): Float = SystemClock.elapsedRealtimeNanos() / 1_000_000f

actual fun measureGPUTime(): Float = 0f

actual fun countDrawCalls(): Int = 0

actual fun countTriangles(): Int = 0

actual fun getSystemTemperature(): Float = 0f

actual fun getSystemPowerUsage(): Float = 0f

actual fun getMemoryUsage(): Float {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    return usedMemory.toFloat() / (1024 * 1024)
}

actual fun getAvailableSystemMemory(): Long = Runtime.getRuntime().freeMemory()
