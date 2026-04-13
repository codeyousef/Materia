@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalObjCRefinement::class
)

package io.materia.examples.triangle

import io.materia.gpu.GpuBackend
import io.materia.renderer.AppleSurfaceHandle
import io.materia.renderer.SurfaceFactory
import kotlin.native.HiddenFromObjC
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGSize
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.MetalKit.MTKView
import platform.MetalKit.MTKViewDelegateProtocol
import platform.darwin.NSObject

/**
 * Minimal iOS host for the triangle example.
 *
 * The app owns the MTKView while Materia owns the rendering bootstrap.
 * Call [start] once the view is attached to the hierarchy, and call [stop]
 * during teardown.
 */
@HiddenFromObjC
class TriangleIosHost(
    private val metalView: MTKView,
    preferredBackends: List<GpuBackend> = listOf(GpuBackend.MOLTENVK)
) : NSObject(), MTKViewDelegateProtocol {

    private var scope = MainScope()
    private val example = TriangleExample(preferredBackends = preferredBackends)
    private var bootResult: TriangleBootResult? = null
    private var isBooting: Boolean = false

    var lastBootLog: TriangleBootLog? = null
        private set

    var lastErrorMessage: String? = null
        private set

    val isRunning: Boolean
        get() = bootResult != null

    fun start(
        onReady: ((TriangleBootLog) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isBooting || bootResult != null) {
            return
        }

        val device = MTLCreateSystemDefaultDevice()
        if (device == null) {
            publishError("Metal is unavailable on this iOS device.", onError)
            return
        }

        metalView.device = device
        metalView.enableSetNeedsDisplay = false
        metalView.paused = false
        metalView.preferredFramesPerSecond = 60
        metalView.delegate = this

        val (width, height) = drawableSize()
        val renderSurface = SurfaceFactory.create(
            AppleSurfaceHandle(
                handle = metalView,
                width = width,
                height = height,
                label = "Triangle MTKView"
            )
        )

        isBooting = true
        scope.launch {
            runCatching {
                example.boot(
                    renderSurface = renderSurface,
                    widthOverride = width,
                    heightOverride = height
                )
            }.onSuccess { result ->
                bootResult = result
                lastBootLog = result.log
                lastErrorMessage = null
                resizeToDrawableSize()
                isBooting = false
                onReady?.invoke(result.log)
            }.onFailure { error ->
                isBooting = false
                publishError(
                    error.message ?: error::class.simpleName ?: "Unknown Apple bootstrap failure",
                    onError
                )
            }
        }
    }

    fun resizeToDrawableSize() {
        val (width, height) = drawableSize()
        bootResult?.resize(width, height)
    }

    fun stop() {
        bootResult?.dispose()
        bootResult = null
        lastBootLog = null
        lastErrorMessage = null
        isBooting = false
        metalView.delegate = null
        metalView.paused = true
        scope.cancel()
        scope = MainScope()
    }

    override fun drawInMTKView(view: MTKView) {
        bootResult?.renderFrame()
    }

    override fun mtkView(view: MTKView, drawableSizeWillChange: CValue<CGSize>) {
        val width = drawableSizeWillChange.useContents { width.toInt().coerceAtLeast(1) }
        val height = drawableSizeWillChange.useContents { height.toInt().coerceAtLeast(1) }
        bootResult?.resize(width, height)
    }

    private fun drawableSize(): Pair<Int, Int> = metalView.drawableSize.useContents {
        width.toInt().coerceAtLeast(1) to height.toInt().coerceAtLeast(1)
    }

    private fun publishError(message: String, onError: ((String) -> Unit)?) {
        lastErrorMessage = message
        onError?.invoke(message)
    }
}

class TriangleIosController(
    metalView: MTKView,
    preferredBackends: List<GpuBackend> = listOf(GpuBackend.MOLTENVK)
) {
    private val host = TriangleIosHost(metalView, preferredBackends)

    val isRunning: Boolean
        get() = host.isRunning

    val lastBootLog: String?
        get() = host.lastBootLog?.pretty()

    val lastErrorMessage: String?
        get() = host.lastErrorMessage

    fun start(
        onReady: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        host.start(
            onReady = { log -> onReady?.invoke(log.pretty()) },
            onError = onError
        )
    }

    fun resizeToDrawableSize() {
        host.resizeToDrawableSize()
    }

    fun stop() {
        host.stop()
    }
}

@HiddenFromObjC
fun createDefaultTriangleIosHost(metalView: MTKView): TriangleIosHost {
    return TriangleIosHost(metalView)
}

fun createDefaultTriangleIosController(metalView: MTKView): TriangleIosController {
    return TriangleIosController(metalView)
}
