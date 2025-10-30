package io.materia.examples.embeddinggalaxy

import io.materia.engine.render.EngineRenderer
import io.materia.engine.render.EngineRendererOptions
import io.materia.engine.render.createEngineRenderer
import io.materia.gpu.GpuBackend
import io.materia.gpu.GpuPowerPreference
import io.materia.renderer.BackendType
import io.materia.renderer.PowerPreference
import io.materia.renderer.RenderSurface
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.TimeSource

data class PerformanceProfile(
    val name: String,
    val targetFps: Double,
    val degradeThresholdMultiplier: Double = 1.25,
    val upgradeThresholdMultiplier: Double = 0.85,
    val sampleWindow: Int = 120,
    val adjustCooldownFrames: Int = 180,
    val initialCooldownFrames: Int = sampleWindow
) {
    val targetFrameMs: Double get() = 1000.0 / targetFps
    val downgradeFrameTimeMs: Double get() = targetFrameMs * degradeThresholdMultiplier
    val upgradeFrameTimeMs: Double get() = targetFrameMs * upgradeThresholdMultiplier

    companion object {
        val Desktop = PerformanceProfile(name = "desktop", targetFps = 60.0)
        val Web = PerformanceProfile(
            name = "web",
            targetFps = 45.0,
            sampleWindow = 90,
            adjustCooldownFrames = 140
        )
        val Mobile = PerformanceProfile(
            name = "mobile",
            targetFps = 30.0,
            sampleWindow = 90,
            adjustCooldownFrames = 200
        )
    }
}

internal class PerformanceGovernor(
    private val profile: PerformanceProfile
) {
    private val samples = DoubleArray(profile.sampleWindow.coerceAtLeast(1))
    private var count = 0
    private var index = 0
    private var sum = 0.0
    private var cooldown = profile.initialCooldownFrames

    fun record(
        frameTimeMs: Double,
        currentQuality: EmbeddingGalaxyScene.Quality
    ): EmbeddingGalaxyScene.Quality? {
        if (frameTimeMs <= 0.0 || profile.sampleWindow <= 0) {
            return null
        }
        if (count < profile.sampleWindow) {
            samples[index] = frameTimeMs
            sum += frameTimeMs
            count += 1
            index = (index + 1) % profile.sampleWindow
            if (count < profile.sampleWindow) return null
        } else {
            val outgoing = samples[index]
            sum -= outgoing
            samples[index] = frameTimeMs
            sum += frameTimeMs
            index = (index + 1) % profile.sampleWindow
        }

        if (cooldown > 0) {
            cooldown -= 1
            return null
        }

        val average = sum / profile.sampleWindow
        return when {
            average > profile.downgradeFrameTimeMs && currentQuality != EmbeddingGalaxyScene.Quality.Performance -> {
                cooldown = profile.adjustCooldownFrames
                currentQuality.previous()
            }

            average < profile.upgradeFrameTimeMs && currentQuality != EmbeddingGalaxyScene.Quality.Fidelity -> {
                cooldown = profile.adjustCooldownFrames
                currentQuality.next()
            }

            else -> null
        }
    }

    fun notifyManualChange() {
        cooldown = max(profile.adjustCooldownFrames, profile.initialCooldownFrames)
    }

    fun reset() {
        count = 0
        index = 0
        sum = 0.0
        cooldown = profile.initialCooldownFrames
    }
}

class EmbeddingGalaxyExample(
    private val sceneConfig: EmbeddingGalaxyScene.Config = EmbeddingGalaxyScene.Config(),
    private val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU, GpuBackend.VULKAN),
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    private val enableFxaa: Boolean = true,
    private val performanceProfile: PerformanceProfile = PerformanceProfile.Desktop
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
            return EmbeddingGalaxyBootResult(
                log,
                EmbeddingGalaxyRuntime(null, scene, enableFxaa, performanceProfile)
            )
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
        return EmbeddingGalaxyBootResult(
            log,
            EmbeddingGalaxyRuntime(renderer, scene, renderer.fxaaEnabled, performanceProfile)
        )
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
    fxaaDefault: Boolean,
    private val performanceProfile: PerformanceProfile
) {
    private var headlessFxaaEnabled = fxaaDefault
    private val performanceGovernor = PerformanceGovernor(performanceProfile)

    fun frame(deltaSeconds: Float) {
        scene.update(deltaSeconds)
        renderer?.let {
            val mark = TimeSource.Monotonic.markNow()
            it.render(scene.scene, scene.camera)
            val frameTime = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
            scene.recordFrameTime(frameTime)
            handlePerformance(frameTime)
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
    fun resetSequence() {
        scene.resetSequence()
        performanceGovernor.reset()
    }

    fun togglePause() = scene.togglePause()
    fun setQuality(quality: EmbeddingGalaxyScene.Quality) {
        scene.setQuality(quality)
        performanceGovernor.notifyManualChange()
    }

    fun metrics(): EmbeddingGalaxyScene.Metrics = scene.metrics()
    fun toggleFxaa() {
        fxaaEnabled = !fxaaEnabled
    }

    fun orbit(deltaYaw: Float, deltaPitch: Float) = scene.orbit(deltaYaw, deltaPitch)
    fun zoom(delta: Float) = scene.zoom(delta)
    fun resetView() = scene.resetOrbit()

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

    private fun handlePerformance(frameTime: Double) {
        val adjustment = performanceGovernor.record(frameTime, scene.quality) ?: return
        scene.setQuality(adjustment)
    }
}

private fun EmbeddingGalaxyScene.Quality.previous(): EmbeddingGalaxyScene.Quality = when (this) {
    EmbeddingGalaxyScene.Quality.Performance -> EmbeddingGalaxyScene.Quality.Performance
    EmbeddingGalaxyScene.Quality.Balanced -> EmbeddingGalaxyScene.Quality.Performance
    EmbeddingGalaxyScene.Quality.Fidelity -> EmbeddingGalaxyScene.Quality.Balanced
}

private fun EmbeddingGalaxyScene.Quality.next(): EmbeddingGalaxyScene.Quality = when (this) {
    EmbeddingGalaxyScene.Quality.Performance -> EmbeddingGalaxyScene.Quality.Balanced
    EmbeddingGalaxyScene.Quality.Balanced -> EmbeddingGalaxyScene.Quality.Fidelity
    EmbeddingGalaxyScene.Quality.Fidelity -> EmbeddingGalaxyScene.Quality.Fidelity
}

private fun GpuBackend.toBackendType(): BackendType = when (this) {
    GpuBackend.WEBGPU -> BackendType.WEBGPU
    GpuBackend.VULKAN, GpuBackend.MOLTENVK -> BackendType.VULKAN
}

private fun GpuPowerPreference.toRendererPowerPreference(): PowerPreference = when (this) {
    GpuPowerPreference.LOW_POWER -> PowerPreference.LOW_POWER
    GpuPowerPreference.HIGH_PERFORMANCE -> PowerPreference.HIGH_PERFORMANCE
}
