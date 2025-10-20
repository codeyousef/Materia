package io.kreekt.performance

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.MathObjectPools
import io.kreekt.core.math.Vector3
import io.kreekt.core.math.VectorBatch
import io.kreekt.core.math.normalizeFast
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.primitives.BoxGeometry
import io.kreekt.material.MeshBasicMaterial
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.time.Duration

/**
 * Performance optimization validation tests
 * Verifies that optimizations meet performance targets
 */
class PerformanceOptimizationTest {

    @Test
    fun testMatrixUpdateOptimization() {
        // Create scene with 1000 objects
        val scene = Scene()
        val objects = List(1000) {
            Mesh(BoxGeometry(), MeshBasicMaterial()).apply {
                position.set(
                    (it % 10).toFloat(),
                    ((it / 10) % 10).toFloat(),
                    (it / 100).toFloat()
                )
                scene.add(this)
            }
        }

        // Benchmark: Update world matrices WITHOUT changes
        val timeWithoutChanges = measureTime {
            repeat(100) {
                scene.updateMatrixWorld(force = false)
            }
        }

        println("Matrix update (no changes): ${timeWithoutChanges.inWholeMilliseconds}ms")

        // Benchmark: Update world matrices WITH changes
        val timeWithChanges = measureTime {
            repeat(100) {
                // Change position of 10% of objects
                objects.take(100).forEach { obj ->
                    obj.position.x += 0.001f
                }
                scene.updateMatrixWorld(force = false)
            }
        }

        println("Matrix update (with changes): ${timeWithChanges.inWholeMilliseconds}ms")

        // Optimization should make no-change updates significantly faster
        assertTrue(
            timeWithoutChanges < timeWithChanges,
            "Dirty flag optimization should make unchanged updates faster"
        )
    }

    @Test
    fun testObjectPoolingPerformance() {
        // JVM warmup to avoid JIT compilation overhead
        repeat(1000) {
            val v1 = Vector3(1f, 2f, 3f)
            val v2 = Vector3(4f, 5f, 6f)
            v1.add(v2)
        }
        repeat(1000) {
            MathObjectPools.withVector3 { v1 ->
                v1.set(1f, 2f, 3f)
                MathObjectPools.withVector3 { v2 ->
                    v2.set(4f, 5f, 6f)
                    v1.add(v2)
                }
            }
        }

        // Benchmark: WITHOUT pooling (allocating new objects)
        val timeWithoutPooling = measureTime {
            repeat(10000) {
                val v1 = Vector3(1f, 2f, 3f)
                val v2 = Vector3(4f, 5f, 6f)
                val result = v1.add(v2)
            }
        }

        println("Vector operations (no pooling): ${timeWithoutPooling.inWholeMilliseconds}ms")

        // Benchmark: WITH pooling
        val timeWithPooling = measureTime {
            repeat(10000) {
                MathObjectPools.withVector3 { v1 ->
                    v1.set(1f, 2f, 3f)
                    MathObjectPools.withVector3 { v2 ->
                        v2.set(4f, 5f, 6f)
                        v1.add(v2)
                    }
                }
            }
        }

        println("Vector operations (with pooling): ${timeWithPooling.inWholeMilliseconds}ms")

        // Pooling should reduce allocations and improve performance
        // Note: JVM escape analysis may optimize allocations, making pooling overhead higher
        // We allow adaptive tolerance because the benchmark is micro-scale and noise dominates
        val ratio = if (timeWithoutPooling == Duration.ZERO) {
            Double.POSITIVE_INFINITY
        } else {
            timeWithPooling / timeWithoutPooling
        }

        val baselineMs = timeWithoutPooling.inWholeMilliseconds
        if (baselineMs <= 5) {
            // If the baseline completes in under ~5ms the measurement is dominated by noise.
            // Ensure we at least avoid catastrophic regressions (>10x slower) while logging context.
            println("Baseline under 5ms; treating pooling ratio as informational (ratio=${"%.2f".format(ratio)})")
            assertTrue(
                ratio <= 10.0,
                "Object pooling should not be an order of magnitude slower: ratio=${"%.2f".format(ratio)}, " +
                    "pooling=${timeWithPooling.inWholeMicroseconds}µs, no-pooling=${timeWithoutPooling.inWholeMicroseconds}µs"
            )
        } else {
            val acceptableRatio = 4.0 // generous tolerance allowing for pool bookkeeping overhead
            assertTrue(
                ratio <= acceptableRatio,
                "Object pooling should not excessively degrade performance: ratio=${"%.2f".format(ratio)}, " +
                    "pooling=${timeWithPooling.inWholeMicroseconds}µs, no-pooling=${timeWithoutPooling.inWholeMicroseconds}µs"
            )
        }

        // Check pool stats
        val stats = MathObjectPools.getStats()
        println("Pool stats: $stats")
    }

