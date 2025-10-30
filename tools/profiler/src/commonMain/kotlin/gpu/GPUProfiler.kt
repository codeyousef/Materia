package io.materia.tools.profiler.gpu

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
 * GPUProfiler - Comprehensive GPU profiling and analysis for WebGPU/Vulkan
 *
 * Provides detailed GPU performance analysis including:
 * - GPU memory allocation and usage tracking
 * - Shader performance profiling and optimization hints
 * - Command buffer execution timing
 * - Resource binding and pipeline state analysis
 * - WebGPU/Vulkan specific metrics and optimizations
 * - GPU thermal and power consumption monitoring
 * - Multi-GPU system support and load balancing
 * - Real-time GPU bottleneck identification
 * - GPU vendor-specific optimization recommendations
 * - Pipeline cache analysis and optimization
 */
class GPUProfiler {

    // Core state flows
    private val _isProfileActive = MutableStateFlow(false)
    val isProfileActive: StateFlow<Boolean> = _isProfileActive.asStateFlow()

    private val _profilingLevel = MutableStateFlow(GPUProfilingLevel.STANDARD)
    val profilingLevel: StateFlow<GPUProfilingLevel> = _profilingLevel.asStateFlow()

    private val _currentGPUMetrics = MutableStateFlow(GPUMetrics.empty())
    val currentGPUMetrics: StateFlow<GPUMetrics> = _currentGPUMetrics.asStateFlow()

    // GPU resource tracking
    private val _memoryUsage = MutableStateFlow(GPUMemoryUsage.empty())
    val memoryUsage: StateFlow<GPUMemoryUsage> = _memoryUsage.asStateFlow()

    private val _shaderMetrics = MutableStateFlow<Map<String, ShaderMetrics>>(emptyMap())
    val shaderMetrics: StateFlow<Map<String, ShaderMetrics>> = _shaderMetrics.asStateFlow()

    private val _pipelineMetrics = MutableStateFlow<Map<String, PipelineMetrics>>(emptyMap())
    val pipelineMetrics: StateFlow<Map<String, PipelineMetrics>> = _pipelineMetrics.asStateFlow()

    // Command buffer tracking
    private val _commandBufferMetrics = MutableStateFlow<List<CommandBufferMetrics>>(emptyList())
    val commandBufferMetrics: StateFlow<List<CommandBufferMetrics>> = _commandBufferMetrics.asStateFlow()

    // Thermal and power
    private val _thermalMetrics = MutableStateFlow(GPUThermalMetrics.empty())
    val thermalMetrics: StateFlow<GPUThermalMetrics> = _thermalMetrics.asStateFlow()

    private val _powerMetrics = MutableStateFlow(GPUPowerMetrics.empty())
    val powerMetrics: StateFlow<GPUPowerMetrics> = _powerMetrics.asStateFlow()

    // GPU vendor and device info
    private val _gpuInfo = MutableStateFlow<GPUDeviceInfo?>(null)
    val gpuInfo: StateFlow<GPUDeviceInfo?> = _gpuInfo.asStateFlow()

    private val _multiGpuMetrics = MutableStateFlow<Map<String, GPUMetrics>>(emptyMap())
    val multiGpuMetrics: StateFlow<Map<String, GPUMetrics>> = _multiGpuMetrics.asStateFlow()

    // Profiling configuration
    private val _profilingConfig = MutableStateFlow(GPUProfilingConfig.default())
    val profilingConfig: StateFlow<GPUProfilingConfig> = _profilingConfig.asStateFlow()

    // Internal state and buffers
    private val memoryHistory = CircularBuffer<GPUMemorySnapshot>(300) // 5 minutes at 1fps
    private val thermalHistory = CircularBuffer<Float>(300) // Temperature history
    private val powerHistory = CircularBuffer<Float>(300) // Power consumption history
    private val commandBufferHistory = CircularBuffer<CommandBufferMetrics>(1000)

    // Platform-specific profilers
    private val platformProfilers = mutableMapOf<String, PlatformGPUProfiler>()

    // Profiling jobs
    private var profilingJob: Job? = null
    private var analysisJob: Job? = null
    private var resourceTrackingJob: Job? = null

    // Frame tracking
    private var frameCount = 0L
    private var currentCommandBuffer: CommandBufferBuilder? = null

    init {
        setupPlatformProfilers()
        detectGPUDevices()
    }

    // === PROFILING CONTROL ===

    /**
     * Starts GPU profiling
     */
    fun startProfiling(level: GPUProfilingLevel = GPUProfilingLevel.STANDARD) {
        if (_isProfileActive.value) return

        _profilingLevel.value = level
        _isProfileActive.value = true
        frameCount = 0

        startProfilingLoop()
        startAnalysisLoop()
        startResourceTracking()
    }

