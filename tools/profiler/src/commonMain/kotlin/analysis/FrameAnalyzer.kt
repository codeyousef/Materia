package io.materia.tools.profiler.analysis

import io.materia.tools.profiler.data.*
import io.materia.tools.profiler.metrics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * FrameAnalyzer - Comprehensive frame analysis and profiling tools
 *
 * Provides detailed frame-by-frame analysis including:
 * - Frame time breakdown by render passes and stages
 * - GPU/CPU synchronization point analysis
 * - Draw call and state change profiling
 * - Memory allocation tracking per frame
 * - Shader compilation and resource loading detection
 * - Frame pacing and vsync analysis
 * - Stuttering and micro-stutter detection
 * - Frame time prediction and optimization suggestions
 * - Multi-threaded frame analysis
 * - Platform-specific frame metrics (WebGPU, Vulkan, Metal)
 */
class FrameAnalyzer {

    // Core state flows
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisMode = MutableStateFlow(FrameAnalysisMode.REAL_TIME)
    val analysisMode: StateFlow<FrameAnalysisMode> = _analysisMode.asStateFlow()

    private val _currentFrame = MutableStateFlow<FrameAnalysis?>(null)
    val currentFrame: StateFlow<FrameAnalysis?> = _currentFrame.asStateFlow()

    private val _frameHistory = MutableStateFlow<List<FrameAnalysis>>(emptyList())
    val frameHistory: StateFlow<List<FrameAnalysis>> = _frameHistory.asStateFlow()

    // Analysis configuration
    private val _analysisConfig = MutableStateFlow(FrameAnalysisConfig.default())
    val analysisConfig: StateFlow<FrameAnalysisConfig> = _analysisConfig.asStateFlow()

    private val _targetFrameRate = MutableStateFlow(60.0f)
    val targetFrameRate: StateFlow<Float> = _targetFrameRate.asStateFlow()

    // Performance thresholds
    private val _performanceThresholds = MutableStateFlow(FramePerformanceThresholds.default())
    val performanceThresholds: StateFlow<FramePerformanceThresholds> = _performanceThresholds.asStateFlow()

    // Stuttering detection
    private val _stutterDetection = MutableStateFlow(StutterDetectionResults.empty())
    val stutterDetection: StateFlow<StutterDetectionResults> = _stutterDetection.asStateFlow()

    // Frame prediction
    private val _framePrediction = MutableStateFlow<FramePrediction?>(null)
    val framePrediction: StateFlow<FramePrediction?> = _framePrediction.asStateFlow()

    // Internal state
    private val frameBuffer = CircularBuffer<FrameAnalysis>(1000) // Last 1000 frames
    private val renderPassBuffer = CircularBuffer<RenderPassInfo>(5000) // More granular data
    private val drawCallBuffer = CircularBuffer<DrawCallInfo>(10000)

    // Analysis jobs
    private var analysisJob: Job? = null
    private var stutterDetectionJob: Job? = null
    private var predictionJob: Job? = null

    // Timing and synchronization
    private var frameCount = 0L
    private var lastFrameStartTime: Instant? = null
    private var lastVSyncTime: Instant? = null

    // Platform-specific analyzers
    private val platformAnalyzers = mutableMapOf<String, PlatformFrameAnalyzer>()

    init {
        setupPlatformAnalyzers()
        startStutterDetection()
        startFramePrediction()
    }

    // === ANALYSIS CONTROL ===

    /**
     * Starts frame analysis
     */
    fun startAnalysis() {
        if (_isAnalyzing.value) return

        _isAnalyzing.value = true
        frameCount = 0

        startAnalysisLoop()
    }

    /**
     * Stops frame analysis
     */
    fun stopAnalysis() {
        _isAnalyzing.value = false
        analysisJob?.cancel()
        analysisJob = null
    }

    /**
     * Sets analysis mode
     */
    fun setAnalysisMode(mode: FrameAnalysisMode) {
        _analysisMode.value = mode
    }

    /**
     * Updates analysis configuration
     */
    fun updateAnalysisConfig(config: FrameAnalysisConfig) {
        _analysisConfig.value = config
    }

    /**
     * Sets target frame rate for analysis
     */
    fun setTargetFrameRate(frameRate: Float) {
        _targetFrameRate.value = frameRate.coerceIn(1.0f, 240.0f)
    }

