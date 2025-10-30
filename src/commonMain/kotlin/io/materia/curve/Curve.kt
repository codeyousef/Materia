package io.materia.curve

import io.materia.core.math.Vector3

/**
 * Base Curve class - T068
 */
abstract class Curve {
    open val type = "Curve"
    var arcLengthDivisions = 200

    abstract fun getPoint(t: Float, optionalTarget: Vector3 = Vector3()): Vector3

    fun getPointAt(u: Float, optionalTarget: Vector3 = Vector3()): Vector3 {
        val t = getUtoTMapping(u)
        return getPoint(t, optionalTarget)
    }

    open fun getUtoTMapping(u: Float): Float = u

    fun getPoints(divisions: Int = 5): List<Vector3> {
        val points = mutableListOf<Vector3>()
        for (d in 0..divisions) {
            points.add(getPoint(d.toFloat() / divisions))
        }
        return points
    }

    fun getSpacedPoints(divisions: Int = 5): List<Vector3> {
        val points = mutableListOf<Vector3>()
        for (d in 0..divisions) {
            points.add(getPointAt(d.toFloat() / divisions))
        }
        return points
    }

    open fun getLength(): Float {
        val lengths = getLengths()
        return lengths.lastOrNull() ?: 0f
    }

    open fun getLengths(divisions: Int = arcLengthDivisions): List<Float> {
        val cache = mutableListOf(0f)
        var last = getPoint(0f)
        var sum = 0f

        for (i in 1..divisions) {
            val current = getPoint(i.toFloat() / divisions)
            sum += current.distanceTo(last)
            cache.add(sum)
            last = current
        }
        return cache
    }

    open fun getTangent(t: Float, optionalTarget: Vector3 = Vector3()): Vector3 {
        val delta = 0.0001f
        val t1 = t - delta
        val t2 = t + delta

        val pt1 = getPoint(if (t1 < 0) 0f else t1)
        val pt2 = getPoint(if (t2 > 1) 1f else t2)

        return optionalTarget.copy(pt2).sub(pt1).normalize()
    }

    fun getTangentAt(u: Float, optionalTarget: Vector3 = Vector3()): Vector3 {
        val t = getUtoTMapping(u)
        return getTangent(t, optionalTarget)
    }
}
