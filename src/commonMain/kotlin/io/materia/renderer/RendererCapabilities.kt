package io.materia.renderer

/**
 * Describes the capabilities and limits of a renderer.
 * Provides information about what features are supported
 * and what the hardware/software limits are.
 *
 * T012: Updated for Feature 019 - WebGPU/Vulkan Primary Renderer
 * Added: backend, supportsCompute, supportsRayTracing, deviceName, driverVersion
 */
data class RendererCapabilities(
    /**
     * Current graphics backend (T012: Feature 019)
     */
    val backend: BackendType = BackendType.WEBGL,

    /**
     * GPU device name (T012: Feature 019, FR-024)
     * Example: "NVIDIA GeForce RTX 4090", "Apple M2", "Intel UHD Graphics 630"
     */
    val deviceName: String = "Unknown",

    /**
     * Driver version string (T012: Feature 019, FR-024)
     */
    val driverVersion: String = "Unknown",

    /**
     * Whether compute shaders are supported (T012: Feature 019)
     */
    val supportsCompute: Boolean = false,

    /**
     * Whether hardware ray tracing is supported (T012: Feature 019)
     */
    val supportsRayTracing: Boolean = false,

    /**
     * Whether MSAA multisampling is supported (T012: Feature 019)
     */
    val supportsMultisampling: Boolean = true,

    /**
     * Maximum texture size (width or height)
     */
    val maxTextureSize: Int = 2048,

    /**
     * Maximum cube map texture size
     */
    val maxCubeMapSize: Int = 1024,

    /**
     * Maximum number of vertex attributes
     */
    val maxVertexAttributes: Int = 16,

    /**
     * Maximum number of vertex uniform vectors
     */
    val maxVertexUniforms: Int = 1024,

    /**
     * Maximum number of fragment uniform vectors
     */
    val maxFragmentUniforms: Int = 1024,

    /**
     * Maximum number of vertex texture image units
     */
    val maxVertexTextures: Int = 0,

    /**
     * Maximum number of fragment texture image units
     */
    val maxFragmentTextures: Int = 16,

    /**
     * Maximum number of combined texture image units
     */
    val maxCombinedTextures: Int = 32,

    /**
     * Maximum 3D texture size
     */
    val maxTextureSize3D: Int = 256,

    /**
     * Maximum array texture layers
     */
    val maxTextureArrayLayers: Int = 256,

    /**
     * Maximum number of color attachments for render targets
     */
    val maxColorAttachments: Int = 8,

    /**
     * Maximum MSAA samples
     */
    val maxSamples: Int = 4,

    /**
     * Maximum uniform buffer size in bytes
     */
    val maxUniformBufferSize: Int = 16384,

    /**
     * Maximum number of uniform buffer bindings
     */
    val maxUniformBufferBindings: Int = 36,

    /**
     * Maximum anisotropy level for texture filtering
     */
    val maxAnisotropy: Float = 16f,

    /**
     * Supported shader precisions
     */
    val vertexShaderPrecisions: ShaderPrecisions = ShaderPrecisions(),

    /**
     * Supported shader precisions for fragment shader
     */
    val fragmentShaderPrecisions: ShaderPrecisions = ShaderPrecisions(),

    /**
     * Supported texture formats
     */
    val textureFormats: Set<TextureFormat> = setOf(
        TextureFormat.RGBA8,
        TextureFormat.RGB8,
        TextureFormat.RGBA16F,
        TextureFormat.RGBA32F
    ),

    /**
     * Supported texture compression formats
     */
    val compressedTextureFormats: Set<CompressedTextureFormat> = emptySet(),

    /**
     * Supported depth formats
     */
    val depthFormats: Set<DepthFormat> = setOf(DepthFormat.DEPTH24_STENCIL8),

    /**
     * Extensions and features supported
     */
    val extensions: Set<String> = emptySet(),

    /**
     * GPU vendor
     */
    val vendor: String = "Unknown",

    /**
     * GPU renderer/device name
     */
    val renderer: String = "Unknown",

    /**
     * API version (e.g., "WebGPU 1.0", "Vulkan 1.3")
     */
    val version: String = "Unknown",

    /**
     * Shading language version
     */
    val shadingLanguageVersion: String = "Unknown",

    /**
     * Whether the renderer supports instanced rendering
     */
    val instancedRendering: Boolean = true,

    /**
     * Whether the renderer supports multiple render targets
     */
    val multipleRenderTargets: Boolean = true,

    /**
     * Whether the renderer supports depth textures
     */
    val depthTextures: Boolean = true,

    /**
     * Whether the renderer supports float textures
     */
    val floatTextures: Boolean = true,

    /**
     * Whether the renderer supports half float textures
     */
    val halfFloatTextures: Boolean = true,

    /**
     * Whether the renderer supports linear filtering on float textures
     */
    val floatTextureLinear: Boolean = true,

    /**
     * Whether the renderer supports standard derivatives in shaders
     */
    val standardDerivatives: Boolean = true,

    /**
     * Whether the renderer supports vertex array objects
     */
    val vertexArrayObjects: Boolean = true,

    /**
     * Whether the renderer supports compute shaders
     */
    val computeShaders: Boolean = false,

    /**
     * Whether the renderer supports geometry shaders
     */
    val geometryShaders: Boolean = false,

    /**
     * Whether the renderer supports tessellation
     */
    val tessellation: Boolean = false,

    /**
     * Whether the renderer supports shadow maps
     */
    val shadowMaps: Boolean = true,

    /**
     * Whether the renderer supports shadow map comparison
     */
    val shadowMapComparison: Boolean = true,

    /**
     * Whether the renderer supports PCF shadow filtering
     */
    val shadowMapPCF: Boolean = true,

    /**
     * Whether the renderer supports parallel shader compilation
     */
    val parallelShaderCompile: Boolean = false,

    /**
     * Whether the renderer supports asynchronous operations
     */
    val asyncOperations: Boolean = false
) {

    /**
     * Checks if a specific extension/feature is supported
     */
    fun supports(extension: String): Boolean {
        return when (extension.lowercase()) {
            "instanced_rendering" -> instancedRendering
            "multiple_render_targets", "mrt" -> multipleRenderTargets
            "depth_textures" -> depthTextures
            "float_textures" -> floatTextures
            "half_float_textures" -> halfFloatTextures
            "float_texture_linear" -> floatTextureLinear
            "standard_derivatives" -> standardDerivatives
            "vertex_array_objects", "vao" -> vertexArrayObjects
            "compute_shaders" -> computeShaders
            "geometry_shaders" -> geometryShaders
            "tessellation" -> tessellation
            "shadow_maps" -> shadowMaps
            "shadow_map_comparison" -> shadowMapComparison
            "shadow_map_pcf" -> shadowMapPCF
            "parallel_shader_compile" -> parallelShaderCompile
            "async_operations" -> asyncOperations
            else -> extensions.contains(extension)
        }
    }

    /**
     * Checks if a texture format is supported
     */
    fun supportsTextureFormat(format: TextureFormat): Boolean {
        return textureFormats.contains(format)
    }

    /**
     * Checks if a compressed texture format is supported
     */
    fun supportsCompressedFormat(format: CompressedTextureFormat): Boolean {
        return compressedTextureFormats.contains(format)
    }

    /**
     * Checks if a depth format is supported
     */
    fun supportsDepthFormat(format: DepthFormat): Boolean {
        return depthFormats.contains(format)
    }

    /**
     * Gets the maximum texture size for a given dimension count
     */
    fun getMaxTextureSize(dimensions: Int): Int {
        return when (dimensions) {
            1, 2 -> maxTextureSize
            3 -> maxTextureSize3D
            else -> maxTextureSize
        }
    }

    /**
     * Estimates if a texture of given size would fit in memory
     */
    fun canFitTexture(
        width: Int,
        height: Int,
        format: TextureFormat = TextureFormat.RGBA8
    ): Boolean {
        if (width > maxTextureSize || height > maxTextureSize) return false

        // Basic estimation - would need actual memory info for better accuracy
        val bytesPerPixel = when (format) {
            TextureFormat.RGBA8 -> 4
            TextureFormat.RGB8 -> 3
            TextureFormat.RGBA16F -> 8
            TextureFormat.RGBA32F -> 16
            else -> 4
        }

        val estimatedBytes = width * height * bytesPerPixel
        return estimatedBytes < 100_000_000 // 100MB limit as rough estimate
    }

    /**
     * Gets a summary of key capabilities
     */
    fun getSummary(): CapabilitiesSummary {
        return CapabilitiesSummary(
            vendor = vendor,
            renderer = renderer,
            version = version,
            maxTextureSize = maxTextureSize,
            maxSamples = maxSamples,
            floatTextures = floatTextures,
            instancedRendering = instancedRendering,
            computeShaders = computeShaders,
            shadowMaps = shadowMaps
        )
    }
}

