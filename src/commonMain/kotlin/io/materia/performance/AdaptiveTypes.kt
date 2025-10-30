/**
 * Data types and enums for adaptive rendering system
 */
package io.materia.performance

data class HardwareInfo(
    val platform: Platform,
    val cpuModel: String,
    val cpuCores: Int,
    val cpuFrequency: Float, // GHz
    val totalMemory: Long,    // Bytes
    val gpuModel: String,
    val displayResolution: Resolution
)

data class GPUCapabilities(
    val vendor: String,
    val renderer: String,
    val vramSize: Long,
    val computeUnits: Int,
    val maxTextureSize: Int,
    val maxTextureUnits: Int,
    val supportsComputeShaders: Boolean,
    val supportsGeometryShaders: Boolean,
    val supportsTessellation: Boolean,
    val supportsRayTracing: Boolean,
    val supportsBindlessTextures: Boolean,
    val supportsMeshShaders: Boolean
)

data class RenderCapabilities(
    val hardware: HardwareInfo,
    val gpu: GPUCapabilities,
    val tier: QualityTier,
    val features: Set<RenderFeature>
)

data class DynamicQualitySettings(
    val resolution: ResolutionScale = ResolutionScale(1.0f, 1.0f),
    val shadowQuality: ShadowQuality = ShadowQuality.MEDIUM,
    val textureQuality: TextureQuality = TextureQuality.MEDIUM,
    val effectQuality: EffectQuality = EffectQuality.MEDIUM,
    val maxLOD: Int = 3,
    val renderDistance: Float = 100f,
    val antiAliasing: AntiAliasing = AntiAliasing.FXAA,
    val anisotropicFiltering: Int = 4,
    val postProcessing: Set<PostProcess> = emptySet(),
    val maxLights: Int = 8,
    val maxShadowCasters: Int = 4,
    val enableReflections: Boolean = true,
    val enableVolumetrics: Boolean = false,
    val enableMotionBlur: Boolean = false,
    val enableDepthOfField: Boolean = true,
    val particleQuality: ParticleQuality = ParticleQuality.MEDIUM,
    val vegetationDensity: Float = 0.5f,
    val enableBatching: Boolean = true,
    val enableInstancing: Boolean = true,
    val enableTextureStreaming: Boolean = false,
    val enableMeshStreaming: Boolean = false
)

data class ResolutionScale(
    val min: Float,
    val max: Float
)

data class Resolution(
    val width: Int,
    val height: Int
)

data class AdaptationEvent(
    val type: AdaptationType,
    val fromTier: QualityTier,
    val toTier: QualityTier,
    val reason: AdaptationReason,
    val timestamp: Long,
    val details: String? = null
)

enum class Platform {
    MOBILE, WEB, DESKTOP, CONSOLE
}

enum class ShadowQuality {
    OFF, LOW, MEDIUM, HIGH, ULTRA
}

enum class TextureQuality {
    LOW, MEDIUM, HIGH, ULTRA
}

enum class EffectQuality {
    LOW, MEDIUM, HIGH, ULTRA
}

enum class ParticleQuality {
    OFF, LOW, MEDIUM, HIGH, ULTRA
}

enum class AntiAliasing {
    NONE, FXAA, TAA, MSAA_2X, MSAA_4X, MSAA_8X
}

enum class PostProcess {
    BLOOM,
    TONE_MAPPING,
    SSAO,
    SSR,
    VOLUMETRIC_FOG,
    CHROMATIC_ABERRATION,
    FILM_GRAIN,
    VIGNETTE,
    DOF,
    MOTION_BLUR
}

enum class RenderFeature {
    BASIC_RENDERING,
    TEXTURE_MAPPING,
    COMPUTE_SHADERS,
    GEOMETRY_SHADERS,
    TESSELLATION,
    RAY_TRACING,
    HIGH_RES_TEXTURES,
    TEXTURE_ARRAYS,
    MULTI_VIEWPORT,
    ADVANCED_SHADERS,
    TILE_BASED_RENDERING,
    WEBGPU_FEATURES,
    BINDLESS_TEXTURES,
    MESH_SHADERS
}

enum class AdaptationType {
    UPGRADE,
    DOWNGRADE,
    FINE_TUNE,
    EMERGENCY,
    MANUAL_OVERRIDE,
    ISSUE_DETECTED
}

enum class AdaptationReason {
    LOW_FPS,
    HIGH_FPS,
    THERMAL_THROTTLE,
    MEMORY_PRESSURE,
    PERFORMANCE_HEADROOM,
    OPTIMIZATION,
    CRITICAL_PERFORMANCE,
    USER_PREFERENCE,
    PERFORMANCE_ISSUE
}

enum class PerformanceIssue {
    SEVERE_FPS_DROP,
    FRAME_SPIKE,
    HIGH_MEMORY,
    HIGH_TEMPERATURE,
    EXCESSIVE_DRAW_CALLS
}
