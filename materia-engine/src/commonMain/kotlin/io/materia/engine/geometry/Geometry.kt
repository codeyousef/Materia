package io.materia.engine.geometry

import io.materia.engine.scene.VertexBuffer

data class GeometryAttribute(
    val offset: Int,
    val components: Int,
    val type: AttributeType
)

enum class AttributeType {
    FLOAT32
}

data class GeometryLayout(
    val stride: Int,
    val attributes: Map<AttributeSemantic, GeometryAttribute>
)

enum class AttributeSemantic {
    POSITION,
    NORMAL,
    UV,
    COLOR
}

class Geometry(
    val vertexBuffer: VertexBuffer,
    val layout: GeometryLayout,
    val indexBuffer: ShortArray? = null
)
