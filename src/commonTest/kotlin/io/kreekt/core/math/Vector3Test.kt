package io.kreekt.core.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Vector3 operations test following API contract requirements.
 *
 * CRITICAL: These tests validate the Vector3 implementation.
 * Following TDD constitutional requirement - tests drive implementation.
 * T011 - Vector3 operations test
 */
class Vector3Test {

    @Test
    fun testVector3Creation() {
        val v = Vector3(1.5f, 2.5f, 3.5f)
        assertEquals(1.5f, v.x)
        assertEquals(2.5f, v.y)
        assertEquals(3.5f, v.z)
    }

    @Test
    fun testVector3Constants() {
        assertEquals(Vector3(0f, 0f, 0f), Vector3.ZERO)
        assertEquals(Vector3(1f, 1f, 1f), Vector3.ONE)
        assertEquals(Vector3(0f, 1f, 0f), Vector3.UP)
        assertEquals(Vector3(1f, 0f, 0f), Vector3.RIGHT)
        assertEquals(Vector3(0f, 0f, -1f), Vector3.FORWARD)
    }

    @Test
    fun testAddition() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val result = v1 + v2

        assertEquals(Vector3(5f, 7f, 9f), result)
        assertNotSame(v1, result) // Immutability check
        assertNotSame(v2, result) // Immutability check
    }

    @Test
    fun testSubtraction() {
        val v1 = Vector3(4f, 5f, 6f)
        val v2 = Vector3(1f, 2f, 3f)
        val result = v1 - v2

        assertEquals(Vector3(3f, 3f, 3f), result)
    }

    @Test
    fun testScalarMultiplication() {
        val v = Vector3(1f, 2f, 3f)
        val result = v * 2f

        assertEquals(Vector3(2f, 4f, 6f), result)
    }

    @Test
    fun testScalarDivision() {
        val v = Vector3(2f, 4f, 6f)
        val result = v / 2f

        assertEquals(Vector3(1f, 2f, 3f), result)
    }

    @Test
    fun testDivisionByZero() {
        val v = Vector3(1f, 2f, 3f)
        val result = v / 0f
        // Division by zero in Float returns Infinity, not an exception
        assertTrue(result.x.isInfinite())
        assertTrue(result.y.isInfinite())
        assertTrue(result.z.isInfinite())
    }

    @Test
    fun testUnaryNegation() {
        val v = Vector3(1f, -2f, 3f)
        val result = -v

        assertEquals(Vector3(-1f, 2f, -3f), result)
    }

    @Test
    fun testDotProduct() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val dot = v1.dot(v2)

        assertEquals(32f, dot) // 1*4 + 2*5 + 3*6 = 32
    }

    @Test
    fun testDotProductCommutative() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)

        assertEquals(v1.dot(v2), v2.dot(v1))
    }

    @Test
    fun testDotProductPerpendicular() {
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)

        assertTrue(abs(v1.dot(v2)) < 0.001f)
    }

    @Test
    fun testCrossProduct() {
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        val cross = v1.cross(v2).clone()

        assertEquals(Vector3(0f, 0f, 1f), cross)
    }

    @Test
    fun testCrossProductAntiCommutative() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)

        assertEquals(v1.clone().cross(v2), -(v2.clone().cross(v1)))
    }

    @Test
    fun testCrossProductParallel() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(2f, 4f, 6f) // v2 = 2 * v1
        val cross = v1.clone().cross(v2)

        assertEquals(Vector3.ZERO, cross)
    }

    @Test
    fun testLength() {
        val v = Vector3(3f, 4f, 0f)
        val length = v.length()

        assertTrue(abs(length - 5f) < 0.001f) // 3-4-5 triangle
    }

    @Test
    fun testLengthZero() {
        assertEquals(0f, Vector3.ZERO.length())
    }

    @Test
    fun testLengthSquared() {
        val v = Vector3(3f, 4f, 0f)
        val lengthSq = v.lengthSquared()

        assertEquals(25f, lengthSq) // 3² + 4² = 25
    }

    @Test
    fun testLengthSquaredConsistency() {
        val v = Vector3(1f, 2f, 3f)
        val lengthSq = v.lengthSquared()
        val length = v.length()

        assertTrue(abs(lengthSq - (length * length)) < 0.001f)
    }

    @Test
    fun testNormalize() {
        val v = Vector3(3f, 4f, 0f)
        val normalized = v.clone().normalize()

        assertTrue(abs(normalized.length() - 1f) < 0.001f)
        assertEquals(Vector3(0.6f, 0.8f, 0f), normalized)
    }

    @Test
    fun testNormalizeZero() {
        val zero = Vector3.ZERO.clone()
        val normalized = zero.normalize()
        // Zero vector normalization returns itself
        assertEquals(Vector3.ZERO, normalized)
    }

    @Test
    fun testNormalizeDirection() {
        val v = Vector3(5f, 10f, 15f)
        val normalized = v.clone().normalize()

        // Cross product should be zero (parallel vectors)
        assertTrue(v.clone().cross(normalized).length() < 0.001f)
    }

    @Test
    fun testDistanceTo() {
        val v1 = Vector3(0f, 0f, 0f)
        val v2 = Vector3(3f, 4f, 0f)
        val distance = v1.distanceTo(v2)

        assertTrue(abs(distance - 5f) < 0.001f)
    }

    @Test
    fun testDistanceSymmetric() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)

        assertEquals(v1.distanceTo(v2), v2.distanceTo(v1))
    }

    @Test
    fun testDistanceToSquared() {
        val v1 = Vector3(0f, 0f, 0f)
        val v2 = Vector3(3f, 4f, 0f)
        val distanceSq = v1.distanceToSquared(v2)

        assertEquals(25f, distanceSq)
    }

    @Test
    fun testLinearInterpolation() {
        val v1 = Vector3(0f, 0f, 0f)
        val v2 = Vector3(10f, 20f, 30f)

        val lerp0 = v1.clone().lerp(v2, 0f)
        val lerp1 = v1.clone().lerp(v2, 1f)
        val lerp05 = v1.clone().lerp(v2, 0.5f)

        assertEquals(v1, lerp0)
        assertEquals(v2, lerp1)
        assertEquals(Vector3(5f, 10f, 15f), lerp05)
    }

    @Test
    fun testClone() {
        val v = Vector3(1f, 2f, 3f)
        val clone = v.clone()

        assertEquals(v, clone)
        assertNotSame(v, clone) // Different instance
    }

    @Test
    fun testSet() {
        val v = Vector3(1f, 2f, 3f)
        v.set(4f, 5f, 6f)

        assertEquals(Vector3(4f, 5f, 6f), v)
    }

    @Test
    fun testOperatorImmutability() {
        val original = Vector3(1f, 2f, 3f)
        val other = Vector3(4f, 5f, 6f)
        val originalCopy = original.clone()

        // All operators return new instances
        val sum = original + other
        val diff = original - other
        val mult = original * 2f
        val div = original / 2f
        val neg = -original

        // Original should remain unchanged
        assertEquals(originalCopy, original)

        // Results should be different instances
        assertNotSame(original, sum)
        assertNotSame(original, diff)
        assertNotSame(original, mult)
        assertNotSame(original, div)
        assertNotSame(original, neg)
    }

    @Test
    fun testHandleNaN() {
        val v = Vector3(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        // Should handle gracefully
        assertTrue(v.length().isNaN())
    }
}