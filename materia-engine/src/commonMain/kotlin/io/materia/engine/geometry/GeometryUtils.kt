package io.materia.engine.geometry

import io.materia.engine.scene.VertexBuffer

fun Geometry.vertexCount(): Int {
    val strideFloats = (layout.stride / Float.SIZE_BYTES).takeIf { it > 0 }
        ?: return 0
    return vertexBuffer.data.size / strideFloats
}

data class InterleavedGeometrySource(
    val positions: FloatArray,
    val normals: FloatArray? = null,
    val uvs: FloatArray? = null,
    val colors: FloatArray? = null,
    val indices: ShortArray? = null
)

fun buildInterleavedGeometry(source: InterleavedGeometrySource): Geometry {
    require(source.positions.size % 3 == 0) { "Positions must be multiple of 3" }
    val vertexCount = source.positions.size / 3
    source.normals?.let { require(it.size / 3 == vertexCount) { "Normals size mismatch" } }
    source.uvs?.let { require(it.size / 2 == vertexCount) { "UV size mismatch" } }
    source.colors?.let { require(it.size / 3 == vertexCount) { "Color size mismatch" } }

    val attributes = mutableListOf<Pair<AttributeSemantic, GeometryAttribute>>()
    var offsetFloats = 0

    attributes += AttributeSemantic.POSITION to GeometryAttribute(
        offset = offsetFloats * Float.SIZE_BYTES,
        components = 3,
        type = AttributeType.FLOAT32
    )
    offsetFloats += 3

    source.normals?.let {
        attributes += AttributeSemantic.NORMAL to GeometryAttribute(
            offset = offsetFloats * Float.SIZE_BYTES,
            components = 3,
            type = AttributeType.FLOAT32
        )
        offsetFloats += 3
    }

    source.uvs?.let {
        attributes += AttributeSemantic.UV to GeometryAttribute(
            offset = offsetFloats * Float.SIZE_BYTES,
            components = 2,
            type = AttributeType.FLOAT32
        )
        offsetFloats += 2
    }

    source.colors?.let {
        attributes += AttributeSemantic.COLOR to GeometryAttribute(
            offset = offsetFloats * Float.SIZE_BYTES,
            components = 3,
            type = AttributeType.FLOAT32
        )
        offsetFloats += 3
    }

    val strideFloats = offsetFloats
    val interleaved = FloatArray(vertexCount * strideFloats)

    fun copyAttribute(sourceArray: FloatArray?, components: Int, startOffset: Int) {
        if (sourceArray == null) return
        var srcIndex = 0
        var dst = startOffset
        repeat(vertexCount) {
            repeat(components) { comp ->
                interleaved[dst + comp] = sourceArray[srcIndex++]
            }
            dst += strideFloats
        }
    }

    copyAttribute(source.positions, 3, 0)
    copyAttribute(source.normals, 3, attributes.offsetFor(AttributeSemantic.NORMAL, strideFloats))
    copyAttribute(source.uvs, 2, attributes.offsetFor(AttributeSemantic.UV, strideFloats))
    copyAttribute(source.colors, 3, attributes.offsetFor(AttributeSemantic.COLOR, strideFloats))

    val layout = GeometryLayout(
        stride = strideFloats * Float.SIZE_BYTES,
        attributes = attributes.toMap()
    )

    return Geometry(
        vertexBuffer = VertexBuffer(interleaved, layout.stride),
        layout = layout,
        indexBuffer = source.indices
    )
}

private fun List<Pair<AttributeSemantic, GeometryAttribute>>.offsetFor(
    semantic: AttributeSemantic,
    strideFloats: Int
): Int {
    val attr = firstOrNull { it.first == semantic }?.second ?: return 0
    return attr.offset / Float.SIZE_BYTES
}
