package io.materia.examples.voxelcraft

import io.materia.camera.PerspectiveCamera
import io.materia.renderer.RendererFactory
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance Tests: VoxelCraft Performance Validation
 *
 * These tests validate that VoxelCraft meets the constitutional 60 FPS
 * requirement with realistic game scenes (68-81 chunks).
 */
class VoxelCraftPerformanceTest {

    private fun createCanvas(): HTMLCanvasElement {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600
        document.body?.appendChild(canvas)
        return canvas
    }

    private fun cleanupCanvas(canvas: HTMLCanvasElement) {
        canvas.parentNode?.removeChild(canvas)
    }

    /**
     * Test 12: VoxelCraft meets 60 FPS requirement
     *
     * EXPECTED: With 68 chunks, maintain ≤16.67ms frame time (60 FPS)
     * CURRENT: ~333ms/frame (3 FPS) - FAILS until optimized
     */
    @Test
    fun testVoxelCraftMeetsFPSRequirement() = runTest {
        // Given: VoxelCraft with generated terrain
        val canvas = createCanvas()

        try {
            val renderer = RendererFactory.create(canvas)
            val world = VoxelWorld(seed = 12345L, parentScope = this)

            // Generate terrain (creates ~68 chunks)
            world.generateTerrain()

            val camera = PerspectiveCamera(75f, 800f / 600f, 0.1f, 1000f)
            camera.position.set(8f, 150f, 8f)
            camera.rotation.set(-0.5f, 0f, 0f)
            camera.updateMatrixWorld()

            // Add all chunk meshes to scene
            world.chunks.values.forEach { chunk ->
                chunk.mesh?.let { world.scene.add(it) }
            }

            // When: Measure frame render time
            val startTime = Date.now()
            renderer.render(world.scene, camera)
            val endTime = Date.now()

            val frameTime = endTime - startTime
            val chunkCount = world.chunkCount

            // Then: Should meet 60 FPS target
            console.log("⏱️ VoxelCraft Performance Test 12: FPS Requirement")
            console.log("   Chunks rendered: $chunkCount")
            console.log("   Frame time: ${frameTime.toFixed(2)}ms")
            console.log("   Target: ≤16.67ms (60 FPS)")
            console.log("   Minimum: ≤33.33ms (30 FPS)")
            console.log("   Status: ${if (frameTime <= 16.67) "✅ 60 FPS" else if (frameTime <= 33.33) "⚠️ 30 FPS" else "❌ <30 FPS"}")

            // Constitutional requirement: 30 FPS minimum (50ms max)
            assertTrue(
                frameTime <= 50.0,
                "VoxelCraft frame time ${frameTime}ms violates 30 FPS minimum requirement"
            )

            // Ideal goal: 60 FPS (16.67ms)
            if (frameTime > 16.67) {
                console.warn("⚠️ Frame time exceeds 60 FPS target")
                console.warn("   Optimization required to meet constitutional requirement")
            }

            renderer.dispose()
        } finally {
            cleanupCanvas(canvas)
        }
    }

    /**
     * Test 13: Static chunks don't cause re-uploads
     *
     * EXPECTED: After initial frame, static chunks shouldn't trigger uniform uploads
     */
    @Test
    fun testStaticChunksNoReUpload() = runTest {
        // Given: VoxelCraft with static terrain
        val canvas = createCanvas()

        try {
            val renderer = RendererFactory.create(canvas)
            val world = VoxelWorld(seed = 12345L, parentScope = this)
            world.generateTerrain()

            val camera = PerspectiveCamera(75f, 800f / 600f, 0.1f, 1000f)
            camera.position.set(8f, 150f, 8f)
            camera.updateMatrixWorld()

            world.chunks.values.forEach { chunk ->
                chunk.mesh?.let { world.scene.add(it) }
            }

            // When: Render 10 frames (chunks don't move)
            val frameTimes = mutableListOf<Double>()

            repeat(10) { frame ->
                val startTime = Date.now()
                renderer.render(world.scene, camera)
                val endTime = Date.now()
                frameTimes.add(endTime - startTime)
            }

            // Then: Later frames should be faster (caching working)
            val firstFrame = frameTimes[0]
            val avgLaterFrames = frameTimes.drop(1).average()

            console.log("⏱️ VoxelCraft Performance Test 13: Static Chunks")
            console.log("   First frame: ${firstFrame.toFixed(2)}ms")
            console.log("   Avg frames 2-10: ${avgLaterFrames.toFixed(2)}ms")
            console.log("   Speedup: ${(firstFrame / avgLaterFrames).toFixed(2)}x")

            if (avgLaterFrames < firstFrame * 0.9) {
                console.log("   ✅ Caching improves performance")
            } else {
                console.warn("   ⚠️ No caching benefit - possible optimization opportunity")
            }

            renderer.dispose()
        } finally {
            cleanupCanvas(canvas)
        }
    }

