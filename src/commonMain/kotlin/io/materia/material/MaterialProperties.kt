package io.materia.material

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.texture.Texture

/**
 * Shared interfaces and mixins for material properties
 * Reduces duplication across material implementations while maintaining type safety
 */

/**
 * Materials that support basic texture mapping
 */
interface TexturedMaterial {
    var map: Texture?
    var alphaMap: Texture?
}

/**
 * Materials that support normal mapping
 */
interface NormalMappedMaterial {
    var normalMap: Texture?
    var normalMapType: NormalMapType
    var normalScale: Vector2
}

/**
 * Materials that support bump mapping
 */
interface BumpMappedMaterial {
    var bumpMap: Texture?
    var bumpScale: Float
}

/**
 * Materials that support displacement mapping
 */
interface DisplacementMappedMaterial {
    var displacementMap: Texture?
    var displacementScale: Float
    var displacementBias: Float
}

/**
 * Materials that support ambient occlusion mapping
 */
interface AOMapMaterial {
    var aoMap: Texture?
    var aoMapIntensity: Float
}

/**
 * Materials that support light mapping
 */
interface LightMappedMaterial {
    var lightMap: Texture?
    var lightMapIntensity: Float
}

/**
 * Materials that support emissive properties
 */
interface EmissiveMaterial {
    var emissive: Color
    var emissiveIntensity: Float
    var emissiveMap: Texture?
}

/**
 * Materials that support environment mapping
 */
interface EnvironmentMappedMaterial {
    var envMap: Texture?
    var combine: Combine
    var reflectivity: Float
    var refractionRatio: Float
}

/**
 * Materials that support wireframe rendering
 */
interface WireframeMaterial {
    var wireframe: Boolean
    var wireframeLinewidth: Float
    var wireframeLinecap: String
    var wireframeLinejoin: String
}

/**
 * Materials that support flat shading
 */
interface FlatShadingMaterial {
    var flatShading: Boolean
}

/**
 * Materials that support fog
 */
interface FogMaterial {
    var fog: Boolean
}

/**
 * Materials that support specular properties
 */
interface SpecularMaterial {
    var specular: Color
    var shininess: Float
    var specularMap: Texture?
}

/**
 * Combine standard material property groups
 */
interface StandardMaterialProperties :
    TexturedMaterial,
    NormalMappedMaterial,
    BumpMappedMaterial,
    DisplacementMappedMaterial,
    AOMapMaterial,
    EmissiveMaterial,
    WireframeMaterial,
    FlatShadingMaterial,
    FogMaterial

/**
 * Combine phong-style material properties
 */
interface PhongMaterialProperties :
    StandardMaterialProperties,
    LightMappedMaterial,
    EnvironmentMappedMaterial,
    SpecularMaterial
