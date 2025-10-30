package io.materia.performance

import io.materia.renderer.Texture

import kotlinx.coroutines.*
import io.materia.core.platform.currentTimeMillis
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.math.min
import kotlin.time.*
import kotlinx.coroutines.Dispatchers
import kotlin.math.PI

/**
 * Adaptive rendering system with hardware capability detection
 *
 * Features:
 * - Cross-platform hardware capability detection
 * - Automatic quality tier selection (Mobile, Standard, High, Ultra)
 * - Real-time performance monitoring and adaptation
 * - Smart feature fallbacks based on capabilities
 * - Memory and thermal management
 * - Dynamic resolution scaling
 * - Automatic LOD adjustment
 * - Frame rate targeting and stabilization
 */
@OptIn(ExperimentalTime::class)
class AdaptiveRenderer {
    // Hardware detection
    private val hardwareDetector = HardwareDetector()
    private val gpuProfiler = GPUProfiler()

    // Performance monitoring
    private val performanceMonitor = PerformanceMonitor()
    private val thermalMonitor = ThermalMonitor()
    private val memoryMonitor = MemoryMonitor()

    // Quality management
    private var currentTier = QualityTier.STANDARD
    private var dynamicQuality = DynamicQualitySettings()

    // Performance targets
    private var targetFrameRate = 60
    private var targetFrameTime = 16.67 // milliseconds

    // State flows for reactive updates
    private val qualityTierFlow = MutableStateFlow(currentTier)
    private val performanceMetricsFlow = MutableSharedFlow<PerformanceMetrics>()
    private val adaptationEventsFlow = MutableSharedFlow<AdaptationEvent>()

    val qualityTier: StateFlow<QualityTier> = qualityTierFlow.asStateFlow()
    val performanceMetrics: SharedFlow<PerformanceMetrics> = performanceMetricsFlow.asSharedFlow()
    val adaptationEvents: SharedFlow<AdaptationEvent> = adaptationEventsFlow.asSharedFlow()

    // Adaptation coroutines
    private var monitoringJob: Job? = null
    private var adaptationJob: Job? = null

    /**
     * Initialize adaptive renderer with hardware detection
     */
    suspend fun initialize(): RenderCapabilities {
        // Detect hardware capabilities
        val hardware = hardwareDetector.detect()

        // Profile GPU capabilities
        val gpuCapabilities = gpuProfiler.profile()

        // Determine initial quality tier
        currentTier = determineQualityTier(hardware, gpuCapabilities)
        qualityTierFlow.value = currentTier

        // Configure initial settings
        dynamicQuality = createQualitySettings(currentTier)

        // Start monitoring
        startPerformanceMonitoring()
        startAdaptation()

        return RenderCapabilities(
            hardware = hardware,
            gpu = gpuCapabilities,
            tier = currentTier,
            features = determineAvailableFeatures(hardware, gpuCapabilities)
        )
    }

    /**
     * Determine quality tier based on hardware
     */
    private fun determineQualityTier(
        hardware: HardwareInfo,
        gpu: GPUCapabilities
    ): QualityTier {
        // Score system based on multiple factors
        var score = 0

        // CPU scoring
        score = score + when {
            hardware.cpuCores >= 8 && hardware.cpuFrequency >= 2.5 -> 30
            hardware.cpuCores >= 4 && hardware.cpuFrequency >= 2.0 -> 20
            hardware.cpuCores >= 2 -> 10
            else -> 5
        }

        // Memory scoring
        score = score + when {
            hardware.totalMemory >= 16_000_000_000L -> 20  // 16GB+
            hardware.totalMemory >= 8_000_000_000L -> 15   // 8GB+
            hardware.totalMemory >= 4_000_000_000L -> 10   // 4GB+
            else -> 5
        }

        // GPU scoring
        score = score + when {
            gpu.computeUnits >= 32 && gpu.vramSize >= 8_000_000_000L -> 50
            gpu.computeUnits >= 16 && gpu.vramSize >= 4_000_000_000L -> 35
            gpu.computeUnits >= 8 && gpu.vramSize >= 2_000_000_000L -> 20
            else -> 10
        }

        // Platform-specific adjustments
        score = adjustScoreForPlatform(score, hardware.platform)

        // Determine tier based on score
        return when {
            score >= 80 -> QualityTier.ULTRA
            score >= 60 -> QualityTier.HIGH
            score >= 40 -> QualityTier.STANDARD
            else -> QualityTier.MOBILE
        }
    }

