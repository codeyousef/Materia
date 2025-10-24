package io.kreekt.engine.scene

import io.kreekt.engine.geometry.Geometry
import io.kreekt.engine.geometry.InterleavedGeometrySource
import io.kreekt.engine.geometry.buildInterleavedGeometry
import io.kreekt.engine.material.Material
import io.kreekt.engine.material.UnlitColorMaterial
import io.kreekt.engine.math.Color

data class VertexBuffer(
    val data: FloatArray,
    val strideBytes: Int
)

data class IndexBuffer(
    val data: ShortArray
)

open class Mesh(
    name: String,
    var geometry: Geometry,
    var material: Material
) : Node(name) {

    fun updateGeometry(newGeometry: Geometry) {
        geometry = newGeometry
    }

    fun updateMaterial(newMaterial: Material) {
        material = newMaterial
    }

    companion object {
        fun fromInterleaved(
            name: String,
            positions: FloatArray,
            normals: FloatArray? = null,
            uvs: FloatArray? = null,
            colors: FloatArray? = null,
            indices: ShortArray? = null,
            material: Material = UnlitColorMaterial(
                label = name,
                color = Color.White
            )
        ): Mesh {
            val geometry = buildInterleavedGeometry(
                InterleavedGeometrySource(
                    positions = positions,
                    normals = normals,
                    uvs = uvs,
                    colors = colors,
                    indices = indices
                )
            )
            return Mesh(name, geometry, material)
        }
    }
}
