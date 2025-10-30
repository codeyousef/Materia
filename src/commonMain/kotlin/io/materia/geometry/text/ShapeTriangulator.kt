package io.materia.geometry.text

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.shape.Shape

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
        val shapePoints = shape.extractPoints().shape
        val holes = shape.extractPoints().holes

        // Triangulate the shape and create flat geometry
        val triangles = triangulateShape(shapePoints, holes)
        val startVertexIndex = vertices.size

        // Add vertices
        for (point in shapePoints) {
            vertices.add(Vector3(point.x, point.y, 0f))
            normals.add(Vector3(0f, 0f, 1f))
            uvs.add(Vector2(point.x, point.y)) // Simple UV mapping
        }

        // Add triangles
        for (triangle in triangles) {
            indices.addAll(triangle.map { it + startVertexIndex })
        }
    }

    /**
     * Triangulate shape (simplified ear-clipping)
     */
    private fun triangulateShape(
        shapePoints: List<Vector2>,
        holes: List<List<Vector2>>
    ): List<List<Int>> {
        // Simple triangulation - in practice, use a robust library like earcut
        val points = shapePoints.toMutableList()
        val triangles = mutableListOf<List<Int>>()

        // Add hole vertices (simplified implementation)
        for (hole in holes) {
            points.addAll(hole)
        }

        // Simple fan triangulation (works for convex shapes)
        for (i in 1 until points.size - 1) {
            triangles.add(listOf(0, i, i + 1))
        }

        return triangles
    }
}
