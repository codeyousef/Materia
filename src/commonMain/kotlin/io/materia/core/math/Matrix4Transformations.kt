package io.materia.core.math

import kotlin.math.*

/**
 * Matrix4 transformation operations including rotation, translation, scale, compose, and decompose
 */

/**
 * Makes this matrix a translation matrix
 */
internal fun Matrix4.makeTranslationMatrix(x: Float, y: Float, z: Float): Matrix4 {
    setIdentity()
    elements[12] = x
    elements[13] = y
    elements[14] = z
    return this
}

/**
 * Makes this matrix a scale matrix
 */
internal fun Matrix4.makeScaleMatrix(x: Float, y: Float, z: Float): Matrix4 {
    setIdentity()
    elements[0] = x
    elements[5] = y
    elements[10] = z
    return this
}

/**
 * Makes this matrix a rotation around X axis
 */
internal fun Matrix4.makeRotationXMatrix(theta: Float): Matrix4 {
    val c = cos(theta)
    val s = sin(theta)
    setIdentity()
    elements[5] = c; elements[9] = -s
    elements[6] = s; elements[10] = c
    return this
}

/**
 * Makes this matrix a rotation around Y axis
 */
internal fun Matrix4.makeRotationYMatrix(theta: Float): Matrix4 {
    val c = cos(theta)
    val s = sin(theta)
    setIdentity()
    elements[0] = c; elements[8] = s
    elements[2] = -s; elements[10] = c
    return this
}

/**
 * Makes this matrix a rotation around Z axis
 */
internal fun Matrix4.makeRotationZMatrix(theta: Float): Matrix4 {
    val c = cos(theta)
    val s = sin(theta)
    setIdentity()
    elements[0] = c; elements[4] = -s
    elements[1] = s; elements[5] = c
    return this
}

/**
 * Get translation component as Vector3
 */
internal fun Matrix4.getTranslationVector(): Vector3 = Vector3(m03, m13, m23)

/**
 * Get rotation component as Quaternion
 */
internal fun Matrix4.getRotationQuaternion(): Quaternion {
    val trace = m00 + m11 + m22
    return when {
        trace > 0f -> {
            val s = sqrt(trace + 1f) * 2f
            Quaternion(
                (m21 - m12) / s,
                (m02 - m20) / s,
                (m10 - m01) / s,
                0.25f * s
            )
        }

        m00 > m11 && m00 > m22 -> {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            Quaternion(
                0.25f * s,
                (m01 + m10) / s,
                (m02 + m20) / s,
                (m21 - m12) / s
            )
        }

        m11 > m22 -> {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            Quaternion(
                (m01 + m10) / s,
                0.25f * s,
                (m12 + m21) / s,
                (m02 - m20) / s
            )
        }

        else -> {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            Quaternion(
                (m02 + m20) / s,
                (m12 + m21) / s,
                0.25f * s,
                (m10 - m01) / s
            )
        }
    }
}

/**
 * Transform a point (with translation)
 */
internal fun Matrix4.transformPointWithTranslation(point: Vector3): Vector3 {
    val x = point.x * m00 + point.y * m01 + point.z * m02 + m03
    val y = point.x * m10 + point.y * m11 + point.z * m12 + m13
    val z = point.x * m20 + point.y * m21 + point.z * m22 + m23
    return Vector3(x, y, z)
}

/**
 * Transform a direction (without translation)
 */
internal fun Matrix4.transformDirectionOnly(direction: Vector3): Vector3 {
    val x = direction.x * m00 + direction.y * m01 + direction.z * m02
    val y = direction.x * m10 + direction.y * m11 + direction.z * m12
    val z = direction.x * m20 + direction.y * m21 + direction.z * m22
    return Vector3(x, y, z)
}

/**
 * Apply translation to this matrix
 */
internal fun Matrix4.applyTranslation(offset: Vector3): Matrix4 {
    return this.multiply(Matrix4.translation(offset.x, offset.y, offset.z))
}

/**
 * Apply rotation to this matrix
 */
internal fun Matrix4.applyRotation(rotation: Quaternion): Matrix4 {
    val rotMatrix = Matrix4().makeRotationFromQuaternion(rotation)
    return this.multiply(rotMatrix)
}

/**
 * Create rotation matrix from quaternion
 */
internal fun Matrix4.makeRotationFromQuaternionInternal(q: Quaternion): Matrix4 {
    val x = q.x;
    val y = q.y;
    val z = q.z;
    val w = q.w
    val x2 = x + x;
    val y2 = y + y;
    val z2 = z + z
    val xx = x * x2;
    val xy = x * y2;
    val xz = x * z2
    val yy = y * y2;
    val yz = y * z2;
    val zz = z * z2
    val wx = w * x2;
    val wy = w * y2;
    val wz = w * z2

    elements[0] = 1f - (yy + zz)
    elements[4] = xy - wz
    elements[8] = xz + wy

    elements[1] = xy + wz
    elements[5] = 1f - (xx + zz)
    elements[9] = yz - wx

    elements[2] = xz - wy
    elements[6] = yz + wx
    elements[10] = 1f - (xx + yy)

    // Last row
    elements[3] = 0f
    elements[7] = 0f
    elements[11] = 0f

    // Last column
    elements[12] = 0f
    elements[13] = 0f
    elements[14] = 0f
    elements[15] = 1f

    return this
}

/**
 * Scales this matrix by the given vector
 */
internal fun Matrix4.applyScale(scale: Vector3): Matrix4 {
    elements[0] *= scale.x
    elements[1] *= scale.x
    elements[2] *= scale.x
    elements[3] *= scale.x

    elements[4] *= scale.y
    elements[5] *= scale.y
    elements[6] *= scale.y
    elements[7] *= scale.y

    elements[8] *= scale.z
    elements[9] *= scale.z
    elements[10] *= scale.z
    elements[11] *= scale.z

    return this
}

/**
 * Set this matrix to look from eye position to target position
 */
internal fun Matrix4.setLookAt(eye: Vector3, target: Vector3, up: Vector3): Matrix4 {
    val z = eye.clone().sub(target).normalize()

    if (abs(z.lengthSq()) < 0.000001f) {
        // eye and target are in the same position
        z.z = 1f
    }

    val x = up.clone().cross(z).normalize()

    if (abs(x.lengthSq()) < 0.000001f) {
        // up and z are parallel
        if (abs(abs(up.z) - 1f) < 0.000001f) {
            z.x += 0.0001f
        } else {
            z.z += 0.0001f
        }
        z.normalize()
        x.copy(up).cross(z).normalize()
    }

    val y = z.clone().cross(x)

    elements[0] = x.x; elements[4] = y.x; elements[8] = z.x
    elements[1] = x.y; elements[5] = y.y; elements[9] = z.y
    elements[2] = x.z; elements[6] = y.z; elements[10] = z.z

    return this
}
