package io.materia.examples.triangle.android

import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.materia.examples.benchmarks.AndroidBenchmarkSession
import io.materia.examples.benchmarks.BenchmarkDefaults
import io.materia.examples.benchmarks.buildAndroidBenchmarkEnvironment
import io.materia.examples.benchmarks.currentAndroidAppHeapEstimateMb
import io.materia.examples.benchmarks.emitAndroidBenchmarkCapture
import io.materia.examples.benchmarks.emitAndroidBenchmarkFailure
import io.materia.examples.benchmarks.readAndroidBenchmarkLaunch
import io.materia.examples.triangle.TriangleExample
import io.materia.gpu.AndroidVulkanAssets
import io.materia.gpu.GpuBackend
import io.materia.gpu.GpuPowerPreference
import io.materia.io.AndroidResourceLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class TriangleActivity : ComponentActivity() {

    private companion object {
        const val TAG = "TriangleActivity"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: TextView

    private val triangleExample = TriangleExample(
        preferredBackends = listOf(GpuBackend.VULKAN),
        powerPreference = GpuPowerPreference.HIGH_PERFORMANCE
    )
    private var benchmarkBootStartNanos: Long = 0L
    private var benchmarkBaselineHeapMb: Double? = null
    private var benchmarkSession: AndroidBenchmarkSession? = null

    private var triangleRuntime: DirectFilamentTriangleRuntime? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNs: Long = 0L
    private var headlessFallbackShown = false

    private val benchmarkLaunch by lazy(LazyThreadSafetyMode.NONE) {
        readAndroidBenchmarkLaunch(intent)
    }

    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            overlayView.text = "Booting Triangle example…"
            Log.i(TAG, "Surface ready; bootstrapping direct Filament triangle")
            initializeRenderer()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            triangleRuntime?.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            stopRenderLoop()
            triangleRuntime?.dispose()
            triangleRuntime = null
            benchmarkSession = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (benchmarkLaunch != null) {
            benchmarkBootStartNanos = System.nanoTime()
            benchmarkBaselineHeapMb = currentAndroidAppHeapEstimateMb()
        }

        AndroidVulkanAssets.initialise(applicationContext)
        AndroidResourceLoader.initialise(assets)

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(holderCallback)
            holder.setFormat(PixelFormat.OPAQUE)
            setZOrderOnTop(false)
        }

        overlayView = TextView(this).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            gravity = Gravity.START or Gravity.TOP
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x66000000)
            text = "Waiting for surface…"
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF121212.toInt())
            addView(
                surfaceView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                overlayView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(root)
    }

    override fun onDestroy() {
        surfaceView.holder.removeCallback(holderCallback)
        stopRenderLoop()
        triangleRuntime?.dispose()
        triangleRuntime = null
        benchmarkSession = null
        super.onDestroy()
    }

    private fun initializeRenderer() {
        if (triangleRuntime != null) {
            return
        }

        runCatching {
            DirectFilamentTriangleRuntime(surfaceView).also { runtime ->
                runtime.initialize()
                triangleRuntime = runtime
                overlayView.text = if (benchmarkLaunch == null) {
                    runtime.buildOverlayText()
                } else {
                    "Running Triangle benchmark…"
                }
                Log.i(TAG, "Renderer boot succeeded: backend=${runtime.backendName}, device=${runtime.deviceName}")
                startRenderLoop()
            }
        }.onFailure { error ->
            Log.e(TAG, "Renderer bootstrap failed", error)
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            val failureMessage = buildFailureMessage("Triangle", error)
            if (benchmarkLaunch != null) {
                emitAndroidBenchmarkFailure("Triangle", failureMessage, error)
                finish()
                return
            }
            overlayView.text = failureMessage
            launchHeadlessFallback(failureMessage)
        }
    }

    private fun startRenderLoop() {
        if (frameCallback != null) return
        val choreographer = Choreographer.getInstance()
        lastFrameTimeNs = System.nanoTime()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val runtime = triangleRuntime ?: return
                runtime.renderFrame(frameTimeNanos)

                val benchmark = benchmarkLaunch
                if (benchmark != null) {
                    if (!runtime.isReadyToRender) {
                        choreographer.postFrameCallback(this)
                        return
                    }

                    val activeSession = benchmarkSession
                    if (activeSession == null) {
                        benchmarkSession = AndroidBenchmarkSession(
                            repeatIndex = benchmark.repeatIndex,
                            workload = BenchmarkDefaults.triangleWorkload(benchmark.runConfig),
                            environment = buildAndroidBenchmarkEnvironment(
                                backend = runtime.backendName,
                                deviceName = runtime.deviceName,
                                driverVersion = runtime.driverVersion,
                                avdName = benchmark.avdName,
                                notes = listOf("Filament OpenGL path", "Host-GPU-assisted")
                            ),
                            baselineHeapMb = benchmarkBaselineHeapMb,
                            bootToFirstFrameMs = (System.nanoTime() - benchmarkBootStartNanos) / 1_000_000.0,
                            sampleNotes = listOf("Choreographer cadence")
                        )
                        lastFrameTimeNs = frameTimeNanos
                        choreographer.postFrameCallback(this)
                        return
                    }

                    val frameTimeMs = ((frameTimeNanos - lastFrameTimeNs) / 1_000_000.0).coerceAtLeast(0.0)
                    lastFrameTimeNs = frameTimeNanos
                    val capture = activeSession.onFrame(frameTimeMs)
                    if (capture != null) {
                        emitAndroidBenchmarkCapture(capture)
                        stopRenderLoop()
                        runtime.dispose()
                        triangleRuntime = null
                        finish()
                        return
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun stopRenderLoop() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    private fun buildFailureMessage(featureName: String, error: Throwable): String {
        val root = error.rootCause()
        if (root is TimeoutCancellationException) {
            return buildTimeoutMessage(featureName)
        }
        if (root is UnsupportedOperationException || root is UnsatisfiedLinkError) {
            val detail = root.message ?: root::class.simpleName ?: "Unknown error"
            return """
                $featureName requires Vulkan support, which was not detected on this device/emulator.
                • Switch to an x86_64 Android emulator with Vulkan enabled, or
                • Use a physical device that supports Vulkan 1.1.
                Details: $detail
            """.trimIndent()
        }

        return buildString {
            appendLine("$featureName renderer failed to start.")
            appendLine(root.message ?: root::class.simpleName ?: "Unknown error")
        }
    }

    private fun Throwable.rootCause(): Throwable {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun buildTimeoutMessage(featureName: String): String = """
        $featureName is taking too long to acquire Vulkan resources.
        • Use an x86_64 emulator with Vulkan graphics enabled, or
        • Switch to a physical device that supports Vulkan 1.1.
    """.trimIndent()

    private fun buildMissingSupportMessage(featureName: String): String = """
        $featureName requires Vulkan support, but this device/emulator does not advertise it.
        • Switch to an x86_64 Android emulator with Vulkan graphics enabled, or
        • Use a physical device that supports Vulkan 1.1.
        Showing headless stats instead.
    """.trimIndent()

    private fun launchHeadlessFallback(preface: String? = null) {
        if (headlessFallbackShown) return
        headlessFallbackShown = true
        lifecycleScope.launch {
            Log.i(TAG, "Launching headless fallback")
            val headless = runCatching {
                withContext(Dispatchers.Default) {
                    triangleExample.boot(renderSurface = null)
                }
            }.onFailure { Log.e(TAG, "Headless fallback failed", it) }.getOrNull()

            withContext(Dispatchers.Main) {
                overlayView.text = buildString {
                    preface?.let {
                        appendLine(it.trim())
                        appendLine()
                    }
                    appendLine("Headless fallback active – rendering disabled.")
                    headless?.log?.let { log ->
                        Log.i(TAG, "Headless boot succeeded: backend=${log.backend}, device=${log.deviceName}")
                        appendLine("Backend : ${log.backend}")
                        appendLine("Device  : ${log.deviceName}")
                        appendLine("Frame   : ${"%.2f".format(log.frameTimeMs)} ms")
                    } ?: appendLine("Unable to collect scene stats without a GPU surface.")
                }.trim()
            }
        }
    }
}
