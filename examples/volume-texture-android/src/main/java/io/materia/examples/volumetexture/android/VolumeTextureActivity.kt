package io.materia.examples.volumetexture.android

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
import io.materia.examples.volumetexture.VolumeTextureExample
import io.materia.examples.volumetexture.VolumeTextureRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class VolumeTextureActivity : ComponentActivity() {

    private companion object {
        const val TAG = "VolumeTextureActivity"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: TextView

    private var sceneRuntime: VolumeTextureRuntime? = null
    private var filamentRuntime: DirectFilamentVolumeTextureRuntime? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var bootJob: Job? = null
    private var lastFrameNanos: Long = 0L

    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            overlayView.text = "Booting Volume Texture example..."
            Log.i(TAG, "Surface ready; bootstrapping shared volume scene")
            initializeRenderer()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            sceneRuntime?.resize(width, height)
            filamentRuntime?.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            bootJob?.cancel()
            bootJob = null
            stopRenderLoop()
            filamentRuntime?.dispose()
            filamentRuntime = null
            sceneRuntime?.dispose()
            sceneRuntime = null
            lastFrameNanos = 0L
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "Waiting for surface..."
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF07131A.toInt())
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
        bootJob?.cancel()
        bootJob = null
        surfaceView.holder.removeCallback(holderCallback)
        stopRenderLoop()
        filamentRuntime?.dispose()
        filamentRuntime = null
        sceneRuntime?.dispose()
        sceneRuntime = null
        super.onDestroy()
    }

    private fun initializeRenderer() {
        if (filamentRuntime != null || bootJob != null) {
            return
        }

        val width = max(surfaceView.width, 1)
        val height = max(surfaceView.height, 1)

        bootJob = lifecycleScope.launch {
            try {
                val bootResult = withContext(Dispatchers.Default) {
                    VolumeTextureExample().boot(
                        renderSurface = null,
                        widthOverride = width,
                        heightOverride = height
                    )
                }

                if (!surfaceView.holder.surface.isValid) {
                    return@launch
                }

                val runtime = DirectFilamentVolumeTextureRuntime(
                    surfaceView = surfaceView,
                    sourceScene = bootResult.runtime.scene,
                    sourceCamera = bootResult.runtime.camera
                )
                runtime.initialize()

                sceneRuntime = bootResult.runtime
                filamentRuntime = runtime
                overlayView.text = runtime.buildOverlayText(
                    textureResolution = bootResult.log.textureResolution,
                    meshCount = bootResult.log.meshCount
                )
                Log.i(TAG, "Renderer boot succeeded: backend=${runtime.backendName}, device=${runtime.deviceName}")
                startRenderLoop()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                Log.e(TAG, "Renderer bootstrap failed", error)
                overlayView.text = buildFailureMessage(error)
            } finally {
                bootJob = null
            }
        }
    }

    private fun startRenderLoop() {
        if (frameCallback != null) return

        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val deltaSeconds = if (lastFrameNanos == 0L) {
                    1f / 60f
                } else {
                    ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0)
                        .toFloat()
                        .coerceAtMost(0.1f)
                }
                lastFrameNanos = frameTimeNanos

                sceneRuntime?.frame(deltaSeconds)
                filamentRuntime?.renderFrame(frameTimeNanos)
                choreographer.postFrameCallback(this)
            }
        }

        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun stopRenderLoop() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        lastFrameNanos = 0L
    }

    private fun buildFailureMessage(error: Throwable): String {
        val root = error.rootCause()
        return buildString {
            appendLine("Volume Texture Android renderer failed to start.")
            appendLine(root.message ?: root::class.simpleName ?: "Unknown error")
        }.trim()
    }

    private fun Throwable.rootCause(): Throwable {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }
}