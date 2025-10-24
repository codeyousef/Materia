package io.kreekt.engine.scene

import io.kreekt.engine.material.UnlitPointsMaterial
import io.kreekt.engine.math.Color

class InstancedPoints(
    name: String,
    val instanceData: FloatArray,
    val componentsPerInstance: Int,
    var material: UnlitPointsMaterial
) : Node(name) {

    fun instanceCount(): Int = instanceData.size / componentsPerInstance

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
        const val COMPONENTS_PER_INSTANCE = 11

        fun create(
            name: String,
            positions: FloatArray,
            colors: FloatArray? = null,
            sizes: FloatArray? = null,
            extras: FloatArray? = null,
            material: UnlitPointsMaterial = UnlitPointsMaterial(label = name, baseColor = Color.White)
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

data class Vec3Components(val x: Float, val y: Float, val z: Float)
data class Vec4Components(val x: Float, val y: Float, val z: Float, val w: Float)
