package io.materia.core.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Vector2 operations test following API contract requirements.
 *
 * CRITICAL: These tests validate the Vector2 implementation.
 * Following TDD constitutional requirement - tests drive implementation.
 * T012 - Vector2 operations test
 */
class Vector2Test {

    @Test
    fun testVector2Creation() {
        val v = Vector2(1.5f, 2.5f)
        assertEquals(1.5f, v.x)
        assertEquals(2.5f, v.y)
    }

    @Test
    fun testVector2Constants() {
        assertEquals(Vector2(0f, 0f), Vector2.ZERO)
        assertEquals(Vector2(1f, 1f), Vector2.ONE)
    }

    @Test
    fun testAddition() {
        val v1 = Vector2(1f, 2f)
        val v2 = Vector2(3f, 4f)
        val result = v1 + v2

        assertEquals(Vector2(4f, 6f), result)
    }

    @Test
    fun testSubtraction() {
        val v1 = Vector2(4f, 6f)
        val v2 = Vector2(1f, 2f)
        val result = v1 - v2

        assertEquals(Vector2(3f, 4f), result)
    }

    @Test
    fun testScalarMultiplication() {
        val v = Vector2(1f, 2f)
        val result = v * 2f

        assertEquals(Vector2(2f, 4f), result)
    }

    @Test
    fun testDotProduct() {
        val v1 = Vector2(1f, 2f)
        val v2 = Vector2(3f, 4f)
        val dot = v1.dot(v2)

        assertEquals(11f, dot) // 1*3 + 2*4 = 11
    }

    @Test
    fun testLength() {
        val v = Vector2(3f, 4f)
        val length = v.length()

        assertTrue(abs(length - 5f) < 0.001f) // 3-4-5 triangle
    }

    @Test
    fun testNormalize() {
        val v = Vector2(3f, 4f)
        val normalized = v.clone().normalize()

        assertTrue(abs(normalized.length() - 1f) < 0.001f)
    }

    @Test
    fun testClone() {
        val v = Vector2(1f, 2f)
        val clone = v.clone()

        assertEquals(v, clone)
        assertNotSame(v, clone)
    }
}