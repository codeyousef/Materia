package io.materia.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPS = 1e-3f

class SplineTests {
    @Test
    fun catmullRomInterpolatesEndPoints() {
        val spline = CatmullRomSpline(
            listOf(
                vec3(0f, 0f, 0f),
                vec3(1f, 0f, 0f),
                vec3(2f, 0f, 0f),
                vec3(3f, 0f, 0f)
            )
        )

        val start = spline.point(0f)
        val mid = spline.point(0.5f)
        val end = spline.point(1f)

        assertEquals(0f, start.x, EPS)
        assertEquals(0f, start.y, EPS)
        assertEquals(0f, start.z, EPS)

        assertEquals(1.5f, mid.x, EPS)
        assertEquals(0f, mid.y, EPS)

        assertEquals(3f, end.x, EPS)
    }

    @Test
    fun tangentIsNormalised() {
        val spline = CatmullRomSpline(
            listOf(
                vec3(0f, 0f, 0f),
                vec3(1f, 1f, 0f),
                vec3(2f, 1f, 0f),
                vec3(3f, 0f, 0f)
            )
        )

        val tangent = spline.tangent(0.4f)
        val length = tangent.length()

        assertTrue(length in 0.99f..1.01f, "tangent must be approximately unit length, was $length")
    }

    @Test
    fun easingFunctionsStayWithinBounds() {
        val values = listOf(
            Easing.linear(-0.2f),
            Easing.linear(1.2f),
            Easing.easeInQuad(0.5f),
            Easing.easeOutQuad(0.5f),
            Easing.easeInOutCubic(0.5f),
            Easing.easeInOutSine(0.5f)
        )

        values.forEach { value ->
            assertTrue(value in 0f..1f, "Easing value $value should stay within [0, 1]")
        }
    }
}