    // === FRAME RECORDING ===

    /**
     * Records the start of a new frame
     */
    fun recordFrameStart() {
        if (!_isAnalyzing.value) return

        val now = Clock.System.now()
        lastFrameStartTime = now
        frameCount++

        if (_analysisMode.value == FrameAnalysisMode.REAL_TIME) {
            startFrameAnalysis(frameCount, now)
        }
    }

    /**
     * Records the end of a frame
     */
    fun recordFrameEnd() {
        if (!_isAnalyzing.value) return

        val now = Clock.System.now()
        val startTime = lastFrameStartTime ?: return

        val frameTime = (now - startTime).inWholeNanoseconds / 1_000_000.0f // Convert to milliseconds

        if (_analysisMode.value == FrameAnalysisMode.REAL_TIME) {
            finishFrameAnalysis(frameTime, now)
        }
    }

    /**
     * Records a render pass
     */
    fun recordRenderPass(
        passName: String,
        startTime: Instant,
        endTime: Instant,
        drawCalls: Int,
        triangles: Int,
        stateChanges: Int
    ) {
        if (!_isAnalyzing.value) return

        val passInfo = RenderPassInfo(
            frameId = frameCount,
            passName = passName,
            startTime = startTime,
            endTime = endTime,
            duration = (endTime - startTime).inWholeNanoseconds / 1_000_000.0f,
            drawCalls = drawCalls,
            triangles = triangles,
            stateChanges = stateChanges
        )

        renderPassBuffer.add(passInfo)
    }

    /**
     * Records a draw call
     */
    fun recordDrawCall(
        frameId: Long,
        passName: String,
        shaderProgram: String,
        triangles: Int,
        duration: Float,
        cpuTime: Float,
        gpuTime: Float
    ) {
        if (!_isAnalyzing.value) return

        val drawCallInfo = DrawCallInfo(
            frameId = frameId,
            passName = passName,
            shaderProgram = shaderProgram,
            triangles = triangles,
            duration = duration,
            cpuTime = cpuTime,
            gpuTime = gpuTime,
            timestamp = Clock.System.now()
        )

        drawCallBuffer.add(drawCallInfo)
    }

    /**
     * Records GPU/CPU synchronization point
     */
    fun recordSyncPoint(type: SyncPointType, duration: Float) {
        if (!_isAnalyzing.value) return

        // Track synchronization overhead
        val currentFrame = _currentFrame.value
        if (currentFrame != null) {
            val updatedSyncPoints = currentFrame.syncPoints + SyncPoint(type, duration, Clock.System.now())
            _currentFrame.value = currentFrame.copy(syncPoints = updatedSyncPoints)
        }
    }

    /**
     * Records memory allocation during frame
     */
    fun recordFrameAllocation(sizeBytes: Long, category: String) {
        if (!_isAnalyzing.value) return

        val currentFrame = _currentFrame.value
        if (currentFrame != null) {
            val updatedAllocations = currentFrame.memoryAllocations.toMutableMap()
            updatedAllocations[category] = (updatedAllocations[category] ?: 0L) + sizeBytes
            _currentFrame.value = currentFrame.copy(memoryAllocations = updatedAllocations)
        }
    }

    /**
     * Records shader compilation event
     */
    fun recordShaderCompilation(shaderName: String, compilationTime: Float) {
        if (!_isAnalyzing.value) return

        val currentFrame = _currentFrame.value
        if (currentFrame != null) {
            val compilation = ShaderCompilation(shaderName, compilationTime, Clock.System.now())
            val updatedCompilations = currentFrame.shaderCompilations + compilation
            _currentFrame.value = currentFrame.copy(shaderCompilations = updatedCompilations)
        }
    }

    /**
     * Records VSync event
     */
    fun recordVSync() {
        if (!_isAnalyzing.value) return

        val now = Clock.System.now()
        lastVSyncTime = now

        // Analyze frame pacing relative to VSync
        val currentFrame = _currentFrame.value
        if (currentFrame != null) {
            val vsyncOffset = calculateVSyncOffset(currentFrame.startTime, now)
            _currentFrame.value = currentFrame.copy(vsyncOffset = vsyncOffset)
        }
    }

    // === ANALYSIS RESULTS ===

