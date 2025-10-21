package io.kreekt.engine.scene

data class VertexBuffer(
    val data: FloatArray,
    val stride: Int
)

data class IndexBuffer(
    val data: ShortArray
)

open class Mesh(
    name: String,
    val vertices: VertexBuffer,
    val indices: IndexBuffer? = null
) : Node(name)
