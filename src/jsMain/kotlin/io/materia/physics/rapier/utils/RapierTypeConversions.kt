/**
 * Type conversions between Materia and Rapier types
 */
package io.materia.physics.rapier.utils

import io.materia.core.math.*
import io.materia.physics.RAPIER

/**
 * Convert Materia Vector3 to Rapier Vector3
 */
fun toRapierVector3(v: Vector3): RAPIER.Vector3 {
    return RAPIER.Vector3(v.x, v.y, v.z)
}

/**
 * Convert Rapier dynamic vector to Materia Vector3
 */
fun fromRapierVector3(v: dynamic): Vector3 {
    return Vector3(v.x as Float, v.y as Float, v.z as Float)
}

/**
 * Convert Materia Quaternion to Rapier Quaternion
 */
fun toRapierQuaternion(q: Quaternion): RAPIER.Quaternion {
    return RAPIER.Quaternion(q.x, q.y, q.z, q.w)
}

/**
 * Convert Rapier dynamic quaternion to Materia Quaternion
 */
fun fromRapierQuaternion(q: dynamic): Quaternion {
    return Quaternion(q.x as Float, q.y as Float, q.z as Float, q.w as Float)
}

/**
 * Extract rotation quaternion from Matrix4
 */
fun Matrix4.extractRotation(): Quaternion {
    val trace = m00 + m11 + m22

    return when {
        trace > 0 -> {
            val s = 0.5f / kotlin.math.sqrt(trace + 1f)
            Quaternion(
                (m21 - m12) * s,
                (m02 - m20) * s,
                (m10 - m01) * s,
                0.25f / s
            )
        }

        m00 > m11 && m00 > m22 -> {
            val s = 2f * kotlin.math.sqrt(1f + m00 - m11 - m22)
            Quaternion(
                0.25f * s,
                (m01 + m10) / s,
                (m02 + m20) / s,
                (m21 - m12) / s
            )
        }

        m11 > m22 -> {
            val s = 2f * kotlin.math.sqrt(1f + m11 - m00 - m22)
            Quaternion(
                (m01 + m10) / s,
                0.25f * s,
                (m12 + m21) / s,
                (m02 - m20) / s
            )
        }

        else -> {
            val s = 2f * kotlin.math.sqrt(1f + m22 - m00 - m11)
            Quaternion(
                (m02 + m20) / s,
                (m12 + m21) / s,
                0.25f * s,
                (m10 - m01) / s
            )
        }
    }
}
