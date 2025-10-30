/**
 * Matrix extensions for physics calculations
 * Provides missing transformation methods for Matrix4 and Matrix3
 */
package io.materia.physics

import io.materia.core.math.Matrix3
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3

/**
 * Matrix4 extensions for physics
 */

/**
 * Creates a matrix from translation, rotation, and scale
 */
fun Matrix4.Companion.fromTranslationRotationScale(
    translation: Vector3,
    rotation: Quaternion,
    scale: Vector3
): Matrix4 {
    val matrix = Matrix4()

    // Apply scale
    val sx = scale.x
    val sy = scale.y
    val sz = scale.z

    // Apply rotation (quaternion to matrix)
    val x = rotation.x
    val y = rotation.y
    val z = rotation.z
    val w = rotation.w

    val x2 = x + x
    val y2 = y + y
    val z2 = z + z
    val xx = x * x2
    val xy = x * y2
    val xz = x * z2
    val yy = y * y2
    val yz = y * z2
    val zz = z * z2
    val wx = w * x2
    val wy = w * y2
    val wz = w * z2

    // Set matrix elements
    matrix.elements[0] = (1f - (yy + zz)) * sx
    matrix.elements[1] = (xy + wz) * sx
    matrix.elements[2] = (xz - wy) * sx
    matrix.elements[3] = 0f

    matrix.elements[4] = (xy - wz) * sy
    matrix.elements[5] = (1f - (xx + zz)) * sy
    matrix.elements[6] = (yz + wx) * sy
    matrix.elements[7] = 0f

    matrix.elements[8] = (xz + wy) * sz
    matrix.elements[9] = (yz - wx) * sz
    matrix.elements[10] = (1f - (xx + yy)) * sz
    matrix.elements[11] = 0f

    // Apply translation
    matrix.elements[12] = translation.x
    matrix.elements[13] = translation.y
    matrix.elements[14] = translation.z
    matrix.elements[15] = 1f

    return matrix
}

/**
 * Creates a matrix from translation and rotation
 */
fun Matrix4.Companion.fromTranslationRotation(
    translation: Vector3,
    rotation: Quaternion
): Matrix4 = fromTranslationRotationScale(translation, rotation, Vector3.ONE)

/**
 * Creates a matrix from translation only
 */
fun Matrix4.Companion.fromTranslation(translation: Vector3): Matrix4 =
    fromTranslationRotationScale(translation, Quaternion.IDENTITY, Vector3.ONE)

// Note: Matrix4.getTranslation() is shadowed by member function - removed

// Note: Matrix4.getRotation() is shadowed by member function - removed
// Original implementation preserved below for reference but commented out
/*
fun Matrix4.getRotation(): Quaternion {
    val scale = getScale()

    // Normalize the matrix to remove scale
    val m11 = elements[0] / scale.x
    val m12 = elements[4] / scale.y
    val m13 = elements[8] / scale.z
    val m21 = elements[1] / scale.x
    val m22 = elements[5] / scale.y
    val m23 = elements[9] / scale.z
    val m31 = elements[2] / scale.x
    val m32 = elements[6] / scale.y
    val m33 = elements[10] / scale.z

    // Convert matrix to quaternion
    val trace = m11 + m22 + m33

    return if (trace > 0f) {
        val s = sqrt(trace + 1f) * 2f // s = 4 * qw
        val w = 0.25f * s
        val x = (m32 - m23) / s
        val y = (m13 - m31) / s
        val z = (m21 - m12) / s
        Quaternion(x, y, z, w)
    } else if (m11 > m22 && m11 > m33) {
        val s = sqrt(1f + m11 - m22 - m33) * 2f // s = 4 * qx
        val w = (m32 - m23) / s
        val x = 0.25f * s
        val y = (m12 + m21) / s
        val z = (m13 + m31) / s
        Quaternion(x, y, z, w)
    } else if (m22 > m33) {
        val s = sqrt(1f + m22 - m11 - m33) * 2f // s = 4 * qy
        val w = (m13 - m31) / s
        val x = (m12 + m21) / s
        val y = 0.25f * s
        val z = (m23 + m32) / s
        Quaternion(x, y, z, w)
    } else {
        val s = sqrt(1f + m33 - m11 - m22) * 2f // s = 4 * qz
        val w = (m21 - m12) / s
        val x = (m13 + m31) / s
        val y = (m23 + m32) / s
        val z = 0.25f * s
        Quaternion(x, y, z, w)
    }
}
*/

/**
 * Matrix3 companion and extensions
 */

// Note: Matrix3.Companion.IDENTITY is shadowed by member property - removed

/**
 * Creates a Matrix3 zero matrix
 */
val Matrix3.Companion.ZERO: Matrix3
    get() = Matrix3(
        floatArrayOf(
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f
        )
    )

// Note: Matrix3.inverse() is shadowed by member function - removed

// Note: Matrix3.determinant() is shadowed by member function - removed

// Note: Matrix3.transpose() is shadowed by member function - removed

// Note: Matrix3.times(Vector3) operator is shadowed by member function - removed

/**
 * Matrix3 addition
 */
operator fun Matrix3.plus(other: Matrix3): Matrix3 {
    return Matrix3(
        floatArrayOf(
            m00 + other.m00, m10 + other.m10, m20 + other.m20,
            m01 + other.m01, m11 + other.m11, m21 + other.m21,
            m02 + other.m02, m12 + other.m12, m22 + other.m22
        )
    )
}

// Note: Matrix3.times(Matrix3) operator is shadowed by member function - removed


/**
 * Quaternion extensions for physics
 */

// Note: Quaternion.Companion.IDENTITY is shadowed by member property - removed

// Note: Quaternion.Companion.fromAxisAngle() is shadowed by member function - removed

// Note: Quaternion.normalized() is shadowed by member function - removed

// Note: Quaternion.inverse() is shadowed by member function - removed

// Note: Quaternion.times(Quaternion) operator is shadowed by member function - removed

