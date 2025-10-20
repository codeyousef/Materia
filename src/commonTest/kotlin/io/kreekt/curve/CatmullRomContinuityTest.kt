package io.kreekt.curve

import io.kreekt.core.math.Vector3
import kotlin.test.Test
import kotlin.test.assertTrue

class CatmullRomContinuityTest {
    @Test
    fun curveProducesContinuousPath() {
        val controlPoints = listOf(
            Vector3(-10f, 0f, 0f),
            Vector3(-5f, 5f, 0f),
            Vector3(0f, 0f, 0f),
            Vector3(5f, -5f, 0f),
            Vector3(10f, 0f, 0f)
        )
        val curve = CatmullRomCurve3(controlPoints, closed = false)
        val sampleCount = 64
        var previous = Vector3()
        curve.getPoint(0f, previous)
        for (i in 1..sampleCount) {
            val t = i / sampleCount.toFloat()
            val current = Vector3()
            curve.getPoint(t, current)
            val distance = previous.distanceTo(current)
            assertTrue(distance < 5f, "Adjacent curve samples should not jump abruptly (distance=$distance)")
            previous = current
        }
    }
}
