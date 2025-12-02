/**
 * Material System - Standard Materials for Scene Graph
 *
 * Provides Three.js-style materials that integrate with the uber shader system.
 */
package io.materia.engine.material

import io.materia.core.math.Color
import io.materia.engine.core.Disposable
import io.materia.engine.shader.ShaderFeature
import io.materia.engine.shader.ShaderLibrary
import io.materia.gpu.*

/**
 * Base interface for all materials.
 */
interface EngineMaterial : Disposable {
    /** Material name for debugging */
    val name: String

    /** Whether the material needs GPU resources rebuilt */
    var needsUpdate: Boolean

    /** Whether the material is visible */
    var visible: Boolean

    /** Whether the material is transparent */
    val transparent: Boolean

    /** Render order (lower = rendered first) */
    var renderOrder: Int

    /** Side to render (front, back, or both) */
    var side: Side

    /** Depth testing enabled */
    var depthTest: Boolean

    /** Depth writing enabled */
    var depthWrite: Boolean

    /**
     * Gets the shader features required by this material.
     */
    fun getRequiredFeatures(): Set<ShaderFeature>

    /**
     * Creates the GPU pipeline for this material.
     */
    fun createPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        depthFormat: GpuTextureFormat?
    ): GpuRenderPipeline

    /**
     * Creates bind group with material uniforms.
     */
    fun createBindGroup(
        device: GpuDevice,
        layout: GpuBindGroupLayout
    ): GpuBindGroup

    /**
     * Updates uniform buffer data.
     */
    fun updateUniforms(buffer: GpuBuffer)
}

/**
 * Which side of faces to render.
 */
enum class Side {
    /** Render front faces only (default) */
    FRONT,
    /** Render back faces only */
    BACK,
    /** Render both sides */
    DOUBLE
}

/**
 * Basic unlit material with a solid color.
 *
 * This is the simplest material, useful for debugging and flat-shaded objects.
 *
 * ```kotlin
 * val material = BasicMaterial(
 *     color = Color(0xFF0000) // Red
 * )
 * ```
 */
class BasicMaterial(
    override val name: String = "BasicMaterial",
    var color: Color = Color(1f, 1f, 1f),
    var opacity: Float = 1f,
    override var transparent: Boolean = false
) : EngineMaterial {

    private var _disposed = false
    override val isDisposed: Boolean get() = _disposed

    override var needsUpdate: Boolean = true
    override var visible: Boolean = true
    override var renderOrder: Int = 0
    override var side: Side = Side.FRONT
    override var depthTest: Boolean = true
    override var depthWrite: Boolean = true

    // Cached GPU resources
    private var cachedPipeline: GpuRenderPipeline? = null
    private var cachedShaderModule: GpuShaderModule? = null

    override fun getRequiredFeatures(): Set<ShaderFeature> = emptySet()

    override fun createPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        depthFormat: GpuTextureFormat?
    ): GpuRenderPipeline {
        cachedPipeline?.let { return it }

        val shaderSource = ShaderLibrary.UNLIT_VERTEX_SHADER + "\n" + ShaderLibrary.UNLIT_FRAGMENT_SHADER

        val shaderModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "$name-shader",
                code = shaderSource
            )
        )
        cachedShaderModule = shaderModule

        val bindGroupLayout = device.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                label = "$name-bind-group-layout",
                entries = listOf(
                    GpuBindGroupLayoutEntry(
                        binding = 0,
                        visibility = setOf(GpuShaderStage.VERTEX),
                        resourceType = GpuBindingResourceType.UNIFORM_BUFFER
                    )
                )
            )
        )

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "$name-pipeline",
                vertexShader = shaderModule,
                fragmentShader = shaderModule,
                colorFormats = listOf(colorFormat),
                depthStencilFormat = depthFormat,
                vertexBuffers = listOf(
                    GpuVertexBufferLayout(
                        arrayStride = 6 * Float.SIZE_BYTES, // position (3) + color (3)
                        attributes = listOf(
                            GpuVertexAttribute(0, GpuVertexFormat.FLOAT32x3, 0),
                            GpuVertexAttribute(1, GpuVertexFormat.FLOAT32x3, 3 * Float.SIZE_BYTES)
                        )
                    )
                ),
                cullMode = when (side) {
                    Side.FRONT -> GpuCullMode.BACK
                    Side.BACK -> GpuCullMode.FRONT
                    Side.DOUBLE -> GpuCullMode.NONE
                },
                depthState = if (depthFormat != null && depthTest) {
                    GpuDepthState(
                        format = depthFormat,
                        depthWriteEnabled = depthWrite,
                        depthCompare = GpuCompareFunction.LESS
                    )
                } else null,
                blendMode = if (transparent) GpuBlendMode.ALPHA else GpuBlendMode.DISABLED,
                bindGroupLayouts = listOf(bindGroupLayout)
            )
        )

        cachedPipeline = pipeline
        needsUpdate = false
        return pipeline
    }

    override fun createBindGroup(device: GpuDevice, layout: GpuBindGroupLayout): GpuBindGroup {
        val uniformBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "$name-uniforms",
                size = 64, // mat4x4
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )

        return device.createBindGroup(
            GpuBindGroupDescriptor(
                label = "$name-bind-group",
                layout = layout,
                entries = listOf(
                    GpuBindGroupEntry(
                        binding = 0,
                        resource = GpuBindingResource.Buffer(uniformBuffer)
                    )
                )
            )
        )
    }

    override fun updateUniforms(buffer: GpuBuffer) {
        // For BasicMaterial, uniforms are just the MVP matrix
        // which is handled externally by the renderer
    }

    override fun dispose() {
        if (_disposed) return
        _disposed = true
        // Note: Pipeline cleanup is managed by the renderer's resource manager
    }
}