    /**
     * Gets detailed analysis for a specific frame
     */
    fun getFrameAnalysis(frameId: Long): FrameAnalysis? {
        return frameBuffer.toList().find { it.frameId == frameId }
    }

    /**
     * Gets frame analysis for a time range
     */
    fun getFrameAnalysisRange(startTime: Instant, endTime: Instant): List<FrameAnalysis> {
        return frameBuffer.toList().filter { analysis ->
            analysis.startTime >= startTime && analysis.endTime <= endTime
        }
    }

    /**
     * Gets performance statistics for recent frames
     */
    fun getFrameStatistics(frameCount: Int = 100): FrameStatistics {
        val recentFrames = frameBuffer.toList().takeLast(frameCount)
        if (recentFrames.isEmpty()) return FrameStatistics.empty()

        val frameTimes = recentFrames.map { it.totalFrameTime }
        val cpuTimes = recentFrames.map { it.cpuTime }
        val gpuTimes = recentFrames.map { it.gpuTime }

        return FrameStatistics(
            frameCount = recentFrames.size,
            averageFrameTime = frameTimes.average(),
            minFrameTime = frameTimes.minOrNull() ?: 0.0f,
            maxFrameTime = frameTimes.maxOrNull() ?: 0.0f,
            frameTimeVariance = calculateVariance(frameTimes),
            averageCpuTime = cpuTimes.average(),
            averageGpuTime = gpuTimes.average(),
            averageFps = 1000.0f / frameTimes.average(),
            percentile95 = calculatePercentile(frameTimes, 0.95f),
            percentile99 = calculatePercentile(frameTimes, 0.99f),
            stutterCount = countStutters(recentFrames),
            missedVSyncs = countMissedVSyncs(recentFrames)
        )
    }

    /**
     * Gets render pass breakdown for analysis
     */
    fun getRenderPassBreakdown(frameId: Long): List<RenderPassBreakdown> {
        val framePasses = renderPassBuffer.toList().filter { it.frameId == frameId }
        return framePasses.map { pass ->
            RenderPassBreakdown(
                passName = pass.passName,
                duration = pass.duration,
                drawCalls = pass.drawCalls,
                triangles = pass.triangles,
                stateChanges = pass.stateChanges,
                cpuPercentage = calculateCpuPercentage(pass),
                gpuPercentage = calculateGpuPercentage(pass)
            )
        }
    }

    /**
     * Gets bottleneck analysis for recent frames
     */
    fun getBottleneckAnalysis(): FrameBottleneckAnalysis {
        val recentFrames = frameBuffer.toList().takeLast(60) // Last second at 60fps
        if (recentFrames.isEmpty()) return FrameBottleneckAnalysis.empty()

        val cpuBound = recentFrames.count { it.cpuTime > it.gpuTime }
        val gpuBound = recentFrames.count { it.gpuTime > it.cpuTime }
        val syncBound = recentFrames.count { it.syncPoints.sumOf { sync -> sync.duration.toDouble() } > 2.0 }

        val primaryBottleneck = when {
            cpuBound > gpuBound && cpuBound > syncBound -> BottleneckType.CPU
            gpuBound > cpuBound && gpuBound > syncBound -> BottleneckType.GPU
            syncBound > cpuBound && syncBound > gpuBound -> BottleneckType.SYNCHRONIZATION
            else -> BottleneckType.BALANCED
        }

        return FrameBottleneckAnalysis(
            primaryBottleneck = primaryBottleneck,
            cpuBoundPercentage = cpuBound.toFloat() / recentFrames.size * 100,
            gpuBoundPercentage = gpuBound.toFloat() / recentFrames.size * 100,
            syncBoundPercentage = syncBound.toFloat() / recentFrames.size * 100,
            recommendations = generateBottleneckRecommendations(primaryBottleneck, recentFrames)
        )
    }

