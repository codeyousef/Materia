package io.materia.shape

import io.materia.core.math.Vector2
import io.materia.curve.Path
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * 2D shape that can be extruded or filled.
 * Supports holes for complex shapes.
 * Compatible with Three.js Shape API.
 */
class Shape : Path {

    val holes = mutableListOf<Path>()
    var uuid: String = generateUuid()

    constructor() : super()

    constructor(points: List<Vector2>) : super(points)

    /**
     * Add a hole to the shape.
     */
    fun addHole(hole: Path): Shape {
        holes.add(hole)
        return this
    }

    /**
     * Extract all points from the shape and its holes.
     */
    fun extractPoints(divisions: Int = 12): ShapePoints {
        return ShapePoints(
            shape = getPoints(divisions),
            holes = holes.map { it.getPoints(divisions) }
        )
    }

    /**
     * Get points for the shape boundary.
     */
    fun getPointsHoles(divisions: Int): List<List<Vector2>> {
        val holesPts = mutableListOf<List<Vector2>>()
        holes.forEach { hole ->
            holesPts.add(hole.getPoints(divisions))
        }
        return holesPts
    }

    companion object {
        private val idCounter: AtomicInt = atomic(0)
        private fun generateUuid(): String {
            return "shape-${idCounter.getAndIncrement()}"
        }
    }
}

/**
 * Container for shape points including holes.
 */
data class ShapePoints(
    val shape: List<Vector2>,
    val holes: List<List<Vector2>>
)