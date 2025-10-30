package io.materia.shape

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.curve.Path

/**
 * ShapePath represents a collection of shapes that can be used to create complex 2D paths
 * Provides SVG-like path commands (moveTo, lineTo, bezierCurveTo, etc.)
 *
 * Based on Three.js ShapePath
 */
class ShapePath {

    var currentPath: Path? = null
    val subPaths = mutableListOf<Path>()
    var color: Color? = null

    /**
     * Move to a new starting point without drawing
     */
    fun moveTo(x: Float, y: Float): ShapePath {
        val path = Path()
        currentPath = path
        path.moveTo(x, y)
        subPaths.add(path)
        return this
    }

    /**
     * Draw a line to the specified point
     */
    fun lineTo(x: Float, y: Float): ShapePath {
        ensureCurrentPath()
        currentPath?.lineTo(x, y)
        return this
    }

    /**
     * Draw a quadratic bezier curve
     */
    fun quadraticCurveTo(cpX: Float, cpY: Float, x: Float, y: Float): ShapePath {
        ensureCurrentPath()
        currentPath?.quadraticCurveTo(cpX, cpY, x, y)
        return this
    }

    /**
     * Draw a cubic bezier curve
     */
    fun bezierCurveTo(
        cp1X: Float,
        cp1Y: Float,
        cp2X: Float,
        cp2Y: Float,
        x: Float,
        y: Float
    ): ShapePath {
        ensureCurrentPath()
        currentPath?.bezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, x, y)
        return this
    }

    /**
     * Draw a circular arc
     */
    fun arc(
        x: Float,
        y: Float,
        radius: Float,
        startAngle: Float,
        endAngle: Float,
        clockwise: Boolean = false
    ): ShapePath {
        ensureCurrentPath()
        currentPath?.arc(x, y, radius, startAngle, endAngle, clockwise)
        return this
    }

    /**
     * Draw an elliptical arc
     */
    fun ellipse(
        x: Float,
        y: Float,
        xRadius: Float,
        yRadius: Float,
        startAngle: Float,
        endAngle: Float,
        clockwise: Boolean = false,
        rotation: Float = 0f
    ): ShapePath {
        ensureCurrentPath()
        currentPath?.ellipse(x, y, xRadius, yRadius, startAngle, endAngle, clockwise, rotation)
        return this
    }

    /**
     * Draw an absolute ellipse arc
     */
    fun absellipse(
        x: Float,
        y: Float,
        xRadius: Float,
        yRadius: Float,
        startAngle: Float,
        endAngle: Float,
        clockwise: Boolean = false,
        rotation: Float = 0f
    ): ShapePath {
        ensureCurrentPath()
        currentPath?.absellipse(x, y, xRadius, yRadius, startAngle, endAngle, clockwise, rotation)
        return this
    }

    /**
     * Draw a spline through control points
     */
    fun splineThru(points: List<Vector2>): ShapePath {
        ensureCurrentPath()
        currentPath?.splineThru(points)
        return this
    }

    /**
     * Convert all subpaths to Shape objects
     */
    fun toShapes(isCCW: Boolean = false): List<Shape> {
        val shapes = mutableListOf<Shape>()

        for (i in subPaths.indices) {
            val tmpPath = subPaths[i]
            val tmpShape = Shape()

            // Copy segments from path to shape
            // Note: Shape extends Path, so it also uses segments
            tmpShape.segments.clear()
            tmpShape.segments.addAll(tmpPath.segments)

            shapes.add(tmpShape)
        }

        // Group shapes with holes
        return groupShapesWithHoles(shapes, isCCW)
    }

    /**
     * Ensure a current path exists
     */
    private fun ensureCurrentPath() {
        if (currentPath == null) {
            val path = Path()
            currentPath = path
            subPaths.add(path)
        }
    }

    /**
     * Group shapes and assign holes to parent shapes
     */
    private fun groupShapesWithHoles(shapes: List<Shape>, isCCW: Boolean): List<Shape> {
        if (shapes.isEmpty()) return emptyList()

        // Sort by area (largest first)
        val sortedShapes = shapes.sortedByDescending { it.getArea() }

        val holesFirst = mutableListOf<Shape>()
        val shapesWithHoles = mutableListOf<Shape>()

        // Determine which shapes are holes
        for (shape in sortedShapes) {
            val solid = shape.getArea() < 0 != isCCW

            if (solid) {
                shapesWithHoles.add(shape)
            } else {
                holesFirst.add(shape)
            }
        }

        // Assign holes to shapes
        for (hole in holesFirst) {
            var assigned = false

            for (shape in shapesWithHoles) {
                if (isPointInsideShape(hole.getPoints()[0], shape.getPoints())) {
                    shape.holes.add(hole)
                    assigned = true
                    break
                }
            }

            if (!assigned) {
                // Hole is actually a shape
                shapesWithHoles.add(hole)
            }
        }

        return shapesWithHoles
    }

    /**
     * Check if a point is inside a shape
     */
    private fun isPointInsideShape(point: Vector2, shapePoints: List<Vector2>): Boolean {
        var inside = false
        val x = point.x
        val y = point.y

        for (i in shapePoints.indices) {
            val j = (i + 1) % shapePoints.size
            val xi = shapePoints[i].x
            val yi = shapePoints[i].y
            val xj = shapePoints[j].x
            val yj = shapePoints[j].y

            // Check for division by zero before calculating intersection
            if (kotlin.math.abs(yj - yi) < io.materia.core.math.EPSILON) continue

            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
        }

        return inside
    }

    companion object {
        /**
         * Create a circle shape path
         */
        fun circle(radius: Float): ShapePath {
            val path = ShapePath()
            path.moveTo(radius, 0f)
            path.arc(0f, 0f, radius, 0f, kotlin.math.PI.toFloat() * 2f, false)
            return path
        }

        /**
         * Create a rectangle shape path
         */
        fun rectangle(x: Float, y: Float, width: Float, height: Float): ShapePath {
            return ShapePath().apply {
                moveTo(x, y)
                lineTo(x + width, y)
                lineTo(x + width, y + height)
                lineTo(x, y + height)
                lineTo(x, y)
            }
        }

        /**
         * Create a rounded rectangle shape path
         */
        fun roundedRectangle(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            radius: Float
        ): ShapePath {
            return ShapePath().apply {
                moveTo(x, y + radius)
                lineTo(x, y + height - radius)
                quadraticCurveTo(x, y + height, x + radius, y + height)
                lineTo(x + width - radius, y + height)
                quadraticCurveTo(x + width, y + height, x + width, y + height - radius)
                lineTo(x + width, y + radius)
                quadraticCurveTo(x + width, y, x + width - radius, y)
                lineTo(x + radius, y)
                quadraticCurveTo(x, y, x, y + radius)
            }
        }
    }
}

// Supporting class
data class Color(val r: Float, val g: Float, val b: Float)