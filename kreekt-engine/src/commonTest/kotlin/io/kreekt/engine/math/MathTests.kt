package io.kreekt.engine.math

import io.kreekt.engine.camera.PerspectiveCamera
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

        val vector = vec3(1f, 2f, 3f)
        val result = transformVector(matrix, vector)

        assertEquals(3f, result.x, EPSILON)
        assertEquals(1f, result.y, EPSILON)
        assertEquals(6f, result.z, EPSILON)
    }

    @Test
    fun quaternionMatchesRotationMatrix() {
        val axis = Vec3.Up
        val quaternion = Quat.identity().setFromAxisAngle(axis, (PI / 2.0).toFloat())
        val matrix = quaternion.normalize().toRotationMatrix()

        val rotated = transformVector(matrix, vec3(1f, 0f, 0f))
        assertEquals(0f, rotated.x, EPSILON)
        assertEquals(0f, rotated.y, EPSILON)
        assertEquals(-1f, rotated.z, EPSILON)
    }

    @Test
    fun perspectiveCameraLookAtProducesExpectedViewMatrix() {
        val camera = PerspectiveCamera()
        camera.transform.setPosition(0f, 0f, 5f)

        camera.lookAt(Vec3.Zero)
        val view = camera.viewMatrix()

        assertEquals(0f, view[12], EPSILON)
        assertEquals(0f, view[13], EPSILON)
        assertEquals(-5f, view[14], EPSILON)
        assertEquals(1f, view[15], EPSILON)

        val transformedOrigin = transformVector(view, Vec3.Zero)
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
        assertSame(updated.data, cached.data)
    }

    private fun transformVector(matrix: Mat4, vector: Vec3): Vec3 {
        val m = matrix.data
        val x = m[0] * vector.x + m[4] * vector.y + m[8] * vector.z + m[12]
        val y = m[1] * vector.x + m[5] * vector.y + m[9] * vector.z + m[13]
        val z = m[2] * vector.x + m[6] * vector.y + m[10] * vector.z + m[14]
        return vec3(x, y, z)
    }
}