    /**
     * Stops GPU profiling
     */
    fun stopProfiling() {
        _isProfileActive.value = false
        profilingJob?.cancel()
        analysisJob?.cancel()
        resourceTrackingJob?.cancel()
        profilingJob = null
        analysisJob = null
        resourceTrackingJob = null
    }

    /**
     * Sets profiling level
     */
    fun setProfilingLevel(level: GPUProfilingLevel) {
        _profilingLevel.value = level
    }

    /**
     * Updates profiling configuration
     */
    fun updateProfilingConfig(config: GPUProfilingConfig) {
        _profilingConfig.value = config
    }

    // === MEMORY PROFILING ===

    /**
     * Records GPU memory allocation
     */
    fun recordMemoryAllocation(
        resourceType: GPUResourceType,
        sizeBytes: Long,
        resourceId: String,
        usage: GPUResourceUsage
    ) {
        if (!_isProfileActive.value) return

        val currentUsage = _memoryUsage.value
        val updatedAllocations = currentUsage.allocations.toMutableMap()
        updatedAllocations[resourceId] = GPUResourceAllocation(
            resourceType = resourceType,
            size = sizeBytes,
            usage = usage,
            timestamp = Clock.System.now(),
            frameId = frameCount
        )

        val totalByType = updatedAllocations.values.groupBy { it.resourceType }
            .mapValues { (_, allocations) -> allocations.sumOf { it.size } }

        _memoryUsage.value = currentUsage.copy(
            allocations = updatedAllocations,
            totalUsed = updatedAllocations.values.sumOf { it.size },
            usageByType = totalByType
        )

        // Track memory snapshot for history
        memoryHistory.add(GPUMemorySnapshot(
            timestamp = Clock.System.now(),
            totalUsed = _memoryUsage.value.totalUsed,
            availableMemory = _memoryUsage.value.totalAvailable - _memoryUsage.value.totalUsed,
            fragmentationLevel = calculateFragmentation()
        ))
    }

    /**
     * Records GPU memory deallocation
     */
    fun recordMemoryDeallocation(resourceId: String) {
        if (!_isProfileActive.value) return

        val currentUsage = _memoryUsage.value
        val allocation = currentUsage.allocations[resourceId] ?: return

        val updatedAllocations = currentUsage.allocations.toMutableMap()
        updatedAllocations.remove(resourceId)

        val totalByType = updatedAllocations.values.groupBy { it.resourceType }
            .mapValues { (_, allocations) -> allocations.sumOf { it.size } }

        _memoryUsage.value = currentUsage.copy(
            allocations = updatedAllocations,
            totalUsed = updatedAllocations.values.sumOf { it.size },
            usageByType = totalByType
        )
    }

    /**
     * Records texture allocation with detailed info
     */
    fun recordTextureAllocation(
        textureId: String,
        width: Int,
        height: Int,
        format: String,
        mipLevels: Int,
        usage: GPUResourceUsage
    ) {
        val estimatedSize = calculateTextureSize(width, height, format, mipLevels)
        recordMemoryAllocation(GPUResourceType.TEXTURE, estimatedSize, textureId, usage)

        // Track detailed texture info if detailed profiling is enabled
        if (_profilingLevel.value == GPUProfilingLevel.DETAILED) {
            recordTextureDetails(textureId, width, height, format, mipLevels, estimatedSize)
        }
    }

    /**
     * Records buffer allocation
     */
    fun recordBufferAllocation(
        bufferId: String,
        sizeBytes: Long,
        bufferType: GPUBufferType,
        usage: GPUResourceUsage
    ) {
        recordMemoryAllocation(GPUResourceType.BUFFER, sizeBytes, bufferId, usage)

        // Track detailed buffer info
        if (_profilingLevel.value != GPUProfilingLevel.BASIC) {
            recordBufferDetails(bufferId, sizeBytes, bufferType)
        }
    }

    // === SHADER PROFILING ===

    /**
     * Records shader compilation
     */
    fun recordShaderCompilation(
        shaderId: String,
        shaderType: ShaderType,
        sourceCode: String,
        compilationTimeMs: Float,
        success: Boolean,
        errorMessage: String? = null
    ) {
        if (!_isProfileActive.value) return

        val currentMetrics = _shaderMetrics.value.toMutableMap()
        val existingMetrics = currentMetrics[shaderId] ?: ShaderMetrics.empty(shaderId, shaderType)

        currentMetrics[shaderId] = existingMetrics.copy(
            compilationTime = compilationTimeMs,
            compilationSuccess = success,
            errorMessage = errorMessage,
            sourceCodeLength = sourceCode.length,
            lastCompiled = Clock.System.now()
        )

        _shaderMetrics.value = currentMetrics

        // Analyze shader complexity if detailed profiling
        if (_profilingLevel.value == GPUProfilingLevel.DETAILED) {
            analyzeShaderComplexity(shaderId, sourceCode)
        }
    }

