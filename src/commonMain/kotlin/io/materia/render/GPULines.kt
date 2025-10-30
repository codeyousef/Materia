package io.materia.render

import io.materia.core.scene.LineSegments
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.LineBasicMaterial

/**
 * Thin line batcher that keeps CPU side arrays for quick updates.
 */
class GPULines(
    segmentCount: Int,
    material: LineBasicMaterial = LineBasicMaterial(vertexColors = true)
) : LineSegments(createGeometry(segmentCount), material) {

    private val positionAttribute = geometry.getAttribute(POSITION_ATTRIBUTE) as BufferAttribute
    private val colorAttribute = geometry.getAttribute(COLOR_ATTRIBUTE) as BufferAttribute

    fun setSegment(
        segmentIndex: Int,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        r: Float,
        g: Float,
        b: Float,
        alpha: Float = 1f
    ) {
        val base = segmentIndex * 2
        positionAttribute.setXYZ(base, x0, y0, z0)
        positionAttribute.setXYZ(base + 1, x1, y1, z1)
        colorAttribute.setXYZ(base, r, g, b)
        colorAttribute.setXYZ(base + 1, r * alpha, g * alpha, b * alpha)
        positionAttribute.needsUpdate = true
        colorAttribute.needsUpdate = true
    }

    fun segmentCount(): Int = positionAttribute.count / 2

    companion object {
        private const val POSITION_ATTRIBUTE = "position"
        private const val COLOR_ATTRIBUTE = "color"

        private fun createGeometry(segmentCount: Int): BufferGeometry {
            require(segmentCount > 0) { "GPULines requires at least one segment" }
            val geometry = BufferGeometry()
            val positions = BufferAttribute(FloatArray(segmentCount * 2 * 3), 3)
            val colors = BufferAttribute(FloatArray(segmentCount * 2 * 3) { 1f }, 3)
            geometry.setAttribute(POSITION_ATTRIBUTE, positions)
            geometry.setAttribute(COLOR_ATTRIBUTE, colors)
            return geometry
        }
    }
}
