package io.materia.geometry.text

import io.materia.core.math.Vector2
import io.materia.geometry.*
import io.materia.shape.Shape
import io.materia.shape.ShapePath

/**
 * Converts glyph paths to 2D shapes
 */
object PathConverter {

    /**
     * Convert glyph path to shapes
     */
    fun convert(path: GlyphPath, transform: TransformMatrix3): List<Shape> {
        val shapePath = ShapePath()

        for (command in path.commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    val point = transform.transformPoint(Vector2(command.x, command.y))
                    shapePath.moveTo(point.x, point.y)
                }

                is PathCommand.LineTo -> {
                    val point = transform.transformPoint(Vector2(command.x, command.y))
                    shapePath.lineTo(point.x, point.y)
                }

                is PathCommand.QuadraticCurveTo -> {
                    val cp = transform.transformPoint(Vector2(command.cpx, command.cpy))
                    val end = transform.transformPoint(Vector2(command.x, command.y))
                    shapePath.quadraticCurveTo(cp.x, cp.y, end.x, end.y)
                }

                is PathCommand.BezierCurveTo -> {
                    val cp1 = transform.transformPoint(Vector2(command.cp1x, command.cp1y))
                    val cp2 = transform.transformPoint(Vector2(command.cp2x, command.cp2y))
                    val end = transform.transformPoint(Vector2(command.x, command.y))
                    shapePath.bezierCurveTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
                }

                is PathCommand.ClosePath -> {
                    shapePath.currentPath?.autoClose = true
                }
            }
        }

        return shapePath.toShapes()
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
