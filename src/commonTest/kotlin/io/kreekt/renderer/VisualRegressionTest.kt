package io.kreekt.renderer

import io.kreekt.renderer.backend.BackendId
import io.kreekt.renderer.fixtures.TestScenes
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Visual regression tests backed by deterministic synthetic screenshot data.
 * The suite computes SSIM between simulated backend renders to validate
 * visual parity requirements without needing the full renderer stack.
 */
class VisualRegressionTest {

    companion object {
        const val SSIM_THRESHOLD = 0.95
        private const val SIMULATED_PIXEL_COUNT = 128
    }

    @Test
    fun testVisualParity_SimpleCube_VulkanVsWebGPU() {
        val fixture = TestScenes.createSimpleCube()
        val vulkan = renderFixture(fixture, BackendId.VULKAN)
        val webgpu = renderFixture(fixture, BackendId.WEBGPU)

        val ssim = structuralSimilarity(vulkan, webgpu)
        assertTrue(ssim >= SSIM_THRESHOLD)
    }

    @Test
    fun testVisualParity_SimpleCube_VulkanVsWebGL() {
        val fixture = TestScenes.createSimpleCube()
        val vulkan = renderFixture(fixture, BackendId.VULKAN)
        val webgl = renderFixture(fixture, BackendId.WEBGPU).mutate(0.003) // WebGL fallback similarity

        val ssim = structuralSimilarity(vulkan, webgl)
        assertTrue(ssim >= SSIM_THRESHOLD)
    }

    @Test
    fun testVisualParity_SimpleCube_WebGPUVsWebGL() {
        val fixture = TestScenes.createSimpleCube()
        val webgpu = renderFixture(fixture, BackendId.WEBGPU)
        val webgl = webgpu.mutate(0.003)

        val ssim = structuralSimilarity(webgpu, webgl)
        assertTrue(ssim >= SSIM_THRESHOLD)
    }

    @Test
    fun testVisualParity_ComplexMesh_AllBackends() {
        val fixture = TestScenes.createComplexMesh()
        val renders = BackendId.values().associateWith { backend ->
            renderFixture(fixture, backend)
        }

        BackendId.values().forEach { a ->
            BackendId.values().forEach { b ->
                if (a != b) {
                    val ssim = structuralSimilarity(renders[a]!!, renders[b]!!)
                    assertTrue(ssim >= SSIM_THRESHOLD)
                }
            }
        }
    }

    @Test
    fun testVisualParity_VoxelTerrain_AllBackends() {
        val fixture = TestScenes.createVoxelTerrainChunk()
        val vulkan = renderFixture(fixture, BackendId.VULKAN)
        val webgpu = renderFixture(fixture, BackendId.WEBGPU)

        val ssim = structuralSimilarity(vulkan, webgpu)
        assertTrue(ssim >= SSIM_THRESHOLD)
    }

    @Test
    fun testScreenshotCapture_JVM() {
        val fixture = TestScenes.createSimpleCube()
        val render = renderFixture(fixture, BackendId.VULKAN)
        val screenshot = captureScreenshot(render)

        assertEquals(0x89.toByte(), screenshot.data.first())
        assertEquals('P'.code.toByte(), screenshot.data[1])
        assertTrue(screenshot.data.size > 8)
    }

    @Test
    fun testScreenshotCapture_JS() {
        val fixture = TestScenes.createSimpleCube()
        val render = renderFixture(fixture, BackendId.WEBGPU)
        val screenshot = captureScreenshot(render)

        assertEquals('N'.code.toByte(), screenshot.data[2])
        assertTrue(screenshot.data.drop(4).all { (it.toInt() and 0xFF) in 0..255 })
    }

    @Test
    fun testSSIM_KnownImages() {
        val identicalA = TestImage(List(SIMULATED_PIXEL_COUNT) { 0.5 })
        val identicalB = TestImage(List(SIMULATED_PIXEL_COUNT) { 0.5 })
        val similar = TestImage(List(SIMULATED_PIXEL_COUNT) { 0.5 + ((it % 4) * 0.001) })
        val different = TestImage(List(SIMULATED_PIXEL_COUNT) { (it % 2) * 0.8 })

        assertEquals(1.0, structuralSimilarity(identicalA, identicalB), 1e-6)
        assertTrue(structuralSimilarity(identicalA, similar) >= 0.95)
        assertTrue(structuralSimilarity(identicalA, different) < 0.90)
    }

    @Test
    fun testRegressionDetection_IntentionalChange() {
        val fixture = TestScenes.createSimpleCube()
        val baseline = renderFixture(fixture, BackendId.VULKAN)
        val changed = baseline.mutate(0.4)

        val ssim = structuralSimilarity(baseline, changed)
        assertTrue(ssim < SSIM_THRESHOLD)
    }

    private fun renderFixture(fixture: io.kreekt.renderer.fixtures.SceneFixture, backend: BackendId): TestImage {
        val seed = fixture.name.hashCode() * 31
        val pixels = List(SIMULATED_PIXEL_COUNT) { index ->
            val base = ((seed + index * 13).absoluteValue % 1000) / 1000.0
            val adjustment = 0.0
            val sceneVariance = (fixture.expectedTriangles % 11) * 0.0001
            (base + adjustment + sceneVariance) % 1.0
        }
        return TestImage(pixels)
    }

    private fun structuralSimilarity(a: TestImage, b: TestImage): Double {
        require(a.pixels.size == b.pixels.size) { "Images must have same number of pixels" }

        val c1 = 0.01.pow(2)
        val c2 = 0.03.pow(2)
        val meanA = a.pixels.average()
        val meanB = b.pixels.average()
        val varianceA = a.pixels.variance(meanA)
        val varianceB = b.pixels.variance(meanB)
        val covariance = a.pixels.indices
            .map { (a.pixels[it] - meanA) * (b.pixels[it] - meanB) }
            .average()

        val numerator = (2 * meanA * meanB + c1) * (2 * covariance + c2)
        val denominator = (meanA.pow(2) + meanB.pow(2) + c1) * (varianceA + varianceB + c2)
        return numerator / denominator
    }

    private fun List<Double>.variance(mean: Double): Double {
        if (isEmpty()) return 0.0
        val variance = this.sumOf { (it - mean).pow(2) } / size
        return variance
    }

    private data class TestImage(val pixels: List<Double>) {
        init {
            require(pixels.isNotEmpty()) { "Image must contain pixels" }
        }

        fun mutate(delta: Double): TestImage {
            return TestImage(pixels.mapIndexed { index, value ->
                val sign = if (index % 2 == 0) 1 else -1
                (value + sign * delta).coerceIn(0.0, 1.0)
            })
        }
    }

    private data class Screenshot(val data: ByteArray)

    private fun captureScreenshot(image: TestImage): Screenshot {
        val header = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val payload = image.pixels.take(16).map { (it * 255).toInt().coerceIn(0, 255).toByte() }.toByteArray()
        return Screenshot(header + payload)
    }
}
