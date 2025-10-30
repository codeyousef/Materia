package io.materia.material

import io.materia.texture.Texture

/**
 * MeshDepthMaterial - Renders depth values as grayscale
 * Three.js r180 compatible
 *
 * Encodes depth information for post-processing effects.
 * Supports multiple depth packing formats.
 * Used for depth-of-field, SSAO, and shadow mapping.
 */
class MeshDepthMaterial : Material() {

    override val type = "MeshDepthMaterial"

    // Depth encoding
    var depthPacking: DepthPacking = DepthPacking.BasicDepthPacking

    // Texture maps
    var map: Texture? = null
    var alphaMap: Texture? = null

    // Displacement
    var displacementMap: Texture? = null
    var displacementScale: Float = 1f
    var displacementBias: Float = 0f

    // Rendering properties
    var wireframe: Boolean = false
    var wireframeLinewidth: Float = 1f
    var fog: Boolean = false

    override fun clone(): Material {
        return MeshDepthMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshDepthMaterial) {
            this.depthPacking = source.depthPacking
            this.map = source.map
            this.alphaMap = source.alphaMap
            this.displacementMap = source.displacementMap
            this.displacementScale = source.displacementScale
            this.displacementBias = source.displacementBias
            this.wireframe = source.wireframe
            this.wireframeLinewidth = source.wireframeLinewidth
            this.fog = source.fog
        }
        return this
    }

    override fun dispose() {
        super.dispose()
        map = null
        alphaMap = null
        displacementMap = null
    }
}
