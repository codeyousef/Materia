package io.materia.engine.geometry

import io.materia.engine.scene.VertexBuffer

/**
 * Describes a single vertex attribute within a vertex buffer.
 *
 * @property offset Byte offset from the start of each vertex.
 * @property components Number of components (e.g., 3 for XYZ).
 * @property type Data type of each component.
 */
data class GeometryAttribute(
    val offset: Int,
    val components: Int,
    val type: AttributeType
)

/** Supported vertex attribute data types. */
enum class AttributeType {
    /** 32-bit floating point. */
    FLOAT32
}

/**
 * Describes the memory layout of interleaved vertex data.
 *
 * @property stride Bytes between consecutive vertices.
 * @property attributes Map of semantic to attribute descriptor.
 */
data class GeometryLayout(
    val stride: Int,
    val attributes: Map<AttributeSemantic, GeometryAttribute>
)

/** Standard vertex attribute semantics. */
enum class AttributeSemantic {
    /** Vertex position (typically vec3). */
    POSITION,
    /** Surface normal (typically vec3). */
    NORMAL,
    /** Texture coordinates (typically vec2). */
    UV,
    /** Vertex color (typically vec3 or vec4). */
    COLOR
}

/**
 * Encapsulates vertex and optional index data for GPU rendering.
 *
 * @property vertexBuffer Interleaved vertex attribute data.
 * @property layout Describes how to interpret the vertex buffer.
 * @property indexBuffer Optional 16-bit indices for indexed drawing.
 */
class Geometry(
    val vertexBuffer: VertexBuffer,
    val layout: GeometryLayout,
    val indexBuffer: ShortArray? = null
)
