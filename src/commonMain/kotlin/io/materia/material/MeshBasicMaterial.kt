package io.materia.material

import io.materia.core.math.Color
import io.materia.texture.Texture

/**
 * Basic mesh material for simple rendering
 * Three.js r180 compatible MeshBasicMaterial
 *
 * Unlit material without lighting calculations.
 * Fastest rendering but no realistic shading.
 * Suitable for UI elements, overlays, and stylized rendering.
 */
class MeshBasicMaterial : Material() {

    override val type: String = "MeshBasicMaterial"

    // Color properties
    var color: Color = Color.WHITE

    // Texture maps
    var map: Texture? = null
    var lightMap: Texture? = null
    var lightMapIntensity: Float = 1f
    var aoMap: Texture? = null
    var aoMapIntensity: Float = 1f
    var specularMap: Texture? = null
    var alphaMap: Texture? = null

    // Environment mapping
    var envMap: Texture? = null
    var combine: Combine = Combine.MultiplyOperation
    var reflectivity: Float = 1f
    var refractionRatio: Float = 0.98f

    // Wireframe
    var wireframe: Boolean = false
    var wireframeLinewidth: Float = 1f
    var wireframeLinecap: String = "round"
    var wireframeLinejoin: String = "round"

    // Fog
    var fog: Boolean = true

    override fun clone(): Material {
        return MeshBasicMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is MeshBasicMaterial) {
            this.color = source.color.clone()
            this.map = source.map
            this.lightMap = source.lightMap
            this.lightMapIntensity = source.lightMapIntensity
            this.aoMap = source.aoMap
            this.aoMapIntensity = source.aoMapIntensity
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
            this.fog = source.fog
        }
        return this
    }

    override fun dispose() {
        super.dispose()
        map = null
        lightMap = null
        aoMap = null
        specularMap = null
        alphaMap = null
        envMap = null
    }
}

