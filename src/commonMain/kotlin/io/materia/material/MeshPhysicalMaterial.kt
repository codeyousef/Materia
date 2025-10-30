package io.materia.material

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.core.scene.Material
import io.materia.texture.CubeTexture
import io.materia.texture.Texture2D
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * Physical-based material with advanced features
 * T101 - Enhanced PBR material following Three.js MeshPhysicalMaterial API
 *
 * MeshPhysicalMaterial extends MeshStandardMaterial with additional
 * physically-based features like clearcoat, transmission, and sheen.
 */
class MeshPhysicalMaterial(
    // Inherit all standard material properties
    color: Color = Color.WHITE,
    metalness: Float = 0f,
    roughness: Float = 1f,
    emissive: Color = Color.BLACK,
    emissiveIntensity: Float = 1f,
    transparent: Boolean = false,
    opacity: Float = 1f,
    alphaTest: Float = 0f,
    map: Texture2D? = null,
    normalMap: Texture2D? = null,
    normalScale: Vector2 = Vector2(1f, 1f),
    metalnessMap: Texture2D? = null,
    roughnessMap: Texture2D? = null,
    aoMap: Texture2D? = null,
    aoMapIntensity: Float = 1f,
    emissiveMap: Texture2D? = null,
    bumpMap: Texture2D? = null,
    bumpScale: Float = 1f,
    displacementMap: Texture2D? = null,
    displacementScale: Float = 1f,
    displacementBias: Float = 0f,
    envMap: CubeTexture? = null,
    envMapIntensity: Float = 1f,
    wireframe: Boolean = false,
    wireframeLinewidth: Float = 1f,
    flatShading: Boolean = false,
    vertexColors: Boolean = false,
    fog: Boolean = true,
    refractionRatio: Float = 0.98f,

    // Physical material extensions
    // Clearcoat properties
    var clearcoat: Float = 0f,
    var clearcoatRoughness: Float = 0f,
    var clearcoatMap: Texture2D? = null,
    var clearcoatRoughnessMap: Texture2D? = null,
    var clearcoatNormalMap: Texture2D? = null,
    var clearcoatNormalScale: Vector2 = Vector2(1f, 1f),

    // Transmission properties (glass, water, etc.)
    var transmission: Float = 0f,
    var transmissionMap: Texture2D? = null,
    var thickness: Float = 0f,
    var thicknessMap: Texture2D? = null,
    var attenuationDistance: Float = Float.POSITIVE_INFINITY,
    var attenuationColor: Color = Color.WHITE,

    // Sheen properties (fabric, velvet, etc.)
    var sheen: Float = 0f,
    var sheenColor: Color = Color.BLACK,
    var sheenColorMap: Texture2D? = null,
    var sheenRoughness: Float = 1f,
    var sheenRoughnessMap: Texture2D? = null,

    // Iridescence properties (soap bubbles, oil films)
    var iridescence: Float = 0f,
    var iridescenceMap: Texture2D? = null,
    var iridescenceIOR: Float = 1.3f,
    var iridescenceThicknessRange: Vector2 = Vector2(100f, 400f),
    var iridescenceThicknessMap: Texture2D? = null,

    // Anisotropy properties (brushed metal, hair)
    var anisotropy: Float = 0f,
    var anisotropyRotation: Float = 0f,
    var anisotropyMap: Texture2D? = null,

    override val id: Int = generateId(),
    override var name: String = "MeshPhysicalMaterial"
) : Material {

    override var needsUpdate: Boolean = true
    override var visible: Boolean = true

    // Standard material that this extends
    private val standardMaterial = MeshStandardMaterial(
        color = color,
        metalness = metalness,
        roughness = roughness,
        emissive = emissive,
        emissiveIntensity = emissiveIntensity,
        transparent = transparent,
        opacity = opacity,
        alphaTest = alphaTest,
        map = map,
        normalMap = normalMap,
        normalScale = normalScale,
        metalnessMap = metalnessMap,
        roughnessMap = roughnessMap,
        aoMap = aoMap,
        aoMapIntensity = aoMapIntensity,
        emissiveMap = emissiveMap,
        bumpMap = bumpMap,
        bumpScale = bumpScale,
        displacementMap = displacementMap,
        displacementScale = displacementScale,
        displacementBias = displacementBias,
        envMap = envMap,
        envMapIntensity = envMapIntensity,
        wireframe = wireframe,
        wireframeLinewidth = wireframeLinewidth,
        flatShading = flatShading,
        vertexColors = vertexColors,
        fog = fog,
        refractionRatio = refractionRatio,
        name = name
    )

    // Delegate standard properties to the standard material
    var color: Color
        get() = standardMaterial.color
        set(value) {
            standardMaterial.color = value; needsUpdate = true
        }

    var metalness: Float
        get() = standardMaterial.metalness
        set(value) {
            standardMaterial.metalness = value; needsUpdate = true
        }

    var roughness: Float
        get() = standardMaterial.roughness
        set(value) {
            standardMaterial.roughness = value; needsUpdate = true
        }

    // User data for custom properties
    val userData: MutableMap<String, Any> = mutableMapOf()

    companion object {
        private val nextId: AtomicInt = atomic(1)
        private fun generateId(): Int = nextId.getAndIncrement()

        /**
         * Create a material with clearcoat preset (car paint, lacquer)
         */
        fun clearcoat(
            color: Color = Color.RED,
            metalness: Float = 0f,
            roughness: Float = 0.1f,
            clearcoat: Float = 1f,
            clearcoatRoughness: Float = 0.03f
        ): MeshPhysicalMaterial = MeshPhysicalMaterial(
            color = color,
            metalness = metalness,
            roughness = roughness,
            clearcoat = clearcoat,
            clearcoatRoughness = clearcoatRoughness
        )

        /**
         * Create a transmission material (glass, water)
         */
        fun transmission(
            color: Color = Color(0.9f, 0.9f, 1f),
            transmission: Float = 1f,
            roughness: Float = 0f,
            thickness: Float = 0.5f
        ): MeshPhysicalMaterial = MeshPhysicalMaterial(
            color = color,
            metalness = 0f,
            roughness = roughness,
            transmission = transmission,
            thickness = thickness,
            transparent = true
        )

        /**
         * Create a sheen material (fabric, velvet)
         */
        fun sheen(
            color: Color = Color(0.2f, 0.1f, 0.4f),
            sheen: Float = 1f,
            sheenColor: Color = Color.WHITE,
            sheenRoughness: Float = 0.9f
        ): MeshPhysicalMaterial = MeshPhysicalMaterial(
            color = color,
            metalness = 0f,
            roughness = 0.8f,
            sheen = sheen,
            sheenColor = sheenColor,
            sheenRoughness = sheenRoughness
        )

        /**
         * Create an iridescent material (soap bubble, oil film)
         */
        fun iridescence(
            color: Color = Color.WHITE,
            iridescence: Float = 1f,
            iridescenceIOR: Float = 1.3f,
            thicknessRange: Vector2 = Vector2(100f, 400f)
        ): MeshPhysicalMaterial = MeshPhysicalMaterial(
            color = color,
            metalness = 0f,
            roughness = 0.1f,
            iridescence = iridescence,
            iridescenceIOR = iridescenceIOR,
            iridescenceThicknessRange = thicknessRange
        )
    }

    /**
     * Copy this material
     */
    fun copy(): MeshPhysicalMaterial = MeshPhysicalMaterial(
        color = color.clone(),
        metalness = metalness,
        roughness = roughness,
        emissive = standardMaterial.emissive.clone(),
        emissiveIntensity = standardMaterial.emissiveIntensity,
        transparent = standardMaterial.transparent,
        opacity = standardMaterial.opacity,
        alphaTest = standardMaterial.alphaTest,
        map = standardMaterial.map,
        normalMap = standardMaterial.normalMap,
        normalScale = standardMaterial.normalScale.clone(),
        metalnessMap = standardMaterial.metalnessMap,
        roughnessMap = standardMaterial.roughnessMap,
        aoMap = standardMaterial.aoMap,
        aoMapIntensity = standardMaterial.aoMapIntensity,
        emissiveMap = standardMaterial.emissiveMap,
        bumpMap = standardMaterial.bumpMap,
        bumpScale = standardMaterial.bumpScale,
        displacementMap = standardMaterial.displacementMap,
        displacementScale = standardMaterial.displacementScale,
        displacementBias = standardMaterial.displacementBias,
        envMap = standardMaterial.envMap,
        envMapIntensity = standardMaterial.envMapIntensity,
        wireframe = standardMaterial.wireframe,
        wireframeLinewidth = standardMaterial.wireframeLinewidth,
        flatShading = standardMaterial.flatShading,
        vertexColors = standardMaterial.vertexColors,
        fog = standardMaterial.fog,
        refractionRatio = standardMaterial.refractionRatio,
        clearcoat = clearcoat,
        clearcoatRoughness = clearcoatRoughness,
        clearcoatMap = clearcoatMap,
        clearcoatRoughnessMap = clearcoatRoughnessMap,
        clearcoatNormalMap = clearcoatNormalMap,
        clearcoatNormalScale = clearcoatNormalScale.clone(),
        transmission = transmission,
        transmissionMap = transmissionMap,
        thickness = thickness,
        thicknessMap = thicknessMap,
        attenuationDistance = attenuationDistance,
        attenuationColor = attenuationColor.clone(),
        sheen = sheen,
        sheenColor = sheenColor.clone(),
        sheenColorMap = sheenColorMap,
        sheenRoughness = sheenRoughness,
        sheenRoughnessMap = sheenRoughnessMap,
        iridescence = iridescence,
        iridescenceMap = iridescenceMap,
        iridescenceIOR = iridescenceIOR,
        iridescenceThicknessRange = iridescenceThicknessRange.clone(),
        iridescenceThicknessMap = iridescenceThicknessMap,
        anisotropy = anisotropy,
        anisotropyRotation = anisotropyRotation,
        anisotropyMap = anisotropyMap,
        name = name
    ).apply {
        userData.putAll(this@MeshPhysicalMaterial.userData)
    }

    /**
     * Check if this material uses transmission
     */
    fun hasTransmission(): Boolean = transmission > 0f

    /**
     * Check if this material uses clearcoat
     */
    fun hasClearcoat(): Boolean = clearcoat > 0f

    /**
     * Check if this material uses sheen
     */
    fun hasSheen(): Boolean = sheen > 0f

    /**
     * Check if this material uses iridescence
     */
    fun hasIridescence(): Boolean = iridescence > 0f

    /**
     * Check if this material uses anisotropy
     */
    fun hasAnisotropy(): Boolean = anisotropy > 0f

    /**
     * Check if this material needs alpha blending
     */
    fun needsAlphaBlending(): Boolean = standardMaterial.needsAlphaBlending() || hasTransmission()

    /**
     * Get the effective IOR (Index of Refraction) for this material
     */
    fun getEffectiveIOR(): Float = when {
        hasTransmission() -> 1.5f // Glass-like
        hasIridescence() -> iridescenceIOR
        else -> 1.4f // Default dielectric IOR
    }

    /**
     * Dispose of GPU resources
     */
    fun dispose() {
        standardMaterial.dispose()
        clearcoatMap?.dispose()
        clearcoatRoughnessMap?.dispose()
        clearcoatNormalMap?.dispose()
        transmissionMap?.dispose()
        thicknessMap?.dispose()
        sheenColorMap?.dispose()
        sheenRoughnessMap?.dispose()
        iridescenceMap?.dispose()
        iridescenceThicknessMap?.dispose()
        anisotropyMap?.dispose()
    }

    override fun toString(): String = "MeshPhysicalMaterial(name='$name', id=$id)"
}