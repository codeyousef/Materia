/**
 * Lighting System Core Types and Interfaces
 * Provides fundamental types for the lighting system
 */
package io.materia.lighting

import io.materia.camera.Camera
import io.materia.core.math.Box3
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.core.scene.Material
import io.materia.core.scene.Object3D
import io.materia.core.scene.Scene
import io.materia.renderer.CubeTexture
import io.materia.renderer.Renderer
import io.materia.renderer.Texture

/**
 * Light Probe interface for global illumination
 */
interface LightProbe {
    val position: Vector3
    val distance: Float
    val intensity: Float
    var irradianceMap: CubeTexture?
    var sh: SphericalHarmonics?

    suspend fun capture(scene: Scene, renderer: Renderer, camera: Camera): ProbeResult<CubeTexture>
    fun getInfluence(position: Vector3): Float
}

/**
 * Light Probe Baker interface
 */
interface LightProbeBaker {
    suspend fun autoPlaceProbes(scene: Scene, density: Float): List<LightProbe>
    suspend fun placeProbesOnGrid(bounds: Box3, spacing: Vector3): List<LightProbe>
    suspend fun placeProbesManual(positions: List<Vector3>): List<LightProbe>
    suspend fun bakeProbe(probe: LightProbe, scene: Scene): BakeResult<Unit>
    suspend fun bakeAllProbes(probes: List<LightProbe>, scene: Scene): BakeResult<Unit>
}

/**
 * Shadow Mapper interface
 */
interface ShadowMapper {
    suspend fun renderShadowMap(
        light: Light,
        scene: Scene,
        camera: Camera,
        objects: List<Object3D>
    ): ShadowResult<Texture>

    fun getShadowMatrix(light: Light, camera: Camera): Matrix4
    fun updateShadowUniforms(light: Light, material: Material)
}

/**
 * Light types enumeration
 */
enum class LightType {
    DIRECTIONAL,
    POINT,
    SPOT,
    AREA,
    HEMISPHERE,
    AMBIENT,
    RECTAREA,
    VOLUMETRIC
}

/**
 * Light interface
 */
interface Light {
    val id: Int
    val type: LightType
    var position: Vector3
    var color: Color
    var intensity: Float
    var castShadow: Boolean
    var shadowMapSize: Int
    var shadowBias: Float
    var shadowNormalBias: Float
    var shadowRadius: Float
}

/**
 * Directional Light interface
 */
interface DirectionalLight : Light {
    override val type: LightType get() = LightType.DIRECTIONAL
    val direction: Vector3
    var shadowCameraTop: Float
    var shadowCameraBottom: Float
    var shadowCameraLeft: Float
    var shadowCameraRight: Float
    var shadowCameraNear: Float
    var shadowCameraFar: Float
}

/**
 * Point Light interface
 */
interface PointLight : Light {
    override val type: LightType get() = LightType.POINT
    var decay: Float
    var distance: Float
    val shadowCameraNear: Float
    val shadowCameraFar: Float
}

/**
 * Spot Light interface
 */
interface SpotLight : Light {
    override val type: LightType get() = LightType.SPOT
    val direction: Vector3
    var angle: Float
    var penumbra: Float
    var decay: Float
    var distance: Float
    val shadowCameraNear: Float
    val shadowCameraFar: Float
}

// AreaLight is defined in AdvancedLights.kt with more complete implementation

/**
 * Hemisphere Light interface
 */
interface HemisphereLight : Light {
    override val type: LightType get() = LightType.HEMISPHERE
    var groundColor: Color
}

/**
 * Ambient Light interface
 */
interface AmbientLight : Light {
    override val type: LightType get() = LightType.AMBIENT
}

/**
 * Rect Area Light interface
 */
interface RectAreaLight : Light {
    override val type: LightType get() = LightType.RECTAREA
    var width: Float
    var height: Float
}

/**
 * Probe compression formats
 */
enum class ProbeCompressionFormat {
    NONE,
    RGBM,
    RGBE,
    LOGLUV,
    SH_L1,
    TETRAHEDRAL
}

/**
 * Probe falloff types
 */
enum class ProbeFalloff {
    LINEAR,
    SMOOTH,
    EXPONENTIAL,
    CUSTOM
}

/**
 * Shadow types
 */
enum class ShadowType {
    NONE,
    BASIC,
    PCF,
    PCF_SOFT,
    VSM,
    ESM,
    PCSS
}

/**
 * Shadow cascade configuration
 */
data class CascadeConfig(
    val numCascades: Int = 4,
    val splitDistribution: Float = 0.8f,
    val maxDistance: Float = 100.0f,
    val fadeRange: Float = 0.1f
)

/**
 * Result types
 */