    /**
     * Adjust score based on platform characteristics
     */
    private fun adjustScoreForPlatform(score: Int, platform: Platform): Int {
        return when (platform) {
            Platform.MOBILE -> (score * 0.7).toInt() // Mobile devices typically need lower settings
            Platform.WEB -> (score * 0.85).toInt()   // Browser overhead
            Platform.DESKTOP -> score                 // Full performance
            Platform.CONSOLE -> (score * 1.1).toInt() // Optimized hardware
        }
    }

    /**
     * Create quality settings for tier
     */
    private fun createQualitySettings(tier: QualityTier): DynamicQualitySettings {
        return when (tier) {
            QualityTier.MOBILE -> DynamicQualitySettings(
                resolution = ResolutionScale(0.5f, 0.75f),
                shadowQuality = ShadowQuality.LOW,
                textureQuality = TextureQuality.LOW,
                effectQuality = EffectQuality.LOW,
                maxLOD = 2,
                renderDistance = 50f,
                antiAliasing = AntiAliasing.NONE,
                anisotropicFiltering = 1,
                postProcessing = setOf(),
                maxLights = 4,
                maxShadowCasters = 2,
                enableReflections = false,
                enableVolumetrics = false,
                enableMotionBlur = false,
                enableDepthOfField = false,
                particleQuality = ParticleQuality.LOW,
                vegetationDensity = 0.25f
            )

            QualityTier.STANDARD -> DynamicQualitySettings(
                resolution = ResolutionScale(0.75f, 1.0f),
                shadowQuality = ShadowQuality.MEDIUM,
                textureQuality = TextureQuality.MEDIUM,
                effectQuality = EffectQuality.MEDIUM,
                maxLOD = 3,
                renderDistance = 100f,
                antiAliasing = AntiAliasing.FXAA,
                anisotropicFiltering = 4,
                postProcessing = setOf(PostProcess.BLOOM, PostProcess.TONE_MAPPING),
                maxLights = 8,
                maxShadowCasters = 4,
                enableReflections = true,
                enableVolumetrics = false,
                enableMotionBlur = false,
                enableDepthOfField = true,
                particleQuality = ParticleQuality.MEDIUM,
                vegetationDensity = 0.5f
            )

            QualityTier.HIGH -> DynamicQualitySettings(
                resolution = ResolutionScale(1.0f, 1.0f),
                shadowQuality = ShadowQuality.HIGH,
                textureQuality = TextureQuality.HIGH,
                effectQuality = EffectQuality.HIGH,
                maxLOD = 4,
                renderDistance = 200f,
                antiAliasing = AntiAliasing.TAA,
                anisotropicFiltering = 8,
                postProcessing = setOf(
                    PostProcess.BLOOM,
                    PostProcess.TONE_MAPPING,
                    PostProcess.SSAO,
                    PostProcess.SSR
                ),
                maxLights = 16,
                maxShadowCasters = 8,
                enableReflections = true,
                enableVolumetrics = true,
                enableMotionBlur = true,
                enableDepthOfField = true,
                particleQuality = ParticleQuality.HIGH,
                vegetationDensity = 0.75f
            )

            QualityTier.ULTRA -> DynamicQualitySettings(
                resolution = ResolutionScale(1.0f, 2.0f), // Support supersampling
                shadowQuality = ShadowQuality.ULTRA,
                textureQuality = TextureQuality.ULTRA,
                effectQuality = EffectQuality.ULTRA,
                maxLOD = 5,
                renderDistance = 500f,
                antiAliasing = AntiAliasing.MSAA_8X,
                anisotropicFiltering = 16,
                postProcessing = setOf(
                    PostProcess.BLOOM,
                    PostProcess.TONE_MAPPING,
                    PostProcess.SSAO,
                    PostProcess.SSR,
                    PostProcess.VOLUMETRIC_FOG,
                    PostProcess.CHROMATIC_ABERRATION,
                    PostProcess.FILM_GRAIN
                ),
                maxLights = 32,
                maxShadowCasters = 16,
                enableReflections = true,
                enableVolumetrics = true,
                enableMotionBlur = true,
                enableDepthOfField = true,
                particleQuality = ParticleQuality.ULTRA,
                vegetationDensity = 1.0f
            )
        }
    }

    /**
     * Start performance monitoring coroutine
     */
    private fun startPerformanceMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                // Collect metrics
                val metrics = PerformanceMetrics(
                    fps = performanceMonitor.getCurrentFPS(),
                    frameTime = performanceMonitor.getAverageFrameTime(),
                    cpuTime = performanceMonitor.getCPUTime(),
                    gpuTime = performanceMonitor.getGPUTime(),
                    drawCalls = performanceMonitor.getDrawCalls(),
                    triangles = performanceMonitor.getTriangleCount(),
                    memoryUsage = memoryMonitor.getCurrentUsage(),
                    vramUsage = gpuProfiler.getVRAMUsage(),
                    temperature = thermalMonitor.getCurrentTemperature(),
                    powerUsage = thermalMonitor.getPowerUsage()
                )

