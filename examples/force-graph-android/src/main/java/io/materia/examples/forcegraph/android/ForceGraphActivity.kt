package io.materia.examples.forcegraph.android

import android.os.Bundle
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
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForceGraphActivity : ComponentActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: TextView

    private val example = ForceGraphExample(
        preferredBackends = listOf(GpuBackend.VULKAN)
    )

    private var bootResult: ForceGraphBootResult? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNs: Long = 0L
    private var overlayFrameCounter = 0

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            overlayView.text = "Booting Force Graph…"
            initialiseRenderer()
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
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val surface = SurfaceFactory.create(holder)
                    example.boot(
                        renderSurface = surface,
                        widthOverride = surfaceView.width.takeIf { it > 0 },
                        heightOverride = surfaceView.height.takeIf { it > 0 }
                    )
                }
            }

            result.onSuccess { boot ->
                bootResult = boot
                overlayView.text = boot.log.pretty()
                boot.runtime.resize(
                    surfaceView.width.takeIf { it > 0 } ?: 1280,
                    surfaceView.height.takeIf { it > 0 } ?: 720
                )
                startRenderLoop()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                overlayView.text = buildString {
                    appendLine("Force Graph failed to start.")
                    appendLine(error.message ?: error::class.simpleName ?: "Unknown error")
                }
            }
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
}
