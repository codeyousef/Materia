package io.materia.material

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.core.scene.Material
import io.materia.texture.CubeTexture
import io.materia.texture.Texture2D
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * Standard physically-based material following Three.js MeshStandardMaterial API
 * T100 - Standard PBR material implementation
 *
 * MeshStandardMaterial provides a standard PBR (Physically Based Rendering) material
 * that uses metallic-roughness workflow. It's simpler than PBRMaterial but provides
 * all essential PBR features for most use cases.
 */
class MeshStandardMaterial(
    // Core PBR properties
    var color: Color = Color.WHITE,
    var metalness: Float = 0f,
    var roughness: Float = 1f,
    var emissive: Color = Color.BLACK,
    var emissiveIntensity: Float = 1f,

    // Transparency
    var transparent: Boolean = false,
    var opacity: Float = 1f,
    var alphaTest: Float = 0f,

    // Texture maps
    var map: Texture2D? = null,
    var normalMap: Texture2D? = null,
    var normalScale: Vector2 = Vector2(1f, 1f),
    var metalnessMap: Texture2D? = null,
    var roughnessMap: Texture2D? = null,
    var aoMap: Texture2D? = null,
    var aoMapIntensity: Float = 1f,
    var emissiveMap: Texture2D? = null,
    var bumpMap: Texture2D? = null,
    var bumpScale: Float = 1f,
    var displacementMap: Texture2D? = null,
    var displacementScale: Float = 1f,
    var displacementBias: Float = 0f,

    // Environment mapping
    var envMap: CubeTexture? = null,
    var envMapIntensity: Float = 1f,

    // Material behavior
    var wireframe: Boolean = false,
    var wireframeLinewidth: Float = 1f,
    var flatShading: Boolean = false,
    var vertexColors: Boolean = false,
    var fog: Boolean = true,

    // Advanced properties
    var refractionRatio: Float = 0.98f,

    override val id: Int = generateId(),
    override var name: String = "MeshStandardMaterial"
) : Material {

    override var needsUpdate: Boolean = true
    override var visible: Boolean = true

    // Blending properties
    var blending: BlendMode = BlendMode.NORMAL
    var side: MaterialSide = MaterialSide.FRONT
    var depthTest: Boolean = true
    var depthWrite: Boolean = true
    var colorWrite: Boolean = true

    // Culling
    var cullFace: CullFace = CullFace.BACK

    // User data for custom properties
    val userData: MutableMap<String, Any> = mutableMapOf()

    companion object {
        private val nextId: AtomicInt = atomic(1)
        private fun generateId(): Int = nextId.getAndIncrement()

        /**
         * Create a material with metallic workflow preset
         */
        fun metal(
            color: Color = Color(0.7f, 0.7f, 0.7f),
            metalness: Float = 1f,
            roughness: Float = 0.1f
        ): MeshStandardMaterial = MeshStandardMaterial(
            color = color,
            metalness = metalness,
            roughness = roughness
        )

        /**
         * Create a material with dielectric workflow preset
         */
        fun dielectric(
            color: Color = Color.WHITE,
            roughness: Float = 0.5f
        ): MeshStandardMaterial = MeshStandardMaterial(
            color = color,
            metalness = 0f,
            roughness = roughness
        )

        /**
         * Create a glass-like material
         */
        fun glass(
            color: Color = Color(0.9f, 0.9f, 1f),
            opacity: Float = 0.3f,
            roughness: Float = 0.1f
        ): MeshStandardMaterial = MeshStandardMaterial(
            color = color,
            metalness = 0f,
            roughness = roughness,
            transparent = true,
            opacity = opacity
        )
    }

    /**
     * Copy this material
     */
    fun copy(): MeshStandardMaterial = MeshStandardMaterial(
        color = color.clone(),
        metalness = metalness,
        roughness = roughness,
        emissive = emissive.clone(),
        emissiveIntensity = emissiveIntensity,
        transparent = transparent,
        opacity = opacity,
        alphaTest = alphaTest,
        map = map,
        normalMap = normalMap,
        normalScale = normalScale.clone(),
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
    ).apply {
        blending = this@MeshStandardMaterial.blending
        side = this@MeshStandardMaterial.side
        depthTest = this@MeshStandardMaterial.depthTest
        depthWrite = this@MeshStandardMaterial.depthWrite
        colorWrite = this@MeshStandardMaterial.colorWrite
        cullFace = this@MeshStandardMaterial.cullFace
        userData.putAll(this@MeshStandardMaterial.userData)
    }

    /**
     * Set values from a map (useful for serialization/deserialization)
     */
    fun setValues(parameters: Map<String, Any>) {
        parameters["color"]?.let { if (it is Color) color = it }
        parameters["metalness"]?.let { if (it is Number) metalness = it.toFloat() }
        parameters["roughness"]?.let { if (it is Number) roughness = it.toFloat() }
        parameters["emissive"]?.let { if (it is Color) emissive = it }
        parameters["emissiveIntensity"]?.let { if (it is Number) emissiveIntensity = it.toFloat() }
        parameters["transparent"]?.let { if (it is Boolean) transparent = it }
        parameters["opacity"]?.let { if (it is Number) opacity = it.toFloat() }
        parameters["alphaTest"]?.let { if (it is Number) alphaTest = it.toFloat() }
        parameters["envMapIntensity"]?.let { if (it is Number) envMapIntensity = it.toFloat() }
        parameters["wireframe"]?.let { if (it is Boolean) wireframe = it }
        parameters["flatShading"]?.let { if (it is Boolean) flatShading = it }
        parameters["vertexColors"]?.let { if (it is Boolean) vertexColors = it }
        parameters["fog"]?.let { if (it is Boolean) fog = it }
        parameters["name"]?.let { if (it is String) name = it }

        needsUpdate = true
    }

    /**
     * Check if this material needs alpha blending
     */
    fun needsAlphaBlending(): Boolean = transparent || opacity < 1f || alphaTest > 0f

    /**
     * Dispose of GPU resources
     */
    fun dispose() {
        map?.dispose()
        normalMap?.dispose()
        metalnessMap?.dispose()
        roughnessMap?.dispose()
        aoMap?.dispose()
        emissiveMap?.dispose()
        bumpMap?.dispose()
        displacementMap?.dispose()
        envMap?.dispose()
    }

    override fun toString(): String = "MeshStandardMaterial(name='$name', id=$id)"
}

// Enums for material properties
enum class BlendMode {
    NORMAL, ADDITIVE, SUBTRACTIVE, MULTIPLY, CUSTOM
}

enum class MaterialSide {
    FRONT, BACK, DOUBLE
}

enum class CullFace {
    FRONT, BACK, NONE
}