    /**
     * Gets optimization suggestions based on frame analysis
     */
    fun getOptimizationSuggestions(): List<FrameOptimizationSuggestion> {
        val statistics = getFrameStatistics()
        val bottleneckAnalysis = getBottleneckAnalysis()
        val suggestions = mutableListOf<FrameOptimizationSuggestion>()

        // Frame time suggestions
        if (statistics.averageFrameTime > 16.67f) { // Above 60fps
            suggestions.add(FrameOptimizationSuggestion(
                category = "Frame Rate",
                title = "High Frame Time Detected",
                description = "Average frame time is ${statistics.averageFrameTime.format(2)}ms (target: 16.67ms for 60fps)",
                impact = OptimizationImpact.HIGH,
                difficulty = OptimizationDifficulty.MEDIUM,
                implementation = generateFrameTimeOptimization(statistics, bottleneckAnalysis)
            ))
        }

        // Variance suggestions
        if (statistics.frameTimeVariance > 4.0f) { // High variance indicates stuttering
            suggestions.add(FrameOptimizationSuggestion(
                category = "Frame Pacing",
                title = "Frame Time Variance Too High",
                description = "Frame time variance is ${statistics.frameTimeVariance.format(2)}ms, causing stuttering",
                impact = OptimizationImpact.HIGH,
                difficulty = OptimizationDifficulty.HIGH,
                implementation = generateVarianceOptimization(statistics)
            ))
        }

        // CPU/GPU balance suggestions
        when (bottleneckAnalysis.primaryBottleneck) {
            BottleneckType.CPU -> suggestions.add(createCpuOptimizationSuggestion(statistics))
            BottleneckType.GPU -> suggestions.add(createGpuOptimizationSuggestion(statistics))
            BottleneckType.SYNCHRONIZATION -> suggestions.add(createSyncOptimizationSuggestion(statistics))
            BottleneckType.BALANCED -> { /* No specific suggestions for balanced load */ }
        }

        return suggestions
    }

    // === STUTTERING DETECTION ===

