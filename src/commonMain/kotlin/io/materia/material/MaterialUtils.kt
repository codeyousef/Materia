package io.materia.material

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.texture.Texture

/**
 * Utility functions for material operations
 * Reduces code duplication in copy() and dispose() methods
 */

/**
 * Copy texture properties from one material to another
 */
inline fun <reified T : TexturedMaterial> T.copyTextureProperties(source: T): T {
    this.map = source.map
    this.alphaMap = source.alphaMap
    return this
}

/**
 * Copy normal map properties from one material to another
 */
inline fun <reified T : NormalMappedMaterial> T.copyNormalMapProperties(source: T): T {
    this.normalMap = source.normalMap
    this.normalMapType = source.normalMapType
    this.normalScale = source.normalScale.clone()
    return this
}

/**
 * Copy bump map properties from one material to another
 */
inline fun <reified T : BumpMappedMaterial> T.copyBumpMapProperties(source: T): T {
    this.bumpMap = source.bumpMap
    this.bumpScale = source.bumpScale
    return this
}

/**
 * Copy displacement map properties from one material to another
 */
inline fun <reified T : DisplacementMappedMaterial> T.copyDisplacementMapProperties(source: T): T {
    this.displacementMap = source.displacementMap
    this.displacementScale = source.displacementScale
    this.displacementBias = source.displacementBias
    return this
}

/**
 * Copy AO map properties from one material to another
 */
inline fun <reified T : AOMapMaterial> T.copyAOMapProperties(source: T): T {
    this.aoMap = source.aoMap
    this.aoMapIntensity = source.aoMapIntensity
    return this
}

/**
 * Copy light map properties from one material to another
 */
inline fun <reified T : LightMappedMaterial> T.copyLightMapProperties(source: T): T {
    this.lightMap = source.lightMap
    this.lightMapIntensity = source.lightMapIntensity
    return this
}

/**
 * Copy emissive properties from one material to another
 */
inline fun <reified T : EmissiveMaterial> T.copyEmissiveProperties(source: T): T {
    this.emissive = source.emissive.clone()
    this.emissiveIntensity = source.emissiveIntensity
    this.emissiveMap = source.emissiveMap
    return this
}

/**
 * Copy environment mapping properties from one material to another
 */
inline fun <reified T : EnvironmentMappedMaterial> T.copyEnvironmentMapProperties(source: T): T {
    this.envMap = source.envMap
    this.combine = source.combine
    this.reflectivity = source.reflectivity
    this.refractionRatio = source.refractionRatio
    return this
}

/**
 * Copy wireframe properties from one material to another
 */
inline fun <reified T : WireframeMaterial> T.copyWireframeProperties(source: T): T {
    this.wireframe = source.wireframe
    this.wireframeLinewidth = source.wireframeLinewidth
    this.wireframeLinecap = source.wireframeLinecap
    this.wireframeLinejoin = source.wireframeLinejoin
    return this
}

/**
 * Copy flat shading property from one material to another
 */
inline fun <reified T : FlatShadingMaterial> T.copyFlatShadingProperty(source: T): T {
    this.flatShading = source.flatShading
    return this
}

/**
 * Copy fog property from one material to another
 */
inline fun <reified T : FogMaterial> T.copyFogProperty(source: T): T {
    this.fog = source.fog
    return this
}

/**
 * Copy specular properties from one material to another
 */
inline fun <reified T : SpecularMaterial> T.copySpecularProperties(source: T): T {
    this.specular = source.specular.clone()
    this.shininess = source.shininess
    this.specularMap = source.specularMap
    return this
}

/**
 * Dispose texture resources
 */
fun TexturedMaterial.disposeTextures() {
    map = null
    alphaMap = null
}

/**
 * Dispose normal map resources
 */
fun NormalMappedMaterial.disposeNormalMap() {
    normalMap = null
}

/**
 * Dispose bump map resources
 */
fun BumpMappedMaterial.disposeBumpMap() {
    bumpMap = null
}

/**
 * Dispose displacement map resources
 */
fun DisplacementMappedMaterial.disposeDisplacementMap() {
    displacementMap = null
}

/**
 * Dispose AO map resources
 */
fun AOMapMaterial.disposeAOMap() {
    aoMap = null
}

/**
 * Dispose light map resources
 */
fun LightMappedMaterial.disposeLightMap() {
    lightMap = null
}

/**
 * Dispose emissive map resources
 */
fun EmissiveMaterial.disposeEmissiveMap() {
    emissiveMap = null
}

/**
 * Dispose environment map resources
 */
fun EnvironmentMappedMaterial.disposeEnvironmentMap() {
    envMap = null
}

/**
 * Dispose specular map resources
 */
fun SpecularMaterial.disposeSpecularMap() {
    specularMap = null
}

/**
 * Dispose all standard material textures
 */
fun StandardMaterialProperties.disposeStandardTextures() {
    disposeTextures()
    disposeNormalMap()
    disposeBumpMap()
    disposeDisplacementMap()
    disposeAOMap()
    disposeEmissiveMap()
}

/**
 * Dispose all phong material textures
 */
fun PhongMaterialProperties.disposePhongTextures() {
    disposeStandardTextures()
    disposeLightMap()
    disposeEnvironmentMap()
    disposeSpecularMap()
}
