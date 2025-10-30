package io.materia.curve

import io.materia.core.math.Vector3

/**
 * CatmullRom spline curve - T069
 */
class CatmullRomCurve3(
    val points: List<Vector3> = emptyList(),
    val closed: Boolean = false,
    val curveType: String = "centripetal",
    val tension: Float = 0.5f
) : Curve3() {

    override val type = "CatmullRomCurve3"

    override fun getPoint(t: Float, optionalTarget: Vector3): Vector3 {
        val point = optionalTarget
        val l = points.size

        val p = (l - if (closed) 0 else 1) * t
        var intPoint = p.toInt()
        var weight = p - intPoint

        if (closed) {
            intPoint += if (intPoint > 0) 0 else (l / maxOf(1, l) + 1) * l
        } else if (weight == 0f && intPoint == l - 1) {
            intPoint = l - 2
            weight = 1f
        }

        val p0 = if (closed || intPoint > 0) points[(intPoint - 1 + l) % l] else points[0]
        val p1 = points[intPoint % l]
        val p2 = points[(intPoint + 1) % l]
        val p3 = if (closed || intPoint + 2 < l) points[(intPoint + 2) % l] else points[l - 1]

        return catmullRom(point, p0, p1, p2, p3, weight, tension)
    }

    private fun catmullRom(
        target: Vector3,
        p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3,
        t: Float, tension: Float
    ): Vector3 {
        val v0 = (p2.x - p0.x) * tension
        val v1 = (p3.x - p1.x) * tension
        val t2 = t * t
        val t3 = t * t2

        target.x = (2 * p1.x - 2 * p2.x + v0 + v1) * t3 +
                (-3 * p1.x + 3 * p2.x - 2 * v0 - v1) * t2 + v0 * t + p1.x

        val v0y = (p2.y - p0.y) * tension
        val v1y = (p3.y - p1.y) * tension
        target.y = (2 * p1.y - 2 * p2.y + v0y + v1y) * t3 +
                (-3 * p1.y + 3 * p2.y - 2 * v0y - v1y) * t2 + v0y * t + p1.y

        val v0z = (p2.z - p0.z) * tension
        val v1z = (p3.z - p1.z) * tension
        target.z = (2 * p1.z - 2 * p2.z + v0z + v1z) * t3 +
                (-3 * p1.z + 3 * p2.z - 2 * v0z - v1z) * t2 + v0z * t + p1.z

        return target
    }
}
