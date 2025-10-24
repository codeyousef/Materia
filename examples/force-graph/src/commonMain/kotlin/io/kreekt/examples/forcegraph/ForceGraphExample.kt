package io.kreekt.examples.forcegraph

import io.kreekt.engine.render.EngineRenderer
import io.kreekt.engine.render.EngineRendererOptions
import io.kreekt.engine.render.createEngineRenderer
import io.kreekt.gpu.GpuBackend
import io.kreekt.gpu.GpuPowerPreference
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.PowerPreference
import io.kreekt.renderer.RenderSurface
import io.kreekt.renderer.RendererConfig
import io.kreekt.renderer.RendererFactory
import io.kreekt.io.loadJson
import kotlin.math.roundToInt
import kotlin.time.TimeSource

class ForceGraphExample(
    private val sceneConfig: ForceGraphScene.Config = ForceGraphScene.Config(),
    private val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU, GpuBackend.VULKAN),
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE
) {

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): ForceGraphBootResult {
        val layout = loadLayout(sceneConfig)
        val scene = ForceGraphScene(layout)
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: 1920
        val targetHeight = heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: 1080

        if (renderSurface == null) {
            val log = ForceGraphBootLog(
                backend = preferredBackends.firstOrNull()?.toBackendType() ?: BackendType.WEBGPU,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                nodeCount = layout.config.nodeCount,
                edgeCount = layout.config.edgeCount,
                mode = ForceGraphScene.Mode.TfIdf,
                frameTimeMs = 0.0
            )
            return ForceGraphBootResult(log, ForceGraphRuntime(null, scene))
        }

        val rendererConfig = RendererConfig(
            preferredBackend = preferredBackends.firstOrNull()?.toBackendType(),
            powerPreference = powerPreference.toRendererPowerPreference(),
            enableValidation = false,
            vsync = true,
            msaaSamples = 1
        )
        val options = EngineRendererOptions(
            preferredBackends = preferredBackends,
            powerPreference = powerPreference,
            clearColor = scene.scene.backgroundColor.copyOf()
        )

        val rendererResult = RendererFactory.createEngineRenderer(
            surface = renderSurface,
            config = rendererConfig,
            options = options
        )
        val renderer = rendererResult.getOrThrow()
        renderer.resize(targetWidth, targetHeight)

        val mark = TimeSource.Monotonic.markNow()
        renderer.render(scene.scene, scene.camera)
        val frameTimeMs = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
        scene.recordFrameTime(frameTimeMs)

        val log = ForceGraphBootLog(
            backend = renderer.backend,
            deviceName = renderer.deviceName,
            driverVersion = renderer.driverVersion,
            nodeCount = layout.config.nodeCount,
            edgeCount = layout.config.edgeCount,
            mode = ForceGraphScene.Mode.TfIdf,
            frameTimeMs = frameTimeMs
        )
        return ForceGraphBootResult(log, ForceGraphRuntime(renderer, scene))
    }

    private suspend fun loadLayout(config: ForceGraphScene.Config): ForceGraphLayout {
        val resourcePath = "data/force-graph.json"
        val baked = runCatching { loadJson<ForceGraphLayout>(resourcePath) }.getOrNull()
        if (baked != null && baked.config.nodeCount == config.nodeCount && baked.config.edgeCount == config.edgeCount) {
            return baked
        }

        return ForceGraphLayoutGenerator.generate(config)
    }
}

data class ForceGraphBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val mode: ForceGraphScene.Mode,
    val frameTimeMs: Double
) {
    fun pretty(): String = buildString {
        appendLine("🔗 Force Graph boot complete")
        appendLine("  Backend : $backend")
        appendLine("  Device  : $deviceName")
        appendLine("  Driver  : $driverVersion")
        appendLine("  Nodes   : ${nodeCount.formatThousands()}")
        appendLine("  Edges   : ${edgeCount.formatThousands()}")
        appendLine("  Mode    : $mode")
        appendLine("  Frame   : ${frameTimeMs.asMsString()} ms")
    }

    private fun Double.asMsString(): String = ((this * 100).roundToInt() / 100.0).toString()
    private fun Int.formatThousands(): String = formatWithCommas()
}

data class ForceGraphBootResult(
    val log: ForceGraphBootLog,
    val runtime: ForceGraphRuntime
)

class ForceGraphRuntime(
    private val renderer: EngineRenderer?,
    val scene: ForceGraphScene
) {
    fun frame(deltaSeconds: Float) {
        scene.update(deltaSeconds)
        renderer?.let {
            val mark = TimeSource.Monotonic.markNow()
            it.render(scene.scene, scene.camera)
            val frameTime = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
            scene.recordFrameTime(frameTime)
        }
    }

    fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            scene.camera.aspect = width.toFloat() / height.toFloat()
            scene.camera.updateProjection()
        }
        renderer?.resize(width, height)
    }

    fun dispose() {
        renderer?.dispose()
    }

    fun toggleMode() = scene.triggerToggle()
    fun setMode(mode: ForceGraphScene.Mode) = scene.setMode(mode)
    fun metrics(): ForceGraphScene.Metrics = scene.metrics()
}

private fun GpuBackend.toBackendType(): BackendType = when (this) {
    GpuBackend.WEBGPU -> BackendType.WEBGPU
    GpuBackend.VULKAN, GpuBackend.MOLTENVK -> BackendType.VULKAN
}

private fun GpuPowerPreference.toRendererPowerPreference(): PowerPreference = when (this) {
    GpuPowerPreference.LOW_POWER -> PowerPreference.LOW_POWER
    GpuPowerPreference.HIGH_PERFORMANCE -> PowerPreference.HIGH_PERFORMANCE
}

private fun Int.formatWithCommas(): String {
    val value = if (this < 0) -this else this
    val digits = value.toString()
    val builder = StringBuilder()
    var count = 0
    for (i in digits.length - 1 downTo 0) {
        builder.append(digits[i])
        count++
        if (count == 3 && i != 0) {
            builder.append(',')
            count = 0
        }
    }
    if (this < 0) builder.append('-')
    return builder.reverse().toString()
}
