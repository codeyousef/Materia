package io.materia.curve

import io.materia.core.math.Vector3

/**
 * Linear 3D curve between two points
 * Three.js compatible LineCurve3
 */
class LineCurve3(
    val v1: Vector3,
    val v2: Vector3
) : Curve3() {

    override val type = "LineCurve3"

    override fun getPoint(t: Float, optionalTarget: Vector3): Vector3 {
        val point = optionalTarget

        if (t == 1f) {
            point.copy(v2)
        } else {
            point.copy(v2).sub(v1)
            point.multiplyScalar(t).add(v1)
        }

        return point
    }

    override fun getTangent(t: Float, optionalTarget: Vector3): Vector3 {
        return optionalTarget.copy(v2).sub(v1).normalize()
    }

    fun clone(): LineCurve3 {
        return LineCurve3(v1.clone(), v2.clone())
    }
}
