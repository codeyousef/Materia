package io.materia.geometry

import io.materia.instancing.InstancedBufferAttribute

/**
 * Geometry tailored for billboarded point clouds driven by per-instance attributes.
 * Each instance provides position (vec3), color (vec3), size (float) and an extra vec4
 * channel that examples can repurpose for animation parameters.
 */
class InstancedPointsGeometry(
    private val count: Int
) : BufferGeometry() {

    val positions = InstancedBufferAttribute(FloatArray(count * 3) { 0f }, 3)
    val colors = InstancedBufferAttribute(FloatArray(count * 3) { 1f }, 3)
    val sizes = InstancedBufferAttribute(FloatArray(count) { 1f }, 1)
    val extra = InstancedBufferAttribute(FloatArray(count * 4) { 0f }, 4)

    init {
        require(count > 0) { "InstancedPointsGeometry requires a positive instance count" }

        setAttribute("position", BufferAttribute(floatArrayOf(0f, 0f, 0f), 3))
        setInstancedAttribute(POSITION_ATTRIBUTE, positions)
        setInstancedAttribute(COLOR_ATTRIBUTE, colors)
        setInstancedAttribute(SIZE_ATTRIBUTE, sizes)
        setInstancedAttribute(EXTRA_ATTRIBUTE, extra)
        this.instanceCount = count
    }

    fun updatePosition(index: Int, x: Float, y: Float, z: Float) {
        positions.setXYZ(index, x, y, z)
    }

    fun updateColor(index: Int, r: Float, g: Float, b: Float) {
        colors.setXYZ(index, r, g, b)
    }

    fun updateSize(index: Int, size: Float) {
        sizes.setX(index, size)
    }

    fun updateExtra(index: Int, x: Float, y: Float, z: Float, w: Float) {
        extra.setXYZW(index, x, y, z, w)
    }

    companion object {
        const val POSITION_ATTRIBUTE = "instancePosition"
        const val COLOR_ATTRIBUTE = "instanceColor"
        const val SIZE_ATTRIBUTE = "instanceSize"
        const val EXTRA_ATTRIBUTE = "instanceExtra"
    }
}