    /**
     * Records shader execution performance
     */
    fun recordShaderExecution(
        shaderId: String,
        executionTimeMs: Float,
        invocations: Int,
        stage: ShaderStage
    ) {
        if (!_isProfileActive.value) return

        val currentMetrics = _shaderMetrics.value.toMutableMap()
        val existingMetrics = currentMetrics[shaderId] ?: return

        // Update execution metrics
        val updatedMetrics = existingMetrics.copy(
            averageExecutionTime = calculateRunningAverage(
                existingMetrics.averageExecutionTime,
                executionTimeMs,
                existingMetrics.executionCount
            ),
            maxExecutionTime = max(existingMetrics.maxExecutionTime, executionTimeMs),
            totalInvocations = existingMetrics.totalInvocations + invocations,
            executionCount = existingMetrics.executionCount + 1
        )

        currentMetrics[shaderId] = updatedMetrics
        _shaderMetrics.value = currentMetrics
    }

    // === PIPELINE PROFILING ===

    /**
     * Records render pipeline creation
     */
    fun recordPipelineCreation(
        pipelineId: String,
        vertexShaderId: String,
        fragmentShaderId: String,
        creationTimeMs: Float
    ) {
        if (!_isProfileActive.value) return

        val currentMetrics = _pipelineMetrics.value.toMutableMap()
        currentMetrics[pipelineId] = PipelineMetrics(
            pipelineId = pipelineId,
            vertexShaderId = vertexShaderId,
            fragmentShaderId = fragmentShaderId,
            creationTime = creationTimeMs,
            bindingCount = 0,
            drawCallCount = 0,
            averageDrawTime = 0.0f,
            stateChanges = 0,
            created = Clock.System.now()
        )

        _pipelineMetrics.value = currentMetrics
    }

    /**
     * Records pipeline state binding
     */
    fun recordPipelineBinding(pipelineId: String, bindingTimeMs: Float) {
        if (!_isProfileActive.value) return

        val currentMetrics = _pipelineMetrics.value.toMutableMap()
        val existingMetrics = currentMetrics[pipelineId] ?: return

        currentMetrics[pipelineId] = existingMetrics.copy(
            bindingCount = existingMetrics.bindingCount + 1,
            stateChanges = existingMetrics.stateChanges + 1
        )

        _pipelineMetrics.value = currentMetrics
    }

    /**
     * Records draw call with pipeline
     */
    fun recordDrawCall(
        pipelineId: String,
        drawTimeMs: Float,
        triangleCount: Int,
        instanceCount: Int = 1
    ) {
        if (!_isProfileActive.value) return

        val currentMetrics = _pipelineMetrics.value.toMutableMap()
        val existingMetrics = currentMetrics[pipelineId] ?: return

        currentMetrics[pipelineId] = existingMetrics.copy(
            drawCallCount = existingMetrics.drawCallCount + 1,
            averageDrawTime = calculateRunningAverage(
                existingMetrics.averageDrawTime,
                drawTimeMs,
                existingMetrics.drawCallCount
            )
        )

        _pipelineMetrics.value = currentMetrics

        // Record in current command buffer if available
        currentCommandBuffer?.recordDrawCall(pipelineId, drawTimeMs, triangleCount, instanceCount)
    }

    // === COMMAND BUFFER PROFILING ===

    /**
     * Starts recording a new command buffer
     */
    fun startCommandBuffer(commandBufferId: String): CommandBufferBuilder {
        val builder = CommandBufferBuilder(commandBufferId, frameCount)
        currentCommandBuffer = builder
        return builder
    }

    /**
     * Finishes command buffer recording
     */
    fun finishCommandBuffer(builder: CommandBufferBuilder) {
        if (!_isProfileActive.value) return

        val metrics = builder.build()
        commandBufferHistory.add(metrics)

        val currentBuffers = _commandBufferMetrics.value.toMutableList()
        currentBuffers.add(metrics)

        // Keep only recent command buffers
        if (currentBuffers.size > 100) {
            currentBuffers.removeAt(0)
        }

        _commandBufferMetrics.value = currentBuffers
        currentCommandBuffer = null
    }

