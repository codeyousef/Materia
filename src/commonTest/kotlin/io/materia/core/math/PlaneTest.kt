package io.materia.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plane implementation test
 * T047 - Plane operations test
 */
class PlaneTest {

    @Test
    fun testPlaneCreation() {
        val normal = Vector3(0f, 1f, 0f)
        val plane = Plane(normal, -5f)

        assertEquals(normal, plane.normal)
        assertEquals(-5f, plane.constant)
    }

    @Test
    fun testPlaneFromNormalAndCoplanarPoint() {
        val normal = Vector3(0f, 1f, 0f)
        val point = Vector3(0f, 5f, 0f)
        val plane = Plane().setFromNormalAndCoplanarPoint(normal, point)

        assertEquals(0f, plane.normal.x)
        assertEquals(1f, plane.normal.y)
        assertEquals(0f, plane.normal.z)
        assertEquals(-5f, plane.constant)
    }

    @Test
    fun testPlaneFromCoplanarPoints() {
        val a = Vector3(0f, 0f, 0f)
        val b = Vector3(1f, 0f, 0f)
        val c = Vector3(0f, 1f, 0f)
        val plane = Plane().setFromCoplanarPoints(a, b, c)

        // Normal should be (0, 0, 1) for XY plane
        assertEquals(0f, plane.normal.x, 1e-6f)
        assertEquals(0f, plane.normal.y, 1e-6f)
        assertEquals(1f, plane.normal.z, 1e-6f)
        assertEquals(0f, plane.constant, 1e-6f)
    }

    @Test
    fun testPlaneDistanceToPoint() {
        val plane = Plane(Vector3(0f, 1f, 0f), 0f) // XZ plane at origin

        val above = Vector3(0f, 5f, 0f)
        val below = Vector3(0f, -3f, 0f)
        val on = Vector3(5f, 0f, 5f)

        assertEquals(5f, plane.distanceToPoint(above))
        assertEquals(-3f, plane.distanceToPoint(below))
        assertEquals(0f, plane.distanceToPoint(on))
    }

    @Test
    fun testPlaneProjectPoint() {
        val plane = Plane(Vector3(0f, 1f, 0f), 0f) // XZ plane at origin
        val point = Vector3(3f, 5f, 7f)
        val projected = plane.projectPoint(point)

        assertEquals(3f, projected.x)
        assertEquals(0f, projected.y) // Projected onto plane
        assertEquals(7f, projected.z)
    }

    @Test
    fun testPlaneIntersectLine() {
        val plane = Plane(Vector3(0f, 1f, 0f), 0f) // XZ plane at origin
        val start = Vector3(0f, 5f, 0f)
        val end = Vector3(0f, -5f, 0f)
        val line = Line3(start, end)

        val intersection = plane.intersectLine(line)
        assertEquals(0f, intersection?.x)
        assertEquals(0f, intersection?.y)
        assertEquals(0f, intersection?.z)
    }

    @Test
    fun testPlaneCoplanarPoint() {
        val plane = Plane(Vector3(0f, 1f, 0f), -5f)
        val point = plane.coplanarPoint()

        assertEquals(0f, point.x)
        assertEquals(5f, point.y)
        assertEquals(0f, point.z)
    }

    @Test
    fun testPlaneNormalize() {
        val plane = Plane(Vector3(0f, 2f, 0f), -10f)
        plane.normalize()

        assertEquals(0f, plane.normal.x)
        assertEquals(1f, plane.normal.y)
        assertEquals(0f, plane.normal.z)
        assertEquals(-5f, plane.constant)
    }

    @Test
    fun testPlaneNegate() {
        val plane = Plane(Vector3(0f, 1f, 0f), -5f)
        plane.negate()

        assertEquals(0f, plane.normal.x, 1e-6f)
        assertEquals(-1f, plane.normal.y, 1e-6f)
        assertEquals(0f, plane.normal.z, 1e-6f)
        assertEquals(5f, plane.constant, 1e-6f)
    }

    @Test
    fun testPlaneTranslate() {
        val plane = Plane(Vector3(0f, 1f, 0f), 0f)
        plane.translate(Vector3(0f, 5f, 0f))

        assertEquals(-5f, plane.constant)
    }

    @Test
    fun testPlaneEquals() {
        val plane1 = Plane(Vector3(0f, 1f, 0f), -5f)
        val plane2 = Plane(Vector3(0f, 1f, 0f), -5f)
        val plane3 = Plane(Vector3(0f, 1f, 0f), -3f)

        assertTrue(plane1.equals(plane2))
        assertTrue(!plane1.equals(plane3))
    }

    @Test
    fun testPlaneClone() {
        val plane = Plane(Vector3(0f, 1f, 0f), -5f)
        val clone = plane.clone()

        assertEquals(plane.normal, clone.normal)
        assertEquals(plane.constant, clone.constant)
        assertTrue(plane !== clone)
    }
}