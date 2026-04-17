package io.materia.examples.benchmarks

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object BenchmarkSnapshotTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) {
            "Usage: BenchmarkSnapshotTool <raw-dir> <snapshot-path> <readme-path>"
        }

        val rawDir = Path.of(args[0])
        val snapshotPath = Path.of(args[1])
        val readmePath = Path.of(args[2])

        val captures = readBenchmarkCaptures(rawDir)
        require(captures.isNotEmpty()) { "No raw benchmark captures found under $rawDir" }

        val aggregates = captures
            .groupBy { BenchmarkMath.matrixKey(it.workload.scene, it.environment.platform) }
            .mapValues { (_, groupedCaptures) -> BenchmarkMath.aggregate(groupedCaptures) }
            .values
            .sortedWith(
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

        val actualKeys = aggregates
            .map { BenchmarkMath.matrixKey(it.workload.scene, it.environment.platform) }
            .toSet()
        require(actualKeys == BenchmarkConstants.expectedMatrixKeys) {
            val missing = BenchmarkConstants.expectedMatrixKeys - actualKeys
            val unexpected = actualKeys - BenchmarkConstants.expectedMatrixKeys
            buildString {
                append("Benchmark matrix incomplete.")
                if (missing.isNotEmpty()) append(" Missing: ${missing.sorted().joinToString()}.")
                if (unexpected.isNotEmpty()) append(" Unexpected: ${unexpected.sorted().joinToString()}.")
            }
        }

        val snapshot = BenchmarkSnapshot(
            generatedAtIsoUtc = Instant.now().toString(),
            methodologySummary = BenchmarkDefaults.methodologySummary,
            results = aggregates
        )

        writeBenchmarkSnapshot(snapshotPath, snapshot)
        replaceReadmeBenchmarkSection(readmePath, BenchmarkMarkdown.renderReadmeSection(snapshot))
    }

    private fun replaceReadmeBenchmarkSection(readmePath: Path, section: String) {
        val current = Files.readString(readmePath)
        val pattern = Regex(
            "${Regex.escape(BenchmarkConstants.readmeBeginMarker)}.*?${Regex.escape(BenchmarkConstants.readmeEndMarker)}",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        require(pattern.containsMatchIn(current)) {
            "README does not contain benchmark markers ${BenchmarkConstants.readmeBeginMarker} / ${BenchmarkConstants.readmeEndMarker}"
        }

        val replacement = buildString {
            appendLine(BenchmarkConstants.readmeBeginMarker)
            appendLine(section.trim())
            append(BenchmarkConstants.readmeEndMarker)
        }
        Files.writeString(readmePath, current.replace(pattern, replacement))
    }
}
