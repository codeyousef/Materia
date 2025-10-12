package io.kreekt.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Vector4 operations test following API contract requirements.
 */
class Vector4Test {

    @Test
    fun testVector4Creation() {
        val v = Vector4(1.5f, 2.5f, 3.5f, 4.5f)
        assertEquals(1.5f, v.x)
        assertEquals(2.5f, v.y)
        assertEquals(3.5f, v.z)
        assertEquals(4.5f, v.w)
    }

    @Test
    fun testVector4Constants() {
        assertEquals(Vector4(0f, 0f, 0f, 0f), Vector4.ZERO)
        assertEquals(Vector4(1f, 1f, 1f, 1f), Vector4.ONE)
    }

    @Test
    fun testAddition() {
        val v1 = Vector4(1f, 2f, 3f, 4f)
        val v2 = Vector4(5f, 6f, 7f, 8f)
        val result = v1 + v2

        assertEquals(Vector4(6f, 8f, 10f, 12f), result)
    }

    @Test
    fun testDotProduct() {
        val v1 = Vector4(1f, 2f, 3f, 4f)
        val v2 = Vector4(5f, 6f, 7f, 8f)
        val dot = v1.dot(v2)

        assertEquals(70f, dot) // 1*5 + 2*6 + 3*7 + 4*8 = 70
    }

    @Test
    fun testLength() {
        val v = Vector4(1f, 2f, 2f, 0f)
        val length = v.length()

        assertEquals(3f, length) // sqrt(1 + 4 + 4 + 0) = 3
    }

    @Test
    fun testClone() {
        val v = Vector4(1f, 2f, 3f, 4f)
        val clone = v.clone()

        assertEquals(v, clone)
        assertNotSame(v, clone)
    }
}