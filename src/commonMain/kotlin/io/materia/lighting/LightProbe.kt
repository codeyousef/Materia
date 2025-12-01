/**
 * Light Probe System
 * Provides light probe placement, baking, and runtime interpolation for global illumination
 *
 * Refactored: Delegates to specialized probe modules
 */
package io.materia.lighting

import io.materia.core.math.Vector3
import io.materia.core.math.Box3
import io.materia.core.platform.currentTimeMillis
import io.materia.core.scene.Scene
import io.materia.camera.Camera
import io.materia.renderer.*
import io.materia.lighting.probe.*
import kotlinx.coroutines.*

// Type aliases for compatibility
typealias ProbeVolumeImpl = io.materia.lighting.probe.ProbeVolume
typealias CompressedProbeDataImpl = io.materia.lighting.probe.CompressedProbeData

/**
 * Light probe implementation with spherical harmonics and cubemap capture
 */
class LightProbeImpl(
    override val position: Vector3,
    override val distance: Float = 10.0f,
    override val intensity: Float = 1.0f
) : LightProbe {
    override var irradianceMap: CubeTexture? = null
    override var sh: SphericalHarmonics? = null

    // Delegate components
    private val captureSystem = ProbeCapture()
    private val influenceCalculator = ProbeInfluenceCalculator()
    private val shGenerator = SphericalHarmonicsGenerator()

    // Probe configuration
    var resolution: Int = 256
    var nearPlane: Float = 0.1f
    var farPlane: Float = 100.0f
    var updateFrequency: Float = 60.0f
    var autoUpdate: Boolean = false

    // Influence calculation
    var falloffType: ProbeFalloff = ProbeFalloff.SMOOTH
    var falloffStrength: Float = 1.0f
    var influenceBounds: Box3? = null

    // Lightmap baking
    var lightmapResolution: Int = 512
    var lightmapUVScale: Float = 1.0f
    var lightmapPadding: Int = 2

    // Quality settings
    var compressionFormat: ProbeCompressionFormat = ProbeCompressionFormat.NONE
    var compressionQuality: Float = 0.8f

    // Runtime state
    private var lastUpdateTime: Float = 0f
    private var captureInProgress: Boolean = false
    private var validData: Boolean = false

    override suspend fun capture(scene: Scene, renderer: Renderer, camera: Camera): ProbeResult<CubeTexture> {
        if (captureInProgress) {
            return ProbeResult.Error(ProbeException("Capture already in progress"))
        }

        return try {
            captureInProgress = true

            // Create 6 cameras for cubemap faces
            val cameras = captureSystem.createCubemapCameras(position)

            // Capture each face
            val faceData = Array(6) { FloatArray(resolution * resolution * 4) }

            for (face in 0 until 6) {
                val faceCamera = cameras[face]
                faceData[face] = captureSystem.captureFace(scene, faceCamera, renderer)
            }

            // Create cubemap from captured data
            irradianceMap = captureSystem.createCubemapFromFaces(faceData)

            // Generate spherical harmonics
            irradianceMap?.let { cubemap ->
                sh = shGenerator.generateFromCubemap(cubemap)
            }

            validData = true
            lastUpdateTime = currentTimeMillis().toFloat() / 1000f

            // Return cubemap
            val cubemap = CubeTextureImpl(
                size = 256,
                format = TextureFormat.RGBA32F,
                filter = TextureFilter.LINEAR
            )
            ProbeResult.Success(cubemap)
        } catch (e: Exception) {
            ProbeResult.Error(ProbeException("Capture failed: ${e.message}"))
        } finally {
            captureInProgress = false
        }
    }

    override fun getInfluence(position: Vector3): Float {
        return influenceCalculator.calculateInfluence(
            probePosition = this.position,
            probeDistance = distance,
            position = position,
            influenceBounds = influenceBounds,
            hasValidData = validData
        )
    }

    /**
     * Get lighting contribution at a surface point
     */
    fun getLightingContribution(
        surfacePosition: Vector3,
        surfaceNormal: Vector3,
        viewDirection: Vector3
    ): Vector3 {
        val influence = getInfluence(surfacePosition)
        if (influence <= 0f) return Vector3.ZERO

        val sphericalHarmonics = sh
        val irrMap = irradianceMap

        return when {
            sphericalHarmonics != null -> {
                // Use spherical harmonics for fast approximation
                val shResult = sphericalHarmonics.evaluate(surfaceNormal)
                shResult * (influence * intensity)
            }
            irrMap != null -> {
                // Sample irradiance map directly
                val irradianceColor = sampleIrradianceMap(irrMap, surfaceNormal)
                irradianceColor * influence * intensity
            }
            else -> Vector3.ZERO
        }
    }

    private fun sampleIrradianceMap(cubemap: CubeTexture, normal: Vector3): Vector3 {
        // Simplified sampling - production would properly sample the cubemap
        return Vector3(
            kotlin.math.abs(normal.x),
            kotlin.math.abs(normal.y),
            kotlin.math.abs(normal.z)
        )
    }
}

/**
 * Light probe baking system
 */
class LightProbeBakerImpl : LightProbeBaker {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Delegate components
    private val placementStrategy = ProbePlacementStrategy()
    private val bakingSystem = ProbeBakingSystem()
    private val networkOptimizer = ProbeNetworkOptimizer()
    private val volumeGenerator = ProbeVolumeGenerator()
    private val dataCompressor = ProbeDataCompressor()

    // Baking configuration
    var bounceCount: Int = 3
    var sampleCount: Int = 1024
    var filterSize: Float = 1.0f
    var denoisingEnabled: Boolean = true
    var progressiveRefinement: Boolean = true

    // Performance settings
    var maxConcurrentBakes: Int = 4
    var tileSize: Int = 64
    var adaptiveQuality: Boolean = true

    override suspend fun autoPlaceProbes(scene: Scene, density: Float): List<LightProbe> =
        placementStrategy.autoPlaceProbes(scene, density)

    override suspend fun placeProbesOnGrid(bounds: Box3, spacing: Vector3): List<LightProbe> =
        placementStrategy.placeProbesOnGrid(bounds, spacing)

    override suspend fun placeProbesManual(positions: List<Vector3>): List<LightProbe> =
        placementStrategy.placeProbesManual(positions)

    override suspend fun bakeProbe(probe: LightProbe, scene: Scene): BakeResult<Unit> =
        bakingSystem.bakeProbe(probe, scene)

    override suspend fun bakeAllProbes(probes: List<LightProbe>, scene: Scene): BakeResult<Unit> =
        bakingSystem.bakeAllProbes(probes, scene)

    suspend fun bakeLightmaps(scene: Scene, resolution: Int): BakeResult<List<io.materia.lighting.probe.LightmapTexture2D>> =
        bakingSystem.bakeLightmaps(scene, resolution)

    fun optimizeProbeNetwork(probes: List<LightProbe>): List<LightProbe> =
        networkOptimizer.optimizeProbeNetwork(probes)

    fun generateProbeVolume(probes: List<LightProbe>): ProbeVolumeImpl =
        volumeGenerator.generateProbeVolume(probes)

    fun compressProbeData(probes: List<LightProbe>): CompressedProbeDataImpl =
        dataCompressor.compressProbeData(probes)
}

// Scene extension functions
private fun Scene.getObjectsWithLightmapUVs(): List<Any> = emptyList() // Stub implementation
