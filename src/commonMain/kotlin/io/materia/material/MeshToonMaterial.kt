package io.materia.material

import io.materia.core.math.Color
import io.materia.texture.Texture

/**
 * MeshToonMaterial - Cel/cartoon shading material
 * Three.js r180 compatible
 *
 * Creates non-photorealistic cartoon/cel shading effect.
 * Uses gradient ramp textures for stepped lighting transitions.
 * Popular for anime-style and stylized rendering.
 */
class MeshToonMaterial : Material() {

    override val type = "MeshToonMaterial"

    // Color properties
    var color: Color = Color(1f, 1f, 1f)
    var emissive: Color = Color(0f, 0f, 0f)
    var emissiveIntensity: Float = 1f

    // Texture maps
    var map: Texture? = null
    var gradientMap: Texture? = null  // Unique to toon material - defines shading steps
    var lightMap: Texture? = null
    var lightMapIntensity: Float = 1f
    var aoMap: Texture? = null
    var aoMapIntensity: Float = 1f
    var emissiveMap: Texture? = null
    var bumpMap: Texture? = null
    var bumpScale: Float = 1f
    var normalMap: Texture? = null
    var normalMapType: NormalMapType = NormalMapType.TangentSpaceNormalMap
    var normalScale: io.materia.core.math.Vector2 = io.materia.core.math.Vector2(1f, 1f)
    var displacementMap: Texture? = null
    var displacementScale: Float = 1f
    var displacementBias: Float = 0f
    var alphaMap: Texture? = null

    // Rendering properties
    var wireframe: Boolean = false
    var wireframeLinewidth: Float = 1f
    var wireframeLinecap: String = "round"
    var wireframeLinejoin: String = "round"
    var fog: Boolean = true

    override fun clone(): Material {
        return MeshToonMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshToonMaterial) {
            this.color = source.color.clone()
            this.emissive = source.emissive.clone()
            this.emissiveIntensity = source.emissiveIntensity
            this.map = source.map
            this.gradientMap = source.gradientMap
            this.lightMap = source.lightMap
            this.lightMapIntensity = source.lightMapIntensity
            this.aoMap = source.aoMap
            this.aoMapIntensity = source.aoMapIntensity
            this.emissiveMap = source.emissiveMap
            this.bumpMap = source.bumpMap
            this.bumpScale = source.bumpScale
            this.normalMap = source.normalMap
            this.normalMapType = source.normalMapType
            this.normalScale = source.normalScale.clone()
            this.displacementMap = source.displacementMap
            this.displacementScale = source.displacementScale
            this.displacementBias = source.displacementBias
            this.alphaMap = source.alphaMap
            this.wireframe = source.wireframe
            this.wireframeLinewidth = source.wireframeLinewidth
            this.wireframeLinecap = source.wireframeLinecap
            this.wireframeLinejoin = source.wireframeLinejoin
            this.fog = source.fog
        }
        return this
    }

    override fun dispose() {
        super.dispose()
        map = null
        gradientMap = null
        lightMap = null
        aoMap = null
        emissiveMap = null
        bumpMap = null
        normalMap = null
        displacementMap = null
        alphaMap = null
    }
}
