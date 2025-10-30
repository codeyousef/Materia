package io.materia.geometry.text

import io.materia.core.math.Vector2
import io.materia.geometry.*
import io.materia.shape.Shape

/**
 * Converts glyph paths to 2D shapes
 */
object PathConverter {

    /**
     * Convert glyph path to shapes
     */
    fun convert(path: GlyphPath, transform: TransformMatrix3): List<Shape> {
        val shapes = mutableListOf<Shape>()
        val currentContour = mutableListOf<Vector2>()
        var currentPoint = Vector2()

        for (command in path.commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    if (currentContour.isNotEmpty()) {
                        shapes.add(Shape(currentContour.toList()))
                        currentContour.clear()
                    }
                    currentPoint = transform.transformPoint(Vector2(command.x, command.y))
                    currentContour.add(currentPoint)
                }

                is PathCommand.LineTo -> {
                    currentPoint = transform.transformPoint(Vector2(command.x, command.y))
                    currentContour.add(currentPoint)
                }

                is PathCommand.QuadraticCurveTo -> {
                    val cp = transform.transformPoint(Vector2(command.cpx, command.cpy))
                    val end = transform.transformPoint(Vector2(command.x, command.y))

                    // Subdivide quadratic curve
                    val curvePoints = CurveSubdivider.subdivideQuadratic(currentPoint, cp, end, 12)
                    currentContour.addAll(curvePoints.drop(1)) // Skip first point (already added)
                    currentPoint = end
                }

                is PathCommand.BezierCurveTo -> {
                    val cp1 = transform.transformPoint(Vector2(command.cp1x, command.cp1y))
                    val cp2 = transform.transformPoint(Vector2(command.cp2x, command.cp2y))
                    val end = transform.transformPoint(Vector2(command.x, command.y))

                    // Subdivide bezier curve
                    val curvePoints =
                        CurveSubdivider.subdivideBezier(currentPoint, cp1, cp2, end, 12)
                    currentContour.addAll(curvePoints.drop(1)) // Skip first point (already added)
                    currentPoint = end
                }

                is PathCommand.ClosePath -> {
                    if (currentContour.isNotEmpty()) {
                        shapes.add(Shape(currentContour.toList()))
                        currentContour.clear()
                    }
                }
            }
        }

        if (currentContour.isNotEmpty()) {
            shapes.add(Shape(currentContour.toList()))
        }

        return shapes
    }
}

/**
 * Curve subdivision utilities
 */
object CurveSubdivider {

    fun subdivideQuadratic(
        start: Vector2,
        control: Vector2,
        end: Vector2,
        segments: Int
    ): List<Vector2> {
        val points = mutableListOf<Vector2>()

        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val point = quadraticBezier(start, control, end, t)
            points.add(point)
        }

        return points
    }

    fun subdivideBezier(
        start: Vector2,
        cp1: Vector2,
        cp2: Vector2,
        end: Vector2,
        segments: Int
    ): List<Vector2> {
        val points = mutableListOf<Vector2>()

        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val point = cubicBezier(start, cp1, cp2, end, t)
            points.add(point)
        }

        return points
    }

    private fun quadraticBezier(p0: Vector2, p1: Vector2, p2: Vector2, t: Float): Vector2 {
        val invT = 1f - t
        return Vector2(
            invT * invT * p0.x + 2f * invT * t * p1.x + t * t * p2.x,
            invT * invT * p0.y + 2f * invT * t * p1.y + t * t * p2.y
        )
    }

    private fun cubicBezier(p0: Vector2, p1: Vector2, p2: Vector2, p3: Vector2, t: Float): Vector2 {
        val invT = 1f - t
        val invT2 = invT * invT
        val invT3 = invT2 * invT
        val t2 = t * t
        val t3 = t2 * t

        return Vector2(
            invT3 * p0.x + 3f * invT2 * t * p1.x + 3f * invT * t2 * p2.x + t3 * p3.x,
            invT3 * p0.y + 3f * invT2 * t * p1.y + 3f * invT * t2 * p2.y + t3 * p3.y
        )
    }
}
