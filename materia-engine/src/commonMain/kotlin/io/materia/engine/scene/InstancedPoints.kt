package io.materia.engine.scene

import io.materia.engine.material.UnlitPointsMaterial
import io.materia.engine.math.Color

/**
 * Efficiently renders many points using GPU instancing.
 *
 * Each instance stores position (3), color (3), size (1), and extra data (4)
 * for a total of 11 floats per point. The [instanceData] array is laid out
 * contiguously and can be updated at runtime via [updateInstance].
 *
 * This node is ideal for particle systems, point clouds, and data visualizations
 * where thousands of points need to be rendered efficiently.
 *
 * @param name Identifier for this node.
 * @param instanceData Flat array containing all instance data.
 * @param componentsPerInstance Number of floats per instance (must be 11).
 * @param material Material controlling point appearance.
 */
class InstancedPoints(
    name: String,
    val instanceData: FloatArray,
    val componentsPerInstance: Int,
    var material: UnlitPointsMaterial
) : Node(name) {

    /**
     * Returns the number of point instances.
     */
    fun instanceCount(): Int = instanceData.size / componentsPerInstance

    /**
     * Updates a single instance's attributes in the data array.
     *
     * @param index Zero-based instance index.
     * @param position World-space position (x, y, z).
     * @param color RGB color components.
     * @param size Point size in world units.
     * @param extra Four extra floats for custom data.
     * @throws IllegalArgumentException If index is out of bounds.
     */
    fun updateInstance(
        index: Int,
        position: Vec3Components,
        color: Vec3Components,
        size: Float,
        extra: Vec4Components
    ) {
        require(index in 0 until instanceCount()) { "Index $index out of bounds" }
        val base = index * componentsPerInstance
        instanceData[base] = position.x
        instanceData[base + 1] = position.y
        instanceData[base + 2] = position.z

        instanceData[base + 3] = color.x
        instanceData[base + 4] = color.y
        instanceData[base + 5] = color.z

        instanceData[base + 6] = size

        instanceData[base + 7] = extra.x
        instanceData[base + 8] = extra.y
        instanceData[base + 9] = extra.z
        instanceData[base + 10] = extra.w
    }

    fun updateMaterial(newMaterial: UnlitPointsMaterial) {
        material = newMaterial
    }

    companion object {
        /** Number of floats per instance: position(3) + color(3) + size(1) + extra(4). */
        const val COMPONENTS_PER_INSTANCE = 11

        /**
         * Creates an InstancedPoints node from separate attribute arrays.
         *
         * @param name Identifier for the node.
         * @param positions Flat XYZ position array (size must be multiple of 3).
         * @param colors Optional RGB color array, defaults to white.
         * @param sizes Optional per-point sizes, defaults to 1.
         * @param extras Optional XYZW extra data per point.
         * @param material Material controlling appearance.
         * @return A new InstancedPoints node.
         */
        fun create(
            name: String,
            positions: FloatArray,
            colors: FloatArray? = null,
            sizes: FloatArray? = null,
            extras: FloatArray? = null,
            material: UnlitPointsMaterial = UnlitPointsMaterial(
                label = name,
                baseColor = Color.White
            )
        ): InstancedPoints {
            require(positions.size % 3 == 0) { "Positions must be multiple of 3" }
            val count = positions.size / 3
            val instanceData = FloatArray(count * COMPONENTS_PER_INSTANCE)

            fun fetch(array: FloatArray?, index: Int, default: Float): Float =
                array?.getOrNull(index) ?: default

            repeat(count) { idx ->
                val base = idx * COMPONENTS_PER_INSTANCE
                val posBase = idx * 3
                instanceData[base] = positions[posBase]
                instanceData[base + 1] = positions[posBase + 1]
                instanceData[base + 2] = positions[posBase + 2]

                instanceData[base + 3] = fetch(colors, posBase, 1f)
                instanceData[base + 4] = fetch(colors, posBase + 1, 1f)
                instanceData[base + 5] = fetch(colors, posBase + 2, 1f)

                instanceData[base + 6] = fetch(sizes, idx, 1f)

                val extraBase = idx * 4
                instanceData[base + 7] = fetch(extras, extraBase, 0f)
                instanceData[base + 8] = fetch(extras, extraBase + 1, 0f)
                instanceData[base + 9] = fetch(extras, extraBase + 2, 0f)
                instanceData[base + 10] = fetch(extras, extraBase + 3, 1f)
            }

            return InstancedPoints(name, instanceData, COMPONENTS_PER_INSTANCE, material)
        }
    }
}

/** Simple 3-component vector for instance data updates. */
data class Vec3Components(val x: Float, val y: Float, val z: Float)

/** Simple 4-component vector for instance data updates. */
data class Vec4Components(val x: Float, val y: Float, val z: Float, val w: Float)
