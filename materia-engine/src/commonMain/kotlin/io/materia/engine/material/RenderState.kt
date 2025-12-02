package io.materia.engine.material

/**
 * GPU render state configuration for materials.
 *
 * Controls depth testing, depth writes, face culling, and alpha blending.
 * Shared across draw calls using the same material to minimize state changes.
 *
 * @property depthTest Whether to compare fragments against the depth buffer.
 * @property depthWrite Whether to write fragment depth to the depth buffer.
 * @property cullMode Which faces to cull (or none).
 * @property blendMode Alpha blending mode for transparency effects.
 */
data class RenderState(
    val depthTest: Boolean = true,
    val depthWrite: Boolean = true,
    val cullMode: CullMode = CullMode.BACK,
    val blendMode: BlendMode = BlendMode.Opaque
)

/** Face culling modes. */
enum class CullMode {
    /** No culling - render both front and back faces. */
    NONE,
    /** Cull front-facing triangles. */
    FRONT,
    /** Cull back-facing triangles (default for closed geometry). */
    BACK
}

/** Alpha blending modes. */
enum class BlendMode {
    /** No blending - fragments fully replace the destination. */
    Opaque,
    /** Standard alpha blending for transparent surfaces. */
    Alpha,
    /** Additive blending for glow and particle effects. */
    Additive
}
