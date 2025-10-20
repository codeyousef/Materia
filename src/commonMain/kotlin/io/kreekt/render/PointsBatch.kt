package io.kreekt.render

import io.kreekt.geometry.InstancedPointsGeometry
import io.kreekt.points.Points
import io.kreekt.points.PointsMaterial

/**
 * Convenience wrapper that exposes typed helpers for instanced billboard clouds.
 */
class PointsBatch(
    val instancedGeometry: InstancedPointsGeometry,
    material: PointsMaterial
) : Points(instancedGeometry, material) {

    fun setInstancePosition(index: Int, x: Float, y: Float, z: Float) {
        instancedGeometry.updatePosition(index, x, y, z)
    }

    fun setInstanceColor(index: Int, r: Float, g: Float, b: Float) {
        instancedGeometry.updateColor(index, r, g, b)
    }

    fun setInstanceSize(index: Int, size: Float) {
        instancedGeometry.updateSize(index, size)
    }

    fun setInstanceExtra(index: Int, x: Float, y: Float, z: Float, w: Float) {
        instancedGeometry.updateExtra(index, x, y, z, w)
    }
}
