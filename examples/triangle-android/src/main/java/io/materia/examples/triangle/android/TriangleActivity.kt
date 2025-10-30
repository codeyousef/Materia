package io.materia.examples.triangle.android

import android.os.Bundle
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
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TriangleActivity : ComponentActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: TextView

    private val triangleExample = TriangleExample(
        preferredBackends = listOf(GpuBackend.VULKAN),
        powerPreference = GpuPowerPreference.HIGH_PERFORMANCE
    )

    private var triangleRuntime: TriangleBootResult? = null
    private var frameCallback: Choreographer.FrameCallback? = null

    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            overlayView.text = "Booting Triangle example…"
            initializeRenderer()
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
            val holder = surfaceView.holder
            val renderSurface = SurfaceFactory.create(holder)

            val result = runCatching {
                triangleExample.boot(
                    renderSurface = renderSurface,
                    widthOverride = surfaceView.width.takeIf { it > 0 },
                    heightOverride = surfaceView.height.takeIf { it > 0 }
                )
            }

            result.onSuccess { bootResult ->
                triangleRuntime = bootResult
                overlayView.text = bootResult.log.pretty()
                startRenderLoop()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                overlayView.text = buildString {
                    appendLine("Triangle renderer failed to start")
                    appendLine(error.message ?: error::class.simpleName ?: "Unknown error")
                }
            }
        }
    }

    private fun startRenderLoop() {
        if (frameCallback != null) return
        val choreographer = Choreographer.getInstance()
        val callback = Choreographer.FrameCallback {
            triangleRuntime?.renderFrame()
            choreographer.postFrameCallback(callback)
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun stopRenderLoop() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }
}