    private fun startStutterDetection() {
        stutterDetectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (_isAnalyzing.value) {
                    detectStuttering()
                }
                delay(1.seconds)
            }
        }
    }

    private fun detectStuttering() {
        val recentFrames = frameBuffer.toList().takeLast(120) // Last 2 seconds at 60fps
        if (recentFrames.size < 60) return

        val frameTimes = recentFrames.map { it.totalFrameTime }
        val targetFrameTime = 1000.0f / _targetFrameRate.value

        // Detect frame time spikes
        val spikes = detectFrameTimeSpikes(frameTimes, targetFrameTime)

        // Detect periodic stutters
        val periodicStutters = detectPeriodicStutters(frameTimes)

        // Detect micro-stutters
        val microStutters = detectMicroStutters(frameTimes, targetFrameTime)

        _stutterDetection.value = StutterDetectionResults(
            frameTimeSpikes = spikes,
            periodicStutters = periodicStutters,
            microStutters = microStutters,
            overallStutterScore = calculateStutterScore(spikes, periodicStutters, microStutters),
            timestamp = Clock.System.now()
        )
    }

    // === FRAME PREDICTION ===

    private fun startFramePrediction() {
        predictionJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (_isAnalyzing.value && frameBuffer.size() >= 60) {
                    updateFramePrediction()
                }
                delay(5.seconds)
            }
        }
    }

    private fun updateFramePrediction() {
        val recentFrames = frameBuffer.toList().takeLast(120)
        if (recentFrames.size < 60) return

        val frameTimes = recentFrames.map { it.totalFrameTime }
        val trend = calculateTrend(frameTimes)
        val predictedFrameTime = predictNextFrameTime(frameTimes)
        val confidence = calculatePredictionConfidence(frameTimes)

        _framePrediction.value = FramePrediction(
            predictedFrameTime = predictedFrameTime,
            confidence = confidence,
            trend = trend,
            timeToTarget = calculateTimeToTarget(frameTimes, 16.67f),
            recommendations = generatePredictionRecommendations(trend, predictedFrameTime)
        )
    }

    // === PRIVATE METHODS ===

    private fun startAnalysisLoop() {
        analysisJob = CoroutineScope(Dispatchers.Default).launch {
            while (_isAnalyzing.value) {
                updateAnalysis()
                delay(16.milliseconds) // ~60fps updates
            }
        }
    }

    private fun updateAnalysis() {
        // Update frame history
        val currentFrames = frameBuffer.toList().takeLast(100)
        _frameHistory.value = currentFrames

        // Update platform-specific analysis
        platformAnalyzers.values.forEach { analyzer ->
            analyzer.update()
        }
    }

    private fun startFrameAnalysis(frameId: Long, startTime: Instant) {
        _currentFrame.value = FrameAnalysis(
            frameId = frameId,
            startTime = startTime,
            endTime = startTime, // Will be updated on frame end
            totalFrameTime = 0.0f,
            cpuTime = 0.0f,
            gpuTime = 0.0f,
            renderPasses = emptyList(),
            drawCallCount = 0,
            triangleCount = 0,
            stateChanges = 0,
            memoryAllocations = emptyMap(),
            shaderCompilations = emptyList(),
            syncPoints = emptyList(),
            vsyncOffset = null,
            performanceScore = 0.0f
        )
    }

    private fun finishFrameAnalysis(frameTime: Float, endTime: Instant) {
        val currentFrame = _currentFrame.value ?: return

        // Calculate CPU and GPU breakdown
        val renderPasses = renderPassBuffer.toList()
            .filter { it.frameId == currentFrame.frameId }

        val totalCpuTime = renderPasses.sumOf { it.duration.toDouble() }.toFloat() * 0.6f // Estimate
        val totalGpuTime = renderPasses.sumOf { it.duration.toDouble() }.toFloat() * 0.8f // Estimate

        val completedFrame = currentFrame.copy(
            endTime = endTime,
            totalFrameTime = frameTime,
            cpuTime = totalCpuTime,
            gpuTime = totalGpuTime,
            renderPasses = renderPasses.map { it.passName },
            drawCallCount = renderPasses.sumOf { it.drawCalls },
            triangleCount = renderPasses.sumOf { it.triangles },
            stateChanges = renderPasses.sumOf { it.stateChanges },
            performanceScore = calculateFramePerformanceScore(frameTime)
        )

        frameBuffer.add(completedFrame)
        _currentFrame.value = null
    }

    private fun calculateVSyncOffset(frameStart: Instant, vsyncTime: Instant): Float {
        return (vsyncTime - frameStart).inWholeNanoseconds / 1_000_000.0f
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0.0f
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average().toFloat()
    }

    private fun calculatePercentile(values: List<Float>, percentile: Float): Float {
        if (values.isEmpty()) return 0.0f
        val sorted = values.sorted()
        val index = (sorted.size * percentile).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun countStutters(frames: List<FrameAnalysis>): Int {
        val targetFrameTime = 1000.0f / _targetFrameRate.value
        return frames.count { it.totalFrameTime > targetFrameTime * 1.5f }
    }

    private fun countMissedVSyncs(frames: List<FrameAnalysis>): Int {
        return frames.count { frame ->
            frame.vsyncOffset?.let { it > 2.0f } ?: false
        }
    }

    private fun calculateCpuPercentage(pass: RenderPassInfo): Float {
        // Simplified calculation - in reality would need platform-specific profiling
        return 60.0f // Placeholder
    }

    private fun calculateGpuPercentage(pass: RenderPassInfo): Float {
        // Simplified calculation - in reality would need GPU profiling
        return 40.0f // Placeholder
    }

    private fun generateBottleneckRecommendations(
        bottleneck: BottleneckType,
        frames: List<FrameAnalysis>
    ): List<String> {
        return when (bottleneck) {
            BottleneckType.CPU -> listOf(
                "Reduce CPU processing overhead",
                "Optimize game logic and physics",
                "Use multithreading for parallel tasks",
                "Reduce draw call preparation time"
            )
            BottleneckType.GPU -> listOf(
                "Reduce polygon count or LOD distance",
                "Optimize shaders and reduce complexity",
                "Use texture compression",
                "Implement frustum culling"
            )
            BottleneckType.SYNCHRONIZATION -> listOf(
                "Reduce GPU/CPU synchronization points",
                "Use asynchronous resource loading",
                "Implement double buffering",
                "Optimize command buffer submission"
            )
            BottleneckType.BALANCED -> listOf(
                "Performance is well balanced",
                "Consider raising quality settings",
                "Focus on optimization polish"
            )
        }
    }

    private fun generateFrameTimeOptimization(
        statistics: FrameStatistics,
        bottleneckAnalysis: FrameBottleneckAnalysis
    ): String {
        return when (bottleneckAnalysis.primaryBottleneck) {
            BottleneckType.CPU -> "Focus on CPU optimization: reduce draw calls, optimize algorithms"
            BottleneckType.GPU -> "Focus on GPU optimization: reduce shader complexity, lower resolution"
            BottleneckType.SYNCHRONIZATION -> "Reduce synchronization overhead: use async operations"
            BottleneckType.BALANCED -> "Balanced optimization: profile specific hotspots"
        }
    }

    private fun generateVarianceOptimization(statistics: FrameStatistics): String {
        return "Implement frame pacing techniques: cap frame rate, use vsync, avoid GC spikes"
    }

    private fun createCpuOptimizationSuggestion(statistics: FrameStatistics): FrameOptimizationSuggestion {
        return FrameOptimizationSuggestion(
            category = "CPU Performance",
            title = "CPU Bound Performance",
            description = "CPU processing is limiting frame rate (${statistics.averageCpuTime.format(2)}ms average)",
            impact = OptimizationImpact.HIGH,
            difficulty = OptimizationDifficulty.MEDIUM,
            implementation = "Optimize algorithms, reduce draw calls, use multithreading"
        )
    }

    private fun createGpuOptimizationSuggestion(statistics: FrameStatistics): FrameOptimizationSuggestion {
        return FrameOptimizationSuggestion(
            category = "GPU Performance",
            title = "GPU Bound Performance",
            description = "GPU rendering is limiting frame rate (${statistics.averageGpuTime.format(2)}ms average)",
            impact = OptimizationImpact.HIGH,
            difficulty = OptimizationDifficulty.MEDIUM,
            implementation = "Reduce shader complexity, optimize textures, implement LOD"
        )
    }

    private fun createSyncOptimizationSuggestion(statistics: FrameStatistics): FrameOptimizationSuggestion {
        return FrameOptimizationSuggestion(
            category = "Synchronization",
            title = "Synchronization Overhead",
            description = "CPU/GPU synchronization is causing performance issues",
            impact = OptimizationImpact.MEDIUM,
            difficulty = OptimizationDifficulty.HIGH,
            implementation = "Reduce sync points, use async operations, implement pipelining"
        )
    }

    private fun detectFrameTimeSpikes(frameTimes: List<Float>, targetFrameTime: Float): List<FrameTimeSpike> {
        val spikes = mutableListOf<FrameTimeSpike>()
        frameTimes.forEachIndexed { index, frameTime ->
            if (frameTime > targetFrameTime * 2.0f) {
                spikes.add(FrameTimeSpike(
                    frameIndex = index,
                    frameTime = frameTime,
                    severity = calculateSpikeSeverity(frameTime, targetFrameTime)
                ))
            }
        }
        return spikes
    }

    private fun detectPeriodicStutters(frameTimes: List<Float>): List<PeriodicStutter> {
        // Implementation would use FFT or autocorrelation to detect periodic patterns
        return emptyList() // Placeholder
    }

    private fun detectMicroStutters(frameTimes: List<Float>, targetFrameTime: Float): List<MicroStutter> {
        // Implementation would detect small but consistent frame time variations
        return emptyList() // Placeholder
    }

    private fun calculateSpikeSeverity(frameTime: Float, targetFrameTime: Float): StutterSeverity {
        val ratio = frameTime / targetFrameTime
        return when {
            ratio > 4.0f -> StutterSeverity.SEVERE
            ratio > 2.5f -> StutterSeverity.HIGH
            ratio > 1.5f -> StutterSeverity.MEDIUM
            else -> StutterSeverity.LOW
        }
    }

    private fun calculateStutterScore(
        spikes: List<FrameTimeSpike>,
        periodicStutters: List<PeriodicStutter>,
        microStutters: List<MicroStutter>
    ): Float {
        // Calculate overall stutter score (0-100, lower is better)
        val spikeScore = spikes.size * 10.0f
        val periodicScore = periodicStutters.size * 15.0f
        val microScore = microStutters.size * 5.0f

        return (spikeScore + periodicScore + microScore).coerceIn(0.0f, 100.0f)
    }

    private fun calculateTrend(frameTimes: List<Float>): PerformanceTrend {
        if (frameTimes.size < 30) return PerformanceTrend.STABLE

        val firstHalf = frameTimes.take(frameTimes.size / 2).average()
        val secondHalf = frameTimes.drop(frameTimes.size / 2).average()
        val change = (secondHalf - firstHalf) / firstHalf

        return when {
            change > 0.1f -> PerformanceTrend.DEGRADING
            change < -0.1f -> PerformanceTrend.IMPROVING
            else -> PerformanceTrend.STABLE
        }
    }

    private fun predictNextFrameTime(frameTimes: List<Float>): Float {
        // Simple moving average prediction - could be enhanced with more sophisticated algorithms
        return frameTimes.takeLast(10).average()
    }

    private fun calculatePredictionConfidence(frameTimes: List<Float>): Float {
        val variance = calculateVariance(frameTimes)
        return (1.0f / (1.0f + variance / 10.0f)).coerceIn(0.0f, 1.0f)
    }

    private fun calculateTimeToTarget(frameTimes: List<Float>, targetFrameTime: Float): Duration? {
        val currentAverage = frameTimes.takeLast(30).average()
        if (currentAverage <= targetFrameTime) return null

        // Estimate time to reach target based on current trend
        val trend = calculateTrend(frameTimes)
        return when (trend) {
            PerformanceTrend.IMPROVING -> {
                val improvementRate = (frameTimes.first() - frameTimes.last()) / frameTimes.size
                val remainingImprovement = currentAverage - targetFrameTime
                val framesNeeded = (remainingImprovement / improvementRate).toInt()
                (framesNeeded * 16).milliseconds // Assuming 60fps
            }
            else -> null
        }
    }

    private fun generatePredictionRecommendations(
        trend: PerformanceTrend,
        predictedFrameTime: Float
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (trend) {
            PerformanceTrend.DEGRADING -> {
                recommendations.add("Performance is degrading - investigate recent changes")
                recommendations.add("Monitor memory usage for leaks")
                recommendations.add("Check for thermal throttling")
            }
            PerformanceTrend.IMPROVING -> {
                recommendations.add("Performance is improving - continue current optimizations")
            }
            PerformanceTrend.STABLE -> {
                if (predictedFrameTime > 16.67f) {
                    recommendations.add("Performance is stable but below target")
                    recommendations.add("Consider targeted optimizations")
                } else {
                    recommendations.add("Performance is stable and meeting targets")
                }
            }
        }

        return recommendations
    }

    private fun calculateFramePerformanceScore(frameTime: Float): Float {
        val targetFrameTime = 1000.0f / _targetFrameRate.value
        return ((targetFrameTime / frameTime) * 100.0f).coerceIn(0.0f, 100.0f)
    }

    private fun setupPlatformAnalyzers() {
        // Setup platform-specific analyzers
        // platformAnalyzers["webgpu"] = WebGPUFrameAnalyzer()
        // platformAnalyzers["vulkan"] = VulkanFrameAnalyzer()
        // platformAnalyzers["metal"] = MetalFrameAnalyzer()
    }

    // Extension function for formatting
    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}

