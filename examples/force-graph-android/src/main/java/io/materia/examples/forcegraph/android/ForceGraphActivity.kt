package io.materia.examples.forcegraph.android

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
import io.materia.examples.forcegraph.ForceGraphBootResult
import io.materia.examples.forcegraph.ForceGraphExample
import io.materia.gpu.AndroidVulkanAssets
import io.materia.gpu.GpuBackend
import io.materia.io.AndroidResourceLoader
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ForceGraphActivity : ComponentActivity() {

    private companion object {
        const val TAG = "ForceGraphActivity"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: TextView

    private val example = ForceGraphExample(
        preferredBackends = listOf(GpuBackend.VULKAN)
    )

    private var bootResult: ForceGraphBootResult? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNs: Long = 0L
    private var overlayFrameCounter = 0
    private var headlessFallbackShown = false

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!AndroidVulkanAssets.hasVulkanSupport()) {
                val message = buildMissingSupportMessage("Force Graph")
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
                    overlayView.text = "Booting Force Graph…"
                    Log.i(TAG, "Surface ready (${width}x${height}); bootstrapping renderer")
                    initialiseRenderer()
                } else {
                    // If dimensions aren't ready yet, try again with a slight delay
                    surfaceView.postDelayed({
                        overlayView.text = "Booting Force Graph…"
                        Log.i(TAG, "Surface ready (delayed); bootstrapping renderer")
                        initialiseRenderer()
                    }, 100)
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            bootResult?.runtime?.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            stopRenderLoop()
            bootResult?.runtime?.dispose()
            bootResult = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        super.onDestroy()
    }

    private fun initialiseRenderer() {
        val holder = surfaceView.holder
        lifecycleScope.launch {
            Log.d(TAG, "initialiseRenderer coroutine started")
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val surface = SurfaceFactory.create(holder)
                    withTimeout(10_000L) { // Increased from 5s to 10s to accommodate slower devices/emulators
                        example.boot(
                            renderSurface = surface,
                            widthOverride = surfaceView.width.takeIf { it > 0 },
                            heightOverride = surfaceView.height.takeIf { it > 0 }
                        )
                    }
                }
            }

            result.onSuccess { boot ->
                Log.i(TAG, "Renderer boot succeeded: backend=${boot.log.backend}, device=${boot.log.deviceName}")
                bootResult = boot
                overlayView.text = boot.log.pretty()
                boot.runtime.resize(
                    surfaceView.width.takeIf { it > 0 } ?: 1280,
                    surfaceView.height.takeIf { it > 0 } ?: 720
                )
                startRenderLoop()
            }.onFailure { error ->
                Log.e(TAG, "Renderer bootstrap failed", error)
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                val failureMessage = buildFailureMessage("Force Graph", error)
                overlayView.text = failureMessage
                launchHeadlessFallback(failureMessage)
            }
            Log.d(TAG, "initialiseRenderer coroutine finished")
        }
    }

    private fun startRenderLoop() {
        if (frameCallback != null) return
        val choreographer = Choreographer.getInstance()
        lastFrameTimeNs = System.nanoTime()
        overlayFrameCounter = 0

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val boot = bootResult
                if (boot == null) {
                    frameCallback = null
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
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    private fun updateOverlay(boot: ForceGraphBootResult) {
        val runtime = boot.runtime
        val metrics = runtime.metrics()
        overlayView.text = buildString {
            appendLine("🔗 Force Graph")
            appendLine("Backend : ${boot.log.backend}")
            appendLine("Device  : ${boot.log.deviceName}")
            appendLine("Mode    : ${metrics.mode}")
            appendLine("Nodes   : ${metrics.nodeCount.formatThousands()}")
            appendLine("Edges   : ${metrics.edgeCount.formatThousands()}")
            appendLine("Frame   : ${metrics.frameTimeMs.formatMs()} ms")
        }
    }

    private fun Int.formatThousands(): String = String.format("%,d", this)

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
                • Use an x86_64 Android emulator with Vulkan graphics enabled in Android Studio, or
                • Deploy to a physical device that advertises Vulkan 1.1 support.
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
        • Ensure the emulator uses an x86_64 system image with Vulkan graphics enabled.
        • Alternatively, deploy to a physical device that supports Vulkan 1.1.
    """.trimIndent()

    private fun buildMissingSupportMessage(featureName: String): String = """
        $featureName requires Vulkan support, but this device/emulator does not advertise it.
        • Switch to an x86_64 Android emulator with Graphics set to Vulkan, or
        • Use a physical device that supports Vulkan 1.1.
        Showing headless metrics instead.
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
                        appendLine("Nodes   : ${log.nodeCount.formatThousands()}")
                        appendLine("Edges   : ${log.edgeCount.formatThousands()}")
                    } ?: appendLine("Unable to collect graph metrics without a GPU surface.")
                }.trim()
            }
        }
    }
}
