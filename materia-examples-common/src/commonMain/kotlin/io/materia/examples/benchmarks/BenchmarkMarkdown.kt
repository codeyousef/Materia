package io.materia.examples.benchmarks

object BenchmarkMarkdown {
    fun renderReadmeSection(snapshot: BenchmarkSnapshot): String {
        val ordered = snapshot.results.sortedWith(
            compareBy<BenchmarkAggregate>(
                {
                    BenchmarkConstants.sceneOrder.indexOf(it.workload.scene)
                        .let { index -> if (index >= 0) index else Int.MAX_VALUE }
                },
                {
                    BenchmarkConstants.platformOrder.indexOf(it.environment.platform)
                        .let { index -> if (index >= 0) index else Int.MAX_VALUE }
                }
            )
        )

        val environmentNotes = buildEnvironmentNotes(ordered)

        return buildString {
            appendLine("## 📊 Benchmarks")
            appendLine()
            appendLine("These numbers are measured on a specific reference machine and should be read as **environment-specific samples**, not universal guarantees.")
            appendLine()
            appendLine("- Methodology: ${snapshot.methodologySummary}")
            appendLine("- Frame metrics are CPU-side wall-clock timings around each render iteration, not GPU timestamp queries.")
            appendLine("- Raw benchmark captures live in `docs/benchmarks/data/raw/`, and the aggregated snapshot lives in `docs/benchmarks/data/latest.json`.")
            appendLine()
            appendLine("| Scene | Platform | Workload | Boot to First Frame | Avg FPS | P95 Frame | Peak Heap Delta | Notes |")
            appendLine("|-------|----------|----------|---------------------|---------|-----------|-----------------|-------|")
            ordered.forEach { result ->
                appendLine(
                    "| ${result.workload.scene} | ${result.environment.platform} | ${result.workload.workloadLabel} | " +
                        "${formatMs(result.bootToFirstFrameMs)} | ${formatFps(result.avgFps)} | ${formatMs(result.p95FrameMs)} | " +
                        "${formatHeap(result.peakHeapDeltaMb)} | ${rowNotes(result)} |"
                )
            }
            appendLine()
            appendLine("Reference environment:")
            environmentNotes.forEach { note ->
                appendLine("- $note")
            }
            appendLine()
            appendLine("Synthetic contract tests in the repo still exist for validation and guardrails, but the README table above is sourced only from these measured benchmark captures.")
        }
    }

    private fun buildEnvironmentNotes(results: List<BenchmarkAggregate>): List<String> {
        val notes = mutableListOf<String>()
        results.firstOrNull { it.environment.platform == "JVM" }?.let { result ->
            notes += buildString {
                append("JVM: ")
                append(result.environment.osName ?: "unknown OS")
                result.environment.runtimeVersion?.let { append(", Java $it") }
                append(", ${result.environment.backend}")
                append(" on ${result.environment.deviceName}")
            }
        }
        results.firstOrNull { it.environment.platform == "Web" }?.let { result ->
            notes += buildString {
                append("Web: ")
                append(result.environment.browserVersion ?: "unknown browser")
                append(", ${result.environment.backend}")
                append(" on ${result.environment.deviceName}")
            }
        }
        results.firstOrNull { it.environment.platform == "Android" }?.let { result ->
            notes += buildString {
                append("Android: ")
                append(result.environment.emulatorName ?: "device")
                append(" (${result.environment.deviceName})")
                append(", ${result.environment.backend}")
                append(", emulator / host-GPU-assisted result")
            }
        }
        return notes
    }

    private fun rowNotes(result: BenchmarkAggregate): String {
        return buildList {
            add(result.environment.backend)
            result.environment.browserVersion?.let { add(it) }
            result.environment.emulatorName?.let { add("emulator") }
            if (result.memoryMetricKind == BenchmarkMemoryMetricKind.UNAVAILABLE) {
                add("memory n/a")
            }
            addAll(result.notes.take(2))
        }.distinct().joinToString(", ")
    }

    private fun formatMs(value: Double): String = "${formatNumber(value)} ms"

    private fun formatFps(value: Double): String = formatNumber(value)

    private fun formatHeap(value: Double?): String = value?.let { "${formatNumber(it)} MB" } ?: "n/a"

    private fun formatNumber(value: Double): String = (kotlin.math.round(value * 100.0) / 100.0).toString()
}