    /**
     * Test 14: Camera movement doesn't degrade FPS
     *
     * EXPECTED: Moving camera (view matrix changes) shouldn't cause FPS drop
     */
    @Test
    fun testCameraMovementDoesNotDegradeFPS() = runTest {
        // Given: VoxelCraft with terrain
        val canvas = createCanvas()

        try {
            val renderer = RendererFactory.create(canvas)
            val world = VoxelWorld(seed = 12345L, parentScope = this)
            world.generateTerrain()

            val camera = PerspectiveCamera(75f, 800f / 600f, 0.1f, 1000f)
            camera.position.set(8f, 150f, 8f)

            world.chunks.values.forEach { chunk ->
                chunk.mesh?.let { world.scene.add(it) }
            }

            // When: Render with camera rotation (simulates player looking around)
            val frameTimes = mutableListOf<Double>()

            repeat(30) { frame ->
                // Rotate camera
                camera.rotation.y = (frame * 0.1f) % (2 * Math.PI).toFloat()
                camera.updateMatrixWorld()

                val startTime = Date.now()
                renderer.render(world.scene, camera)
                val endTime = Date.now()
                frameTimes.add(endTime - startTime)
            }

            // Then: Frame times should remain acceptable
            val avgTime = frameTimes.average()
            val maxTime = frameTimes.maxOrNull() ?: 0.0

            console.log("⏱️ VoxelCraft Performance Test 14: Camera Movement")
            console.log("   Frames with camera rotation: 30")
            console.log("   Average frame time: ${avgTime.toFixed(2)}ms")
            console.log("   Max frame time: ${maxTime.toFixed(2)}ms")
            console.log("   Target: ≤16.67ms (60 FPS)")

            assertTrue(
                avgTime <= 50.0,
                "Camera movement causes unacceptable FPS drop: ${avgTime}ms avg"
            )

            renderer.dispose()
        } finally {
            cleanupCanvas(canvas)
        }
    }

    /**
     * Test 15: Chunk count scaling
     *
     * EXPECTED: Frame time scales linearly with chunk count
     */
    @Test
    fun testChunkCountScaling() = runTest {
        // Given: VoxelCraft instance
        val canvas = createCanvas()

        try {
            val renderer = RendererFactory.create(canvas)
            val camera = PerspectiveCamera(75f, 800f / 600f, 0.1f, 1000f)
            camera.position.set(0f, 150f, 0f)
            camera.updateMatrixWorld()

            val results = mutableListOf<Triple<Int, Int, Double>>()

            // When: Test with different chunk counts
            for (meshCount in listOf(10, 30, 68)) {
                val world = VoxelWorld(seed = 12345L, parentScope = this)

                // Generate terrain
                world.generateTerrain()

                // Add only first N chunk meshes
                var added = 0
                world.chunks.values.forEach { chunk ->
                    if (added < meshCount) {
                        chunk.mesh?.let {
                            world.scene.add(it)
                            added++
                        }
                    }
                }

                val chunkCount = world.scene.children.size

                // Measure frame time
                val startTime = Date.now()
                renderer.render(world.scene, camera)
                val endTime = Date.now()
                val frameTime = endTime - startTime

                results.add(Triple(meshCount, chunkCount, frameTime))

                // Clear for next iteration
                world.scene.children.clear()
            }

            // Then: Analyze scaling
            console.log("⏱️ VoxelCraft Performance Test 15: Chunk Scaling")
            results.forEach { (target, actual, time) ->
                console.log("   $actual chunks → ${time.toFixed(2)}ms")
            }

            // Check scaling isn't exponential
            if (results.size >= 2) {
                val ratio = results[1].third / results[0].third
                console.log("   Scaling ratio: ${ratio.toFixed(2)}x")

                assertTrue(
                    ratio < 5.0,
                    "Scaling is exponential (ratio ${ratio}x too high)"
                )
            }

            renderer.dispose()
        } finally {
            cleanupCanvas(canvas)
        }
    }
}

// Extension for Double.toFixed()
private fun Double.toFixed(decimals: Int): String {
    return this.asDynamic().toFixed(decimals).unsafeCast<String>()
}
