package io.materia.helper

import io.materia.core.math.Vector3
import io.materia.core.math.Color
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute
import io.materia.material.LineBasicMaterial

/**
 * Debug helper implementations - T052
 * Visual debugging helpers for axes and grids
 */

/**
 * AxesHelper - Displays colored axes (X=red, Y=green, Z=blue)
 */
class AxesHelper(size: Float = 1f) : Object3D() {
    init {
        val geometry = BufferGeometry()

        val vertices = floatArrayOf(
            0f, 0f, 0f, size, 0f, 0f,    // X axis
            0f, 0f, 0f, 0f, size, 0f,    // Y axis
            0f, 0f, 0f, 0f, 0f, size     // Z axis
        )

        val colors = floatArrayOf(
            1f, 0f, 0f, 1f, 0f, 0f,    // Red for X
            0f, 1f, 0f, 0f, 1f, 0f,    // Green for Y
            0f, 0f, 1f, 0f, 0f, 1f     // Blue for Z
        )

        geometry.setAttribute("position", BufferAttribute(vertices, 3))
        geometry.setAttribute("color", BufferAttribute(colors, 3))

        val material = LineBasicMaterial()
        material.vertexColors = true

        name = "AxesHelper"
        // type = "LineSegments" // type is val in Object3D
    }

    fun dispose() {
        // Cleanup resources
    }
}

/**
 * GridHelper - Displays a grid on the XZ plane
 */
class GridHelper(
    size: Float = 10f,
    divisions: Int = 10,
    colorCenterLine: Color = Color(0x444444),
    colorGrid: Color = Color(0x888888)
) : Object3D() {

    init {
        val center = divisions / 2
        val step = size / divisions
        val halfSize = size / 2

        val vertices = mutableListOf<Float>()
        val colors = mutableListOf<Float>()

        // Generate grid lines
        for (i in 0..divisions) {
            val pos = -halfSize + i * step

            // Lines parallel to X axis
            vertices.addAll(listOf(-halfSize, 0f, pos, halfSize, 0f, pos))

            // Lines parallel to Z axis
            vertices.addAll(listOf(pos, 0f, -halfSize, pos, 0f, halfSize))

            // Alternate colors for center lines
            val color = if (i == center) colorCenterLine else colorGrid

            // Add colors for both lines
            colors.addAll(listOf(color.r, color.g, color.b, color.r, color.g, color.b))
            colors.addAll(listOf(color.r, color.g, color.b, color.r, color.g, color.b))
        }

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(vertices.toFloatArray(), 3))
        geometry.setAttribute("color", BufferAttribute(colors.toFloatArray(), 3))

        val material = LineBasicMaterial()
        material.vertexColors = true
        material.toneMapped = false

        name = "GridHelper"
        // type = "LineSegments" // type is val in Object3D
    }

    fun dispose() {
        // Cleanup resources
    }
}