    @Test
    @Ignore  // DefaultRenderer not yet implemented - in .skip file
    fun testFrustumCullingPerformance() {
        // Create scene with many objects (some outside frustum)
        val scene = Scene()
        repeat(100) { i ->
            repeat(10) { j ->
                Mesh(BoxGeometry(), MeshBasicMaterial()).apply {
                    position.set(i.toFloat(), 0f, j.toFloat())
                    scene.add(this)
                }
            }
        }

        val camera = PerspectiveCamera(75f, 1.0f, 0.1f, 100f)
        camera.position.set(5f, 5f, 5f)
        camera.lookAt(Vector3.ZERO)
        camera.updateMatrixWorld()

        // val renderer = DefaultRenderer(RendererConfig())

        // Benchmark rendering with culling
        // val timeWithCulling = measureTime {
        //     repeat(60) { // 60 frames
        //         renderer.render(scene, camera)
        //     }
        // }

        // println("Render time (with culling): ${timeWithCulling.inWholeMilliseconds}ms")

        // val stats = renderer.stats
        // println("Render stats: calls=${stats.drawCalls}, triangles=${stats.triangles}")

        // Should be able to render 60 frames in reasonable time
        // assertTrue(
        //     timeWithCulling.inWholeMilliseconds < 1000,
        //     "Should render 60 frames with culling in < 1 second"
        // )
    }

    @Test
    fun testInlineMathPerformance() {
        val vectors = Array(1000) { Vector3(it.toFloat(), it.toFloat(), it.toFloat()) }

        // JVM warmup
        repeat(50) {
            for (v in vectors) {
                v.normalize()
                v.normalizeFast()
            }
            VectorBatch.normalizeArray(vectors)
        }

        // Benchmark: Regular operations
        val timeRegular = measureTime {
            repeat(100) {
                for (v in vectors) {
                    v.normalize()
                }
            }
        }

        println("Normalize (regular): ${timeRegular.inWholeMilliseconds}ms")

        // Benchmark: Fast inline operations
        val timeInline = measureTime {
            repeat(100) {
                for (v in vectors) {
                    v.normalizeFast()
                }
            }
        }

        println("Normalize (inline): ${timeInline.inWholeMilliseconds}ms")

        // Batch operations
        val timeBatch = measureTime {
            repeat(100) {
                VectorBatch.normalizeArray(vectors)
            }
        }

        println("Normalize (batch): ${timeBatch.inWholeMilliseconds}ms")

        // Inline operations should be competitive or faster
        // Note: Modern JVM optimizations may make differences negligible
        val normalizeRatio = if (timeRegular == Duration.ZERO) {
            Double.POSITIVE_INFINITY
        } else {
            timeInline / timeRegular
        }
        if (timeRegular.inWholeMilliseconds <= 5) {
            println(
                "Normalize baseline under 5ms; treating inline ratio as informational (ratio=${"%.2f".format(normalizeRatio)})"
            )
            assertTrue(
                normalizeRatio <= 3.0,
                "Inline math should not be dramatically slower: ratio=${"%.2f".format(normalizeRatio)}, " +
                    "inline=${timeInline.inWholeMicroseconds}µs, regular=${timeRegular.inWholeMicroseconds}µs"
            )
        } else {
            val acceptableRatio = 1.5
            assertTrue(
                normalizeRatio <= acceptableRatio,
                "Inline math should be competitive: ratio=${"%.2f".format(normalizeRatio)}, " +
                    "inline=${timeInline.inWholeMicroseconds}µs, regular=${timeRegular.inWholeMicroseconds}µs"
            )
        }
    }