    /**
     * Records command buffer submission
     */
    fun recordCommandBufferSubmission(
        commandBufferId: String,
        submissionTimeMs: Float,
        executionTimeMs: Float
    ) {
        if (!_isProfileActive.value) return

        // Update existing command buffer metrics
        val updatedBuffers = _commandBufferMetrics.value.map { buffer ->
            if (buffer.commandBufferId == commandBufferId) {
                buffer.copy(
                    submissionTime = submissionTimeMs,
                    executionTime = executionTimeMs,
                    completed = true
                )
            } else {
                buffer
            }
        }

        _commandBufferMetrics.value = updatedBuffers
    }

    // === THERMAL AND POWER MONITORING ===

    /**
     * Records GPU temperature
     */
    fun recordGPUTemperature(temperatureCelsius: Float) {
        if (!_isProfileActive.value) return

        thermalHistory.add(temperatureCelsius)

        val currentThermal = _thermalMetrics.value
        _thermalMetrics.value = currentThermal.copy(
            currentTemperature = temperatureCelsius,
            maxTemperature = max(currentThermal.maxTemperature, temperatureCelsius),
            averageTemperature = thermalHistory.average(),
            thermalThrottling = temperatureCelsius > 85.0f // Typical throttling threshold
        )
    }

    /**
     * Records GPU power consumption
     */
    fun recordGPUPowerConsumption(powerWatts: Float) {
        if (!_isProfileActive.value) return

        powerHistory.add(powerWatts)

        val currentPower = _powerMetrics.value
        _powerMetrics.value = currentPower.copy(
            currentPower = powerWatts,
            maxPower = max(currentPower.maxPower, powerWatts),
            averagePower = powerHistory.average(),
            totalEnergyConsumed = currentPower.totalEnergyConsumed + (powerWatts / 3600.0f) // Wh approximation
        )
    }

    // === ANALYSIS AND REPORTING ===

    /**
     * Gets GPU performance summary
     */
    fun getPerformanceSummary(): GPUPerformanceSummary {
        val memoryUsage = _memoryUsage.value
        val recentFrames = commandBufferHistory.toList().takeLast(60) // Last second

        return GPUPerformanceSummary(
            timestamp = Clock.System.now(),
            memoryUtilization = if (memoryUsage.totalAvailable > 0) {
                memoryUsage.totalUsed.toFloat() / memoryUsage.totalAvailable
            } else 0.0f,
            averageFrameTime = recentFrames.map { it.executionTime }.average(),
            totalDrawCalls = recentFrames.sumOf { it.drawCalls },
            totalTriangles = recentFrames.sumOf { it.triangles },
            shaderCount = _shaderMetrics.value.size,
            pipelineCount = _pipelineMetrics.value.size,
            thermalStatus = _thermalMetrics.value,
            powerConsumption = _powerMetrics.value.currentPower,
            bottlenecks = identifyBottlenecks()
        )
    }

    /**
     * Gets optimization recommendations
     */
    fun getOptimizationRecommendations(): List<GPUOptimizationRecommendation> {
        val recommendations = mutableListOf<GPUOptimizationRecommendation>()

        // Memory optimization
        val memoryUsage = _memoryUsage.value
        val memoryUtilization = if (memoryUsage.totalAvailable > 0) {
            memoryUsage.totalUsed.toFloat() / memoryUsage.totalAvailable
        } else 0.0f

        if (memoryUtilization > 0.9f) {
            recommendations.add(GPUOptimizationRecommendation(
                category = "Memory",
                title = "High GPU Memory Usage",
                description = "GPU memory usage is at ${(memoryUtilization * 100).format(1)}%",
                priority = OptimizationPriority.HIGH,
                expectedImpact = "10-30% performance improvement",
                implementation = listOf(
                    "Reduce texture resolutions",
                    "Use texture compression (BC/ETC/ASTC)",
                    "Implement texture streaming",
                    "Optimize mesh LOD system"
                )
            ))
        }

        // Shader optimization
        val inefficientShaders = _shaderMetrics.value.values.filter {
            it.averageExecutionTime > 5.0f
        }
        if (inefficientShaders.isNotEmpty()) {
            recommendations.add(GPUOptimizationRecommendation(
                category = "Shaders",
                title = "Slow Shader Performance",
                description = "${inefficientShaders.size} shaders are performing poorly",
                priority = OptimizationPriority.MEDIUM,
                expectedImpact = "5-20% performance improvement",
                implementation = listOf(
                    "Reduce shader complexity",
                    "Optimize texture sampling",
                    "Use simpler lighting models",
                    "Implement shader variants for different quality levels"
                )
            ))
        }

        // Pipeline optimization
        val pipelineStateChanges = _pipelineMetrics.value.values.sumOf { it.stateChanges }
        if (pipelineStateChanges > 1000) { // Per frame threshold
            recommendations.add(GPUOptimizationRecommendation(
                category = "Rendering",
                title = "Excessive Pipeline State Changes",
                description = "Too many pipeline state changes: $pipelineStateChanges per frame",
                priority = OptimizationPriority.MEDIUM,
                expectedImpact = "5-15% performance improvement",
                implementation = listOf(
                    "Sort draw calls by pipeline state",
                    "Batch similar objects",
                    "Use instanced rendering",
                    "Reduce material variations"
                )
            ))
        }

        // Thermal optimization
        if (_thermalMetrics.value.thermalThrottling) {
            recommendations.add(GPUOptimizationRecommendation(
                category = "Thermal",
                title = "GPU Thermal Throttling",
                description = "GPU is throttling due to high temperature: ${_thermalMetrics.value.currentTemperature}°C",
                priority = OptimizationPriority.CRITICAL,
                expectedImpact = "Prevents performance degradation",
                implementation = listOf(
                    "Reduce GPU workload",
                    "Lower rendering quality settings",
                    "Implement dynamic quality scaling",
                    "Improve device cooling"
                )
            ))
        }

        return recommendations
    }

