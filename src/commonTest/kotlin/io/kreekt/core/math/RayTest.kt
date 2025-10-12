package io.kreekt.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ray implementation test
 * T046 - Ray operations test
 */
class RayTest {

    @Test
    fun testRayCreation() {
        val origin = Vector3(0f, 0f, 0f)
        val direction = Vector3(1f, 0f, 0f)
        val ray = Ray(origin, direction)

        assertEquals(origin, ray.origin)
        assertEquals(direction, ray.direction)
    }

    @Test
    fun testRayAt() {
        val ray = Ray(Vector3(1f, 2f, 3f), Vector3(1f, 0f, 0f))
        val point = ray.at(5f)

        assertEquals(6f, point.x)
        assertEquals(2f, point.y)
        assertEquals(3f, point.z)
    }

    @Test
    fun testRayIntersectSphere() {
        val ray = Ray(Vector3(0f, 0f, -5f), Vector3(0f, 0f, 1f))
        val sphere = Sphere(Vector3(0f, 0f, 0f), 1f)

        val intersection = ray.intersectSphere(sphere)
        assertNotNull(intersection)
        assertEquals(4f, intersection.x) // Distance to near intersection
        assertEquals(6f, intersection.y) // Distance to far intersection
    }

    @Test
    fun testRayIntersectPlane() {
        val ray = Ray(Vector3(0f, 0f, -5f), Vector3(0f, 0f, 1f))
        val plane = Plane(Vector3(0f, 0f, 1f), 0f) // XY plane at origin

        val intersection = ray.intersectPlane(plane)
        assertNotNull(intersection)
        assertEquals(0f, intersection.x)
        assertEquals(0f, intersection.y)
        assertEquals(0f, intersection.z)
    }

    @Test
    fun testRayIntersectBox() {
        val ray = Ray(Vector3(-2f, 0f, 0f), Vector3(1f, 0f, 0f))
        val box = Box3(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))

        val intersection = ray.intersectBox(box)
        assertNotNull(intersection)
        assertEquals(-1f, intersection.x)
        assertEquals(0f, intersection.y)
        assertEquals(0f, intersection.z)
    }

    @Test
    fun testRayRecast() {
        val ray = Ray(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        ray.recast(2f)

        assertEquals(2f, ray.origin.x)
        assertEquals(0f, ray.origin.y)
        assertEquals(0f, ray.origin.z)
    }

    @Test
    fun testRayClosestPointToPoint() {
        val ray = Ray(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        val point = Vector3(5f, 3f, 0f)

        val closest = ray.closestPointToPoint(point)
        assertEquals(5f, closest.x) // Projects to (5, 0, 0)
        assertEquals(0f, closest.y)
        assertEquals(0f, closest.z)
    }

    @Test
    fun testRayDistanceToPoint() {
        val ray = Ray(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        val point = Vector3(5f, 3f, 0f)

        val distance = ray.distanceToPoint(point)
        assertEquals(3f, distance) // Distance from (5, 3, 0) to (5, 0, 0)
    }

    @Test
    fun testRayLookAt() {
        val ray = Ray(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        ray.lookAt(Vector3(0f, 1f, 0f))

        assertEquals(0f, ray.direction.x)
        assertEquals(1f, ray.direction.y)
        assertEquals(0f, ray.direction.z)
    }

    @Test
    fun testRayClone() {
        val ray = Ray(Vector3(1f, 2f, 3f), Vector3(0f, 1f, 0f))
        val clone = ray.clone()

        assertEquals(ray.origin, clone.origin)
        assertEquals(ray.direction, clone.direction)
        assertTrue(ray !== clone)
    }
}