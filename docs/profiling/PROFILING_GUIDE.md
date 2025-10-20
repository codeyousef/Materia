# KreeKt Profiling Guide

## Overview

KreeKt includes a comprehensive profiling system designed to help you identify performance bottlenecks and optimize your 3D applications. The profiling infrastructure provides:

- **Zero-cost when disabled**: Profiling adds no overhead when turned off
- **Cross-platform support**: Works on JVM, JS, Native, Android, and iOS
- **Multiple verbosity levels**: From minimal to detailed profiling
- **Real-time dashboard**: Monitor performance during development
- **Comprehensive reports**: HTML, JSON, and text export formats
- **Automatic recommendations**: Get optimization suggestions

## Quick Start

### Basic Profiling

```kotlin
import io.kreekt.profiling.*

// Enable profiling
PerformanceProfiler.configure(ProfilerConfig(enabled = true))

// In your render loop
fun renderFrame() {
    PerformanceProfiler.startFrame()

    // Your rendering code
    renderer.render(scene, camera)

    PerformanceProfiler.endFrame()
}

// Get statistics
val stats = PerformanceProfiler.getFrameStats()
println("Average FPS: ${stats.averageFps}")
println("Average frame time: ${stats.averageFrameTime / 1_000_000}ms")
```

### Using the Profiling Dashboard

```kotlin
val dashboard = ProfilingDashboard()

// Enable with default configuration
dashboard.enable()

// Check performance status
if (!dashboard.isPerformanceAcceptable()) {
    println("Performance issues detected!")
    println(dashboard.getFormattedText())
}

// Get performance grade
val grade = dashboard.getPerformanceGrade() // A, B, C, D, or F
```

### Measuring Specific Operations

```kotlin
// Measure a code block
PerformanceProfiler.measure("myOperation", ProfileCategory.RENDERING) {
    // Your code here
    processGeometry()
}

// Use a scope for more control
val scope = PerformanceProfiler.startScope("complexOperation", ProfileCategory.GEOMETRY)
try {
    doComplexWork()
} finally {
    scope.end()
}
```

## Advanced Usage

### Profiling Sessions

Create focused profiling sessions to analyze specific scenarios:

```kotlin
val session = ProfilingHelpers.createSession("LoadingBenchmark")

// Run your scenario
loadAllAssets()
renderTestScene()

// End session and get summary
val summary = session.end()
summary.printSummary()
```

### Profiled Renderer

Wrap your renderer for automatic instrumentation:

```kotlin
val baseRenderer = createRenderer().getOrThrow()
val renderer = baseRenderer.withProfiling()

// All render calls are now profiled automatically
renderer.render(scene, camera)
```

### Scene Graph Profiling

Profile scene operations:

```kotlin
// Profile scene traversal
scene.traverseProfiled { obj ->
    // Process each object
    processObject(obj)
}

// Analyze scene complexity
val complexity = scene.getComplexity()
if (complexity.isComplex()) {
    println("Scene has ${complexity.totalNodes} nodes at depth ${complexity.maxDepth}")
}
```

### Geometry Profiling

Profile geometry operations:

```kotlin
// Analyze geometry complexity
val complexity = geometry.analyzeComplexity()
println("Geometry has ${complexity.vertexCount} vertices, ${complexity.triangleCount} triangles")
println("Memory usage: ${complexity.getMemoryUsageMB()}MB")

// Get optimization recommendations
complexity.getRecommendations().forEach { recommendation ->
    println("- $recommendation")
}

// Profile specific operations
GeometryProfiler.profileNormalCalculation {
    geometry.computeVertexNormals()
}
```

### Animation Profiling

Profile animation systems:

```kotlin
// Profile animation mixer update
AnimationProfiler.profileMixerUpdate(deltaTime, mixer.getActionCount()) {
    mixer.update(deltaTime)
}

// Analyze animation complexity
val complexity = AnimationProfiler.analyzeAnimationComplexity(
    trackCount = clip.tracks.size,
    keyframeCount = totalKeyframes,
    duration = clip.duration,
    boneCount = skeleton.bones.size
)

if (complexity.isComplex()) {
    complexity.getRecommendations().forEach { println(it) }
}
```

### Physics Profiling

Profile physics simulation:

```kotlin
// Profile physics step
PhysicsProfiler.profilePhysicsStep(deltaTime, world.bodyCount) {
    world.step(deltaTime)
}

// Analyze physics complexity
val complexity = PhysicsProfiler.analyzePhysicsComplexity(
    bodyCount = world.bodies.size,
    constraintCount = world.constraints.size,
    contactCount = world.contacts.size
)

println("Physics complexity score: ${complexity.getComplexityScore()}")
println("Estimated CPU usage: ${complexity.estimateCPUUsage()}%")
```