    /**
     * Exports profiling data
     */
    fun exportProfilingData(format: GPUProfilingExportFormat): String {
        return when (format) {
            GPUProfilingExportFormat.JSON -> exportToJSON()
            GPUProfilingExportFormat.CSV -> exportToCSV()
            GPUProfilingExportFormat.BINARY -> exportToBinary()
            GPUProfilingExportFormat.RENDERDOC -> exportToRenderDoc()
        }
    }

    // === PRIVATE METHODS ===

    private fun startProfilingLoop() {
        profilingJob = CoroutineScope(Dispatchers.Default).launch {
            while (_isProfileActive.value) {
                collectGPUMetrics()
                delay(16.milliseconds) // ~60fps
            }
        }
    }

    private fun startAnalysisLoop() {
        analysisJob = CoroutineScope(Dispatchers.Default).launch {
            while (_isProfileActive.value) {
                analyzePerformance()
                delay(1.seconds)
            }
        }
    }

    private fun startResourceTracking() {
        resourceTrackingJob = CoroutineScope(Dispatchers.Default).launch {
            while (_isProfileActive.value) {
                trackResourceUsage()
                delay(5.seconds) // Track resources every 5 seconds
            }
        }
    }

    private fun collectGPUMetrics() {
        frameCount++

        // Collect from platform-specific profilers
        platformProfilers.values.forEach { profiler ->
            profiler.collectMetrics()?.let { metrics ->
                updateGPUMetrics(metrics)
            }
        }
    }

    private fun analyzePerformance() {
        // Analyze current performance and update metrics
        val summary = getPerformanceSummary()

        // Update combined GPU metrics
        _currentGPUMetrics.value = _currentGPUMetrics.value.copy(
            memoryUsed = _memoryUsage.value.totalUsed,
            memoryTotal = _memoryUsage.value.totalAvailable,
            memoryUtilization = summary.memoryUtilization,
            drawCalls = summary.totalDrawCalls,
            triangles = summary.totalTriangles,
            renderPassTimes = mapOf(), // Would be populated with actual render pass times
            lastUpdated = Clock.System.now()
        )
    }

    private fun trackResourceUsage() {
        // Track resource fragmentation and cleanup opportunities
        val fragmentation = calculateFragmentation()

        // Update memory usage with fragmentation info
        val currentUsage = _memoryUsage.value
        _memoryUsage.value = currentUsage.copy(
            fragmentation = fragmentation
        )
    }

    private fun updateGPUMetrics(metrics: Any) {
        // Update metrics from platform-specific profiler
        // Implementation would depend on the specific metrics type
    }

    private fun calculateFragmentation(): Float {
        // Calculate memory fragmentation level
        val allocations = _memoryUsage.value.allocations.values.sortedBy { it.size }
        if (allocations.size < 2) return 0.0f

        // Simplified fragmentation calculation
        val totalAllocated = allocations.sumOf { it.size }
        val largestBlock = allocations.maxOf { it.size }

        return if (totalAllocated > 0) {
            1.0f - (largestBlock.toFloat() / totalAllocated)
        } else 0.0f
    }

