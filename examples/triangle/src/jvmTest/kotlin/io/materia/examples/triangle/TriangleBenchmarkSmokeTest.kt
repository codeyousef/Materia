package io.materia.examples.triangle

import io.materia.examples.benchmarks.BenchmarkCapture
import io.materia.examples.benchmarks.BenchmarkRunConfig
import io.materia.examples.benchmarks.readBenchmarkCapture
import io.materia.examples.benchmarks.writeBenchmarkCapture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriangleBenchmarkSmokeTest {
    @Test
    fun benchmarkRunnerProducesValidJsonCapture() = runBlocking {
        assumeTrue(
            System.getenv("MATERIA_ENABLE_JVM_BENCHMARK_SMOKE") == "1",
            "Set MATERIA_ENABLE_JVM_BENCHMARK_SMOKE=1 to run the hardware-backed JVM smoke test"
        )
        assumeTrue(
            !System.getenv("DISPLAY").isNullOrBlank() || !System.getenv("WAYLAND_DISPLAY").isNullOrBlank(),
            "A graphical Linux session is required for the hidden-surface GLFW smoke test"
        )

        val capture = runTriangleJvmBenchmarkCapture(
            config = BenchmarkRunConfig(width = 640, height = 360, warmupFrames = 1, measuredFrames = 3, repeats = 1),
            repeatIndex = 1
        )
        val tempFile = createTempFile(prefix = "triangle-benchmark-", suffix = ".json")
        writeBenchmarkCapture(tempFile, capture)

        val payload = Files.readString(tempFile)
        val decoded: BenchmarkCapture = readBenchmarkCapture(tempFile)

        assertTrue(payload.contains("\"boot_to_first_frame_ms\""), "Expected published metric names in output JSON at ${tempFile.absolutePathString()}")
        assertEquals("Triangle", decoded.workload.scene)
        assertEquals("JVM", decoded.environment.platform)
        assertEquals(1, decoded.sample.repeatIndex)
        assertTrue(decoded.sample.avgFps > 0.0)
        assertTrue(decoded.sample.measuredFrameTimesMs.size == 3)
    }
}
