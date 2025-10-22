package io.kreekt.engine.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private const val EPSILON = 1e-4f

class MathTests {
    @Test
    fun matrixTimesVectorHandlesTranslation() {
        val transform = Transform().setPosition(2f, -1f, 3f)
        val matrix = transform.matrix()

        val vector = Vector3f(1f, 2f, 3f)
        val result = transformVector(matrix, vector)

        assertEquals(3f, result.x, EPSILON)
        assertEquals(1f, result.y, EPSILON)
        assertEquals(6f, result.z, EPSILON)
    }

    @Test
    fun quaternionMatchesRotationMatrix() {
        val axis = Vector3f.Up
        val quaternion = Quaternion.identity().setFromAxisAngle(axis, (PI / 2.0).toFloat())
        val matrix = quaternion.normalize().toRotationMatrix()

        val rotated = transformVector(matrix, Vector3f(1f, 0f, 0f))
        assertEquals(0f, rotated.x, EPSILON)
        assertEquals(0f, rotated.y, EPSILON)
        assertEquals(-1f, rotated.z, EPSILON)
    }

    @Test
    fun perspectiveCameraLookAtProducesExpectedViewMatrix() {
        val camera = PerspectiveCamera()
        camera.transform.setPosition(0f, 0f, 5f)

        camera.lookAt(Vector3f.Zero)
        val view = camera.viewMatrix()

        assertEquals(0f, view[12], EPSILON)
        assertEquals(0f, view[13], EPSILON)
        assertEquals(-5f, view[14], EPSILON)
        assertEquals(1f, view[15], EPSILON)

        val transformedOrigin = transformVector(view, Vector3f.Zero)
        assertEquals(0f, transformedOrigin.x, EPSILON)
        assertEquals(0f, transformedOrigin.y, EPSILON)
        assertEquals(-5f, transformedOrigin.z, EPSILON)
    }

    @Test
    fun transformInvalidatesMatrixOnMutation() {
        val transform = Transform()
        val initial = transform.matrix()

        transform.setPosition(1f, 2f, 3f)
        val updated = transform.matrix()
        assertEquals(1f, updated[12], EPSILON)
        assertEquals(2f, updated[13], EPSILON)
        assertEquals(3f, updated[14], EPSILON)

        val cached = transform.matrix()
        assertSame(updated, cached)
    }

    private fun transformVector(matrix: FloatArray, vector: Vector3f): Vector3f {
        val x = matrix[0] * vector.x + matrix[4] * vector.y + matrix[8] * vector.z + matrix[12]
        val y = matrix[1] * vector.x + matrix[5] * vector.y + matrix[9] * vector.z + matrix[13]
        val z = matrix[2] * vector.x + matrix[6] * vector.y + matrix[10] * vector.z + matrix[14]
        return Vector3f(x, y, z)
    }
}
