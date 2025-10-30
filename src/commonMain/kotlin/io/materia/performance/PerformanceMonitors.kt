/**
 * Performance monitoring components for adaptive rendering
 */
package io.materia.performance

/**
 * Hardware detector
 */
class HardwareDetector {
    fun detect(): HardwareInfo {
        return detectHardware()
    }
}

/**
 * GPU profiler
 */
class GPUProfiler {
    fun profile(): GPUCapabilities {
        return profileGPU()
    }

    fun getVRAMUsage(): Float {
        return getCurrentVRAMUsage()
    }
}

/**
 * Performance monitor
 */
class PerformanceMonitor {
    private val frameTimesBuffer = ArrayDeque<Float>(60)
    private var lastFrameTime = 0L

    fun getCurrentFPS(): Float {
        return if (frameTimesBuffer.isNotEmpty()) {
            1000f / frameTimesBuffer.average().toFloat()
        } else 60f
    }

    fun getAverageFrameTime(): Float {
        return frameTimesBuffer.average().toFloat()
    }

    fun getCPUTime(): Float {
        return measureCPUTime()
    }

    fun getGPUTime(): Float {
        return measureGPUTime()
    }

    fun getDrawCalls(): Int {
        return countDrawCalls()
    }

    fun getTriangleCount(): Int {
        return countTriangles()
    }

    fun recordFrame(deltaTime: Float) {
        frameTimesBuffer.addLast(deltaTime)
        if (frameTimesBuffer.size > 60) {
            frameTimesBuffer.removeFirst()
        }
    }
}

/**
 * Thermal monitor
 */
class ThermalMonitor {
    fun getCurrentTemperature(): Float {
        return getSystemTemperature()
    }

    fun getPowerUsage(): Float {
        return getSystemPowerUsage()
    }
}

/**
 * Memory monitor
 */
class MemoryMonitor {
    fun getCurrentUsage(): Float {
        return getMemoryUsage()
    }

    fun getAvailableMemory(): Long {
        return getAvailableSystemMemory()
    }
}

// Platform-specific expect declarations
expect fun detectHardware(): HardwareInfo
expect fun profileGPU(): GPUCapabilities
expect fun getCurrentVRAMUsage(): Float
expect fun measureCPUTime(): Float
expect fun measureGPUTime(): Float
expect fun countDrawCalls(): Int
expect fun countTriangles(): Int
expect fun getSystemTemperature(): Float
expect fun getSystemPowerUsage(): Float
expect fun getMemoryUsage(): Float
expect fun getAvailableSystemMemory(): Long
