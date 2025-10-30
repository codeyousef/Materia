package io.materia.material

import io.materia.core.math.Color
import io.materia.texture.Texture

/**
 * MeshLambertMaterial - Diffuse-only shading material
 * Three.js r180 compatible
 *
 * Uses Lambertian reflectance for diffuse lighting calculations.
 * More performant than Phong/Standard materials but no specular highlights.
 * Ideal for mobile platforms and low-spec hardware.
 */
class MeshLambertMaterial : Material() {

    override val type = "MeshLambertMaterial"

    // Color properties
    var color: Color = Color(1f, 1f, 1f)
    var emissive: Color = Color(0f, 0f, 0f)
    var emissiveIntensity: Float = 1f

    // Texture maps
    var map: Texture? = null
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
    var specularMap: Texture? = null
    var alphaMap: Texture? = null

    // Environment mapping
    var envMap: Texture? = null
    var combine: Combine = Combine.MultiplyOperation
    var reflectivity: Float = 1f
    var refractionRatio: Float = 0.98f

    // Rendering properties
    var wireframe: Boolean = false
    var wireframeLinewidth: Float = 1f
    var wireframeLinecap: String = "round"
    var wireframeLinejoin: String = "round"
    var flatShading: Boolean = false
    var fog: Boolean = true

    override fun clone(): Material {
        return MeshLambertMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshLambertMaterial) {
            this.color = source.color.clone()
            this.emissive = source.emissive.clone()
            this.emissiveIntensity = source.emissiveIntensity
            this.map = source.map
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
            this.specularMap = source.specularMap
            this.alphaMap = source.alphaMap
            this.envMap = source.envMap
            this.combine = source.combine
            this.reflectivity = source.reflectivity
            this.refractionRatio = source.refractionRatio
            this.wireframe = source.wireframe
            this.wireframeLinewidth = source.wireframeLinewidth
            this.wireframeLinecap = source.wireframeLinecap
            this.wireframeLinejoin = source.wireframeLinejoin
            this.flatShading = source.flatShading
            this.fog = source.fog
        }
        return this
    }

    override fun dispose() {
        super.dispose()
        // Textures are managed externally, just null references
        map = null
        lightMap = null
        aoMap = null
        emissiveMap = null
        bumpMap = null
        normalMap = null
        displacementMap = null
        specularMap = null
        alphaMap = null
        envMap = null
    }
}
