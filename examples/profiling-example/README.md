# Materia Profiling Example

This example demonstrates how to use the comprehensive profiling system in Materia to identify
performance bottlenecks and optimize your 3D application.

## Running the Example

```bash
# JVM
./gradlew :examples:profiling-example:jvmRun

# JavaScript
./gradlew :examples:profiling-example:jsBrowserDevelopmentRun

# Native
./gradlew :examples:profiling-example:nativeRun
```

## What This Example Demonstrates

1. **Basic Profiling Setup**: Enabling and configuring the profiler
2. **Rendering Pipeline Profiling**: Measuring frame rendering performance
3. **Scene Graph Profiling**: Analyzing scene complexity and traversal
4. **Geometry Profiling**: Tracking buffer generation and optimization
5. **Animation Profiling**: Measuring animation system performance
6. **Real-time Dashboard**: Monitoring performance during execution
7. **Report Generation**: Creating HTML, JSON, and text reports
8. **Performance Recommendations**: Getting automated optimization suggestions

## Key Features Shown

### 1. Automatic Renderer Profiling

```kotlin
// Wrap renderer with profiling
val renderer = createRenderer().getOrThrow().withProfiling()

// All render calls are automatically profiled
renderer.render(scene, camera)
```

### 2. Profiling Dashboard

```kotlin
val dashboard = ProfilingDashboard()
dashboard.enable()

// Check performance in real-time
println(dashboard.getFormattedText())
println("Performance Grade: ${dashboard.getPerformanceGrade()}")
```

### 3. Scene Complexity Analysis

```kotlin
val complexity = scene.getComplexity()
println("Scene nodes: ${complexity.totalNodes}")
println("Max depth: ${complexity.maxDepth}")
println("Complexity score: ${complexity.getComplexityScore()}")
```

### 4. Hotspot Detection

```kotlin
val hotspots = PerformanceProfiler.getHotspots()
hotspots.take(5).forEach { hotspot ->
    println("${hotspot.name}: ${hotspot.percentage}% of frame time")
}
```

### 5. Performance Report Generation

```kotlin
// Generate comprehensive HTML report
val htmlReport = ProfilingReport.generateHtmlReport()
File("performance-report.html").writeText(htmlReport)

// Export to Chrome trace format
val trace = PerformanceProfiler.export(ExportFormat.CHROME_TRACE)
File("trace.json").writeText(trace)
```

## Expected Output

When you run this example, you'll see:

1. Real-time performance dashboard with FPS, frame time, and hotspots
2. Scene complexity analysis showing node count and hierarchy depth
3. Geometry analysis with vertex/triangle counts and memory usage
4. Performance recommendations for optimization
5. Final report showing all profiling data

## Performance Targets

The example validates against Materia's constitutional requirements:

- **60 FPS**: Must maintain 60 frames per second
- **16.67ms frame time**: 95th percentile should be under target
- **Minimal dropped frames**: Less than 5% frame drops

## Output Files

The example generates several output files:

- `performance-report.html` - Comprehensive HTML report with visualizations
- `profiling-data.json` - JSON export for programmatic analysis
- `profiling-data.csv` - CSV export for spreadsheet analysis
- `trace.json` - Chrome trace format for chrome://tracing

## Learning Points

After running this example, you'll understand:

1. How to enable and configure profiling
2. How to identify performance bottlenecks
3. How to analyze scene and geometry complexity
4. How to generate performance reports
5. How to interpret profiling data
6. How to get automated optimization recommendations

## Next Steps

- Try modifying scene complexity and observe performance impact
- Experiment with different profiling verbosity levels
- Compare performance across different platforms
- Use the profiling data to optimize your own applications
