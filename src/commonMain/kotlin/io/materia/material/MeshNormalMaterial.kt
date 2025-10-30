package io.materia.material

import io.materia.texture.Texture

/**
 * MeshNormalMaterial - Visualizes surface normals as RGB colors
 * Three.js r180 compatible
 *
 * Maps normal vector components (x, y, z) to RGB color channels.
 * Useful for debugging geometry normals and lighting issues.
 * X→Red, Y→Green, Z→Blue
 */
class MeshNormalMaterial : Material() {

    override val type = "MeshNormalMaterial"

    // Normal mapping
    var bumpMap: Texture? = null
    var bumpScale: Float = 1f
    var normalMap: Texture? = null
    var normalMapType: NormalMapType = NormalMapType.TangentSpaceNormalMap
    var normalScale: io.materia.core.math.Vector2 = io.materia.core.math.Vector2(1f, 1f)

    // Displacement
    var displacementMap: Texture? = null
    var displacementScale: Float = 1f
    var displacementBias: Float = 0f

    // Rendering properties
    var wireframe: Boolean = false
    var wireframeLinewidth: Float = 1f
    var flatShading: Boolean = false
    var fog: Boolean = false

    override fun clone(): Material {
        return MeshNormalMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshNormalMaterial) {
            this.bumpMap = source.bumpMap
            this.bumpScale = source.bumpScale
            this.normalMap = source.normalMap
            this.normalMapType = source.normalMapType
            this.normalScale = source.normalScale.clone()
            this.displacementMap = source.displacementMap
            this.displacementScale = source.displacementScale
            this.displacementBias = source.displacementBias
            this.wireframe = source.wireframe
            this.wireframeLinewidth = source.wireframeLinewidth
            this.flatShading = source.flatShading
            this.fog = source.fog
        }
        return this
    }

    override fun dispose() {
        super.dispose()
        bumpMap = null
        normalMap = null
        displacementMap = null
    }
}
