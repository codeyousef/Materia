package io.kreekt.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sphere implementation test
 * T048 - Sphere bounding sphere test
 */
class SphereTest {

    @Test
    fun testSphereCreation() {
        val center = Vector3(1f, 2f, 3f)
        val sphere = Sphere(center, 5f)

        assertEquals(center, sphere.center)
        assertEquals(5f, sphere.radius)
    }

    @Test
    fun testSphereEmpty() {
        val sphere = Sphere()
        assertTrue(sphere.isEmpty())

        sphere.set(Vector3(0f, 0f, 0f), 1f)
        assertFalse(sphere.isEmpty())
    }

    @Test
    fun testSphereMakeEmpty() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 5f)
        sphere.makeEmpty()
        assertTrue(sphere.isEmpty())
    }

    @Test
    fun testSphereSetFromPoints() {
        val points = arrayOf(
            Vector3(1f, 0f, 0f),
            Vector3(-1f, 0f, 0f),
            Vector3(0f, 1f, 0f),
            Vector3(0f, -1f, 0f),
            Vector3(0f, 0f, 1f),
            Vector3(0f, 0f, -1f)
        )
        val sphere = Sphere().setFromPoints(points)

        // Center should be at origin
        assertTrue(sphere.center.equals(Vector3(0f, 0f, 0f), 0.01f))
        // Radius should be 1
        assertTrue(kotlin.math.abs(sphere.radius - 1f) < 0.01f)
    }

    @Test
    fun testSphereContainsPoint() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 2f)

        assertTrue(sphere.containsPoint(Vector3(0f, 0f, 0f)))
        assertTrue(sphere.containsPoint(Vector3(1f, 1f, 0f)))
        assertFalse(sphere.containsPoint(Vector3(3f, 0f, 0f)))
    }

    @Test
    fun testSphereDistanceToPoint() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 2f)

        assertEquals(-2f, sphere.distanceToPoint(Vector3(0f, 0f, 0f))) // Inside
        assertEquals(0f, sphere.distanceToPoint(Vector3(2f, 0f, 0f))) // On surface
        assertEquals(1f, sphere.distanceToPoint(Vector3(3f, 0f, 0f))) // Outside
    }

    @Test
    fun testSphereIntersectsSphere() {
        val sphere1 = Sphere(Vector3(0f, 0f, 0f), 2f)
        val sphere2 = Sphere(Vector3(3f, 0f, 0f), 2f)
        val sphere3 = Sphere(Vector3(5f, 0f, 0f), 1f)

        assertTrue(sphere1.intersectsSphere(sphere2)) // Overlap
        assertFalse(sphere1.intersectsSphere(sphere3)) // No overlap
    }

    @Test
    fun testSphereIntersectsBox() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 2f)
        val box1 = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val box2 = Box3(Vector3(3f, 3f, 3f), Vector3(4f, 4f, 4f))

        assertTrue(sphere.intersectsBox(box1))
        assertFalse(sphere.intersectsBox(box2))
    }

    @Test
    fun testSphereIntersectsPlane() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 2f)
        val plane1 = Plane(Vector3(0f, 1f, 0f), 0f) // Through center
        val plane2 = Plane(Vector3(0f, 1f, 0f), -5f) // Far below

        assertTrue(sphere.intersectsPlane(plane1))
        assertFalse(sphere.intersectsPlane(plane2))
    }

    @Test
    fun testSphereClampPoint() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 2f)

        val inside = sphere.clampPoint(Vector3(0.5f, 0.5f, 0f))
        assertEquals(0.5f, inside.x)
        assertEquals(0.5f, inside.y)
        assertEquals(0f, inside.z)

        val outside = sphere.clampPoint(Vector3(4f, 0f, 0f))
        assertEquals(2f, outside.x) // Clamped to sphere surface
        assertEquals(0f, outside.y)
        assertEquals(0f, outside.z)
    }

    @Test
    fun testSphereGetBoundingBox() {
        val sphere = Sphere(Vector3(1f, 2f, 3f), 2f)
        val box = sphere.getBoundingBox()

        assertEquals(-1f, box.min.x)
        assertEquals(0f, box.min.y)
        assertEquals(1f, box.min.z)
        assertEquals(3f, box.max.x)
        assertEquals(4f, box.max.y)
        assertEquals(5f, box.max.z)
    }

    @Test
    fun testSphereUnion() {
        val sphere1 = Sphere(Vector3(0f, 0f, 0f), 1f)
        val sphere2 = Sphere(Vector3(3f, 0f, 0f), 1f)
        sphere1.union(sphere2)

        // Should create minimal bounding sphere containing both
        assertTrue(sphere1.containsPoint(Vector3(0f, 0f, 0f)))
        assertTrue(sphere1.containsPoint(Vector3(3f, 0f, 0f)))
    }

    @Test
    fun testSphereTranslate() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 2f)
        sphere.translate(Vector3(1f, 2f, 3f))

        assertEquals(1f, sphere.center.x)
        assertEquals(2f, sphere.center.y)
        assertEquals(3f, sphere.center.z)
        assertEquals(2f, sphere.radius)
    }

    @Test
    fun testSphereExpandByPoint() {
        val sphere = Sphere(Vector3(0f, 0f, 0f), 1f)
        sphere.expandByPoint(Vector3(3f, 0f, 0f))

        // Should expand to include the point
        assertTrue(sphere.containsPoint(Vector3(3f, 0f, 0f)))
        assertTrue(sphere.containsPoint(Vector3(-1f, 0f, 0f)))
    }

    @Test
    fun testSphereEquals() {
        val sphere1 = Sphere(Vector3(0f, 0f, 0f), 2f)
        val sphere2 = Sphere(Vector3(0f, 0f, 0f), 2f)
        val sphere3 = Sphere(Vector3(0f, 0f, 0f), 3f)

        assertTrue(sphere1.equals(sphere2))
        assertFalse(sphere1.equals(sphere3))
    }

    @Test
    fun testSphereClone() {
        val sphere = Sphere(Vector3(1f, 2f, 3f), 4f)
        val clone = sphere.clone()

        assertEquals(sphere.center, clone.center)
        assertEquals(sphere.radius, clone.radius)
        assertTrue(sphere !== clone)
    }
}