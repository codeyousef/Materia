package io.materia.examples.triangle.android

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
import io.materia.examples.triangle.TriangleBootResult
import io.materia.examples.triangle.TriangleExample
import io.materia.gpu.AndroidVulkanAssets
import io.materia.gpu.GpuBackend
import io.materia.gpu.GpuPowerPreference
import io.materia.io.AndroidResourceLoader
import io.materia.renderer.SurfaceFactory
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

    private var triangleRuntime: TriangleBootResult? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var headlessFallbackShown = false

    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!AndroidVulkanAssets.hasVulkanSupport()) {
                val message = buildMissingSupportMessage("Triangle")
                Log.w(TAG, "Vulkan not advertised; launching headless fallback")
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
                    overlayView.text = "Booting Triangle example…"
                    Log.i(TAG, "Surface ready (${width}x${height}); bootstrapping renderer")
                    initializeRenderer()
                } else {
                    // If dimensions aren't ready yet, try again with a slight delay
                    surfaceView.postDelayed({
                        overlayView.text = "Booting Triangle example…"
                        Log.i(TAG, "Surface ready (delayed); bootstrapping renderer")
                        initializeRenderer()
                    }, 100)
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            triangleRuntime?.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            stopRenderLoop()
            triangleRuntime?.dispose()
            triangleRuntime = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidVulkanAssets.initialise(applicationContext)
        AndroidResourceLoader.initialise(assets)

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(holderCallback)
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
        super.onDestroy()
    }

    private fun initializeRenderer() {
        lifecycleScope.launch {
            Log.d(TAG, "initializeRenderer coroutine started")
            val holder = surfaceView.holder
            val renderSurface = SurfaceFactory.create(holder)

            val result = runCatching {
                withContext(Dispatchers.Default) {
                    withTimeout(10_000L) { // Increased from 5s to 10s to accommodate slower devices/emulators
                        triangleExample.boot(
                            renderSurface = renderSurface,
                            widthOverride = surfaceView.width.takeIf { it > 0 },
                            heightOverride = surfaceView.height.takeIf { it > 0 }
                        )
                    }
                }
            }

            result.onSuccess { bootResult ->
                Log.i(TAG, "Renderer boot succeeded: backend=${bootResult.log.backend}, device=${bootResult.log.deviceName}")
                triangleRuntime = bootResult
                overlayView.text = bootResult.log.pretty()
                startRenderLoop()
            }.onFailure { error ->
                Log.e(TAG, "Renderer bootstrap failed", error)
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                val failureMessage = buildFailureMessage("Triangle", error)
                overlayView.text = failureMessage
                launchHeadlessFallback(failureMessage)
            }
            Log.d(TAG, "initializeRenderer coroutine finished")
        }
    }

    private fun startRenderLoop() {
        if (frameCallback != null) return
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                triangleRuntime?.renderFrame()
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
