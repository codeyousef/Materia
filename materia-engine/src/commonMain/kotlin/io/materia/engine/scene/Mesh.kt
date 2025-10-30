package io.materia.engine.scene

import io.materia.engine.geometry.Geometry
import io.materia.engine.geometry.InterleavedGeometrySource
import io.materia.engine.geometry.buildInterleavedGeometry
import io.materia.engine.material.Material
import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.math.Color

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