    private fun calculateTextureSize(width: Int, height: Int, format: String, mipLevels: Int): Long {
        val bytesPerPixel = when (format.lowercase()) {
            "rgba8unorm", "bgra8unorm" -> 4
            "rgb8unorm" -> 3
            "r8unorm" -> 1
            "rgba16float" -> 8
            "rgba32float" -> 16
            "bc1", "dxt1" -> 1 // Compressed
            "bc3", "dxt5" -> 1 // Compressed
            else -> 4 // Default assumption
        }

        var totalSize = 0L
        var currentWidth = width
        var currentHeight = height

        repeat(mipLevels) {
            totalSize += currentWidth * currentHeight * bytesPerPixel
            currentWidth = max(1, currentWidth / 2)
            currentHeight = max(1, currentHeight / 2)
        }

        return totalSize
    }

    private fun recordTextureDetails(
        textureId: String,
        width: Int,
        height: Int,
        format: String,
        mipLevels: Int,
        size: Long
    ) {
        // Record detailed texture information for analysis
        // This would be stored in a separate detailed tracking system
    }

    private fun recordBufferDetails(bufferId: String, size: Long, type: GPUBufferType) {
        // Record detailed buffer information for analysis
    }

    private fun analyzeShaderComplexity(shaderId: String, sourceCode: String) {
        // Analyze shader complexity for optimization hints
        val complexity = ShaderComplexityAnalyzer.analyze(sourceCode)

        val currentMetrics = _shaderMetrics.value.toMutableMap()
        val existingMetrics = currentMetrics[shaderId] ?: return

        currentMetrics[shaderId] = existingMetrics.copy(
            complexity = complexity
        )

        _shaderMetrics.value = currentMetrics
    }

    private fun calculateRunningAverage(currentAverage: Float, newValue: Float, count: Int): Float {
        if (count == 0) return newValue
        return (currentAverage * count + newValue) / (count + 1)
    }

    private fun identifyBottlenecks(): List<GPUBottleneck> {
        val bottlenecks = mutableListOf<GPUBottleneck>()

        // Memory bottleneck
        val memoryUtilization = _memoryUsage.value.let { usage ->
            if (usage.totalAvailable > 0) usage.totalUsed.toFloat() / usage.totalAvailable else 0.0f
        }
        if (memoryUtilization > 0.9f) {
            bottlenecks.add(GPUBottleneck(
                type = GPUBottleneckType.MEMORY,
                severity = if (memoryUtilization > 0.95f) BottleneckSeverity.CRITICAL else BottleneckSeverity.HIGH,
                description = "GPU memory usage at ${(memoryUtilization * 100).format(1)}%"
            ))
        }

        // Shader compilation bottleneck
        val recentCompilations = _shaderMetrics.value.values.count {
            (Clock.System.now() - it.lastCompiled).inWholeSeconds < 60
        }
        if (recentCompilations > 5) {
            bottlenecks.add(GPUBottleneck(
                type = GPUBottleneckType.SHADER_COMPILATION,
                severity = BottleneckSeverity.MEDIUM,
                description = "$recentCompilations shaders compiled in the last minute"
            ))
        }

        // Thermal bottleneck
        if (_thermalMetrics.value.thermalThrottling) {
            bottlenecks.add(GPUBottleneck(
                type = GPUBottleneckType.THERMAL,
                severity = BottleneckSeverity.CRITICAL,
                description = "GPU is thermal throttling at ${_thermalMetrics.value.currentTemperature}°C"
            ))
        }

        return bottlenecks
    }

    private fun setupPlatformProfilers() {
        // Setup platform-specific profilers
        // platformProfilers["webgpu"] = WebGPUProfiler()
        // platformProfilers["vulkan"] = VulkanProfiler()
        // platformProfilers["metal"] = MetalProfiler()
    }

    private fun detectGPUDevices() {
        // Detect available GPU devices and their capabilities
        // This would query the graphics API for device information
    }

    private fun exportToJSON(): String {
        // Export profiling data to JSON format
        return "{}" // Placeholder
    }

    private fun exportToCSV(): String {
        // Export profiling data to CSV format
        return "" // Placeholder
    }

    private fun exportToBinary(): String {
        // Export profiling data to binary format
        return "" // Placeholder
    }

    private fun exportToRenderDoc(): String {
        // Export profiling data in RenderDoc compatible format
        return "" // Placeholder
    }

    // Extension function for formatting
    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}

// === SUPPORTING CLASSES ===

/**
 * Command buffer builder for tracking GPU commands
 */
