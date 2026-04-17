package io.materia.examples.triangle

import io.materia.examples.benchmarks.BenchmarkCapture
import io.materia.examples.benchmarks.BenchmarkDefaults
import io.materia.examples.benchmarks.BenchmarkMemoryMetricKind
import io.materia.examples.benchmarks.BenchmarkRecorder
import io.materia.examples.benchmarks.BenchmarkRunConfig
import io.materia.examples.benchmarks.benchmarkCapturePath
import io.materia.examples.benchmarks.buildJvmBenchmarkEnvironment
import io.materia.examples.benchmarks.currentJvmHeapUsageMb
import io.materia.examples.benchmarks.withHiddenGlfwWindow
import io.materia.examples.benchmarks.writeBenchmarkCapture
import io.materia.gpu.GpuBackend
import io.materia.gpu.initializeGpuContext
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.glfwPollEvents
import java.nio.file.Path

private const val NANOS_PER_MILLISECOND: Double = 1_000_000.0

suspend fun runTriangleJvmBenchmarkCapture(
    config: BenchmarkRunConfig,
    repeatIndex: Int
): BenchmarkCapture {
    val workload = BenchmarkDefaults.triangleWorkload(config)
    val baselineHeapMb = currentJvmHeapUsageMb()
    val recorder = BenchmarkRecorder(
        repeatIndex = repeatIndex,
        baselineHeapMb = baselineHeapMb,
        memoryMetricKind = BenchmarkMemoryMetricKind.JVM_HEAP_DELTA_MB,
        notes = listOf("Hidden GLFW surface")
    )

    return withHiddenGlfwWindow(config.width, config.height, "Triangle Benchmark") { window ->
        val example = TriangleExample(preferredBackends = listOf(GpuBackend.VULKAN))
        val bootStartNanos = System.nanoTime()
        val surface = SurfaceFactory.create(window)
        initializeGpuContext(surface)
        val boot = runBlocking {
            example.boot(
                renderSurface = surface,
                widthOverride = config.width,
                heightOverride = config.height
            )
        }
        val bootToFirstFrameMs = (System.nanoTime() - bootStartNanos) / NANOS_PER_MILLISECOND

        try {
            repeat(config.warmupFrames) {
                glfwPollEvents()
                val iterationStart = System.nanoTime()
                boot.renderFrame()
                val frameTimeMs = (System.nanoTime() - iterationStart) / NANOS_PER_MILLISECOND
                recorder.observeHeapUsage(currentJvmHeapUsageMb())
            }

            repeat(config.measuredFrames) {
                glfwPollEvents()
                val iterationStart = System.nanoTime()
                boot.renderFrame()
                val frameTimeMs = (System.nanoTime() - iterationStart) / NANOS_PER_MILLISECOND
                recorder.recordFrame(frameTimeMs, currentJvmHeapUsageMb())
            }

            BenchmarkCapture(
                workload = workload,
                environment = buildJvmBenchmarkEnvironment(
                    backend = "VULKAN",
                    deviceName = boot.log.deviceName,
                    driverVersion = boot.log.driverVersion,
                    notes = listOf("Hidden GLFW surface", "High-performance adapter requested")
                ),
                sample = recorder.build(bootToFirstFrameMs)
            )
        } finally {
            boot.dispose()
        }
    }
}

fun main(args: Array<String>) = runBlocking {
    val rawDir = Path.of(args.firstOrNull() ?: "docs/benchmarks/data/raw")
    val config = BenchmarkDefaults.runConfig
    repeat(config.repeats) { index ->
        val capture = runTriangleJvmBenchmarkCapture(config, index + 1)
        writeBenchmarkCapture(
            benchmarkCapturePath(rawDir, capture.workload.scene, "JVM", index + 1),
            capture
        )
    }
}
