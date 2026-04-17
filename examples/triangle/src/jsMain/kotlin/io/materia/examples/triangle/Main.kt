package io.materia.examples.triangle

import io.materia.examples.benchmarks.BenchmarkCapture
import io.materia.examples.benchmarks.BenchmarkDefaults
import io.materia.examples.benchmarks.BenchmarkMemoryMetricKind
import io.materia.examples.benchmarks.BenchmarkRecorder
import io.materia.examples.benchmarks.BrowserBenchmarkParams
import io.materia.examples.benchmarks.BrowserBenchmarkWatchdog
import io.materia.examples.benchmarks.buildWebBenchmarkEnvironment
import io.materia.examples.benchmarks.configureBenchmarkCanvas
import io.materia.examples.benchmarks.currentJsHeapUsageSample
import io.materia.examples.benchmarks.postBenchmarkCapture
import io.materia.examples.benchmarks.postBenchmarkFailure
import io.materia.examples.benchmarks.readBrowserBenchmarkParams
import io.materia.examples.benchmarks.yieldBrowserFrame
import io.materia.gpu.initializeGpuContext
import io.materia.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement

private val console = js("console")

fun main() {
    val scope = MainScope()
    scope.launch {
        val benchmarkParams = readBrowserBenchmarkParams()
        if (benchmarkParams != null) {
            runTriangleBenchmark(benchmarkParams)
            return@launch
        }

        val canvas = ensureCanvas()
        
        // Ensure canvas has proper render dimensions (not just CSS size)
        val renderWidth = canvas.clientWidth.takeIf { it > 0 } ?: canvas.width.takeIf { it > 0 } ?: 1280
        val renderHeight = canvas.clientHeight.takeIf { it > 0 } ?: canvas.height.takeIf { it > 0 } ?: 720
        canvas.width = renderWidth
        canvas.height = renderHeight
        
        console.log("Canvas: render=${canvas.width}x${canvas.height}, client=${canvas.clientWidth}x${canvas.clientHeight}")
        
        val surface = WebGPUSurface(canvas)
        initializeGpuContext(surface)  // Pre-initialize wgpu4k context

        val example = TriangleExample()
        val result = example.boot(
            renderSurface = surface,
            widthOverride = canvas.width,
            heightOverride = canvas.height
        )
        val message = result.log.pretty()

        println(message)

        // Update the info overlay stats instead of appending a pre element
        document.getElementById("objects")?.textContent = "2"
        document.getElementById("renderer")?.textContent = "WebGPU"
        
        // Hide loading overlay
        document.getElementById("loading-overlay")?.let { 
            it.asDynamic().style.display = "none"
        }

        var frameCount = 0
        fun renderLoop(timestamp: Double) {
            result.renderFrame()
            frameCount++
            if (frameCount % 60 == 0) {
                console.log("Rendered $frameCount frames")
            }
            window.requestAnimationFrame(::renderLoop)
        }

        window.requestAnimationFrame(::renderLoop)

        window.onresize = {
            val width = canvas.clientWidth.takeIf { it > 0 } ?: canvas.width
            val height = canvas.clientHeight.takeIf { it > 0 } ?: canvas.height
            canvas.width = width
            canvas.height = height
            surface.resize(width, height)
            result.resize(width, height)
            result.renderFrame()
            null
        }
    }
}

private suspend fun runTriangleBenchmark(params: BrowserBenchmarkParams) {
    val canvas = ensureCanvas()
    configureBenchmarkCanvas(canvas, params.runConfig)

    runCatching {
        val watchdog = BrowserBenchmarkWatchdog(
            reportUrl = params.reportUrl,
            scene = "Triangle",
            maxDurationMs = params.maxDurationMs
        )
        watchdog.markStage("baseline_heap")
        val (baselineHeapMb, memoryMetricKind) = currentJsHeapUsageSample()
        val recorder = BenchmarkRecorder(
            repeatIndex = params.repeatIndex,
            baselineHeapMb = baselineHeapMb,
            memoryMetricKind = memoryMetricKind,
            notes = listOf("Direct render-loop timing")
        )

        val surface = WebGPUSurface(canvas)
        watchdog.markStage("initialize_gpu_context")
        val bootStart = window.performance.now()
        initializeGpuContext(surface)
        watchdog.markStage("boot_example")
        val result = TriangleExample().boot(
            renderSurface = surface,
            widthOverride = params.runConfig.width,
            heightOverride = params.runConfig.height
        )
        val bootToFirstFrameMs = window.performance.now() - bootStart

        try {
            watchdog.markStage("warmup_frames")
            repeat(params.runConfig.warmupFrames) {
                result.renderFrame()
                recorder.observeHeapUsage(currentJsHeapUsageSample().first)
                if ((it + 1) % 30 == 0) {
                    yieldBrowserFrame()
                }
            }

            watchdog.markStage("measured_frames")
            repeat(params.runConfig.measuredFrames) {
                val frameStart = window.performance.now()
                result.renderFrame()
                val frameTimeMs = window.performance.now() - frameStart
                recorder.recordFrame(frameTimeMs, currentJsHeapUsageSample().first)
                if ((it + 1) % 30 == 0) {
                    yieldBrowserFrame()
                }
            }

            watchdog.markStage("posting_results")
            postBenchmarkCapture(
                params.reportUrl,
                BenchmarkCapture(
                    workload = BenchmarkDefaults.triangleWorkload(params.runConfig),
                    environment = buildWebBenchmarkEnvironment(
                        backend = result.log.backend.name,
                        deviceName = result.log.deviceName,
                        driverVersion = result.log.driverVersion
                    ),
                    sample = recorder.build(bootToFirstFrameMs)
                )
            )
            watchdog.complete()
        } finally {
            result.dispose()
        }
    }.onFailure { error ->
        postBenchmarkFailure(
            params.reportUrl,
            scene = "Triangle",
            message = error.message ?: error::class.simpleName ?: "Unknown benchmark failure"
        )
        throw error
    }
}

private fun ensureCanvas(): HTMLCanvasElement {
    // Try to find the canvas from the HTML template first
    val existing = document.getElementById("materia-canvas") 
        ?: document.getElementById("triangle-canvas")
    if (existing is HTMLCanvasElement) {
        // Set proper dimensions if not already set
        if (existing.width == 0) existing.width = 640
        if (existing.height == 0) existing.height = 480
        return existing
    }

    val canvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
        id = "triangle-canvas"
        width = 640
        height = 480
        style.width = "640px"
        style.height = "480px"
        style.display = "block"
        style.margin = "24px auto"
        style.backgroundColor = "#000"
    }
    document.body?.appendChild(canvas)
    return canvas
}