/**
 * Precision information for a specific precision level
 */
data class PrecisionInfo(
    val supported: Boolean = false,
    val rangeMin: Int = 0,
    val rangeMax: Int = 0,
    val precision: Int = 0
)

/**
 * Precision support for vertex and fragment shaders
 */
data class ShaderPrecisions(
    val lowp: PrecisionInfo = PrecisionInfo(true, -8, 7, 8),
    val mediump: PrecisionInfo = PrecisionInfo(true, -14, 13, 10),
    val highp: PrecisionInfo = PrecisionInfo(false, -62, 61, 16)
)

/**
 * Texture formats
 */
enum class TextureFormat {
    RGBA8,
    RGB8,
    RG8,
    R8,
    RGBA16F,
    RGB16F,
    RG16F,
    R16F,
    RGBA32F,
    RGB32F,
    RG32F,
    R32F,
    RGBA8UI,
    RGB8UI,
    RG8UI,
    R8UI,
    RGBA16UI,
    RGB16UI,
    RG16UI,
    R16UI,
    RGBA32UI,
    RGB32UI,
    RG32UI,
    R32UI,
    SRGB8,
    SRGB8_ALPHA8
}

/**
 * Compressed texture formats
 */
enum class CompressedTextureFormat {
    // Desktop compression
    DXT1,
    DXT3,
    DXT5,
    BC4,
    BC5,
    BC6H,
    BC7,

