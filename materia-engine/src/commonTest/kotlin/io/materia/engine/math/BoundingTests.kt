package io.materia.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPS = 1e-4f

class BoundingTests {
    @Test
    fun aabbIncludesPoints() {
        val aabb = Aabb()
        aabb.include(vec3(1f, 2f, 3f))
        aabb.include(vec3(-1f, 0f, 4f))

        assertEquals(-1f, aabb.min.x, EPS)
        assertEquals(0f, aabb.min.y, EPS)
        assertEquals(3f, aabb.min.z, EPS)

        assertEquals(1f, aabb.max.x, EPS)
        assertEquals(2f, aabb.max.y, EPS)
        assertEquals(4f, aabb.max.z, EPS)
    }

    @Test
    fun aabbTransformsWithMatrix() {
        val aabb = Aabb.fromCenterSize(vec3(0f, 0f, 0f), vec3(2f, 2f, 2f))
        val transform = mat4().setIdentity()
        transform[12] = 5f
        transform[13] = -2f
        transform[14] = 3f

        val scaled = mat4().setIdentity()
        scaled[0] = 2f
        scaled[5] = 0.5f
        scaled[10] = 3f

        val combined = mat4().multiply(transform, scaled)

        val out = aabb.transform(combined)
        assertEquals(3f, out.min.x, EPS)
        assertEquals(-2.5f, out.min.y, EPS)
        assertEquals(0f, out.min.z, EPS)

        assertEquals(7f, out.max.x, EPS)
        assertEquals(-1.5f, out.max.y, EPS)
        assertEquals(6f, out.max.z, EPS)
    }

    @Test
    fun frustumRejectsOutsideAabb() {
        val frustum = Frustum()
            .setPlane(0, 1f, 0f, 0f, 1f)   // Left  (x >= -1)
            .setPlane(1, -1f, 0f, 0f, 1f)  // Right (x <= 1)
            .setPlane(2, 0f, 1f, 0f, 1f)   // Bottom (y >= -1)
            .setPlane(3, 0f, -1f, 0f, 1f)  // Top    (y <= 1)
            .setPlane(4, 0f, 0f, 1f, 1f)   // Near   (z >= -1)
            .setPlane(5, 0f, 0f, -1f, 1f)  // Far    (z <= 1)

        val inside = Aabb.fromCenterSize(vec3(0f, 0f, 0f), vec3(1f, 1f, 1f))
        val outside = Aabb.fromCenterSize(vec3(3f, 0f, 0f), vec3(1f, 1f, 1f))

        assertTrue(frustum.intersects(inside))
        assertFalse(frustum.intersects(outside))
    }
}
