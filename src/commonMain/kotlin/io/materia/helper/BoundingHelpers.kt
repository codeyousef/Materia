package io.materia.helper

import io.materia.core.math.Box3
import io.materia.core.math.Color
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.LineBasicMaterial

/**
 * Bounding box helper implementations - T053
 * Visual helpers for object and box bounds
 */

/**
 * BoxHelper - Shows wireframe bounding box around an object
 */
class BoxHelper(
    var `object`: Object3D,
    color: Color = Color(0xffff00)
) : Object3D() {

    private val geometry = BufferGeometry()
    private val material = LineBasicMaterial().apply {
        this.color = color
        toneMapped = false
    }

    init {
        val positions = FloatArray(24 * 3) // 12 edges * 2 vertices * 3 components

        geometry.setAttribute("position", BufferAttribute(positions, 3))

        name = "BoxHelper"
        // type = "LineSegments" // type is val in Object3D

        update()
    }

    fun update() {
        val box = Box3()
        // Calculate bounding box from object and its children
        // This would traverse object hierarchy and compute bounds
        // For now, using object's geometry bounds if available
        val objectGeometry = (`object` as? io.materia.core.scene.Mesh)?.geometry
        if (objectGeometry != null) {
            objectGeometry.computeBoundingBox()
            val bbox = objectGeometry.boundingBox
            if (bbox != null) {
                box.copy(bbox)
            }
        }

        if (box.isEmpty()) return

        val min = box.min
        val max = box.max

        val positions = floatArrayOf(
            // Bottom edges
            min.x, min.y, min.z, max.x, min.y, min.z,
            max.x, min.y, min.z, max.x, min.y, max.z,
            max.x, min.y, max.z, min.x, min.y, max.z,
            min.x, min.y, max.z, min.x, min.y, min.z,

            // Top edges
            min.x, max.y, min.z, max.x, max.y, min.z,
            max.x, max.y, min.z, max.x, max.y, max.z,
            max.x, max.y, max.z, min.x, max.y, max.z,
            min.x, max.y, max.z, min.x, max.y, min.z,

            // Vertical edges
            min.x, min.y, min.z, min.x, max.y, min.z,
            max.x, min.y, min.z, max.x, max.y, min.z,
            max.x, min.y, max.z, max.x, max.y, max.z,
            min.x, min.y, max.z, min.x, max.y, max.z
        )

        // Update positions by replacing the entire attribute
        this.geometry.setAttribute("position", BufferAttribute(positions, 3))

        this.geometry.computeBoundingSphere()
    }

    fun setFromObject(`object`: Object3D) {
        this.`object` = `object`
        update()
    }

    fun dispose() {
        geometry.dispose()
    }
}

/**
 * Box3Helper - Shows wireframe box from Box3 bounds
 */
class Box3Helper(
    var box: Box3,
    color: Color = Color(0xffff00)
) : Object3D() {

    private val geometry = BufferGeometry()
    private val material = LineBasicMaterial().apply {
        this.color = color
        toneMapped = false
    }

    init {
        val positions = FloatArray(24 * 3)
        geometry.setAttribute("position", BufferAttribute(positions, 3))

        name = "Box3Helper"
        // type = "LineSegments" // type is val in Object3D

        update()
    }

    fun update() {
        if (box.isEmpty()) return

        val min = box.min
        val max = box.max

        val positions = floatArrayOf(
            // Bottom edges
            min.x, min.y, min.z, max.x, min.y, min.z,
            max.x, min.y, min.z, max.x, min.y, max.z,
            max.x, min.y, max.z, min.x, min.y, max.z,
            min.x, min.y, max.z, min.x, min.y, min.z,

            // Top edges
            min.x, max.y, min.z, max.x, max.y, min.z,
            max.x, max.y, min.z, max.x, max.y, max.z,
            max.x, max.y, max.z, min.x, max.y, max.z,
            min.x, max.y, max.z, min.x, max.y, min.z,

            // Vertical edges
            min.x, min.y, min.z, min.x, max.y, min.z,
            max.x, min.y, min.z, max.x, max.y, min.z,
            max.x, min.y, max.z, max.x, max.y, max.z,
            min.x, min.y, max.z, min.x, max.y, max.z
        )

        // Update positions by replacing the entire attribute
        geometry.setAttribute("position", BufferAttribute(positions, 3))

        geometry.computeBoundingSphere()
    }

    fun setFromBox(box: Box3) {
        this.box.copy(box)
        update()
    }

    fun dispose() {
        geometry.dispose()
    }
}