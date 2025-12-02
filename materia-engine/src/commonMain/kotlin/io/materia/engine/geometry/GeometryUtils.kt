package io.materia.engine.geometry

import io.materia.engine.scene.VertexBuffer

/**
 * Returns the number of vertices based on buffer size and stride.
 *
 * @return The vertex count, or 0 if stride is invalid.
 */
fun Geometry.vertexCount(): Int {
    val strideFloats = (layout.stride / Float.SIZE_BYTES).takeIf { it > 0 }
        ?: return 0
    return vertexBuffer.data.size / strideFloats
}

/**
 * Source data for building interleaved geometry from separate attribute arrays.
 *
 * @property positions Flat XYZ position array (required, size must be multiple of 3).
 * @property normals Optional flat XYZ normal array.
 * @property uvs Optional flat UV coordinate array.
 * @property colors Optional flat RGB color array.
 * @property indices Optional index array for indexed drawing.
 */
data class InterleavedGeometrySource(
    val positions: FloatArray,
    val normals: FloatArray? = null,
    val uvs: FloatArray? = null,
    val colors: FloatArray? = null,
    val indices: ShortArray? = null
)

/**
 * Builds a [Geometry] from separate attribute arrays by interleaving them.
 *
 * Produces a tightly packed vertex buffer with attributes in order:
 * position, normal (if provided), uv (if provided), color (if provided).
 *
 * @param source The source attribute arrays.
 * @return A geometry with interleaved vertex data.
 * @throws IllegalArgumentException If array sizes are inconsistent.
 */
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
