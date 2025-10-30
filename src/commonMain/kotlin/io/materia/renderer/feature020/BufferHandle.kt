package io.materia.renderer.feature020

/**
 * Platform-agnostic GPU buffer handle.
 * Feature 020 - Production-Ready Renderer
 *
 * JVM: Wraps VkBuffer (Long)
 * JS: Wraps GPUBuffer (dynamic)
 */
data class BufferHandle(
    val handle: Any?,
    val size: Int,
    val usage: BufferUsage
) {
    fun isValid(): Boolean = handle != null && size > 0
}

/**
 * Buffer usage flags.
 */
enum class BufferUsage {
    VERTEX,   // Vertex buffer (position + attributes)
    INDEX,    // Index buffer (triangle indices)
    UNIFORM   // Uniform buffer (shader constants)
}
