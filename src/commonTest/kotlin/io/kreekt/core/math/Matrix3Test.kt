package io.kreekt.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Matrix3 operations test
 */
class Matrix3Test {

    @Test
    fun testMatrix3Creation() {
        val m = Matrix3()
        assertTrue(m.isIdentity())
    }

    @Test
    fun testIdentity() {
        val m = Matrix3.identity()
        assertTrue(m.isIdentity())
    }

    @Test
    fun testDeterminant() {
        val m = Matrix3.identity()
        assertEquals(1f, m.determinant())
    }

    @Test
    fun testInverse() {
        val m = Matrix3.identity()
        val inverse = m.clone().invert()
        assertTrue(inverse.isIdentity())
    }

    @Test
    fun testTranspose() {
        val m = Matrix3().fromArray(
            floatArrayOf(
                1f, 2f, 3f,
                4f, 5f, 6f,
                7f, 8f, 9f
            )
        )

        val transposed = m.clone().transpose()

        assertEquals(1f, transposed.elements[0])
        assertEquals(4f, transposed.elements[1])
        assertEquals(7f, transposed.elements[2])
    }

    @Test
    fun testClone() {
        val m = Matrix3.identity()
        val clone = m.clone()

        assertEquals(m.determinant(), clone.determinant())
        assertNotSame(m, clone)
    }
}