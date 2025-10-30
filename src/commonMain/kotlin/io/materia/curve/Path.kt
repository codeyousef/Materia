package io.materia.curve

import io.materia.core.math.Vector2

/**
 * Path - 2D path for shapes
 * Represents a series of connected curves forming a path
 * with support for lines, quadratic and cubic bezier curves
 */
open class Path {

    // Use simplified path segments instead of full curve classes
    sealed class PathSegment {
        data class MoveTo(val point: Vector2) : PathSegment()
        data class LineTo(val point: Vector2) : PathSegment()
        data class QuadraticCurveTo(val control: Vector2, val point: Vector2) : PathSegment()
        data class BezierCurveTo(val control1: Vector2, val control2: Vector2, val point: Vector2) :
            PathSegment()

        data class ArcTo(
            val center: Vector2,
            val radius: Vector2,
            val startAngle: Float,
            val endAngle: Float,
            val clockwise: Boolean
        ) : PathSegment()
    }

    val segments = mutableListOf<PathSegment>()
    var currentPoint = Vector2()
    var autoClose = false

    constructor()

    constructor(points: List<Vector2>) {
        if (points.isNotEmpty()) {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
    }

    /**
     * Get points along the path
     */
    open fun getPoints(divisions: Int = 12): List<Vector2> {
        val points = mutableListOf<Vector2>()
        var current = Vector2()

        for (segment in segments) {
            when (segment) {
                is PathSegment.MoveTo -> {
                    current = segment.point.clone()
                    if (points.isEmpty()) points.add(current.clone())
                }

                is PathSegment.LineTo -> {
                    points.add(segment.point.clone())
                    current = segment.point.clone()
                }

                is PathSegment.QuadraticCurveTo -> {
                    // Generate points for quadratic bezier curve
                    for (i in 1..divisions) {
                        val t = i.toFloat() / divisions
                        val oneMinusT = 1f - t
                        val p = current.clone().multiplyScalar(oneMinusT * oneMinusT)
                            .add(segment.control.clone().multiplyScalar(2f * oneMinusT * t))
                            .add(segment.point.clone().multiplyScalar(t * t))
                        points.add(p)
                    }
                    current = segment.point.clone()
                }

                is PathSegment.BezierCurveTo -> {
                    // Generate points for cubic bezier curve
                    for (i in 1..divisions) {
                        val t = i.toFloat() / divisions
                        val oneMinusT = 1f - t
                        val p = current.clone().multiplyScalar(oneMinusT * oneMinusT * oneMinusT)
                            .add(
                                segment.control1.clone()
                                    .multiplyScalar(3f * oneMinusT * oneMinusT * t)
                            )
                            .add(segment.control2.clone().multiplyScalar(3f * oneMinusT * t * t))
                            .add(segment.point.clone().multiplyScalar(t * t * t))
                        points.add(p)
                    }
                    current = segment.point.clone()
                }

                is PathSegment.ArcTo -> {
                    // Generate points for arc
                    val angleRange = if (segment.clockwise) {
                        if (segment.endAngle < segment.startAngle) {
                            segment.endAngle + 2f * kotlin.math.PI.toFloat() - segment.startAngle
                        } else {
                            segment.endAngle - segment.startAngle
                        }
                    } else {
                        if (segment.startAngle < segment.endAngle) {
                            segment.startAngle + 2f * kotlin.math.PI.toFloat() - segment.endAngle
                        } else {
                            segment.startAngle - segment.endAngle
                        }
                    }

                    for (i in 1..divisions) {
                        val t = i.toFloat() / divisions
                        val angle =
                            segment.startAngle + (if (segment.clockwise) angleRange * t else -angleRange * t)
                        val x = segment.center.x + segment.radius.x * kotlin.math.cos(angle)
                        val y = segment.center.y + segment.radius.y * kotlin.math.sin(angle)
                        points.add(Vector2(x, y))
                    }
                    current = points.lastOrNull()?.clone() ?: current
                }
            }
        }

        if (autoClose && points.size > 2) {
            // Ensure the path is closed
            val firstPoint = points.firstOrNull()
            val lastPoint = points.lastOrNull()
            if (firstPoint != null && lastPoint != null && firstPoint.distanceToSquared(lastPoint) > 0.0001f) {
                points.add(firstPoint.clone())
            }
        }

        return points
    }

    /**
     * Move to a point without drawing
     */
    open fun moveTo(x: Float, y: Float): Path {
        currentPoint.set(x, y)
        segments.add(PathSegment.MoveTo(currentPoint.clone()))
        return this
    }

    /**
     * Draw line to point
     */
    open fun lineTo(x: Float, y: Float): Path {
        val endPoint = Vector2(x, y)
        segments.add(PathSegment.LineTo(endPoint))
        currentPoint.set(x, y)
        return this
    }

    /**
     * Draw quadratic curve
     */
    open fun quadraticCurveTo(cpX: Float, cpY: Float, x: Float, y: Float): Path {
        val controlPoint = Vector2(cpX, cpY)
        val endPoint = Vector2(x, y)
        segments.add(PathSegment.QuadraticCurveTo(controlPoint, endPoint))
        currentPoint.set(x, y)
        return this
    }

    /**
     * Draw bezier curve
     */
    open fun bezierCurveTo(
        cp1X: Float,
        cp1Y: Float,
        cp2X: Float,
        cp2Y: Float,
        x: Float,
        y: Float
    ): Path {
        val cp1 = Vector2(cp1X, cp1Y)
        val cp2 = Vector2(cp2X, cp2Y)
        val endPoint = Vector2(x, y)
        segments.add(PathSegment.BezierCurveTo(cp1, cp2, endPoint))
        currentPoint.set(x, y)
        return this
    }

    /**
     * Draw spline through points
     */
    open fun splineThru(points: List<Vector2>): Path {
        // Simplification: connect points with lines in this baseline implementation
        // A proper implementation would use Catmull-Rom or B-spline
        for (point in points) {
            lineTo(point.x, point.y)
        }
        return this
    }

    /**
     * Draw arc
     */
    open fun arc(
        aX: Float, aY: Float,
        aRadius: Float,
        aStartAngle: Float, aEndAngle: Float,
        aClockwise: Boolean
    ): Path {
        val center = Vector2(aX, aY)
        val radius = Vector2(aRadius, aRadius)
        segments.add(PathSegment.ArcTo(center, radius, aStartAngle, aEndAngle, aClockwise))

        // Update current point to the end of the arc
        val endAngle = if (aClockwise && aEndAngle < aStartAngle) {
            aEndAngle + 2 * kotlin.math.PI.toFloat()
        } else {
            aEndAngle
        }
        currentPoint.set(
            aX + aRadius * kotlin.math.cos(endAngle),
            aY + aRadius * kotlin.math.sin(endAngle)
        )
        return this
    }

    /**
     * Draw ellipse
     */
    open fun ellipse(
        aX: Float, aY: Float,
        xRadius: Float, yRadius: Float,
        aStartAngle: Float, aEndAngle: Float,
        aClockwise: Boolean,
        aRotation: Float
    ): Path {
        // For ellipse, we use arc with different x and y radii
        val center = Vector2(aX, aY)
        val radius = Vector2(xRadius, yRadius)
        segments.add(
            PathSegment.ArcTo(
                center,
                radius,
                aStartAngle + aRotation,
                aEndAngle + aRotation,
                aClockwise
            )
        )

        // Update current point to the end of the ellipse
        val endAngle = if (aClockwise && aEndAngle < aStartAngle) {
            aEndAngle + 2 * kotlin.math.PI.toFloat()
        } else {
            aEndAngle
        }
        val cosEnd = kotlin.math.cos(endAngle)
        val sinEnd = kotlin.math.sin(endAngle)
        val cosRot = kotlin.math.cos(aRotation)
        val sinRot = kotlin.math.sin(aRotation)
        currentPoint.set(
            aX + xRadius * cosEnd * cosRot - yRadius * sinEnd * sinRot,
            aY + xRadius * cosEnd * sinRot + yRadius * sinEnd * cosRot
        )
        return this
    }

    /**
     * Draw ellipse (absolute positioning)
     */
    open fun absellipse(
        aX: Float, aY: Float,
        xRadius: Float, yRadius: Float,
        aStartAngle: Float, aEndAngle: Float,
        aClockwise: Boolean, aRotation: Float
    ): Path {
        // Same implementation as ellipse for absolute positioning
        return ellipse(aX, aY, xRadius, yRadius, aStartAngle, aEndAngle, aClockwise, aRotation)
    }

    /**
     * Get area of the path (for hole detection)
     */
    open fun getArea(): Float {
        val points = getPoints()
        if (points.size < 3) return 0f

        var area = 0f
        val n = points.size

        // Shoelace formula for polygon area
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }

        return kotlin.math.abs(area * 0.5f)
    }
}
