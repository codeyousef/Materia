package io.materia.profiling

/**
 * Comprehensive profiling report generator.
 * Generates detailed performance analysis reports with recommendations.
 */
object ProfilingReport {

    /**
     * Generate a complete profiling report
     */
    fun generateReport(includeRecommendations: Boolean = true): Report {
        val frameStats = PerformanceProfiler.getFrameStats()
        val hotspots = PerformanceProfiler.getHotspots()
        val memoryStats = PerformanceProfiler.getMemoryStats()

        val report = Report(
            timestamp = io.materia.core.platform.Platform.currentTimeNanos(),
            frameStats = frameStats,
            hotspots = hotspots,
            memoryStats = memoryStats,
            recommendations = if (includeRecommendations) generateRecommendations(
                frameStats,
                hotspots,
                memoryStats
            ) else emptyList()
        )

        return report
    }

    /**
     * Generate performance recommendations
     */
    private fun generateRecommendations(
        frameStats: FrameStats,
        hotspots: List<Hotspot>,
        memoryStats: MemoryStats?
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        // Frame rate recommendations
        if (!frameStats.meetsTargetFps(60)) {
            recommendations.add(
                Recommendation(
                    severity = Severity.HIGH,
                    category = "Performance",
                    message = "Not meeting 60 FPS target (average: ${
                        io.materia.core.platform.formatDouble(
                            frameStats.averageFps,
                            1
                        )
                    } FPS)",
                    suggestion = "Review hotspots and optimize critical rendering paths"
                )
            )
        }

        if (frameStats.droppedFrames > frameStats.frameCount * 0.05) {
            recommendations.add(
                Recommendation(
                    severity = Severity.MEDIUM,
                    category = "Performance",
                    message = "${frameStats.droppedFrames} dropped frames detected (${
                        io.materia.core.platform.formatFloat(
                            frameStats.droppedFrames * 100f / frameStats.frameCount,
                            1
                        )
                    }%)",
                    suggestion = "Investigate frame time spikes in hotspots"
                )
            )
        }

        // Hotspot recommendations
        hotspots.take(5).forEach { hotspot ->
            if (hotspot.percentage > 20f) {
                recommendations.add(
                    Recommendation(
                        severity = Severity.HIGH,
                        category = "Hotspot",
                        message = "${hotspot.name} consuming ${
                            io.materia.core.platform.formatFloat(
                                hotspot.percentage,
                                1
                            )
                        }% of frame time",
                        suggestion = "Optimize this operation or reduce call frequency (${hotspot.callCount} calls)"
                    )
                )
            } else if (hotspot.percentage > 10f) {
                recommendations.add(
                    Recommendation(
                        severity = Severity.MEDIUM,
                        category = "Hotspot",
                        message = "${hotspot.name} consuming ${
                            io.materia.core.platform.formatFloat(
                                hotspot.percentage,
                                1
                            )
                        }% of frame time",
                        suggestion = "Consider optimization if this is a critical path"
                    )
                )
            }
        }

        // Memory recommendations
        memoryStats?.let { memory ->
            if (memory.trend > 10 * 1024 * 1024) { // 10MB growth
                recommendations.add(
                    Recommendation(
                        severity = Severity.HIGH,
                        category = "Memory",
                        message = "Memory usage trending upward (${formatBytes(memory.trend)} growth)",
                        suggestion = "Investigate potential memory leaks or excessive allocations"
                    )
                )
            }

            if (memory.gcPressure > 0.5f) {
                recommendations.add(
                    Recommendation(
                        severity = Severity.MEDIUM,
                        category = "Memory",
                        message = "High GC pressure detected (${
                            io.materia.core.platform.formatFloat(
                                memory.gcPressure * 100,
                                1
                            )
                        }%)",
                        suggestion = "Reduce object allocations, use object pooling"
                    )
                )
            }

            if (memory.allocations > 100 * 1024 * 1024) { // 100MB/s
                recommendations.add(
                    Recommendation(
                        severity = Severity.MEDIUM,
                        category = "Memory",
                        message = "High allocation rate (${formatBytes(memory.allocations)}/s)",
                        suggestion = "Review hotspots for temporary object creation"
                    )
                )
            }
        }

        return recommendations
    }

    /**
     * Generate a text report
     */
    fun generateTextReport(): String {
        val report = generateReport()

        return buildString {
            appendLine("=".repeat(80))
            appendLine("Materia Performance Report")
            appendLine("=".repeat(80))
            appendLine()

            // Frame statistics
            appendLine("Frame Statistics:")
            appendLine("-".repeat(80))
            appendLine(
                "  Average FPS:        ${
                    io.materia.core.platform.formatDouble(
                        report.frameStats.averageFps,
                        2
                    )
                }"
            )
            appendLine("  Average Frame Time: ${formatNanos(report.frameStats.averageFrameTime)}")
            appendLine("  Min Frame Time:     ${formatNanos(report.frameStats.minFrameTime)}")
            appendLine("  Max Frame Time:     ${formatNanos(report.frameStats.maxFrameTime)}")
            appendLine("  95th Percentile:    ${formatNanos(report.frameStats.percentile95)}")
            appendLine("  99th Percentile:    ${formatNanos(report.frameStats.percentile99)}")
            appendLine("  Frame Count:        ${report.frameStats.frameCount}")
            appendLine("  Dropped Frames:     ${report.frameStats.droppedFrames}")
            appendLine("  Target Met (60fps): ${if (report.frameStats.meetsTargetFps(60)) "✓ YES" else "✗ NO"}")
            appendLine()

            // Hotspots
            if (report.hotspots.isNotEmpty()) {
                appendLine("Performance Hotspots:")
                appendLine("-".repeat(80))
                appendLine("  Name                                     Total Time      Calls     Avg Time   % Time")
                appendLine("  " + "-".repeat(78))

                report.hotspots.take(10).forEach { hotspot ->
                    val name = hotspot.name.take(40).padEnd(40)
                    val totalTime = formatNanos(hotspot.totalTime).padStart(12)
                    val calls = hotspot.callCount.toString().padStart(10)
                    val avgTime = formatNanos(hotspot.averageTime).padStart(12)
                    val pct =
                        io.materia.core.platform.formatFloat(hotspot.percentage, 1).padStart(7)
                    appendLine("  $name $totalTime $calls $avgTime $pct%")
                }
                appendLine()
            }

            // Memory statistics
            report.memoryStats?.let { memory ->
                appendLine("Memory Statistics:")
                appendLine("-".repeat(80))
                appendLine("  Current Usage:      ${formatBytes(memory.current)}")
                appendLine("  Peak Usage:         ${formatBytes(memory.peak)}")
                appendLine("  Trend:              ${formatBytes(memory.trend)}")
                appendLine("  Allocation Rate:    ${formatBytes(memory.allocations)}/s")
                appendLine(
                    "  GC Pressure:        ${
                        io.materia.core.platform.formatFloat(
                            memory.gcPressure * 100,
                            1
                        )
                    }%"
                )
                appendLine()
            }

            // Recommendations
            if (report.recommendations.isNotEmpty()) {
                appendLine("Recommendations:")
                appendLine("-".repeat(80))

                report.recommendations.groupBy { it.severity }.forEach { (severity, recs) ->
                    appendLine("  ${severity.name} Priority:")
                    recs.forEach { rec ->
                        appendLine("    [${rec.category}] ${rec.message}")
                        appendLine("      → ${rec.suggestion}")
                        appendLine()
                    }
                }
            }

            appendLine("=".repeat(80))
        }
    }

    /**
     * Generate an HTML report
     */
    fun generateHtmlReport(): String {
        val report = generateReport()

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("  <title>Materia Performance Report</title>")
            appendLine("  <style>")
            appendLine("    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 20px; background: #f5f5f5; }")
            appendLine("    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }")
            appendLine("    h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }")
            appendLine("    h2 { color: #555; margin-top: 30px; border-bottom: 1px solid #ddd; padding-bottom: 5px; }")
            appendLine("    table { width: 100%; border-collapse: collapse; margin: 20px 0; }")
            appendLine("    th { background: #4CAF50; color: white; padding: 12px; text-align: left; }")
            appendLine("    td { padding: 10px; border-bottom: 1px solid #ddd; }")
            appendLine("    tr:hover { background: #f9f9f9; }")
            appendLine("    .stat { display: inline-block; margin: 10px 20px 10px 0; }")
            appendLine("    .stat-label { font-weight: bold; color: #666; }")
            appendLine("    .stat-value { font-size: 1.3em; color: #333; }")
            appendLine("    .recommendation { margin: 15px 0; padding: 15px; border-left: 4px solid; }")
            appendLine("    .high { border-color: #f44336; background: #ffebee; }")
            appendLine("    .medium { border-color: #ff9800; background: #fff3e0; }")
            appendLine("    .low { border-color: #2196F3; background: #e3f2fd; }")
            appendLine("    .pass { color: #4CAF50; font-weight: bold; }")
            appendLine("    .fail { color: #f44336; font-weight: bold; }")
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <div class='container'>")
            appendLine("    <h1>Materia Performance Report</h1>")

            // Frame stats
            appendLine("    <h2>Frame Statistics</h2>")
            appendLine(
                "    <div class='stat'><div class='stat-label'>Average FPS</div><div class='stat-value'>${
                    io.materia.core.platform.formatDouble(
                        report.frameStats.averageFps,
                        2
                    )
                }</div></div>"
            )
            appendLine(
                "    <div class='stat'><div class='stat-label'>Average Frame Time</div><div class='stat-value'>${
                    formatNanos(
                        report.frameStats.averageFrameTime
                    )
                }</div></div>"
            )
            appendLine(
                "    <div class='stat'><div class='stat-label'>95th Percentile</div><div class='stat-value'>${
                    formatNanos(
                        report.frameStats.percentile95
                    )
                }</div></div>"
            )
            appendLine("    <div class='stat'><div class='stat-label'>Dropped Frames</div><div class='stat-value'>${report.frameStats.droppedFrames}</div></div>")
            appendLine(
                "    <div class='stat'><div class='stat-label'>60 FPS Target</div><div class='stat-value ${
                    if (report.frameStats.meetsTargetFps(
                            60
                        )
                    ) "pass" else "fail"
                }'>${if (report.frameStats.meetsTargetFps(60)) "PASS" else "FAIL"}</div></div>"
            )

            // Hotspots
            if (report.hotspots.isNotEmpty()) {
                appendLine("    <h2>Performance Hotspots</h2>")
                appendLine("    <table>")
                appendLine("      <tr><th>Name</th><th>Total Time</th><th>Calls</th><th>Avg Time</th><th>% Time</th></tr>")
                report.hotspots.take(10).forEach { hotspot ->
                    appendLine("      <tr>")
                    appendLine("        <td>${hotspot.name}</td>")
                    appendLine("        <td>${formatNanos(hotspot.totalTime)}</td>")
                    appendLine("        <td>${hotspot.callCount}</td>")
                    appendLine("        <td>${formatNanos(hotspot.averageTime)}</td>")
                    appendLine(
                        "        <td>${
                            io.materia.core.platform.formatFloat(
                                hotspot.percentage,
                                1
                            )
                        }%</td>"
                    )
                    appendLine("      </tr>")
                }
                appendLine("    </table>")
            }

            // Memory stats
            report.memoryStats?.let { memory ->
                appendLine("    <h2>Memory Statistics</h2>")
                appendLine(
                    "    <div class='stat'><div class='stat-label'>Current Usage</div><div class='stat-value'>${
                        formatBytes(
                            memory.current
                        )
                    }</div></div>"
                )
                appendLine(
                    "    <div class='stat'><div class='stat-label'>Peak Usage</div><div class='stat-value'>${
                        formatBytes(
                            memory.peak
                        )
                    }</div></div>"
                )
                appendLine(
                    "    <div class='stat'><div class='stat-label'>Allocation Rate</div><div class='stat-value'>${
                        formatBytes(
                            memory.allocations
                        )
                    }/s</div></div>"
                )
                appendLine(
                    "    <div class='stat'><div class='stat-label'>GC Pressure</div><div class='stat-value'>${
                        io.materia.core.platform.formatFloat(
                            memory.gcPressure * 100,
                            1
                        )
                    }%</div></div>"
                )
            }

            // Recommendations
            if (report.recommendations.isNotEmpty()) {
                appendLine("    <h2>Recommendations</h2>")
                report.recommendations.forEach { rec ->
                    val cssClass = when (rec.severity) {
                        Severity.HIGH -> "high"
                        Severity.MEDIUM -> "medium"
                        Severity.LOW -> "low"
                    }
                    appendLine("    <div class='recommendation $cssClass'>")
                    appendLine("      <strong>[${rec.category}] ${rec.message}</strong>")
                    appendLine("      <p>→ ${rec.suggestion}</p>")
                    appendLine("    </div>")
                }
            }

            appendLine("  </div>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    /**
     * Format nanoseconds to human-readable string
     */
    private fun formatNanos(nanos: Long): String {
        return when {
            nanos < 1_000 -> "${nanos}ns"
            nanos < 1_000_000 -> "${io.materia.core.platform.formatDouble(nanos / 1_000.0, 2)}μs"
            nanos < 1_000_000_000 -> "${
                io.materia.core.platform.formatDouble(
                    nanos / 1_000_000.0,
                    2
                )
            }ms"

            else -> "${io.materia.core.platform.formatDouble(nanos / 1_000_000_000.0, 2)}s"
        }
    }

    /**
     * Format bytes to human-readable string
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${io.materia.core.platform.formatDouble(bytes / 1024.0, 2)}KB"
            bytes < 1024 * 1024 * 1024 -> "${
                io.materia.core.platform.formatDouble(
                    bytes / (1024.0 * 1024.0),
                    2
                )
            }MB"

            else -> "${
                io.materia.core.platform.formatDouble(
                    bytes / (1024.0 * 1024.0 * 1024.0),
                    2
                )
            }GB"
        }
    }
}

/**
 * Complete profiling report
 */
data class Report(
    val timestamp: Long,
    val frameStats: FrameStats,
    val hotspots: List<Hotspot>,
    val memoryStats: MemoryStats?,
    val recommendations: List<Recommendation>
)

/**
 * Performance recommendation
 */
data class Recommendation(
    val severity: Severity,
    val category: String,
    val message: String,
    val suggestion: String
)

/**
 * Recommendation severity
 */
enum class Severity {
    HIGH,
    MEDIUM,
    LOW
}
