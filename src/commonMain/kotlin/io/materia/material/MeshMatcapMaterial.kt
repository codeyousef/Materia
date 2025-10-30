package io.materia.material

import io.materia.core.math.Color
import io.materia.texture.Texture

/**
 * MeshMatcapMaterial - Material Capture shading
 * Three.js r180 compatible
 *
 * Uses a matcap (material capture) texture for shading without lighting.
 * Matcap texture encodes lighting and material response in view space.
 * Extremely performant - no real-time lighting calculations needed.
 * Popular for sculpting apps and stylized rendering.
 */
class MeshMatcapMaterial : Material() {

    override val type = "MeshMatcapMaterial"

    // Color and texture
    var color: Color = Color(1f, 1f, 1f)
    var matcap: Texture? = null  // The matcap texture - typically a sphere render
    var map: Texture? = null
    var alphaMap: Texture? = null

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
    var flatShading: Boolean = false
    var fog: Boolean = false

    override fun clone(): Material {
        return MeshMatcapMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshMatcapMaterial) {
            this.color = source.color.clone()
            this.matcap = source.matcap
            this.map = source.map
            this.alphaMap = source.alphaMap
            this.bumpMap = source.bumpMap
            this.bumpScale = source.bumpScale
            this.normalMap = source.normalMap
            this.normalMapType = source.normalMapType
            this.normalScale = source.normalScale.clone()
            this.displacementMap = source.displacementMap
            this.displacementScale = source.displacementScale
            this.displacementBias = source.displacementBias
            this.flatShading = source.flatShading
            this.fog = source.fog
        }
        return this
    }

    override fun dispose() {
        super.dispose()
        matcap = null
        map = null
        alphaMap = null
        bumpMap = null
        normalMap = null
        displacementMap = null
    }
}
