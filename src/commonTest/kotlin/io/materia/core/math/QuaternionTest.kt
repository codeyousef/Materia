package io.materia.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Quaternion SLERP test
 * T018 - Tests for Quaternion implementation
 */
class QuaternionTest {

    @Test
    fun testQuaternionCreation() {
        val q = Quaternion(0f, 0f, 0f, 1f)
        assertEquals(0f, q.x)
        assertEquals(0f, q.y)
        assertEquals(0f, q.z)
        assertEquals(1f, q.w)
    }

    @Test
    fun testQuaternionIdentity() {
        val q = Quaternion()
        assertTrue(q.isIdentity())
        assertEquals(1f, q.length())
    }

    @Test
    fun testQuaternionMultiplication() {
        val q1 = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 4)
        val q2 = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 4)
        val result = q1.clone().multiply(q2)

        // Should be equivalent to 90 degree rotation around Z
        val expected = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 2)

        assertTrue(abs(result.x - expected.x) < 0.001f)
        assertTrue(abs(result.y - expected.y) < 0.001f)
        assertTrue(abs(result.z - expected.z) < 0.001f)
        assertTrue(abs(result.w - expected.w) < 0.001f)
    }

    @Test
    fun testQuaternionSlerp() {
        val q1 = Quaternion() // Identity
        val q2 = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 2) // 90 degrees

        val result = Quaternion.slerp(q1, q2, 0.5f)

        // Should be 45 degree rotation
        val expected = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 4)

        assertTrue(abs(result.x - expected.x) < 0.001f)
        assertTrue(abs(result.y - expected.y) < 0.001f)
        assertTrue(abs(result.z - expected.z) < 0.001f)
        assertTrue(abs(result.w - expected.w) < 0.001f)
    }

    @Test
    fun testQuaternionFromEuler() {
        val euler = Euler(PI.toFloat() / 2, 0f, 0f) // 90 degrees X rotation
        val q = Quaternion().setFromEuler(euler)

        assertTrue(abs(q.length() - 1f) < 0.001f) // Unit quaternion
        assertTrue(abs(q.x - sqrt(0.5f)) < 0.001f)
        assertTrue(abs(q.w - sqrt(0.5f)) < 0.001f)
    }

    @Test
    fun testQuaternionNormalize() {
        val q = Quaternion(1f, 2f, 3f, 4f)
        val normalized = q.clone().normalize()

        assertTrue(abs(normalized.length() - 1f) < 0.001f)
    }

    @Test
    fun testQuaternionInverse() {
        val q = Quaternion().setFromAxisAngle(Vector3(1f, 0f, 0f), PI.toFloat() / 4)
        val inverse = q.clone().invert()
        val product = q.clone().multiply(inverse)

        // q * q^-1 should be identity
        assertTrue(product.isIdentity())
    }

    @Test
    fun testQuaternionConjugate() {
        val q = Quaternion(1f, 2f, 3f, 4f)
        val conjugate = q.clone().conjugate()

        assertEquals(-q.x, conjugate.x)
        assertEquals(-q.y, conjugate.y)
        assertEquals(-q.z, conjugate.z)
        assertEquals(q.w, conjugate.w)
    }

    @Test
    fun testQuaternionDot() {
        val q1 = Quaternion(1f, 2f, 3f, 4f)
        val q2 = Quaternion(5f, 6f, 7f, 8f)

        val dot = q1.dot(q2)
        val expected = 1f * 5f + 2f * 6f + 3f * 7f + 4f * 8f

        assertEquals(expected, dot)
    }

    @Test
    fun testQuaternionAngle() {
        val q1 = Quaternion()
        val q2 = Quaternion().setFromAxisAngle(Vector3(0f, 1f, 0f), PI.toFloat() / 2)

        val angle = q1.angleTo(q2)

        assertTrue(abs(angle - PI.toFloat() / 2) < 0.001f)
    }

    @Test
    fun testQuaternionRotateVector() {
        val q = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), PI.toFloat() / 2)
        val v = Vector3(1f, 0f, 0f)

        v.applyQuaternion(q)

        // After 90 degree Z rotation, (1, 0, 0) should become (0, 1, 0)
        assertTrue(abs(v.x - 0f) < 0.001f)
        assertTrue(abs(v.y - 1f) < 0.001f)
        assertTrue(abs(v.z - 0f) < 0.001f)
    }

    @Test
    fun testQuaternionFromMatrix() {
        val matrix = Matrix4().makeRotationY(PI.toFloat() / 3)
        val q = Quaternion().setFromRotationMatrix(matrix)

        // Convert back to matrix and compare
        val matrix2 = Matrix4().makeRotationFromQuaternion(q)

        for (i in matrix.elements.indices) {
            assertTrue(abs(matrix.elements[i] - matrix2.elements[i]) < 0.001f)
        }
    }

    @Test
    fun testQuaternionClone() {
        val q = Quaternion(1f, 2f, 3f, 4f)
        val clone = q.clone()

        assertEquals(q.x, clone.x)
        assertEquals(q.y, clone.y)
        assertEquals(q.z, clone.z)
        assertEquals(q.w, clone.w)

        assertTrue(q !== clone)
    }

    @Test
    fun testQuaternionEquals() {
        val q1 = Quaternion(1f, 2f, 3f, 4f)
        val q2 = Quaternion(1f, 2f, 3f, 4f)
        val q3 = Quaternion(1f, 2f, 3f, 5f)

        assertTrue(q1.equals(q2))
        assertTrue(!q1.equals(q3))
    }
}