                // Emit metrics
                performanceMetricsFlow.emit(metrics)

                // Check for issues
                detectPerformanceIssues(metrics)

                delay(100) // Update every 100ms
            }
        }
    }

    /**
     * Start adaptation coroutine
     */
    private fun startAdaptation() {
        adaptationJob = CoroutineScope(Dispatchers.Default).launch {
            val metricsBuffer = ArrayDeque<PerformanceMetrics>(30) // 3 seconds of data

            performanceMetrics.collect { metrics ->
                metricsBuffer.addLast(metrics)
                if (metricsBuffer.size > 30) {
                    metricsBuffer.removeFirst()
                }

                // Adapt based on rolling average
                if (metricsBuffer.size >= 10) {
                    adaptQuality(metricsBuffer)
                }
            }
        }
    }

    /**
     * Adapt quality based on performance metrics
     */
    private suspend fun adaptQuality(metrics: ArrayDeque<PerformanceMetrics>) {
        val avgFps = metrics.map { it.fps }.average()
        val avgFrameTime = metrics.map { it.frameTime }.average()
        val maxTemp = metrics.maxOf { it.temperature }
        val avgMemoryUsage = metrics.map { it.memoryUsage }.average()

        // Check if adaptation needed
        when {
            // Critical: Thermal throttling
            maxTemp > 85 -> {
                downgradeQuality(AdaptationReason.THERMAL_THROTTLE)
            }

            // Critical: Out of memory
            avgMemoryUsage > 0.95 -> {
                downgradeQuality(AdaptationReason.MEMORY_PRESSURE)
            }

            // Poor performance: Below target by >20%
            avgFps < targetFrameRate * 0.8 -> {
                if (canDowngrade()) {
                    downgradeQuality(AdaptationReason.LOW_FPS)
                } else {
                    // Fine-tune current settings
                    adjustDynamicSettings(metrics)
                }
            }

            // Good performance: Meeting target with headroom
            avgFps > targetFrameRate * 1.1 && avgFrameTime < targetFrameTime * 0.9 -> {
                if (canUpgrade() && maxTemp < 70) {
                    considerUpgrade(metrics)
                }
            }

            // Stable performance: Fine-tune
            else -> {
                optimizeCurrentSettings(metrics)
            }
        }
    }

    /**
     * Downgrade quality settings
     */
    private suspend fun downgradeQuality(reason: AdaptationReason) {
        val newTier = when (currentTier) {
            QualityTier.ULTRA -> QualityTier.HIGH
            QualityTier.HIGH -> QualityTier.STANDARD
            QualityTier.STANDARD -> QualityTier.MOBILE
            QualityTier.MOBILE -> QualityTier.MOBILE // Can't go lower
        }
        if (newTier != currentTier) {
            currentTier = newTier
            qualityTierFlow.value = newTier
            dynamicQuality = createQualitySettings(newTier)

            adaptationEventsFlow.emit(
                AdaptationEvent(
                    type = AdaptationType.DOWNGRADE,
                    fromTier = currentTier,
                    toTier = newTier,
                    reason = reason,
                    timestamp = currentTimeMillis()
                )
            )
        } else {
            // Already at lowest, try emergency measures
            applyEmergencyMeasures()
        }
    }

    /**
     * Consider upgrading quality if stable
     */
    private suspend fun considerUpgrade(metrics: ArrayDeque<PerformanceMetrics>) {
        // Check stability over longer period
        if (metrics.size < 30) return // Need 3 seconds of stable performance

        val variance = calculateVariance(metrics.map { it.fps })
        if (variance > 5.0) return // Too unstable

        val newTier = when (currentTier) {
            QualityTier.MOBILE -> QualityTier.STANDARD
            QualityTier.STANDARD -> QualityTier.HIGH
            QualityTier.HIGH -> QualityTier.ULTRA
            QualityTier.ULTRA -> QualityTier.ULTRA // Can't go higher
        }

        if (newTier != currentTier) {
            // Test upgrade temporarily
            val testSuccess = testQualityUpgrade(newTier)

            if (testSuccess) {
                currentTier = newTier
                qualityTierFlow.value = newTier
                dynamicQuality = createQualitySettings(newTier)

                adaptationEventsFlow.emit(
                    AdaptationEvent(
                        type = AdaptationType.UPGRADE,
                        fromTier = currentTier,
                        toTier = newTier,
                        reason = AdaptationReason.PERFORMANCE_HEADROOM,
                        timestamp = currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Fine-tune dynamic settings without changing tier
     */
    private suspend fun adjustDynamicSettings(metrics: ArrayDeque<PerformanceMetrics>) {
        val avgGpuTime = metrics.map { it.gpuTime }.average()
        val avgDrawCalls = metrics.map { it.drawCalls }.average()

        // Identify bottleneck
        when {
            avgGpuTime > targetFrameTime * 0.8 -> {
                // GPU bound - reduce GPU load
                dynamicQuality = dynamicQuality.copy(
                    resolution = ResolutionScale(
                        min = max(0.5f, dynamicQuality.resolution.min - 0.1f),
                        max = dynamicQuality.resolution.max
                    ),
                    shadowQuality = downgradeEnum(dynamicQuality.shadowQuality),
                    postProcessing = dynamicQuality.postProcessing - PostProcess.VOLUMETRIC_FOG
                )
            }

            avgDrawCalls > 3000 -> {
                // Too many draw calls - reduce complexity
                dynamicQuality = dynamicQuality.copy(
                    maxLOD = max(1, dynamicQuality.maxLOD - 1),
                    renderDistance = dynamicQuality.renderDistance * 0.8f,
                    vegetationDensity = dynamicQuality.vegetationDensity * 0.75f
                )
            }

            else -> {
                // General optimization
                dynamicQuality = dynamicQuality.copy(
                    particleQuality = downgradeEnum(dynamicQuality.particleQuality),
                    maxLights = max(4, dynamicQuality.maxLights - 2)
                )
            }
        }
        adaptationEventsFlow.emit(
            AdaptationEvent(
                type = AdaptationType.FINE_TUNE,
                fromTier = currentTier,
                toTier = currentTier,
                reason = AdaptationReason.OPTIMIZATION,
                timestamp = currentTimeMillis()
            )
        )
    }

    /**
     * Optimize current settings for stable performance
     */
    private fun optimizeCurrentSettings(metrics: ArrayDeque<PerformanceMetrics>) {
        // Analyze patterns and optimize
        val drawCallSpikes = countSpikes(metrics.map { it.drawCalls })
        val memoryTrend = analyzeTrend(metrics.map { it.memoryUsage })

        if (drawCallSpikes > 3) {
            // Enable more aggressive batching
            dynamicQuality = dynamicQuality.copy(
                enableBatching = true,
                enableInstancing = true
            )
        }

        if (memoryTrend > 0.01) { // Rising memory usage
            // Enable more aggressive memory management
            dynamicQuality = dynamicQuality.copy(
                enableTextureStreaming = true,
                enableMeshStreaming = true
            )
        }
    }

    /**
     * Apply emergency measures when at lowest tier
     */
    private suspend fun applyEmergencyMeasures() {
        dynamicQuality = dynamicQuality.copy(
            resolution = ResolutionScale(0.25f, 0.5f), // Very low resolution
            shadowQuality = ShadowQuality.OFF,
            postProcessing = emptySet(),
            antiAliasing = AntiAliasing.NONE,
            enableReflections = false,
            enableVolumetrics = false,
            particleQuality = ParticleQuality.OFF,
            vegetationDensity = 0.1f,
            renderDistance = 25f
        )

        adaptationEventsFlow.emit(
            AdaptationEvent(
                type = AdaptationType.EMERGENCY,
                fromTier = currentTier,
                toTier = currentTier,
                reason = AdaptationReason.CRITICAL_PERFORMANCE,
                timestamp = currentTimeMillis()
            )
        )
    }

    /**
     * Test quality upgrade temporarily
     */
    private suspend fun testQualityUpgrade(tier: QualityTier): Boolean {
        val testSettings = createQualitySettings(tier)
        val originalSettings = dynamicQuality

        dynamicQuality = testSettings

        // Test for 1 second
        delay(1000)

        // Collect test metrics
        val testFps = performanceMonitor.getCurrentFPS()

        // Revert
        dynamicQuality = originalSettings

        return testFps >= targetFrameRate * 0.95
    }

    /**
     * Detect performance issues
     */
    private suspend fun detectPerformanceIssues(metrics: PerformanceMetrics) {
        val issues = mutableListOf<PerformanceIssue>()

        if (metrics.fps < targetFrameRate * 0.5) {
            issues.add(PerformanceIssue.SEVERE_FPS_DROP)
        }

        if (metrics.frameTime > (targetFrameTime * 2)) {
            issues.add(PerformanceIssue.FRAME_SPIKE)
        }

        if (metrics.memoryUsage > 0.9) {
            issues.add(PerformanceIssue.HIGH_MEMORY)
        }

        if (metrics.temperature > 80) {
            issues.add(PerformanceIssue.HIGH_TEMPERATURE)
        }

        if (metrics.drawCalls > 5000) {
            issues.add(PerformanceIssue.EXCESSIVE_DRAW_CALLS)
        }

        issues.forEach { issue ->
            adaptationEventsFlow.emit(
                AdaptationEvent(
                    type = AdaptationType.ISSUE_DETECTED,
                    fromTier = currentTier,
                    toTier = currentTier,
                    reason = AdaptationReason.PERFORMANCE_ISSUE,
                    timestamp = currentTimeMillis(),
                    details = issue.toString()
                )
            )
        }
    }

    /**
     * Get current quality settings
     */
    fun getCurrentSettings(): DynamicQualitySettings = dynamicQuality

    /**
     * Set target frame rate
     */
    fun setTargetFrameRate(fps: Int) {
        targetFrameRate = fps
        targetFrameTime = 1000.0 / fps
    }

    /**
     * Override quality tier manually
     */
    suspend fun setQualityTier(tier: QualityTier) {
        currentTier = tier
        qualityTierFlow.value = tier
        dynamicQuality = createQualitySettings(tier)

        adaptationEventsFlow.emit(
            AdaptationEvent(
                type = AdaptationType.MANUAL_OVERRIDE,
                fromTier = currentTier,
                toTier = tier,
                reason = AdaptationReason.USER_PREFERENCE,
                timestamp = currentTimeMillis()
            )
        )
    }

    /**
     * Enable/disable automatic adaptation
     */
    fun setAutoAdaptation(enabled: Boolean) {
        if (enabled && adaptationJob == null) {
            startAdaptation()
        } else if (!enabled) {
            adaptationJob?.cancel()
            adaptationJob = null
        }
    }

    /**
     * Shutdown adaptive renderer
     */
    fun shutdown() {
        monitoringJob?.cancel()
        adaptationJob?.cancel()
    }

    // Helper functions
    private fun canUpgrade(): Boolean = currentTier != QualityTier.ULTRA
    private fun canDowngrade(): Boolean = currentTier != QualityTier.MOBILE

    private fun calculateVariance(values: List<Float>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun countSpikes(values: List<Int>): Int {
        val mean = values.average()
        val threshold = mean * 1.5
        return values.count { it > threshold }
    }

    private fun analyzeTrend(values: List<Float>): Float {
        if (values.size < 2) return 0f
        return (values.last() - values.first()) / values.size
    }

    private inline fun <reified T : Enum<T>> downgradeEnum(value: T): T {
        val values = enumValues<T>()
        val currentIndex = values.indexOf(value)
        return if (currentIndex > 0) values[currentIndex - 1] else value
    }

    private fun determineAvailableFeatures(
        hardware: HardwareInfo,
        gpu: GPUCapabilities
    ): Set<RenderFeature> {
        val features = mutableSetOf<RenderFeature>()

        // Basic features always available
        features.add(RenderFeature.BASIC_RENDERING)
        features.add(RenderFeature.TEXTURE_MAPPING)

        // GPU-dependent features
        if (gpu.supportsComputeShaders) features.add(RenderFeature.COMPUTE_SHADERS)
        if (gpu.supportsGeometryShaders) features.add(RenderFeature.GEOMETRY_SHADERS)
        if (gpu.supportsTessellation) features.add(RenderFeature.TESSELLATION)
        if (gpu.supportsRayTracing) features.add(RenderFeature.RAY_TRACING)

        // Memory-dependent features
        if (gpu.vramSize >= 2_000_000_000) {
            features.add(RenderFeature.HIGH_RES_TEXTURES)
            features.add(RenderFeature.TEXTURE_ARRAYS)
        }

        // Platform-specific features
        when (hardware.platform) {
            Platform.DESKTOP -> {
                features.add(RenderFeature.MULTI_VIEWPORT)
                features.add(RenderFeature.ADVANCED_SHADERS)
            }

            Platform.MOBILE -> {
                features.add(RenderFeature.TILE_BASED_RENDERING)
            }

            Platform.WEB -> {
                features.add(RenderFeature.WEBGPU_FEATURES)
            }

            else -> {}
        }

        return features
    }
}

// Support classes and types are in separate files:
// - PerformanceMonitors.kt
// - AdaptiveTypes.kt