package io.kreekt.engine.math

import kotlin.math.cos
import kotlin.math.sin

class Transform {
    val position: Vector3f = Vector3f()
    val rotationEuler: Vector3f = Vector3f()
    val scale: Vector3f = Vector3f(1f, 1f, 1f)

    private val matrixCache = FloatArray(16) { 0f }
    private var dirty = true

    fun markDirty() {
        dirty = true
    }

    fun matrix(): FloatArray {
        if (!dirty) return matrixCache
        val sx = scale.x
        val sy = scale.y
        val sz = scale.z
        val rx = rotationEuler.x
        val ry = rotationEuler.y
        val rz = rotationEuler.z

        val cx = cos(rx)
        val sxSin = sin(rx)
        val cy = cos(ry)
        val sySin = sin(ry)
        val cz = cos(rz)
        val szSin = sin(rz)

        val m00 = cy * cz
        val m01 = cy * szSin
        val m02 = -sySin

        val m10 = sxSin * sySin * cz - cx * szSin
        val m11 = sxSin * sySin * szSin + cx * cz
        val m12 = sxSin * cy

        val m20 = cx * sySin * cz + sxSin * szSin
        val m21 = cx * sySin * szSin - sxSin * cz
        val m22 = cx * cy

        matrixCache[0] = m00 * sx
        matrixCache[1] = m10 * sx
        matrixCache[2] = m20 * sx
        matrixCache[3] = 0f

        matrixCache[4] = m01 * sy
        matrixCache[5] = m11 * sy
        matrixCache[6] = m21 * sy
        matrixCache[7] = 0f

        matrixCache[8] = m02 * sz
        matrixCache[9] = m12 * sz
        matrixCache[10] = m22 * sz
        matrixCache[11] = 0f

        matrixCache[12] = position.x
        matrixCache[13] = position.y
        matrixCache[14] = position.z
        matrixCache[15] = 1f

        dirty = false
        return matrixCache
    }

    fun setPosition(x: Float, y: Float, z: Float): Transform {
        position.set(x, y, z)
        dirty = true
        return this
    }

    fun setRotationEuler(x: Float, y: Float, z: Float): Transform {
        rotationEuler.set(x, y, z)
        dirty = true
        return this
    }

    fun setScale(x: Float, y: Float, z: Float): Transform {
        scale.set(x, y, z)
        dirty = true
        return this
    }
}