    // Mobile compression
    ETC1,
    ETC2_RGB,
    ETC2_RGBA8,
    EAC_R11,
    EAC_RG11,

    ASTC_4x4,
    ASTC_5x4,
    ASTC_5x5,
    ASTC_6x5,
    ASTC_6x6,
    ASTC_8x5,
    ASTC_8x6,
    ASTC_8x8,
    ASTC_10x5,
    ASTC_10x6,
    ASTC_10x8,
    ASTC_10x10,
    ASTC_12x10,
    ASTC_12x12,

    PVRTC_RGB_2BPP,
    PVRTC_RGB_4BPP,
    PVRTC_RGBA_2BPP,
    PVRTC_RGBA_4BPP
}

/**
 * Depth buffer formats
 */
enum class DepthFormat {
    DEPTH16,
    DEPTH24,
    DEPTH32F,
    DEPTH24_STENCIL8,
    DEPTH32F_STENCIL8
}

/**
 * Summary of key capabilities for easy display
 */
data class CapabilitiesSummary(
    val vendor: String,
    val renderer: String,
    val version: String,
    val maxTextureSize: Int,
    val maxSamples: Int,
    val floatTextures: Boolean,
    val instancedRendering: Boolean,
    val computeShaders: Boolean,
    val shadowMaps: Boolean
) {
    override fun toString(): String {
        return buildString {
            appendLine("GPU: $vendor $renderer")
            appendLine("API: $version")
            appendLine("Max Texture Size: ${maxTextureSize}x$maxTextureSize")
            appendLine("Max MSAA: ${maxSamples}x")
            appendLine("Float Textures: ${if (floatTextures) "Yes" else "No"}")
            appendLine("Instanced Rendering: ${if (instancedRendering) "Yes" else "No"}")
            appendLine("Compute Shaders: ${if (computeShaders) "Yes" else "No"}")
            appendLine("Shadow Maps: ${if (shadowMaps) "Yes" else "No"}")
        }
    }
}

/**
 * Utilities for working with capabilities
 */
object CapabilitiesUtils {

    /**
     * Gets a conservative capability set for compatibility
     */
    fun getCompatibilityCapabilities(): RendererCapabilities {
        return RendererCapabilities(
            maxTextureSize = 2048,
            maxCubeMapSize = 512,
            maxVertexAttributes = 16,
            maxVertexUniforms = 256,
            maxFragmentUniforms = 256,
            maxVertexTextures = 0,
            maxFragmentTextures = 8,
            maxCombinedTextures = 8,
            maxSamples = 1,
            maxAnisotropy = 1f,
            textureFormats = setOf(TextureFormat.RGBA8, TextureFormat.RGB8),
            floatTextures = false,
            halfFloatTextures = false,
            floatTextureLinear = false,
            instancedRendering = false,
            multipleRenderTargets = false,
            computeShaders = false,
            geometryShaders = false,
            tessellation = false
        )
    }

