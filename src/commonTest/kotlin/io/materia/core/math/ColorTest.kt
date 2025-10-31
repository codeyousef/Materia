package io.materia.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Color implementation test
 * T045 - Color operations test
 */
class ColorTest {

    @Test
    fun testColorCreation() {
        val color = Color(0.5f, 0.7f, 0.9f)
        assertEquals(0.5f, color.r)
        assertEquals(0.7f, color.g)
        assertEquals(0.9f, color.b)
    }

    @Test
    fun testColorFromHex() {
        val color = Color(0xFF8800)
        assertEquals(1f, color.r)
        assertTrue(color.g > 0.53f && color.g < 0.54f) // ~0.533
        assertEquals(0f, color.b)
    }

    @Test
    fun testColorConstants() {
        assertEquals(Color(1f, 1f, 1f), Color.WHITE)
        assertEquals(Color(0f, 0f, 0f), Color.BLACK)
        assertEquals(Color(1f, 0f, 0f), Color.RED)
        assertEquals(Color(0f, 1f, 0f), Color.GREEN)
        assertEquals(Color(0f, 0f, 1f), Color.BLUE)
    }

    @Test
    fun testColorArithmetic() {
        val c1 = Color(0.2f, 0.3f, 0.4f)
        val c2 = Color(0.1f, 0.2f, 0.3f)

        val sum = c1.clone().add(c2)
        assertEquals(0.3f, sum.r, 1e-6f)
        assertEquals(0.5f, sum.g, 1e-6f)
        assertEquals(0.7f, sum.b, 1e-6f)

        val mult = c1.clone().multiplyScalar(2f)
        assertEquals(0.4f, mult.r, 1e-6f)
        assertEquals(0.6f, mult.g, 1e-6f)
        assertEquals(0.8f, mult.b, 1e-6f)
    }

    @Test
    fun testColorLerp() {
        val c1 = Color(0f, 0f, 0f)
        val c2 = Color(1f, 1f, 1f)

        val mid = c1.clone().lerp(c2, 0.5f)
        assertEquals(0.5f, mid.r)
        assertEquals(0.5f, mid.g)
        assertEquals(0.5f, mid.b)
    }

    @Test
    fun testColorHSL() {
        val color = Color(1f, 0f, 0f) // Red
        val hsl = color.getHSL()

        assertEquals(0f, hsl.x) // Hue for red
        assertEquals(1f, hsl.y) // Full saturation
        assertEquals(0.5f, hsl.z) // 50% lightness
    }

    @Test
    fun testColorClone() {
        val color = Color(0.1f, 0.2f, 0.3f)
        val clone = color.clone()

        assertEquals(color.r, clone.r)
        assertEquals(color.g, clone.g)
        assertEquals(color.b, clone.b)
        assertTrue(color !== clone)
    }
}