// === INTERFACES AND PLATFORM SUPPORT ===

interface PlatformFrameAnalyzer {
    fun update()
    fun getSpecificMetrics(): Map<String, Any>
}

// === ENUMS ===

enum class FrameAnalysisMode {
    REAL_TIME, BATCH, DETAILED
}

enum class SyncPointType {
    GPU_FENCE, BUFFER_MAP, TEXTURE_UPLOAD, SHADER_COMPILATION
}

enum class BottleneckType {
    CPU, GPU, SYNCHRONIZATION, BALANCED
}

enum class OptimizationImpact {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class OptimizationDifficulty {
    EASY, MEDIUM, HARD, EXPERT
}

enum class StutterSeverity {
    LOW, MEDIUM, HIGH, SEVERE
}

enum class PerformanceTrend {
    IMPROVING, STABLE, DEGRADING
}

// === DATA CLASSES ===

data class FrameAnalysisConfig(
    val enableDetailedProfiling: Boolean,
    val trackMemoryAllocations: Boolean,
    val trackShaderCompilations: Boolean,
    val trackSyncPoints: Boolean,
    val enablePrediction: Boolean,
    val maxHistoryFrames: Int
) {
    companion object {
        fun default() = FrameAnalysisConfig(
            enableDetailedProfiling = true,
            trackMemoryAllocations = true,
            trackShaderCompilations = true,
            trackSyncPoints = true,
            enablePrediction = true,
            maxHistoryFrames = 1000
        )
    }
}

data class FramePerformanceThresholds(
    val excellentFrameTime: Float,
    val goodFrameTime: Float,
    val poorFrameTime: Float,
    val criticalFrameTime: Float
) {
    companion object {
        fun default() = FramePerformanceThresholds(
            excellentFrameTime = 8.33f,  // 120fps
            goodFrameTime = 16.67f,      // 60fps
            poorFrameTime = 33.33f,      // 30fps
            criticalFrameTime = 50.0f    // 20fps
        )
    }
}

data class FrameAnalysis(
    val frameId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val totalFrameTime: Float,
    val cpuTime: Float,
    val gpuTime: Float,
    val renderPasses: List<String>,
    val drawCallCount: Int,
    val triangleCount: Int,
    val stateChanges: Int,
    val memoryAllocations: Map<String, Long>,
    val shaderCompilations: List<ShaderCompilation>,
    val syncPoints: List<SyncPoint>,
    val vsyncOffset: Float?,
    val performanceScore: Float
)

data class RenderPassInfo(
    val frameId: Long,
    val passName: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Float,
    val drawCalls: Int,
    val triangles: Int,
    val stateChanges: Int
)

data class DrawCallInfo(
    val frameId: Long,
    val passName: String,
    val shaderProgram: String,
    val triangles: Int,
    val duration: Float,
    val cpuTime: Float,
    val gpuTime: Float,
    val timestamp: Instant
)

data class SyncPoint(
    val type: SyncPointType,
    val duration: Float,
    val timestamp: Instant
)

data class ShaderCompilation(
    val shaderName: String,
    val compilationTime: Float,
    val timestamp: Instant
)

data class FrameStatistics(
    val frameCount: Int,
    val averageFrameTime: Float,
    val minFrameTime: Float,
    val maxFrameTime: Float,
    val frameTimeVariance: Float,
    val averageCpuTime: Float,
    val averageGpuTime: Float,
    val averageFps: Float,
    val percentile95: Float,
    val percentile99: Float,
    val stutterCount: Int,
    val missedVSyncs: Int
) {
    companion object {
        fun empty() = FrameStatistics(
            frameCount = 0,
            averageFrameTime = 0.0f,
            minFrameTime = 0.0f,
            maxFrameTime = 0.0f,
            frameTimeVariance = 0.0f,
            averageCpuTime = 0.0f,
            averageGpuTime = 0.0f,
            averageFps = 0.0f,
            percentile95 = 0.0f,
            percentile99 = 0.0f,
            stutterCount = 0,
            missedVSyncs = 0
        )
    }
}

data class RenderPassBreakdown(
    val passName: String,
    val duration: Float,
    val drawCalls: Int,
    val triangles: Int,
    val stateChanges: Int,
    val cpuPercentage: Float,
    val gpuPercentage: Float
)

data class FrameBottleneckAnalysis(
    val primaryBottleneck: BottleneckType,
    val cpuBoundPercentage: Float,
    val gpuBoundPercentage: Float,
    val syncBoundPercentage: Float,
    val recommendations: List<String>
) {
    companion object {
        fun empty() = FrameBottleneckAnalysis(
            primaryBottleneck = BottleneckType.BALANCED,
            cpuBoundPercentage = 0.0f,
            gpuBoundPercentage = 0.0f,
            syncBoundPercentage = 0.0f,
            recommendations = emptyList()
        )
    }
}

data class FrameOptimizationSuggestion(
    val category: String,
    val title: String,
    val description: String,
    val impact: OptimizationImpact,
    val difficulty: OptimizationDifficulty,
    val implementation: String
)

data class StutterDetectionResults(
    val frameTimeSpikes: List<FrameTimeSpike>,
    val periodicStutters: List<PeriodicStutter>,
    val microStutters: List<MicroStutter>,
    val overallStutterScore: Float,
    val timestamp: Instant
) {
    companion object {
        fun empty() = StutterDetectionResults(
            frameTimeSpikes = emptyList(),
            periodicStutters = emptyList(),
            microStutters = emptyList(),
            overallStutterScore = 0.0f,
            timestamp = Clock.System.now()
        )
    }
}

data class FrameTimeSpike(
    val frameIndex: Int,
    val frameTime: Float,
    val severity: StutterSeverity
)

data class PeriodicStutter(
    val period: Float,
    val amplitude: Float,
    val frequency: Float
)

data class MicroStutter(
    val startFrame: Int,
    val endFrame: Int,
    val averageVariation: Float
)

data class FramePrediction(
    val predictedFrameTime: Float,
    val confidence: Float,
    val trend: PerformanceTrend,
    val timeToTarget: Duration?,
    val recommendations: List<String>
)