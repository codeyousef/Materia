package io.materia.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Box3 implementation test
 * T047 - Box3 bounding box test
 */
class Box3Test {

    @Test
    fun testBox3Creation() {
        val min = Vector3(-1f, -2f, -3f)
        val max = Vector3(1f, 2f, 3f)
        val box = Box3(min, max)

        assertEquals(min, box.min)
        assertEquals(max, box.max)
    }

    @Test
    fun testBox3Empty() {
        val box = Box3()
        assertTrue(box.isEmpty())

        box.set(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        assertFalse(box.isEmpty())
    }

    @Test
    fun testBox3MakeEmpty() {
        val box = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        box.makeEmpty()
        assertTrue(box.isEmpty())
    }

    @Test
    fun testBox3FromPoints() {
        val points = arrayOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 2f, 3f),
            Vector3(-1f, -2f, -3f),
            Vector3(0.5f, 0.5f, 0.5f)
        )
        val box = Box3.fromPoints(points)

        assertEquals(-1f, box.min.x)
        assertEquals(-2f, box.min.y)
        assertEquals(-3f, box.min.z)
        assertEquals(1f, box.max.x)
        assertEquals(2f, box.max.y)
        assertEquals(3f, box.max.z)
    }

    @Test
    fun testBox3ExpandByPoint() {
        val box = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        box.expandByPoint(Vector3(2f, 3f, 4f))

        assertEquals(0f, box.min.x)
        assertEquals(0f, box.min.y)
        assertEquals(0f, box.min.z)
        assertEquals(2f, box.max.x)
        assertEquals(3f, box.max.y)
        assertEquals(4f, box.max.z)
    }

    @Test
    fun testBox3ExpandByVector() {
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        box.expandByVector(Vector3(1f, 2f, 3f))

        assertEquals(-2f, box.min.x)
        assertEquals(-3f, box.min.y)
        assertEquals(-4f, box.min.z)
        assertEquals(2f, box.max.x)
        assertEquals(3f, box.max.y)
        assertEquals(4f, box.max.z)
    }

    @Test
    fun testBox3ExpandByScalar() {
        val box = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        box.expandByScalar(1f)

        assertEquals(-1f, box.min.x)
        assertEquals(-1f, box.min.y)
        assertEquals(-1f, box.min.z)
        assertEquals(2f, box.max.x)
        assertEquals(2f, box.max.y)
        assertEquals(2f, box.max.z)
    }

    @Test
    fun testBox3ContainsPoint() {
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))

        assertTrue(box.containsPoint(Vector3(0f, 0f, 0f)))
        assertTrue(box.containsPoint(Vector3(1f, 1f, 1f)))
        assertFalse(box.containsPoint(Vector3(2f, 0f, 0f)))
    }

    @Test
    fun testBox3ContainsBox() {
        val box1 = Box3(Vector3(-2f, -2f, -2f), Vector3(2f, 2f, 2f))
        val box2 = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val box3 = Box3(Vector3(0f, 0f, 0f), Vector3(3f, 3f, 3f))

        assertTrue(box1.containsBox(box2))
        assertFalse(box1.containsBox(box3))
    }

    @Test
    fun testBox3IntersectsBox() {
        val box1 = Box3(Vector3(0f, 0f, 0f), Vector3(2f, 2f, 2f))
        val box2 = Box3(Vector3(1f, 1f, 1f), Vector3(3f, 3f, 3f))
        val box3 = Box3(Vector3(3f, 3f, 3f), Vector3(4f, 4f, 4f))

        assertTrue(box1.intersectsBox(box2))
        assertFalse(box1.intersectsBox(box3))
    }

    @Test
    fun testBox3IntersectsSphere() {
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val sphere1 = Sphere(Vector3(0f, 0f, 0f), 1f)
        val sphere2 = Sphere(Vector3(5f, 0f, 0f), 1f)

        assertTrue(box.intersectsSphere(sphere1))
        assertFalse(box.intersectsSphere(sphere2))
    }

    @Test
    fun testBox3IntersectsPlane() {
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val plane1 = Plane(Vector3(0f, 1f, 0f), 0f) // Through center
        val plane2 = Plane(Vector3(0f, 1f, 0f), -10f) // Far below

        assertTrue(box.intersectsPlane(plane1))
        assertFalse(box.intersectsPlane(plane2))
    }

    @Test
    fun testBox3ClampPoint() {
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))

        val inside = box.clampPoint(Vector3(0.5f, 0.5f, 0.5f))
        assertEquals(0.5f, inside.x)
        assertEquals(0.5f, inside.y)
        assertEquals(0.5f, inside.z)

        val outside = box.clampPoint(Vector3(2f, -3f, 0f))
        assertEquals(1f, outside.x)
        assertEquals(-1f, outside.y)
        assertEquals(0f, outside.z)
    }

    @Test
    fun testBox3DistanceToPoint() {
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))

        assertEquals(0f, box.distanceToPoint(Vector3(0f, 0f, 0f))) // Inside
        assertEquals(1f, box.distanceToPoint(Vector3(2f, 0f, 0f))) // Outside
    }

    @Test
    fun testBox3GetCenter() {
        val box = Box3(Vector3(-1f, -2f, -3f), Vector3(1f, 2f, 3f))
        val center = box.getCenter()

        assertEquals(0f, center.x)
        assertEquals(0f, center.y)
        assertEquals(0f, center.z)
    }

    @Test
    fun testBox3GetSize() {
        val box = Box3(Vector3(-1f, -2f, -3f), Vector3(1f, 2f, 3f))
        val size = box.getSize()

        assertEquals(2f, size.x)
        assertEquals(4f, size.y)
        assertEquals(6f, size.z)
    }

    @Test
    fun testBox3Union() {
        val box1 = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        val box2 = Box3(Vector3(0.5f, 0.5f, 0.5f), Vector3(2f, 2f, 2f))
        box1.union(box2)

        assertEquals(0f, box1.min.x)
        assertEquals(0f, box1.min.y)
        assertEquals(0f, box1.min.z)
        assertEquals(2f, box1.max.x)
        assertEquals(2f, box1.max.y)
        assertEquals(2f, box1.max.z)
    }

    @Test
    fun testBox3Intersection() {
        val box1 = Box3(Vector3(0f, 0f, 0f), Vector3(2f, 2f, 2f))
        val box2 = Box3(Vector3(1f, 1f, 1f), Vector3(3f, 3f, 3f))
        box1.intersect(box2)

        assertEquals(1f, box1.min.x)
        assertEquals(1f, box1.min.y)
        assertEquals(1f, box1.min.z)
        assertEquals(2f, box1.max.x)
        assertEquals(2f, box1.max.y)
        assertEquals(2f, box1.max.z)
    }

    @Test
    fun testBox3Translate() {
        val box = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        box.translate(Vector3(1f, 2f, 3f))

        assertEquals(1f, box.min.x)
        assertEquals(2f, box.min.y)
        assertEquals(3f, box.min.z)
        assertEquals(2f, box.max.x)
        assertEquals(3f, box.max.y)
        assertEquals(4f, box.max.z)
    }

    @Test
    fun testBox3Equals() {
        val box1 = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        val box2 = Box3(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        val box3 = Box3(Vector3(0f, 0f, 0f), Vector3(2f, 2f, 2f))

        assertTrue(box1.equals(box2))
        assertFalse(box1.equals(box3))
    }

    @Test
    fun testBox3Clone() {
        val box = Box3(Vector3(-1f, -2f, -3f), Vector3(1f, 2f, 3f))
        val clone = box.clone()

        assertEquals(box.min, clone.min)
        assertEquals(box.max, clone.max)
        assertTrue(box !== clone)
    }
}