## Configuration Options

### Profiler Configuration

```kotlin
PerformanceProfiler.configure(ProfilerConfig(
    enabled = true,                           // Enable/disable profiling
    trackMemory = true,                       // Track memory usage
    frameHistorySize = 300,                   // Keep 300 frames in history
    memoryHistorySize = 60,                   // Keep 60 memory snapshots
    frameStatsWindow = 60,                    // Calculate stats over 60 frames
    memorySnapshotInterval = 10,              // Take memory snapshot every 10 frames
    verbosity = ProfileVerbosity.NORMAL       // Profiling detail level
))
```

### Verbosity Levels

- `MINIMAL`: Only frame statistics, minimal overhead
- `NORMAL`: Frame stats + hotspot detection
- `DETAILED`: Everything including individual measurements
- `TRACE`: Maximum detail with memory tracking

### Dashboard Configuration

```kotlin
dashboard.enable(DashboardConfig(
    updateIntervalMs = 1000,                  // Update every second
    showHotspots = true,                      // Show performance hotspots
    showMemory = true,                        // Show memory statistics
    showFrameGraph = true,                    // Show frame time graph
    showRecommendations = true,               // Show optimization suggestions
    maxHotspots = 10,                         // Show top 10 hotspots
    maxRecommendations = 5,                   // Show top 5 recommendations
    verbosity = ProfileVerbosity.NORMAL
))
```

## Generating Reports

### Text Report

```kotlin
val textReport = ProfilingReport.generateTextReport()
println(textReport)
```

### HTML Report

```kotlin
val htmlReport = ProfilingReport.generateHtmlReport()
File("performance-report.html").writeText(htmlReport)
```

### JSON Export

```kotlin
val json = PerformanceProfiler.export(ExportFormat.JSON)
File("profiling-data.json").writeText(json)
```

### CSV Export

```kotlin
val csv = PerformanceProfiler.export(ExportFormat.CSV)
File("profiling-data.csv").writeText(csv)
```

### Chrome Trace Format

```kotlin
val trace = PerformanceProfiler.export(ExportFormat.CHROME_TRACE)
File("trace.json").writeText(trace)
// Open in chrome://tracing
```

## Performance Targets

### Constitutional Requirements

KreeKt has constitutional performance requirements:

- **60 FPS**: Must maintain 60 FPS (16.67ms per frame)
- **5MB Size**: Base library must be under 5MB
- **100k Triangles**: Should handle 100k triangles at 60 FPS

### Checking Compliance

```kotlin
val stats = PerformanceProfiler.getFrameStats()

// Check if meeting 60 FPS target
if (stats.meetsTargetFps(60)) {
    println("✓ Meeting 60 FPS constitutional requirement")
} else {
    println("✗ NOT meeting 60 FPS requirement")
    println("  Average FPS: ${stats.averageFps}")
    println("  95th percentile frame time: ${stats.percentile95 / 1_000_000}ms")
}

// Get recommendations
val report = ProfilingReport.generateReport()
report.recommendations
    .filter { it.severity == Severity.HIGH }
    .forEach { println("⚠ ${it.message}") }
```

## Best Practices

### 1. Profile in Release Mode

Always profile in release mode with optimizations enabled:

```kotlin
// Only enable profiling in debug builds if needed
if (BuildConfig.DEBUG) {
    ProfilingHelpers.enableDevelopmentProfiling()
} else {
    // Lightweight profiling in production
    ProfilingHelpers.enableProductionProfiling()
}
```

### 2. Use Profiling Sessions

Group related profiling data into sessions:

```kotlin
val session = ProfilingHelpers.createSession("AssetLoadingTest")

// Run scenario
loadModels()
loadTextures()
compileShaders()

val summary = session.end()
summary.printSummary()
```

### 3. Focus on Hotspots

Concentrate optimization efforts on the biggest hotspots:

```kotlin
val hotspots = PerformanceProfiler.getHotspots()

hotspots.filter { it.percentage > 10f }.forEach { hotspot ->
    println("⚠ ${hotspot.name} consuming ${hotspot.percentage}% of frame time")
    println("  Called ${hotspot.callCount} times")
    println("  Average time: ${hotspot.averageTime / 1_000_000}ms")
}
```

### 4. Monitor Memory

Track memory usage to prevent leaks:

```kotlin
val memoryStats = PerformanceProfiler.getMemoryStats()
memoryStats?.let { memory ->
    if (memory.trend > 10 * 1024 * 1024) { // 10MB growth
        println("⚠ Memory trending upward: ${memory.trend / (1024 * 1024)}MB")
    }

    if (memory.gcPressure > 0.5f) {
        println("⚠ High GC pressure: ${memory.gcPressure * 100}%")
    }
}
```

