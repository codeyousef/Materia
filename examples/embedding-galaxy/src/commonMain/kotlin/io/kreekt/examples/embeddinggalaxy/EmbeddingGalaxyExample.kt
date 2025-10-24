package io.kreekt.examples.embeddinggalaxy

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
import kotlin.math.roundToInt
import kotlin.time.TimeSource

class EmbeddingGalaxyExample(
    private val sceneConfig: EmbeddingGalaxyScene.Config = EmbeddingGalaxyScene.Config(),
    private val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU, GpuBackend.VULKAN),
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    private val enableFxaa: Boolean = true
) {

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): EmbeddingGalaxyBootResult {
        val scene = EmbeddingGalaxyScene(sceneConfig)
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: 1920
        val targetHeight = heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: 1080

        if (renderSurface == null) {
            val log = EmbeddingGalaxyBootLog(
                backend = preferredBackends.firstOrNull()?.toBackendType() ?: BackendType.WEBGPU,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                pointCount = scene.activePointCount,
                clusterCount = sceneConfig.clusterCount,
                quality = scene.quality,
                fxaaEnabled = enableFxaa,
                frameTimeMs = 0.0
            )
            return EmbeddingGalaxyBootResult(log, EmbeddingGalaxyRuntime(null, scene, enableFxaa))
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
            clearColor = scene.clearColor.copyOf(),
            enableFxaa = enableFxaa
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

        val log = EmbeddingGalaxyBootLog(
            backend = renderer.backend,
            deviceName = renderer.deviceName,
            driverVersion = renderer.driverVersion,
            pointCount = scene.activePointCount,
            clusterCount = sceneConfig.clusterCount,
            quality = scene.quality,
            fxaaEnabled = renderer.fxaaEnabled,
            frameTimeMs = frameTimeMs
        )
        return EmbeddingGalaxyBootResult(log, EmbeddingGalaxyRuntime(renderer, scene, renderer.fxaaEnabled))
    }
}

data class EmbeddingGalaxyBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val pointCount: Int,
    val clusterCount: Int,
    val quality: EmbeddingGalaxyScene.Quality,
    val fxaaEnabled: Boolean,
    val frameTimeMs: Double
) {
    fun pretty(): String = buildString {
        val fxaaState = if (fxaaEnabled) "on" else "off"
        appendLine("🌌 Embedding Galaxy boot complete")
        appendLine("  Backend : $backend")
        appendLine("  Device  : $deviceName")
        appendLine("  Driver  : $driverVersion")
        appendLine("  Points  : ${pointCount.formatCompact()}")
        appendLine("  Clusters: $clusterCount")
        appendLine("  Quality : $quality")
        appendLine("  FXAA    : $fxaaState")
        appendLine("  Frame   : ${frameTimeMs.formatMs()} ms")
    }

    private fun Double.formatMs(): String =
        ((this * 100.0).roundToInt() / 100.0).toString()

    private fun Int.formatCompact(): String =
        when {
            this >= 1_000_000 -> "${(this / 1_000_000.0).format(1)}M"
            this >= 1_000 -> "${(this / 1_000.0).format(1)}K"
            else -> toString()
        }

    private fun Double.format(decimals: Int): String {
        val factor = pow10(decimals)
        return ((this * factor).roundToInt() / factor).toString()
    }

    private fun pow10(decimals: Int): Double {
        var result = 1.0
        repeat(decimals) { result *= 10.0 }
        return result
    }
}

data class EmbeddingGalaxyBootResult(
    val log: EmbeddingGalaxyBootLog,
    val runtime: EmbeddingGalaxyRuntime
)

class EmbeddingGalaxyRuntime(
    private val renderer: EngineRenderer?,
    val scene: EmbeddingGalaxyScene,
    fxaaDefault: Boolean
) {
    private var headlessFxaaEnabled = fxaaDefault

    fun frame(deltaSeconds: Float) {
        scene.update(deltaSeconds)
        renderer?.let {
            val mark = TimeSource.Monotonic.markNow()
            it.render(scene.scene, scene.camera)
            val frameTime = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
            scene.recordFrameTime(frameTime)
        }
    }

    fun renderFrame() = frame(1f / 60f)

    fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            scene.resize(width.toFloat() / height.toFloat())
        }
        renderer?.resize(width, height)
    }

    fun dispose() {
        renderer?.dispose()
    }

    fun triggerQuery() = scene.triggerQuery()
    fun resetSequence() = scene.resetSequence()
    fun togglePause() = scene.togglePause()
    fun setQuality(quality: EmbeddingGalaxyScene.Quality) = scene.setQuality(quality)
    fun metrics(): EmbeddingGalaxyScene.Metrics = scene.metrics()
    fun toggleFxaa() {
        fxaaEnabled = !fxaaEnabled
    }

    var fxaaEnabled: Boolean
        get() = renderer?.fxaaEnabled ?: headlessFxaaEnabled
        set(value) {
            if (renderer != null) {
                renderer.fxaaEnabled = value
            } else {
                headlessFxaaEnabled = value
            }
        }

    val activePointCount: Int get() = scene.activePointCount
    val quality: EmbeddingGalaxyScene.Quality get() = scene.quality
}

private fun GpuBackend.toBackendType(): BackendType = when (this) {
    GpuBackend.WEBGPU -> BackendType.WEBGPU
    GpuBackend.VULKAN, GpuBackend.MOLTENVK -> BackendType.VULKAN
}

private fun GpuPowerPreference.toRendererPowerPreference(): PowerPreference = when (this) {
    GpuPowerPreference.LOW_POWER -> PowerPreference.LOW_POWER
    GpuPowerPreference.HIGH_PERFORMANCE -> PowerPreference.HIGH_PERFORMANCE
}