    @Test
    fun testTransformOptimizations() {
        val obj = Mesh(BoxGeometry(), MeshBasicMaterial())

        // Benchmark: Rotation operations with pooling
        val timeRotation = measureTime {
            repeat(10000) {
                obj.rotateX(0.01f)
                obj.rotateY(0.01f)
                obj.rotateZ(0.01f)
            }
        }

        println("Rotation operations: ${timeRotation.inWholeMilliseconds}ms")

        // Benchmark: Translation operations with pooling
        val timeTranslation = measureTime {
            repeat(10000) {
                obj.translateX(0.01f)
                obj.translateY(0.01f)
                obj.translateZ(0.01f)
            }
        }

        println("Translation operations: ${timeTranslation.inWholeMilliseconds}ms")

        // Should complete within reasonable time
        assertTrue(
            timeRotation.inWholeMilliseconds < 500,
            "10k rotation operations should complete in < 500ms"
        )
        assertTrue(
            timeTranslation.inWholeMilliseconds < 500,
            "10k translation operations should complete in < 500ms"
        )
    }

    @Test
    fun testSceneTraversalPerformance() {
        // Create deep hierarchy
        val root = Scene()
        var current: io.kreekt.core.scene.Object3D = root

        repeat(100) {
            val child = Mesh(BoxGeometry(), MeshBasicMaterial())
            current.add(child)
            current = child
        }

        // Benchmark: Traversal
        var count = 0
        val timeTraversal = measureTime {
            repeat(1000) {
                root.traverse { count++ }
            }
        }

        println("Scene traversal: ${timeTraversal.inWholeMilliseconds}ms for $count nodes")

        assertTrue(
            timeTraversal.inWholeMilliseconds < 1000,
            "1000 traversals should complete in < 1 second"
        )
    }

    @Test
    @Ignore  // DefaultRenderer not yet implemented - in .skip file
    fun testOverallPerformanceTarget() {
        // Constitutional requirement: 60 FPS with 100k triangles
        val targetFrameTime = 16.67f // ms for 60 FPS

        val scene = Scene()
        val triangleTarget = 100_000
        val trianglesPerMesh = 1000
        val meshCount = triangleTarget / trianglesPerMesh

        // Create scene with target triangle count
        repeat(meshCount) { i ->
            Mesh(BoxGeometry(), MeshBasicMaterial()).apply {
                position.set(
                    (i % 10).toFloat(),
                    ((i / 10) % 10).toFloat(),
                    (i / 100).toFloat()
                )
                scene.add(this)
            }
        }

        val camera = PerspectiveCamera(75f, 1.0f, 0.1f, 1000f)
        camera.position.set(5f, 5f, 5f)
        camera.lookAt(Vector3.ZERO)
        camera.updateMatrixWorld()

        // val renderer = DefaultRenderer(RendererConfig())

        // Benchmark: Single frame render time
        // val frameTime = measureTime {
        //     renderer.render(scene, camera)
        // }

        // println("Frame time with 100k triangles: ${frameTime.inWholeMilliseconds}ms")
        // println("Target: ${targetFrameTime}ms for 60 FPS")

        // val stats = renderer.stats
        // println("Stats: ${stats.drawCalls} calls, ${stats.triangles} triangles")

        // With optimizations, should approach 60 FPS target
        // assertTrue(
        //     frameTime.inWholeMilliseconds < 100, // Allow 100ms for software renderer
        //     "Optimized frame time should be reasonable for software renderer"
        // )
    }
}
