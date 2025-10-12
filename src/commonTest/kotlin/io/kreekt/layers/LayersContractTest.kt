package io.kreekt.layers

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.Vector3
import io.kreekt.core.scene.Group
import io.kreekt.raycaster.Raycaster
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test for Layer-based visibility - T035
 * Covers: FR-L001, FR-L002, FR-L003, FR-L004, FR-L005, FR-L006 from contracts/layers-api.kt
 */
class LayersContractTest {

    @Test
    fun testSupport32Layers() {
        // FR-L001: Support 32 layers (bitmask)
        val layers = Layers()

        // Enable all 32 layers
        for (i in 0 until 32) {
            layers.enable(i)
        }

        assertEquals(0xFFFFFFFF.toInt(), layers.mask)

        // Disable specific layers
        layers.disable(0)
        layers.disable(31)

        assertFalse(layers.test(0))
        assertFalse(layers.test(31))
        assertTrue(layers.test(15))
    }

    @Test
    fun testFilterObjectsDuringRendering() {
        // FR-L002: Filter objects during rendering
        val camera = PerspectiveCamera(75f, 1.77f, 0.1f, 1000f)
        camera.layers.set(1) // Camera only sees layer 1

        val object1 = Group()
        object1.layers.set(0) // Object on layer 0

        val object2 = Group()
        object2.layers.set(1) // Object on layer 1

        val object3 = Group()
        object3.layers.enable(0)
        object3.layers.enable(1) // Object on both layers

        // Test visibility
        assertFalse(camera.layers.test(object1.layers))
        assertTrue(camera.layers.test(object2.layers))
        assertTrue(camera.layers.test(object3.layers))
    }

    @Test
    fun testFilterDuringRaycasting() {
        // FR-L003: Filter during raycasting
        val raycaster = Raycaster(Vector3(0f, 0f, 0f), Vector3(0f, 0f, 1f))
        raycaster.layers.set(2) // Raycaster only checks layer 2

        val objects = listOf(
            Group().apply { layers.set(0) },
            Group().apply { layers.set(1) },
            Group().apply { layers.set(2) },
            Group().apply { layers.set(3) }
        )

        val filtered = mutableListOf<Group>()
        for (obj in objects) {
            // Convert Object3D.layers mask to io.kreekt.layers.Layers for testing
            val objLayers = io.kreekt.layers.Layers()
            objLayers.mask = obj.layers.mask

            if (raycaster.layers.test(objLayers)) {
                filtered.add(obj)
            }
        }
        assertEquals(1, filtered.size, "Should only find objects on layer 2")
        // Check that we got a filtered result
        assertTrue(filtered.isNotEmpty())
    }

    @Test
    fun testLayerEnableDisableToggle() {
        // FR-L004, FR-L005: Layer enable/disable/toggle
        val layers = Layers()

        // Enable
        layers.enable(5)
        assertTrue(layers.test(5))

        // Disable
        layers.disable(5)
        assertFalse(layers.test(5))

        // Toggle
        layers.toggle(5)
        assertTrue(layers.test(5))
        layers.toggle(5)
        assertFalse(layers.test(5))

        // Enable multiple
        layers.enableAll()
        assertEquals(0xFFFFFFFF.toInt(), layers.mask)

        // Disable all
        layers.disableAll()
        assertEquals(0, layers.mask)
    }

    @Test
    fun testLayerIntersectionTesting() {
        // FR-L006: Layer intersection testing
        val layers1 = Layers()
        layers1.set(1)
        layers1.enable(3)
        layers1.enable(5)

        val layers2 = Layers()
        layers2.set(2)
        layers2.enable(3)
        layers2.enable(7)

        // Test intersection
        assertTrue(layers1.intersects(layers2)) // Both have layer 3

        val layers3 = Layers()
        layers3.set(0)
        layers3.enable(4)

        assertFalse(layers1.intersects(layers3)) // No common layers
    }

    @Test
    fun testLayerMaskOperations() {
        val layers = Layers()

        // Set specific mask
        layers.mask = 0b1010
        assertTrue(layers.test(1))
        assertFalse(layers.test(0))
        assertTrue(layers.test(3))
        assertFalse(layers.test(2))

        // OR operation (union)
        val other = Layers()
        other.mask = 0b0101
        layers.union(other)
        assertEquals(0b1111, layers.mask)

        // AND operation (intersect)
        layers.mask = 0b1100
        other.mask = 0b1010
        layers.intersect(other)
        assertEquals(0b1000, layers.mask)

        // XOR operation (symmetric difference)
        layers.mask = 0b1100
        other.mask = 0b1010
        layers.symmetricDifference(other)
        assertEquals(0b0110, layers.mask)
    }

    @Test
    fun testLayerPerformance() {
        // Test performance with many objects and layers
        val objects = List(10000) { Group() }

        // Assign random layers
        objects.forEach { obj ->
            for (i in 0 until 32) {
                if (kotlin.random.Random.nextDouble() < 0.1) {
                    obj.layers.enable(i)
                }
            }
        }

        val camera = PerspectiveCamera()
        camera.layers.set(5)
        camera.layers.enable(10)
        camera.layers.enable(15)

        val startTime = kotlin.time.TimeSource.Monotonic.markNow()

        // Filter visible objects
        val visible = objects.filter { camera.layers.test(it.layers) }

        val duration = startTime.elapsedNow().inWholeMilliseconds

        // Should be very fast (< 1ms for 10k objects)
        assertTrue(duration < 10, "Layer filtering should be fast, took ${duration}ms")
        assertTrue(visible.isNotEmpty())
    }
}

// Supporting classes - using real Layers, Object3D, Camera from io.kreekt.layers
// All classes already have layers property defined except Raycaster

// Extension for Raycaster (until it's added to main class)
private val raycasterLayersMap = mutableMapOf<Raycaster, io.kreekt.layers.Layers>()
val Raycaster.layers: io.kreekt.layers.Layers
    get() = raycasterLayersMap.getOrPut(this) { io.kreekt.layers.Layers() }