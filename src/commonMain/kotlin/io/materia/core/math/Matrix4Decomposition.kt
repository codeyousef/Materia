package io.materia.core.math

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Matrix4 decomposition operations for breaking down transformations into components
 */

/**
 * Compose this matrix from position, quaternion, and scale
 */
internal fun Matrix4.composeFromPQS(
    position: Vector3,
    quaternion: Quaternion,
    scale: Vector3
): Matrix4 {
    makeRotationFromQuaternionInternal(quaternion)
    this.applyScale(scale)
    updatePositionFromVector(position)
    return this
}

/**
 * Decompose this matrix into position, quaternion, and scale
 */
internal fun Matrix4.decomposeToPQS(
    position: Vector3,
    quaternion: Quaternion,
    scale: Vector3
): Matrix4 {
    // Extract position
    position.x = elements[12]
    position.y = elements[13]
    position.z = elements[14]

    // Extract scale
    val sx = Vector3(elements[0], elements[1], elements[2]).length()
    val sy = Vector3(elements[4], elements[5], elements[6]).length()
    val sz = Vector3(elements[8], elements[9], elements[10]).length()

    scale.x = sx
    scale.y = sy
    scale.z = sz

    // Remove scale from the matrix (with zero-division protection)
    val epsilon = 0.000001f
    val invSx = if (abs(sx) < epsilon) 1f else 1f / sx
    val invSy = if (abs(sy) < epsilon) 1f else 1f / sy
    val invSz = if (abs(sz) < epsilon) 1f else 1f / sz

    val m11 = elements[0] * invSx
    val m12 = elements[4] * invSy
    val m13 = elements[8] * invSz
    val m21 = elements[1] * invSx
    val m22 = elements[5] * invSy
    val m23 = elements[9] * invSz
    val m31 = elements[2] * invSx
    val m32 = elements[6] * invSy
    val m33 = elements[10] * invSz

    // Extract quaternion from rotation matrix
    val trace = m11 + m22 + m33

    if (trace > 0) {
        val s = 0.5f / sqrt(trace + 1.0f)
        quaternion.w = 0.25f / s
        quaternion.x = (m32 - m23) * s
        quaternion.y = (m13 - m31) * s
        quaternion.z = (m21 - m12) * s
    } else if ((m11 > m22) && (m11 > m33)) {
        val discriminant = 1.0f + m11 - m22 - m33
        if (discriminant < 0.00001f) {
            quaternion.set(0f, 0f, 0f, 1f)
            return this
        }
        val s = 2.0f * sqrt(discriminant)
        quaternion.w = (m32 - m23) / s
        quaternion.x = 0.25f * s
        quaternion.y = (m12 + m21) / s
        quaternion.z = (m13 + m31) / s
    } else if (m22 > m33) {
        val discriminant = 1.0f + m22 - m11 - m33
        if (discriminant < 0.00001f) {
            quaternion.set(0f, 0f, 0f, 1f)
            return this
        }
        val s = 2.0f * sqrt(discriminant)
        quaternion.w = (m13 - m31) / s
        quaternion.x = (m12 + m21) / s
        quaternion.y = 0.25f * s
        quaternion.z = (m23 + m32) / s
    } else {
        val discriminant = 1.0f + m33 - m11 - m22
        if (discriminant < 0.00001f) {
            quaternion.set(0f, 0f, 0f, 1f)
            return this
        }
        val s = 2.0f * sqrt(discriminant)
        quaternion.w = (m21 - m12) / s
        quaternion.x = (m13 + m31) / s
        quaternion.y = (m23 + m32) / s
        quaternion.z = 0.25f * s
    }

    return this
}