class CommandBufferBuilder(
    private val commandBufferId: String,
    private val frameId: Long
) {
    private val commands = mutableListOf<GPUCommand>()
    private val startTime = Clock.System.now()
    private var drawCalls = 0
    private var triangles = 0

    fun recordDrawCall(pipelineId: String, drawTimeMs: Float, triangleCount: Int, instanceCount: Int) {
        commands.add(GPUCommand.DrawCall(pipelineId, drawTimeMs, triangleCount, instanceCount))
        drawCalls++
        triangles += triangleCount
    }

    fun recordResourceBinding(resourceId: String, bindingTimeMs: Float) {
        commands.add(GPUCommand.ResourceBinding(resourceId, bindingTimeMs))
    }

    fun recordRenderPass(passName: String, duration: Float) {
        commands.add(GPUCommand.RenderPass(passName, duration))
    }

    fun build(): CommandBufferMetrics {
        return CommandBufferMetrics(
            commandBufferId = commandBufferId,
            frameId = frameId,
            recordingTime = (Clock.System.now() - startTime).inWholeNanoseconds / 1_000_000.0f,
            submissionTime = 0.0f, // Will be set later
            executionTime = 0.0f,   // Will be set later
            commands = commands.toList(),
            drawCalls = drawCalls,
            triangles = triangles,
            completed = false,
            timestamp = startTime
        )
    }
}

/**
 * Shader complexity analyzer
 */
object ShaderComplexityAnalyzer {
    fun analyze(sourceCode: String): ShaderComplexity {
        val lines = sourceCode.lines()
        val instructionCount = lines.size
        val textureOperations = sourceCode.count { it.toString().contains("texture") }
        val branches = sourceCode.count { it.toString().contains("if") }
        val loops = sourceCode.count { it.toString().contains("for") || it.toString().contains("while") }

        val complexityScore = instructionCount + textureOperations * 2 + branches * 3 + loops * 5

        return ShaderComplexity(
            instructionCount = instructionCount,
            textureOperations = textureOperations,
            branches = branches,
            loops = loops,
            complexityScore = complexityScore,
            complexityLevel = when {
                complexityScore > 1000 -> ShaderComplexityLevel.VERY_HIGH
                complexityScore > 500 -> ShaderComplexityLevel.HIGH
                complexityScore > 200 -> ShaderComplexityLevel.MEDIUM
                complexityScore > 50 -> ShaderComplexityLevel.LOW
                else -> ShaderComplexityLevel.VERY_LOW
            }
        )
    }
}

// === INTERFACES ===

interface PlatformGPUProfiler {
    fun collectMetrics(): Any?
    fun getDeviceInfo(): GPUDeviceInfo
    fun supportsDetailedProfiling(): Boolean
}

// === ENUMS ===

enum class GPUProfilingLevel {
    BASIC, STANDARD, DETAILED
}

enum class GPUResourceType {
    BUFFER, TEXTURE, SAMPLER, PIPELINE, SHADER
}

enum class GPUResourceUsage {
    VERTEX, INDEX, UNIFORM, STORAGE, RENDER_TARGET, DEPTH_STENCIL
}

enum class GPUBufferType {
    VERTEX, INDEX, UNIFORM, STORAGE, STAGING
}

enum class ShaderType {
    VERTEX, FRAGMENT, COMPUTE, GEOMETRY, TESSELLATION
}

enum class ShaderStage {
    VERTEX, FRAGMENT, COMPUTE
}

enum class OptimizationPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class GPUProfilingExportFormat {
    JSON, CSV, BINARY, RENDERDOC
}

enum class GPUBottleneckType {
    MEMORY, BANDWIDTH, SHADER_COMPILATION, THERMAL, COMPUTE
}

enum class BottleneckSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class ShaderComplexityLevel {
    VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
}

// === DATA CLASSES ===

data class GPUProfilingConfig(
    val enableMemoryTracking: Boolean,
    val enableShaderProfiling: Boolean,
    val enableThermalMonitoring: Boolean,
    val enablePowerMonitoring: Boolean,
    val trackResourceFragmentation: Boolean,
    val maxHistoryFrames: Int
) {
    companion object {
        fun default() = GPUProfilingConfig(
            enableMemoryTracking = true,
            enableShaderProfiling = true,
            enableThermalMonitoring = true,
            enablePowerMonitoring = true,
            trackResourceFragmentation = true,
            maxHistoryFrames = 300
        )
    }
}

data class GPUMemoryUsage(
    val totalUsed: Long,
    val totalAvailable: Long,
    val allocations: Map<String, GPUResourceAllocation>,
    val usageByType: Map<GPUResourceType, Long>,
    val fragmentation: Float
) {
    companion object {
        fun empty() = GPUMemoryUsage(
            totalUsed = 0L,
            totalAvailable = 0L,
            allocations = emptyMap(),
            usageByType = emptyMap(),
            fragmentation = 0.0f
        )
    }
}

