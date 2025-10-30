package io.materia.instancing

import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferGeometry
import io.materia.material.Material

/**
 * Instanced mesh for rendering many instances of the same geometry
 * with different transformations in a single draw call
 */
class InstancedMesh(
    val geometry: BufferGeometry,
    val material: Material,
    val count: Int
) : Object3D() {

    private val instanceMatrices = FloatArray(count * 16)
    private val instanceColors: FloatArray? = null

    var instanceMatrix: InstancedBufferAttribute =
        InstancedBufferAttribute(instanceMatrices, 16, false)
    var instanceColor: InstancedBufferAttribute? = null

    init {
        // Initialize all instance matrices to identity
        for (i in 0 until count) {
            val offset = i * 16
            // Set identity matrix
            instanceMatrices[offset + 0] = 1f
            instanceMatrices[offset + 5] = 1f
            instanceMatrices[offset + 10] = 1f
            instanceMatrices[offset + 15] = 1f
        }
    }

    /**
     * Set the transformation matrix for a specific instance
     */
    fun setMatrixAt(index: Int, matrix: Matrix4) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Instance index $index out of bounds [0, $count)")
        }

        val offset = index * 16
        matrix.toArray(instanceMatrices, offset)
        instanceMatrix.needsUpdate = true
    }

    /**
     * Get the transformation matrix for a specific instance
     */
    fun getMatrixAt(index: Int, matrix: Matrix4) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Instance index $index out of bounds [0, $count)")
        }

        val offset = index * 16
        matrix.fromArray(instanceMatrices, offset)
    }

    /**
     * Set the color for a specific instance
     */
    fun setColorAt(index: Int, color: Color) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Instance index $index out of bounds [0, $count)")
        }

        if (instanceColor == null) {
            instanceColor = InstancedBufferAttribute(FloatArray(count * 3), 3, false)
        }

        val offset = index * 3
        val ic = instanceColor ?: return
        ic.array[offset] = color.r
        ic.array[offset + 1] = color.g
        ic.array[offset + 2] = color.b
        ic.needsUpdate = true
    }

    /**
     * Get the color for a specific instance
     */
    fun getColorAt(index: Int, color: Color) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Instance index $index out of bounds [0, $count)")
        }

        if (instanceColor == null) {
            color.set(1f, 1f, 1f)
            return
        }

        val offset = index * 3
        val ic = instanceColor ?: return
        color.r = ic.array[offset]
        color.g = ic.array[offset + 1]
        color.b = ic.array[offset + 2]
    }

    /**
     * Dispose of this instanced mesh
     */
    fun dispose() {
        geometry.dispose()
        material.dispose()
    }
}