    /**
     * Gets high-end capabilities for modern hardware
     */
    fun getHighEndCapabilities(): RendererCapabilities {
        return RendererCapabilities(
            maxTextureSize = 16384,
            maxCubeMapSize = 16384,
            maxVertexAttributes = 32,
            maxVertexUniforms = 4096,
            maxFragmentUniforms = 4096,
            maxVertexTextures = 32,
            maxFragmentTextures = 32,
            maxCombinedTextures = 192,
            maxSamples = 16,
            maxAnisotropy = 16f,
            textureFormats = TextureFormat.values().toSet(),
            compressedTextureFormats = CompressedTextureFormat.values().toSet(),
            floatTextures = true,
            halfFloatTextures = true,
            floatTextureLinear = true,
            instancedRendering = true,
            multipleRenderTargets = true,
            computeShaders = true,
            geometryShaders = true,
            tessellation = true,
            parallelShaderCompile = true,
            asyncOperations = true
        )
    }

    /**
     * Merges capabilities, taking the minimum of limits
     */
    fun merge(cap1: RendererCapabilities, cap2: RendererCapabilities): RendererCapabilities {
        return RendererCapabilities(
            maxTextureSize = minOf(cap1.maxTextureSize, cap2.maxTextureSize),
            maxCubeMapSize = minOf(cap1.maxCubeMapSize, cap2.maxCubeMapSize),
            maxVertexAttributes = minOf(cap1.maxVertexAttributes, cap2.maxVertexAttributes),
            maxVertexUniforms = minOf(cap1.maxVertexUniforms, cap2.maxVertexUniforms),
            maxFragmentUniforms = minOf(cap1.maxFragmentUniforms, cap2.maxFragmentUniforms),
            maxVertexTextures = minOf(cap1.maxVertexTextures, cap2.maxVertexTextures),
            maxFragmentTextures = minOf(cap1.maxFragmentTextures, cap2.maxFragmentTextures),
            maxCombinedTextures = minOf(cap1.maxCombinedTextures, cap2.maxCombinedTextures),
            maxSamples = minOf(cap1.maxSamples, cap2.maxSamples),
            maxAnisotropy = minOf(cap1.maxAnisotropy, cap2.maxAnisotropy),
            textureFormats = cap1.textureFormats.intersect(cap2.textureFormats),
            compressedTextureFormats = cap1.compressedTextureFormats.intersect(cap2.compressedTextureFormats),
            depthFormats = cap1.depthFormats.intersect(cap2.depthFormats),
            extensions = cap1.extensions.intersect(cap2.extensions),
            instancedRendering = cap1.instancedRendering && cap2.instancedRendering,
            multipleRenderTargets = cap1.multipleRenderTargets && cap2.multipleRenderTargets,
            depthTextures = cap1.depthTextures && cap2.depthTextures,
            floatTextures = cap1.floatTextures && cap2.floatTextures,
            halfFloatTextures = cap1.halfFloatTextures && cap2.halfFloatTextures,
            floatTextureLinear = cap1.floatTextureLinear && cap2.floatTextureLinear,
            standardDerivatives = cap1.standardDerivatives && cap2.standardDerivatives,
            vertexArrayObjects = cap1.vertexArrayObjects && cap2.vertexArrayObjects,
            computeShaders = cap1.computeShaders && cap2.computeShaders,
            geometryShaders = cap1.geometryShaders && cap2.geometryShaders,
            tessellation = cap1.tessellation && cap2.tessellation,
            shadowMaps = cap1.shadowMaps && cap2.shadowMaps,
            shadowMapComparison = cap1.shadowMapComparison && cap2.shadowMapComparison,
            shadowMapPCF = cap1.shadowMapPCF && cap2.shadowMapPCF,
            parallelShaderCompile = cap1.parallelShaderCompile && cap2.parallelShaderCompile,
            asyncOperations = cap1.asyncOperations && cap2.asyncOperations
        )
    }
}
