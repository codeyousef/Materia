package io.materia.material

import io.materia.core.math.Vector3
import io.materia.texture.Texture

/**
 * MeshDistanceMaterial - Renders distance from reference point
 * Three.js r180 compatible
 *
 * Computes distance from a reference position (typically light source).
 * Used for point light shadows and distance field effects.
 * Encodes distance as RGBA values for precision.
 */
class MeshDistanceMaterial : Material() {

    override val type = "MeshDistanceMaterial"

    // Reference position for distance calculation
    var referencePosition: Vector3 = Vector3(0f, 0f, 0f)
    var nearDistance: Float = 1f
    var farDistance: Float = 1000f

    // Texture maps
    var map: Texture? = null
    var alphaMap: Texture? = null

    // Displacement
    var displacementMap: Texture? = null
    var displacementScale: Float = 1f
    var displacementBias: Float = 0f

    // Rendering properties
    var fog: Boolean = false

    override fun clone(): Material {
        return MeshDistanceMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshDistanceMaterial) {
            this.referencePosition = source.referencePosition.clone()
            this.nearDistance = source.nearDistance
            this.farDistance = source.farDistance
            this.map = source.map
            this.alphaMap = source.alphaMap
            this.displacementMap = source.displacementMap
            this.displacementScale = source.displacementScale
            this.displacementBias = source.displacementBias
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