### 5. Automate Performance Testing

Integrate profiling into your CI/CD:

```kotlin
@Test
fun testPerformanceRegression() {
    ProfilingHelpers.enableDevelopmentProfiling()

    val session = ProfilingHelpers.createSession("PerformanceTest")

    // Run test scenario
    repeat(100) {
        renderComplexScene()
    }

    val summary = session.end()

    // Assert performance requirements
    assertTrue(summary.averageFps >= 58.0, "Must maintain near 60 FPS")

    val hotspots = summary.hotspots
    hotspots.forEach { hotspot ->
        assertTrue(
            hotspot.percentage < 30f,
            "${hotspot.name} consuming too much time: ${hotspot.percentage}%"
        )
    }
}
```

## Common Profiling Scenarios

### Game Loop Profiling

```kotlin
fun gameLoop(deltaTime: Float) {
    ProfilingHelpers.profileGameLoop(
        deltaTime = deltaTime,
        updatePhase = {
            updatePhysics(deltaTime)
            updateAnimations(deltaTime)
            updateAI(deltaTime)
        },
        renderPhase = {
            renderer.render(scene, camera)
        }
    )
}
```

### Scene Rendering Profiling

```kotlin
ProfilingHelpers.profileSceneRender(
    sceneName = "MainScene",
    culling = { frustumCulling() },
    sorting = { depthSort() },
    drawCalls = { executeDraw() }
)
```

### Asset Loading Profiling

```kotlin
GeometryProfiler.profilePrimitiveGeneration("SphereGeometry") {
    SphereGeometry(radius = 1f, segments = 32)
}

val geometry = GeometryProfiler.profileBufferUpload(bufferSize) {
    uploadGeometryToGPU(geometry)
}
```

## Troubleshooting

### High Frame Times

```kotlin
val hotspots = PerformanceProfiler.getHotspots()
hotspots.take(5).forEach { hotspot ->
    when (hotspot.category) {
        ProfileCategory.RENDERING -> println("Rendering bottleneck: ${hotspot.name}")
        ProfileCategory.PHYSICS -> println("Physics bottleneck: ${hotspot.name}")
        ProfileCategory.SCENE_GRAPH -> println("Scene graph bottleneck: ${hotspot.name}")
        else -> println("Other bottleneck: ${hotspot.name}")
    }
}
```

### Memory Issues

```kotlin
val memoryStats = PerformanceProfiler.getMemoryStats()
memoryStats?.let { memory ->
    println("Current: ${memory.current / (1024 * 1024)}MB")
    println("Peak: ${memory.peak / (1024 * 1024)}MB")
    println("Allocation rate: ${memory.allocations / (1024 * 1024)}MB/s")

    if (memory.gcPressure > 0.3f) {
        println("Reduce object allocations:")
        println("- Use object pooling")
        println("- Reuse buffers")
        println("- Avoid temporary objects in hot paths")
    }
}
```

### Profiler Overhead

The profiler is designed to have minimal overhead:

- **Disabled**: Zero overhead (inlined no-ops)
- **MINIMAL**: < 1% overhead
- **NORMAL**: 1-3% overhead
- **DETAILED**: 3-5% overhead
- **TRACE**: 5-10% overhead

Choose the appropriate verbosity level for your needs.

## Platform-Specific Notes

### JVM

- Full memory tracking via Runtime API
- High-precision timing via System.nanoTime()
- JFR integration available

### JavaScript

- Memory tracking via performance.memory (when available)
- High-precision timing via performance.now()
- Chrome DevTools integration

### Native

- Limited memory tracking
- Platform-specific timing
- Best used with external profilers

### Android

- Memory tracking via Runtime API
- Android Profiler integration
- Battery impact monitoring recommended

### iOS

- Limited memory tracking
- Instruments integration recommended
- Consider energy impact

## Further Resources

- [Performance Optimization Guide](./OPTIMIZATION_GUIDE.md)
- [Memory Management Best Practices](./MEMORY_GUIDE.md)
- [Rendering Pipeline Deep Dive](./RENDERING_PIPELINE.md)
- [API Reference](../api/profiling/index.html)

## IBL Convolution Profiling

Use `IBLConvolutionProfiler` to inspect the CPU cost of irradiance and prefilter generation. The profiler records the most recent durations and sample counts:

```kotlin
val metrics = IBLConvolutionProfiler.snapshot()
println("Prefilter: ${metrics.prefilterMs} ms, samples=${metrics.prefilterSamples}")
println("Irradiance: ${metrics.irradianceMs} ms")
```

These values bubble up into `RenderStats` (`iblCpuMs`, `iblPrefilterMipCount`, `iblLastRoughness`) so the WebGPU renderer can surface lighting diagnostics without additional instrumentation.
