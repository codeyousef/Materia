package io.materia.engine.scene

import io.materia.engine.geometry.Geometry
import io.materia.engine.geometry.InterleavedGeometrySource
import io.materia.engine.geometry.buildInterleavedGeometry
import io.materia.engine.material.Material
import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.math.Color

/**
 * Container for interleaved vertex data uploaded to the GPU.
 *
 * @property data The raw float array containing vertex attributes.
 * @property strideBytes Number of bytes between consecutive vertices.
 */
data class VertexBuffer(
    val data: FloatArray,
    val strideBytes: Int
)

/**
 * Container for index data used for indexed drawing.
 *
 * @property data Array of 16-bit vertex indices.
 */
data class IndexBuffer(
    val data: ShortArray
)

/**
 * A renderable 3D object combining geometry and material.
 *
 * Mesh extends [Node] to participate in the scene graph and adds rendering
 * data via [geometry] and visual appearance via [material]. Use the [fromInterleaved]
 * factory for convenient construction from raw attribute arrays.
 *
 * @param name Identifier for this mesh.
 * @param geometry The vertex and index data defining the shape.
 * @param material The material controlling how the mesh is shaded.
 */
open class Mesh(
    name: String,
    var geometry: Geometry,
    var material: Material
) : Node(name) {

    /**
     * Replaces the geometry with new data.
     *
     * @param newGeometry The new geometry to use for rendering.
     */
    fun updateGeometry(newGeometry: Geometry) {
        geometry = newGeometry
    }

    /**
     * Replaces the material with a new one.
     *
     * @param newMaterial The new material controlling appearance.
     */
    fun updateMaterial(newMaterial: Material) {
        material = newMaterial
    }

    companion object {
        /**
         * Creates a mesh from separate attribute arrays, interleaving them automatically.
         *
         * @param name Identifier for the mesh.
         * @param positions Flat array of XYZ positions (required).
         * @param normals Optional flat array of XYZ normals.
         * @param uvs Optional flat array of UV coordinates.
         * @param colors Optional flat array of RGB vertex colors.
         * @param indices Optional index array for indexed drawing.
         * @param material Material to apply, defaults to white unlit.
         * @return A new mesh with interleaved geometry.
         */
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
