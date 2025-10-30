/**
 * Texture types for the rendering system
 */
package io.materia.renderer

/**
 * Base texture interface
 */
interface Texture {
    val id: Int
    var needsUpdate: Boolean
    val width: Int
    val height: Int
    val size: Int get() = width // For square textures

    /**
     * Dispose texture resources
     */
    fun dispose()
}

/**
 * Cube texture interface for environment mapping
 */
interface CubeTexture : Texture {
    override val size: Int
}


/**
 * 3D texture for volume rendering
 */
interface Texture3D : Texture {
    override val width: Int
    override val height: Int
    val depth: Int
}