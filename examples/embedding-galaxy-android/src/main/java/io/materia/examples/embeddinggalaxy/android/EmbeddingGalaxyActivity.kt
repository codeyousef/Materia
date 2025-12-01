package io.materia.examples.embeddinggalaxy.android

import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.materia.engine.render.UnlitPipelineFactory
import io.materia.examples.embeddinggalaxy.EmbeddingGalaxyBootResult
import io.materia.examples.embeddinggalaxy.EmbeddingGalaxyExample
import io.materia.examples.embeddinggalaxy.PerformanceProfile
import io.materia.gpu.AndroidVulkanAssets
import io.materia.gpu.GpuBackend
import io.materia.io.AndroidResourceLoader
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class EmbeddingGalaxyActivity : ComponentActivity() {

    private companion object {
        const val TAG = "EmbeddingGalaxyActivity"
        private const val BOOT_ATTEMPT_TIMEOUT_MS = 12_000L
        private const val BOOT_FALLBACK_DELAY_MS = 14_000L
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: TextView

    private val example = EmbeddingGalaxyExample(
        preferredBackends = listOf(GpuBackend.VULKAN),
        performanceProfile = PerformanceProfile.Mobile
    )

    private var bootResult: EmbeddingGalaxyBootResult? = null
    private var bootJob: Job? = null
    private var bootTimeoutJob: Job? = null
    private var bootAbandoned = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNs: Long = 0L
    private var overlayFrameCounter = 0
    private var headlessFallbackShown = false

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!AndroidVulkanAssets.hasVulkanSupport()) {
                val message = buildMissingSupportMessage("Embedding Galaxy")
                Log.w(TAG, "Vulkan not advertised; falling back headless")
                overlayView.text = message
                launchHeadlessFallback(message)
                return
            }
            overlayView.text = "Preparing surface…"
            Log.i(TAG, "Surface created; waiting for dimensions before initializing")

            // Post initialization with a small delay to ensure the surface is fully ready
            // This prevents race conditions where the surface exists but isn't fully initialized
            surfaceView.post {
                val width = surfaceView.width
                val height = surfaceView.height
                if (width > 0 && height > 0) {
                    overlayView.text = "Booting Embedding Galaxy…"
                    Log.i(
                        TAG,
                        "Surface ready (${width}x${height}); bootstrapping renderer"
                    )
                    bootAbandoned = false
                    headlessFallbackShown = false
                    bootResult = null
                    scheduleBootTimeout()
                    initialiseRenderer()
                } else {
                    // If dimensions aren't ready yet, try again with a slight delay
                    surfaceView.postDelayed({
                        overlayView.text = "Booting Embedding Galaxy…"
                        Log.i(TAG, "Surface ready (delayed); bootstrapping renderer")
                        bootAbandoned = false
                        headlessFallbackShown = false
                        bootResult = null
                        scheduleBootTimeout()
                        initialiseRenderer()
                    }, 100)
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, "Surface changed (format=$format, width=$width, height=$height)")
            bootResult?.runtime?.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.i(TAG, "Surface destroyed; stopping renderer")
            stopRenderLoop()
            bootResult?.runtime?.dispose()
            bootResult = null
            bootJob?.cancel()
            bootJob = null
            bootTimeoutJob?.cancel()
            bootTimeoutJob = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable quad-based point rendering fallback for Android
        // This is needed because some Android devices/emulators don't support
        // POINT_LIST primitive topology properly
        UnlitPipelineFactory.useQuadPointsFallback = true
        
        AndroidVulkanAssets.initialise(applicationContext)
        AndroidResourceLoader.initialise(assets)

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(surfaceCallback)
            setZOrderOnTop(false)
        }

        overlayView = TextView(this).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x66000000)
            text = "Waiting for surface…"
        }

        val root = FrameLayout(this).apply {
            addView(
                surfaceView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                overlayView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(root)
    }

    override fun onDestroy() {
        surfaceView.holder.removeCallback(surfaceCallback)
        stopRenderLoop()
        bootResult?.runtime?.dispose()
        bootResult = null
        bootJob?.cancel()
        bootJob = null
        bootTimeoutJob?.cancel()
        bootTimeoutJob = null
        super.onDestroy()
    }

    private fun initialiseRenderer() {
        val holder = surfaceView.holder
        bootJob?.cancel()
        bootJob = lifecycleScope.launch {
            Log.d(TAG, "initialiseRenderer coroutine started")
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val surface = SurfaceFactory.create(holder)
                    Log.d(
                        TAG,
                        "Created RenderSurface (width=${surface.width}, height=${surface.height}); waiting for example boot"
                    )
                    withTimeout(BOOT_ATTEMPT_TIMEOUT_MS) {
                        example.boot(
                            renderSurface = surface,
                            widthOverride = surfaceView.width.takeIf { it > 0 },
                            heightOverride = surfaceView.height.takeIf { it > 0 }
                        )
                    }
                }
            }

            result.onSuccess { boot ->
                if (bootAbandoned || headlessFallbackShown) {
                    Log.w(TAG, "Renderer boot completed after fallback; disposing result")
                    withContext(Dispatchers.Default) {
                        boot.runtime.dispose()
                    }
                    return@launch
                }
                bootTimeoutJob?.cancel()
                Log.i(TAG, "Renderer boot succeeded: backend=${boot.log.backend}, device=${boot.log.deviceName}")
                bootResult = boot
                overlayView.text = boot.log.pretty()
                boot.runtime.resize(
                    surfaceView.width.takeIf { it > 0 } ?: 1280,
                    surfaceView.height.takeIf { it > 0 } ?: 720
                )
                Log.d(
                    TAG,
                    "Renderer resized to ${surfaceView.width}x${surfaceView.height}; starting render loop"
                )
                startRenderLoop()
            }.onFailure { error ->
                bootTimeoutJob?.cancel()
                if (bootAbandoned && headlessFallbackShown) {
                    Log.w(TAG, "Renderer bootstrap failed after fallback already active", error)
                    return@launch
                }
                Log.e(TAG, "Renderer bootstrap failed", error)
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                val failureMessage = buildFailureMessage("Embedding Galaxy", error)
                overlayView.text = failureMessage
                launchHeadlessFallback(failureMessage)
            }
            Log.d(TAG, "initialiseRenderer coroutine finished")
        }
    }

    private fun scheduleBootTimeout() {
        bootTimeoutJob?.cancel()
        bootTimeoutJob = lifecycleScope.launch {
            delay(BOOT_FALLBACK_DELAY_MS)
            if (bootResult == null && !headlessFallbackShown) {
                bootAbandoned = true
                val timeoutMessage = buildTimeoutMessage("Embedding Galaxy")
                Log.w(TAG, "Renderer boot timed out; switching to headless fallback")
                overlayView.text = timeoutMessage
                stopRenderLoop()
                bootJob?.cancel()
                Log.d(TAG, "Boot job cancelled due to timeout; launching headless fallback")
                launchHeadlessFallback(timeoutMessage)
            }
        }
        Log.d(TAG, "Boot timeout scheduled for ${BOOT_FALLBACK_DELAY_MS}ms")
    }

    private fun startRenderLoop() {
        if (frameCallback != null) return
        val choreographer = Choreographer.getInstance()
        lastFrameTimeNs = System.nanoTime()
        overlayFrameCounter = 0
        Log.d(TAG, "Render loop starting")

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val boot = bootResult
                if (boot == null) {
                    frameCallback = null
                    Log.d(TAG, "Render loop callback exiting; boot result missing")
                    return
                }

                val deltaSeconds = ((frameTimeNanos - lastFrameTimeNs) / 1_000_000_000.0)
                    .toFloat()
                    .coerceIn(0f, 0.1f)
                lastFrameTimeNs = frameTimeNanos

                boot.runtime.frame(deltaSeconds)
                overlayFrameCounter++
                if (overlayFrameCounter % 20 == 0) {
                    updateOverlay(boot)
                }

                choreographer.postFrameCallback(this)
            }
        }

        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun stopRenderLoop() {
        Log.d(TAG, "Render loop stopping")
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    private fun updateOverlay(boot: EmbeddingGalaxyBootResult) {
        val runtime = boot.runtime
        val metrics = runtime.metrics()
        val fxaaState = if (runtime.fxaaEnabled) "on" else "off"
        overlayView.text = buildString {
            appendLine("🌌 Embedding Galaxy")
            appendLine("Backend : ${boot.log.backend}")
            appendLine("Device  : ${boot.log.deviceName}")
            appendLine("Quality : ${runtime.quality}")
            appendLine("Points  : ${runtime.activePointCount.formatCompact()}")
            appendLine("FXAA    : $fxaaState")
            appendLine("Frame   : ${metrics.frameTimeMs.formatMs()} ms")
        }
    }

    private fun Int.formatCompact(): String = when {
        this >= 1_000_000 -> String.format("%.1fM", this / 1_000_000.0)
        this >= 1_000 -> String.format("%.1fK", this / 1_000.0)
        else -> toString()
    }

    private fun Double.formatMs(): String = String.format("%.2f", this)

    private fun buildFailureMessage(featureName: String, error: Throwable): String {
        val root = error.rootCause()
        if (root is TimeoutCancellationException) {
            return buildTimeoutMessage(featureName)
        }
        if (root is UnsupportedOperationException || root is UnsatisfiedLinkError) {
            val detail = root.message ?: root::class.simpleName ?: "Unknown error"
            return """
                $featureName requires Vulkan access, which is unavailable on this device/emulator.
                • Ensure the Android emulator is an x86_64 image with Vulkan-enabled graphics, or
                • Run on a physical device that advertises Vulkan 1.1 support.
                Details: $detail
            """.trimIndent()
        }

        return buildString {
            appendLine("$featureName failed to start.")
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
        $featureName is taking too long to acquire a Vulkan surface.
        • Confirm the emulator is using an x86_64 system image with Vulkan graphics enabled.
        • If Vulkan is unavailable, run on a physical device or switch Rendering Mode to Automatic.
    """.trimIndent()

    private fun buildMissingSupportMessage(featureName: String): String = """
        $featureName requires Vulkan support, but this device or emulator does not advertise it.
        • Use an x86_64 Android emulator (API 30+) with Graphics set to Vulkan in Android Studio, or
        • Deploy to a physical device that supports Vulkan 1.1.
        Falling back to headless mode so the example can still enumerate scene data.
    """.trimIndent()

    private fun launchHeadlessFallback(preface: String? = null) {
        if (headlessFallbackShown) return
        headlessFallbackShown = true
        lifecycleScope.launch {
            Log.i(TAG, "Launching headless fallback")
            val headless = runCatching {
                withContext(Dispatchers.Default) {
                    example.boot(renderSurface = null)
                }
            }.onFailure { Log.e(TAG, "Headless fallback failed", it) }
                .onSuccess { Log.i(TAG, "Headless boot succeeded (${it.log.backend} on ${it.log.deviceName})") }
                .getOrNull()

            withContext(Dispatchers.Main) {
                Log.d(TAG, "Updating overlay with headless fallback state")
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
                        appendLine("Points  : ${log.pointCount.formatCompact()}")
                    } ?: appendLine("Unable to populate scene metrics without a GPU surface.")
                }.trim()
            }
        }
    }

}
