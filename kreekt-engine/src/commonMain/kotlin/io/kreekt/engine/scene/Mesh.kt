package io.kreekt.engine.scene

import io.kreekt.engine.geometry.Geometry
import io.kreekt.engine.material.Material

data class VertexBuffer(
    val data: FloatArray,
    val strideBytes: Int
)

data class IndexBuffer(
    val data: ShortArray
)

open class Mesh(
    name: String,
    val geometry: Geometry,
    val material: Material
) : Node(name)