sealed class ProbeResult<out T> {
    data class Success<T>(val data: T) : ProbeResult<T>()
    data class Error(val exception: ProbeException) : ProbeResult<Nothing>()
}

sealed class BakeResult<out T> {
    data class Success<T>(val data: T) : BakeResult<T>()
    data class Error(val exception: BakeException) : BakeResult<Nothing>()
}

sealed class ShadowResult<out T> {
    data class Success<T>(val data: T) : ShadowResult<T>()
    data class Error(val exception: ShadowException) : ShadowResult<Nothing>()
}

sealed class RenderResult<out T> {
    data class Success<T>(val data: T) : RenderResult<T>()
    data class Error(val error: String) : RenderResult<Nothing>()
}

/**
 * Exception types
 */
open class ProbeException(message: String) : Exception(message)
open class BakeException(message: String) : Exception(message)
open class ShadowException(message: String) : Exception(message)

// Renderer interface is already defined in io.materia.renderer.Renderer
// Box3 is already defined in io.materia.core.math.Box3

/**
 * Bounding Sphere
 */
data class Sphere(
    val center: Vector3 = Vector3(),
    val radius: Float = 0f
) {
    fun containsPoint(point: Vector3): Boolean {
        return center.distanceSquared(point) <= (radius * radius)
    }

    fun intersectsSphere(sphere: Sphere): Boolean {
        val radiusSum = radius + sphere.radius
        return center.distanceSquared(sphere.center) <= (radiusSum * radiusSum)
    }
}

/**
 * Ray for intersection testing
 */
data class Ray(
    val origin: Vector3,
    val direction: Vector3
) {
    fun at(t: Float): Vector3 = origin + (direction * t)
}

/**
 * Frustum for culling
 */
data class Frustum(
    val planes: Array<Plane> = Array(6) { Plane() }
) {
    fun containsPoint(point: Vector3): Boolean {
        return planes.all { it.distanceToPoint(point) >= 0 }
    }

    fun intersectsSphere(sphere: Sphere): Boolean {
        return planes.all {
            it.distanceToPoint(sphere.center) >= -sphere.radius
        }
    }

    fun intersectsBox(box: Box3): Boolean {
        // Check if box is on the positive side of all planes
        for (plane in planes) {
            var positiveVertex = box.min.clone()
            var negativeVertex = box.max.clone()

            if (plane.normal.x >= 0) {
                positiveVertex.x = box.max.x
                negativeVertex.x = box.min.x
            }
            if (plane.normal.y >= 0) {
                positiveVertex.y = box.max.y
                negativeVertex.y = box.min.y
            }
            if (plane.normal.z >= 0) {
                positiveVertex.z = box.max.z
                negativeVertex.z = box.min.z
            }

            if (plane.distanceToPoint(positiveVertex) < 0) {
                return false
            }
        }
        return true
    }
}

/**
 * Plane for frustum culling
 */
data class Plane(
    val normal: Vector3 = Vector3(1f, 0f, 0f),
    val constant: Float = 0f
) {
    fun distanceToPoint(point: Vector3): Float {
        return normal.dot(point) + constant
    }

    fun normalize(): Plane {
        val inverseNormalLength = 1f / normal.length()
        return copy(
            normal = (normal * inverseNormalLength),
            constant = (constant * inverseNormalLength)
        )
    }
}

// Additional types for shadow mapping
enum class ShadowFilter {
    NONE,
    PCF,
    PCF_SOFT,
    PCSS,
    VSM,
    ESM,
    CONTACT
}

interface ShadowMap {
    val texture: Texture
    val lightSpaceMatrix: Matrix4
    val near: Float
    val far: Float
    val bias: Float
}

interface ShadowCascade {
    val texture: Texture
    val projectionViewMatrix: Matrix4
    val splitDistance: Float
}

interface CascadedShadowMap {
    val cascades: List<ShadowCascade>
}

interface CubeShadowMap {
    val cubeTexture: CubeTexture
    val matrices: Array<Matrix4>
    val near: Float
    val far: Float
}

// Probe types
data class ProbeVolume(
    val bounds: Box3,
    val probes: List<LightProbe>,
    val spacing: Vector3
)

data class ProbeInfluence(
    val probe: LightProbe,
    val weight: Float
)

data class CompressedProbeData(
    val data: ByteArray,
    val metadata: ProbeMetadata
)

data class ProbeMetadata(
    val format: ProbeCompressionFormat,
    val originalSize: Int,
    val compressedSize: Int
)

// Shadow exceptions
class UnsupportedShadowType(message: String) : ShadowException(message)
class ShadowMapGenerationFailed(message: String) : ShadowException(message)
class BakingFailed(message: String) : BakeException(message)
