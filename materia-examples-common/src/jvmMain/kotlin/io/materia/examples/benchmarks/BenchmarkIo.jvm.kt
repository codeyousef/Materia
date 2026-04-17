package io.materia.examples.benchmarks

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

private const val BYTES_PER_MEGABYTE: Double = 1024.0 * 1024.0

fun currentJvmHeapUsageMb(): Double = Runtime.getRuntime().let { runtime ->
    (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE
}

fun readBenchmarkCapture(path: Path): BenchmarkCapture =
    BenchmarkJson.codec.decodeFromString(Files.readString(path))

fun writeBenchmarkCapture(path: Path, capture: BenchmarkCapture) {
    path.parent?.createDirectories()
    Files.writeString(path, BenchmarkJson.codec.encodeToString(capture))
}

fun writeBenchmarkSnapshot(path: Path, snapshot: BenchmarkSnapshot) {
    path.parent?.createDirectories()
    Files.writeString(path, BenchmarkJson.codec.encodeToString(snapshot))
}

fun readBenchmarkCaptures(rawDir: Path): List<BenchmarkCapture> {
    require(rawDir.exists()) { "Raw benchmark directory does not exist: $rawDir" }

    return Files.walk(rawDir).use { stream ->
        stream
            .filter { it.isRegularFile() && it.name.endsWith(".json") }
            .sorted()
            .map(::readBenchmarkCapture)
            .toList()
    }
}

fun benchmarkCapturePath(
    rawDir: Path,
    scene: String,
    platform: String,
    repeatIndex: Int
): Path {
    val sceneSlug = scene.lowercase().replace(' ', '-')
    val platformSlug = platform.lowercase()
    return rawDir.resolve("$sceneSlug-$platformSlug-repeat$repeatIndex.json")
}
