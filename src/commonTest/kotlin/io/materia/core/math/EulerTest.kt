package io.materia.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Euler angles conversion test
 * T019 - Tests for Euler angle implementation
 */
class EulerTest {

    @Test
    fun testEulerCreation() {
        val euler = Euler(PI.toFloat() / 4, PI.toFloat() / 2, 0f)
        assertEquals(PI.toFloat() / 4, euler.x)
        assertEquals(PI.toFloat() / 2, euler.y)
        assertEquals(0f, euler.z)
    }

    @Test
    fun testEulerToQuaternion() {
        val euler = Euler(PI.toFloat() / 2, 0f, 0f) // 90 degrees X
        val q = euler.toQuaternion()
        assertTrue(abs(q.length() - 1f) < 0.001f) // Unit quaternion
        assertTrue(abs(q.x - 0.707f) < 0.01f) // sin(45°)
        assertTrue(abs(q.w - 0.707f) < 0.01f) // cos(45°)
    }

    @Test
    fun testEulerFromQuaternion() {
        val q = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 2)
        val euler = Euler().setFromQuaternion(q)
        assertTrue(abs(euler.z - PI.toFloat() / 2) < 0.001f) // 90 degrees Z
    }

    @Test
    fun testEulerFromRotationMatrix() {
        val matrix = Matrix4().makeRotationY(PI.toFloat() / 3) // 60 degrees Y
        val euler = Euler().setFromRotationMatrix(matrix)
        assertTrue(abs(euler.y - PI.toFloat() / 3) < 0.001f)
    }

    @Test
    fun testEulerOrderConversions() {
        val angles = Euler(PI.toFloat() / 6, PI.toFloat() / 4, PI.toFloat() / 3)

        // Test different rotation orders
        val orderXYZ = angles.clone()
        orderXYZ.order = EulerOrder.XYZ

        val orderYXZ = angles.clone()
        orderYXZ.order = EulerOrder.YXZ

        // Convert to quaternion and back to ensure consistency
        val q1 = orderXYZ.toQuaternion()
        val q2 = orderYXZ.toQuaternion()

        // Different orders should produce different quaternions
        assertTrue(
            abs(q1.x - q2.x) > 0.001f || abs(q1.y - q2.y) > 0.001f ||
                    abs(q1.z - q2.z) > 0.001f || abs(q1.w - q2.w) > 0.001f
        )
    }

    @Test
    fun testEulerRecompose() {
        val euler = Euler(0.1f, 0.2f, 0.3f)

        // Convert to quaternion and back
        val q = euler.toQuaternion()
        val euler2 = Euler().setFromQuaternion(q)
        euler2.order = euler.order

        // Should be approximately the same
        assertTrue(abs(euler.x - euler2.x) < 0.001f)
        assertTrue(abs(euler.y - euler2.y) < 0.001f)
        assertTrue(abs(euler.z - euler2.z) < 0.001f)
    }

    @Test
    fun testEulerClone() {
        val euler = Euler(1f, 2f, 3f, EulerOrder.ZYX)
        val clone = euler.clone()

        assertEquals(euler.x, clone.x)
        assertEquals(euler.y, clone.y)
        assertEquals(euler.z, clone.z)
        assertEquals(euler.order, clone.order)

        // Ensure it's a different instance
        assertTrue(euler !== clone)
    }

    @Test
    fun testEulerSet() {
        val euler = Euler()
        euler.set(0.5f, 1.0f, 1.5f, EulerOrder.YZX)

        assertEquals(0.5f, euler.x)
        assertEquals(1.0f, euler.y)
        assertEquals(1.5f, euler.z)
        assertEquals(EulerOrder.YZX, euler.order)
    }
}