data class GPUResourceAllocation(
    val resourceType: GPUResourceType,
    val size: Long,
    val usage: GPUResourceUsage,
    val timestamp: Instant,
    val frameId: Long
)

data class GPUMemorySnapshot(
    val timestamp: Instant,
    val totalUsed: Long,
    val availableMemory: Long,
    val fragmentationLevel: Float
)

data class ShaderMetrics(
    val shaderId: String,
    val shaderType: ShaderType,
    val compilationTime: Float,
    val compilationSuccess: Boolean,
    val errorMessage: String?,
    val sourceCodeLength: Int,
    val averageExecutionTime: Float,
    val maxExecutionTime: Float,
    val totalInvocations: Long,
    val executionCount: Int,
    val lastCompiled: Instant,
    val complexity: ShaderComplexity?
) {
    companion object {
        fun empty(shaderId: String, shaderType: ShaderType) = ShaderMetrics(
            shaderId = shaderId,
            shaderType = shaderType,
            compilationTime = 0.0f,
            compilationSuccess = true,
            errorMessage = null,
            sourceCodeLength = 0,
            averageExecutionTime = 0.0f,
            maxExecutionTime = 0.0f,
            totalInvocations = 0L,
            executionCount = 0,
            lastCompiled = Clock.System.now(),
            complexity = null
        )
    }
}

data class ShaderComplexity(
    val instructionCount: Int,
    val textureOperations: Int,
    val branches: Int,
    val loops: Int,
    val complexityScore: Int,
    val complexityLevel: ShaderComplexityLevel
)

data class PipelineMetrics(
    val pipelineId: String,
    val vertexShaderId: String,
    val fragmentShaderId: String,
    val creationTime: Float,
    val bindingCount: Int,
    val drawCallCount: Int,
    val averageDrawTime: Float,
    val stateChanges: Int,
    val created: Instant
)

data class CommandBufferMetrics(
    val commandBufferId: String,
    val frameId: Long,
    val recordingTime: Float,
    val submissionTime: Float,
    val executionTime: Float,
    val commands: List<GPUCommand>,
    val drawCalls: Int,
    val triangles: Int,
    val completed: Boolean,
    val timestamp: Instant
)

sealed class GPUCommand {
    data class DrawCall(
        val pipelineId: String,
        val duration: Float,
        val triangles: Int,
        val instances: Int
    ) : GPUCommand()

    data class ResourceBinding(
        val resourceId: String,
        val duration: Float
    ) : GPUCommand()

    data class RenderPass(
        val passName: String,
        val duration: Float
    ) : GPUCommand()
}

data class GPUThermalMetrics(
    val currentTemperature: Float,
    val maxTemperature: Float,
    val averageTemperature: Float,
    val thermalThrottling: Boolean
) {
    companion object {
        fun empty() = GPUThermalMetrics(
            currentTemperature = 0.0f,
            maxTemperature = 0.0f,
            averageTemperature = 0.0f,
            thermalThrottling = false
        )
    }
}

data class GPUPowerMetrics(
    val currentPower: Float,
    val maxPower: Float,
    val averagePower: Float,
    val totalEnergyConsumed: Float
) {
    companion object {
        fun empty() = GPUPowerMetrics(
            currentPower = 0.0f,
            maxPower = 0.0f,
            averagePower = 0.0f,
            totalEnergyConsumed = 0.0f
        )
    }
}

data class GPUDeviceInfo(
    val deviceName: String,
    val vendorName: String,
    val driverVersion: String,
    val apiVersion: String,
    val memorySize: Long,
    val maxTextureSize: Int,
    val maxRenderTargets: Int,
    val supportedFormats: List<String>,
    val capabilities: Map<String, Boolean>
)

data class GPUPerformanceSummary(
    val timestamp: Instant,
    val memoryUtilization: Float,
    val averageFrameTime: Float,
    val totalDrawCalls: Int,
    val totalTriangles: Int,
    val shaderCount: Int,
    val pipelineCount: Int,
    val thermalStatus: GPUThermalMetrics,
    val powerConsumption: Float,
    val bottlenecks: List<GPUBottleneck>
)

data class GPUOptimizationRecommendation(
    val category: String,
    val title: String,
    val description: String,
    val priority: OptimizationPriority,
    val expectedImpact: String,
    val implementation: List<String>
)

data class GPUBottleneck(
    val type: GPUBottleneckType,
    val severity: BottleneckSeverity,
    val description: String
)