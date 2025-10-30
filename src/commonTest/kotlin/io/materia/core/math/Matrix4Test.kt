package io.materia.core.math

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Matrix4 operations test
 */
class Matrix4Test {

    @Test
    fun testMatrix4Creation() {
        val m = Matrix4()
        // Default should be identity matrix
        assertTrue(m.isIdentity())
    }

    @Test
    fun testIdentity() {
        val m = Matrix4.identity()
        assertTrue(m.isIdentity())
    }

    @Test
    fun testSetFromArray() {
        val array = floatArrayOf(
            1f, 2f, 3f, 4f,
            5f, 6f, 7f, 8f,
            9f, 10f, 11f, 12f,
            13f, 14f, 15f, 16f
        )
        val m = Matrix4(array)

        assertEquals(1f, m.elements[0])
        assertEquals(16f, m.elements[15])
    }

    @Test
    fun testMultiplication() {
        val m1 = Matrix4.identity()
        val m2 = Matrix4.identity().setPosition(Vector3(1f, 2f, 3f))

        val result = m1.clone().multiply(m2)

        val translation = result.getTranslation()
        assertEquals(Vector3(1f, 2f, 3f), translation)
    }

    @Test
    fun testDeterminant() {
        val m = Matrix4.identity()
        assertEquals(1f, m.determinant())
    }

    @Test
    fun testInverse() {
        val m = Matrix4.identity().setPosition(Vector3(1f, 2f, 3f))
        val inverse = m.clone().invert()

        val result = m.clone().multiply(inverse)
        assertTrue(result.isIdentity())
    }

    @Test
    fun testTranspose() {
        val m = Matrix4(
            floatArrayOf(
                1f, 2f, 3f, 4f,
                5f, 6f, 7f, 8f,
                9f, 10f, 11f, 12f,
                13f, 14f, 15f, 16f
            )
        )

        val transposed = m.clone().transpose()

        assertEquals(1f, transposed.elements[0])
        assertEquals(5f, transposed.elements[1])
        assertEquals(9f, transposed.elements[2])
        assertEquals(13f, transposed.elements[3])
    }

    @Test
    fun testTranslation() {
        val translation = Vector3(1f, 2f, 3f)
        val m = Matrix4().setPosition(translation)

        assertEquals(translation, m.getTranslation())
    }

    @Test
    fun testClone() {
        val m = Matrix4().setPosition(Vector3(1f, 2f, 3f))
        val clone = m.clone()

        assertEquals(m.getTranslation(), clone.getTranslation())
        assertNotSame(m, clone)
    }
}