/**
 * Standard PBR material with metallic-roughness workflow.
 *
 * This material supports physically-based rendering with:
 * - Base color (albedo)
 * - Metallic factor
 * - Roughness factor
 * - Normal mapping
 * - Emissive
 *
 * ```kotlin
 * val material = StandardMaterial(
 *     baseColor = Color(0xCCCCCC),
 *     metallic = 0.5f,
 *     roughness = 0.3f
 * )
 * ```
 */
class StandardMaterial(
    override val name: String = "StandardMaterial",
    var baseColor: Color = Color(1f, 1f, 1f),
    var metallic: Float = 0f,
    var roughness: Float = 0.5f,
    var emissive: Color = Color(0f, 0f, 0f),
    var emissiveIntensity: Float = 1f,
    var normalScale: Float = 1f,
    var aoIntensity: Float = 1f,
    var opacity: Float = 1f,
    var alphaCutoff: Float = 0f,
    override var transparent: Boolean = false
) : EngineMaterial {

    private var _disposed = false
    override val isDisposed: Boolean get() = _disposed

    override var needsUpdate: Boolean = true
    override var visible: Boolean = true
    override var renderOrder: Int = 0
    override var side: Side = Side.FRONT
    override var depthTest: Boolean = true
    override var depthWrite: Boolean = true

    // Texture references (managed externally)
    var baseColorMap: Any? = null
    var normalMap: Any? = null
    var metallicRoughnessMap: Any? = null
    var aoMap: Any? = null
    var emissiveMap: Any? = null

    // Cached resources
    private var cachedPipeline: GpuRenderPipeline? = null

    override fun getRequiredFeatures(): Set<ShaderFeature> = buildSet {
        add(ShaderFeature.USE_DIRECTIONAL_LIGHT)
        if (baseColorMap != null) add(ShaderFeature.USE_TEXTURE)
        if (normalMap != null) add(ShaderFeature.USE_NORMAL_MAP)
        if (metallicRoughnessMap != null) add(ShaderFeature.USE_METALLIC_ROUGHNESS_MAP)
        if (aoMap != null) add(ShaderFeature.USE_AO_MAP)
        if (emissiveMap != null) add(ShaderFeature.USE_EMISSIVE_MAP)
        if (alphaCutoff > 0f) add(ShaderFeature.USE_ALPHA_CUTOFF)
    }

    override fun createPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        depthFormat: GpuTextureFormat?
    ): GpuRenderPipeline {
        cachedPipeline?.let { if (!needsUpdate) return it }

        val features = getRequiredFeatures()
        val vertexSource = ShaderLibrary.compileShader(
            ShaderLibrary.STANDARD_VERTEX_SHADER,
            features
        )
        val fragmentSource = ShaderLibrary.compileShader(
            ShaderLibrary.STANDARD_FRAGMENT_SHADER,
            features
        )

        val vertexModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "$name-vertex",
                code = vertexSource
            )
        )

        val fragmentModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "$name-fragment",
                code = fragmentSource
            )
        )

        // Build vertex buffer layout based on features
        val attributes = mutableListOf(
            GpuVertexAttribute(0, GpuVertexFormat.FLOAT32x3, 0), // position
            GpuVertexAttribute(1, GpuVertexFormat.FLOAT32x3, 12), // normal
            GpuVertexAttribute(2, GpuVertexFormat.FLOAT32x2, 24)  // uv
        )

        var stride = 32 // position + normal + uv

        // Create bind group layouts
        val bindGroupLayouts = mutableListOf<GpuBindGroupLayout>()

        // Group 0: Camera uniforms
        bindGroupLayouts.add(
            device.createBindGroupLayout(
                GpuBindGroupLayoutDescriptor(
                    label = "$name-camera-layout",
                    entries = listOf(
                        GpuBindGroupLayoutEntry(0, setOf(GpuShaderStage.VERTEX, GpuShaderStage.FRAGMENT), GpuBindingResourceType.UNIFORM_BUFFER),
                        GpuBindGroupLayoutEntry(1, setOf(GpuShaderStage.FRAGMENT), GpuBindingResourceType.UNIFORM_BUFFER)
                    )
                )
            )
        )

        // Group 1: Model uniforms
        bindGroupLayouts.add(
            device.createBindGroupLayout(
                GpuBindGroupLayoutDescriptor(
                    label = "$name-model-layout",
                    entries = listOf(
                        GpuBindGroupLayoutEntry(0, setOf(GpuShaderStage.VERTEX), GpuBindingResourceType.UNIFORM_BUFFER)
                    )
                )
            )
        )

        // Group 2: Material uniforms and textures
        val materialEntries = mutableListOf(
            GpuBindGroupLayoutEntry(0, setOf(GpuShaderStage.FRAGMENT), GpuBindingResourceType.UNIFORM_BUFFER)
        )
        if (ShaderFeature.USE_TEXTURE in features) {
            materialEntries.add(GpuBindGroupLayoutEntry(1, setOf(GpuShaderStage.FRAGMENT), GpuBindingResourceType.TEXTURE))
            materialEntries.add(GpuBindGroupLayoutEntry(2, setOf(GpuShaderStage.FRAGMENT), GpuBindingResourceType.SAMPLER))
        }
        bindGroupLayouts.add(
            device.createBindGroupLayout(
                GpuBindGroupLayoutDescriptor(
                    label = "$name-material-layout",
                    entries = materialEntries
                )
            )
        )

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "$name-pipeline",
                vertexShader = vertexModule,
                fragmentShader = fragmentModule,
                colorFormats = listOf(colorFormat),
                depthStencilFormat = depthFormat,
                vertexBuffers = listOf(
                    GpuVertexBufferLayout(
                        arrayStride = stride,
                        attributes = attributes
                    )
                ),
                cullMode = when (side) {
                    Side.FRONT -> GpuCullMode.BACK
                    Side.BACK -> GpuCullMode.FRONT
                    Side.DOUBLE -> GpuCullMode.NONE
                },
                depthState = if (depthFormat != null && depthTest) {
                    GpuDepthState(
                        format = depthFormat,
                        depthWriteEnabled = depthWrite,
                        depthCompare = GpuCompareFunction.LESS
                    )
                } else null,
                blendMode = if (transparent) GpuBlendMode.ALPHA else GpuBlendMode.DISABLED,
                bindGroupLayouts = bindGroupLayouts
            )
        )

        cachedPipeline = pipeline
        needsUpdate = false
        return pipeline
    }

    override fun createBindGroup(device: GpuDevice, layout: GpuBindGroupLayout): GpuBindGroup {
        val uniformBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "$name-material-uniforms",
                size = 64, // Enough for MaterialUniforms struct
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )

        updateUniforms(uniformBuffer)

        return device.createBindGroup(
            GpuBindGroupDescriptor(
                label = "$name-material-bind-group",
                layout = layout,
                entries = listOf(
                    GpuBindGroupEntry(0, GpuBindingResource.Buffer(uniformBuffer))
                )
            )
        )
    }

    override fun updateUniforms(buffer: GpuBuffer) {
        // MaterialUniforms:
        // baseColor: vec4 (16 bytes)
        // emissive: vec3 + metallic: f32 (16 bytes)
        // roughness: f32, alphaCutoff: f32, normalScale: f32, aoStrength: f32 (16 bytes)
        val data = FloatArray(16).apply {
            // baseColor
            this[0] = baseColor.r
            this[1] = baseColor.g
            this[2] = baseColor.b
            this[3] = opacity

            // emissive + metallic
            this[4] = emissive.r * emissiveIntensity
            this[5] = emissive.g * emissiveIntensity
            this[6] = emissive.b * emissiveIntensity
            this[7] = metallic

            // roughness, alphaCutoff, normalScale, aoStrength
            this[8] = roughness
            this[9] = alphaCutoff
            this[10] = normalScale
            this[11] = aoIntensity

            // padding
            this[12] = 0f
            this[13] = 0f
            this[14] = 0f
            this[15] = 0f
        }

        buffer.writeFloats(data)
    }

    override fun dispose() {
        if (_disposed) return
        _disposed = true
    }
}
