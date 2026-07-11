package io.materia.geometry.text

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.shape.Shape
import io.materia.shape.ShapeUtils

/**
 * Shape triangulation for text geometry
 */
object ShapeTriangulator {

    /**
     * Triangulate shape into flat geometry
     */
    fun triangulate(
        shape: Shape,
        vertices: MutableList<Vector3>,
        normals: MutableList<Vector3>,
        uvs: MutableList<Vector2>,
        indices: MutableList<Int>
    ) {
        // Get shape points
        val extractedPoints = shape.extractPoints()
        val shapePoints = extractedPoints.shape
        val holes = extractedPoints.holes

        // Triangulate the shape and create flat geometry
        val triangles = ShapeUtils.triangulateShape(shapePoints, holes)
        val startVertexIndex = vertices.size
        val allPoints = buildList {
            addAll(shapePoints)
            holes.forEach(::addAll)
        }

        // Add vertices
        for (point in allPoints) {
            vertices.add(Vector3(point.x, point.y, 0f))
            normals.add(Vector3(0f, 0f, 1f))
            uvs.add(Vector2(point.x, point.y)) // Simple UV mapping
        }

        // Add triangles
        for (triangle in triangles) {
            indices.addAll(triangle.map { it + startVertexIndex })
        }
    }
}
