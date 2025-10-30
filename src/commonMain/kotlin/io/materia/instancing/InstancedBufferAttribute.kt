package io.materia.instancing

import io.materia.geometry.BufferAttribute

/**
 * Instanced buffer attribute for per-instance data
 * Extends BufferAttribute with mesh-per-instance divisor support
 */
class InstancedBufferAttribute(
    array: FloatArray,
    itemSize: Int,
    normalized: Boolean = false,
    var meshPerAttribute: Int = 1
) : BufferAttribute(array, itemSize, normalized) {

    data class UpdateRange(
        var offset: Int = 0,
        var count: Int = -1
    )

    override var updateRange: IntRange
        get() = if (_updateRange.count == -1) {
            IntRange.EMPTY
        } else {
            _updateRange.offset until (_updateRange.offset + _updateRange.count)
        }
        set(value) {
            _updateRange = if (value.isEmpty()) {
                UpdateRange(0, -1)
            } else {
                UpdateRange(value.first, value.last - value.first + 1)
            }
        }

    private var _updateRange = UpdateRange()

    // Override setters to track update range
    override fun setX(index: Int, value: Float) {
        super.setX(index, value)
        trackUpdate(index)
    }

    override fun setY(index: Int, value: Float) {
        super.setY(index, value)
        trackUpdate(index)
    }

    override fun setZ(index: Int, value: Float) {
        super.setZ(index, value)
        trackUpdate(index)
    }

    override fun setW(index: Int, value: Float) {
        super.setW(index, value)
        trackUpdate(index)
    }

    override fun setXY(index: Int, x: Float, y: Float) {
        super.setXY(index, x, y)
        trackUpdate(index)
    }

    override fun setXYZ(index: Int, x: Float, y: Float, z: Float) {
        super.setXYZ(index, x, y, z)
        trackUpdate(index)
    }

    override fun setXYZW(index: Int, x: Float, y: Float, z: Float, w: Float) {
        super.setXYZW(index, x, y, z, w)
        trackUpdate(index)
    }

    private fun trackUpdate(index: Int) {
        needsUpdate = true
        if (_updateRange.count == -1) {
            // First update
            _updateRange = UpdateRange(index, 1)
        } else {
            // Expand range to include new index
            val newStart = minOf(_updateRange.offset, index)
            val newEnd = maxOf(_updateRange.offset + _updateRange.count - 1, index)
            _updateRange = UpdateRange(newStart, newEnd - newStart + 1)
        }
    }

    override fun clone(): InstancedBufferAttribute {
        return InstancedBufferAttribute(
            array.copyOf(),
            itemSize,
            normalized,
            meshPerAttribute
        ).apply {
            needsUpdate = this@InstancedBufferAttribute.needsUpdate
            _updateRange = this@InstancedBufferAttribute._updateRange.copy()
        }
    }
}
