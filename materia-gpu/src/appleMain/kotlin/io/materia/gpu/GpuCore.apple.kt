package io.materia.gpu

import io.materia.renderer.RenderSurface
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.CommandEncoderDescriptor
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.ComputePipelineDescriptor
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.DeviceDescriptor
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUComputePipeline
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.GPUVertexStepMode
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.ProgrammableStage
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.RenderingContext
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.writeBuffer
import kotlinx.coroutines.runBlocking

internal data class AppleWgpuContextBundle(
    val context: WGPUContext,
    val resize: ((width: Int, height: Int) -> Unit)? = null
)

internal object AppleWgpuContextHolder {
    var bundle: AppleWgpuContextBundle? = null
        private set

    val wgpuContext: WGPUContext?
        get() = bundle?.context

    fun install(newBundle: AppleWgpuContextBundle) {
        close()
        bundle = newBundle
    }

    fun close() {
        bundle?.context?.close()
        bundle = null
    }
}

internal expect suspend fun createAppleWgpuContextBundle(surface: RenderSurface): AppleWgpuContextBundle

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance {
    return GpuInstance(descriptor)
}

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter {
        check(!disposed) { "GpuInstance has been disposed." }
        val ctx = AppleWgpuContextHolder.wgpuContext
            ?: error("Apple GPU context not initialized. Call initializeGpuContext(surface) first.")
        val resolvedInfo = runCatching { ctx.adapter.info }.getOrNull()

        return GpuAdapter(
            backend = GpuBackend.MOLTENVK,
            options = options,
            info = GpuAdapterInfo(
                name = resolvedInfo?.device ?: "Apple GPU",
                vendor = resolvedInfo?.vendor,
                architecture = resolvedInfo?.architecture,
                driverVersion = resolvedInfo?.description
            )
        )
    }

    actual fun dispose() {
        disposed = true
        AppleWgpuContextHolder.close()
    }
}

actual class GpuAdapter actual constructor(
    actual val backend: GpuBackend,
    actual val options: GpuRequestAdapterOptions,
    actual val info: GpuAdapterInfo
) {
    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice {
        AppleWgpuContextHolder.wgpuContext
            ?: error("wgpu4k context not available")
        return GpuDevice(this, descriptor)
    }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    internal val wgpuDevice: GPUDevice
        get() = AppleWgpuContextHolder.wgpuContext?.device
            ?: error("wgpu4k context not available")

    actual val queue: GpuQueue = GpuQueue(descriptor.label)

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        val wgpuBuffer = wgpuDevice.createBuffer(
            BufferDescriptor(
                label = descriptor.label ?: "",
                size = descriptor.size.toULong(),
                usage = descriptor.usage.toWgpuBufferUsage(),
                mappedAtCreation = descriptor.mappedAtCreation
            )
        )
        return GpuBuffer(this, descriptor, wgpuBuffer)
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val (width, height, depth) = descriptor.size
        val wgpuTexture = wgpuDevice.createTexture(
            TextureDescriptor(
                label = descriptor.label ?: "",
                size = io.ygdrasil.webgpu.Extent3D(width.toUInt(), height.toUInt(), depth.toUInt()),
                mipLevelCount = descriptor.mipLevelCount.toUInt(),
                sampleCount = descriptor.sampleCount.toUInt(),
                dimension = descriptor.dimension.toWgpu(),
                format = descriptor.format.toWgpu(),
                usage = descriptor.usage.toWgpuTextureUsage()
            )
        )
        return GpuTexture(this, descriptor, wgpuTexture)
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val wgpuSampler = wgpuDevice.createSampler(
            SamplerDescriptor(
                label = descriptor.label ?: "",
                addressModeU = descriptor.addressModeU.toWgpu(),
                addressModeV = descriptor.addressModeV.toWgpu(),
                addressModeW = descriptor.addressModeW.toWgpu(),
                magFilter = descriptor.magFilter.toWgpu(),
                minFilter = descriptor.minFilter.toWgpu(),
                mipmapFilter = descriptor.mipmapFilter.toWgpu(),
                lodMinClamp = descriptor.lodMinClamp,
                lodMaxClamp = descriptor.lodMaxClamp
            )
        )
        return GpuSampler(this, descriptor, wgpuSampler)
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        val entries = descriptor.entries.map { entry ->
            BindGroupLayoutEntry(
                binding = entry.binding.toUInt(),
                visibility = entry.visibility.toWgpu(),
                buffer = when (entry.resourceType) {
                    GpuBindingResourceType.UNIFORM_BUFFER -> BufferBindingLayout(type = GPUBufferBindingType.Uniform)
                    GpuBindingResourceType.STORAGE_BUFFER -> BufferBindingLayout(type = GPUBufferBindingType.Storage)
                    else -> null
                },
                sampler = when (entry.resourceType) {
                    GpuBindingResourceType.SAMPLER -> SamplerBindingLayout()
                    else -> null
                },
                texture = when (entry.resourceType) {
                    GpuBindingResourceType.TEXTURE -> TextureBindingLayout()
                    else -> null
                }
            )
        }
        val wgpuLayout = wgpuDevice.createBindGroupLayout(
            BindGroupLayoutDescriptor(label = descriptor.label ?: "", entries = entries)
        )
        return GpuBindGroupLayout(this, descriptor, wgpuLayout)
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        val entries = descriptor.entries.map { entry ->
            BindGroupEntry(
                binding = entry.binding.toUInt(),
                resource = when (val resource = entry.resource) {
                    is GpuBindingResource.Buffer -> BufferBinding(
                        buffer = resource.buffer.wgpuBuffer,
                        offset = resource.offset.toULong(),
                        size = resource.size?.toULong() ?: resource.buffer.descriptor.size.toULong()
                    )

                    is GpuBindingResource.Sampler -> resource.sampler.wgpuSampler
                    is GpuBindingResource.Texture -> resource.textureView.wgpuTextureView
                }
            )
        }
        val wgpuBindGroup = wgpuDevice.createBindGroup(
            BindGroupDescriptor(
                label = descriptor.label ?: "",
                layout = descriptor.layout.wgpuLayout,
                entries = entries
            )
        )
        return GpuBindGroup(descriptor.layout, descriptor, wgpuBindGroup)
    }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder {
        val wgpuEncoder = wgpuDevice.createCommandEncoder(
            descriptor?.let { CommandEncoderDescriptor(label = it.label ?: "") }
        )
        return GpuCommandEncoder(this, descriptor, wgpuEncoder)
    }

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule {
        val wgpuModule = wgpuDevice.createShaderModule(
            ShaderModuleDescriptor(label = descriptor.label ?: "", code = descriptor.code)
        )
        return GpuShaderModule(this, descriptor, wgpuModule)
    }

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline {
        val vertexBuffers = descriptor.vertexBuffers.map { buffer ->
            VertexBufferLayout(
                arrayStride = buffer.arrayStride.toULong(),
                stepMode = buffer.stepMode.toWgpu(),
                attributes = buffer.attributes.map { attribute ->
                    VertexAttribute(
                        format = attribute.format.toWgpu(),
                        offset = attribute.offset.toULong(),
                        shaderLocation = attribute.shaderLocation.toUInt()
                    )
                }
            )
        }

        val fragmentState = descriptor.fragmentShader?.let { fragmentShader ->
            FragmentState(
                module = fragmentShader.wgpuModule,
                entryPoint = "main",
                targets = descriptor.colorFormats.map { format ->
                    ColorTargetState(
                        format = format.toWgpu(),
                        blend = descriptor.blendMode.toWgpu()
                    )
                }
            )
        }

        val depthStencil = descriptor.depthState?.let { depthState ->
            DepthStencilState(
                format = depthState.format.toWgpu(),
                depthWriteEnabled = depthState.depthWriteEnabled,
                depthCompare = depthState.depthCompare.toWgpu()
            )
        }

        val layout: GPUPipelineLayout? = if (descriptor.bindGroupLayouts.isNotEmpty()) {
            wgpuDevice.createPipelineLayout(
                PipelineLayoutDescriptor(
                    bindGroupLayouts = descriptor.bindGroupLayouts.map { it.wgpuLayout }
                )
            )
        } else {
            null
        }

        val wgpuPipeline = wgpuDevice.createRenderPipeline(
            RenderPipelineDescriptor(
                label = descriptor.label ?: "",
                layout = layout,
                vertex = VertexState(
                    module = descriptor.vertexShader.wgpuModule,
                    entryPoint = "main",
                    buffers = vertexBuffers
                ),
                fragment = fragmentState,
                primitive = PrimitiveState(
                    topology = descriptor.primitiveTopology.toWgpu(),
                    frontFace = descriptor.frontFace.toWgpu(),
                    cullMode = descriptor.cullMode.toWgpu()
                ),
                depthStencil = depthStencil
            )
        )
        return GpuRenderPipeline(this, descriptor, wgpuPipeline)
    }

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline {
        val wgpuPipeline = wgpuDevice.createComputePipeline(
            ComputePipelineDescriptor(
                label = descriptor.label ?: "",
                compute = ProgrammableStage(
                    module = descriptor.shader.wgpuModule,
                    entryPoint = "main"
                )
            )
        )
        return GpuComputePipeline(this, descriptor, wgpuPipeline)
    }

    actual fun destroy() {
        wgpuDevice.close()
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    private val wgpuQueue
        get() = AppleWgpuContextHolder.wgpuContext?.device?.queue
            ?: error("wgpu4k context not available")

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (commandBuffers.isEmpty()) return
        wgpuQueue.submit(commandBuffers.map { it.wgpuCommandBuffer })
    }

    fun writeBuffer(buffer: GpuBuffer, offset: Long, data: ByteArray) {
        wgpuQueue.writeBuffer(buffer.wgpuBuffer, offset.toULong(), data)
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    private var configuredDevice: GpuDevice? = null
    private var currentConfiguration: GpuSurfaceConfiguration? = null
    private var width: Int = 0
    private var height: Int = 0

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        val ctx = AppleWgpuContextHolder.wgpuContext ?: error("wgpu4k context not available")
        configuredDevice = device
        width = configuration.width.coerceAtLeast(1)
        height = configuration.height.coerceAtLeast(1)
        currentConfiguration = configuration.copy(width = width, height = height)
        ctx.surface.configure(
            SurfaceConfiguration(
                device = device.wgpuDevice,
                format = configuration.format.toWgpu(),
                usage = configuration.usage.toWgpuTextureUsage(),
                alphaMode = configuration.alphaMode.toWgpu()
            )
        )
    }

    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat {
        val ctx = AppleWgpuContextHolder.wgpuContext ?: error("wgpu4k context not available")
        return ctx.surface.supportedFormats.find { it == GPUTextureFormat.BGRA8Unorm }
            ?.toMateriaFormat()
            ?: ctx.renderingContext.textureFormat.toMateriaFormat()
    }

    actual fun acquireFrame(): GpuSurfaceFrame {
        val ctx = AppleWgpuContextHolder.wgpuContext ?: error("wgpu4k context not available")
        val device = configuredDevice ?: error("Surface not configured")
        val currentTexture = ctx.renderingContext.getCurrentTexture()
        val texture = GpuTexture(
            device,
            GpuTextureDescriptor(
                size = Triple(width.coerceAtLeast(1), height.coerceAtLeast(1), 1),
                format = ctx.renderingContext.textureFormat.toMateriaFormat(),
                usage = GpuTextureUsage.RENDER_ATTACHMENT.mask
            ),
            currentTexture
        )
        val view = texture.createView(GpuTextureViewDescriptor())
        return GpuSurfaceFrame(texture, view)
    }

    actual fun present(frame: GpuSurfaceFrame) {
        AppleWgpuContextHolder.wgpuContext?.surface?.present()
            ?: error("wgpu4k context not available")
    }

    actual fun resize(width: Int, height: Int) {
        val nextWidth = width.coerceAtLeast(1)
        val nextHeight = height.coerceAtLeast(1)
        this.width = nextWidth
        this.height = nextHeight
        AppleWgpuContextHolder.bundle?.resize?.invoke(nextWidth, nextHeight)
        val device = configuredDevice ?: return
        val configuration = currentConfiguration ?: return
        configure(device, configuration.copy(width = nextWidth, height = nextHeight))
    }
}

actual suspend fun initializeGpuContext(surface: RenderSurface) {
    AppleWgpuContextHolder.install(createAppleWgpuContextBundle(surface))
}

actual fun GpuSurface.attachRenderSurface(surface: RenderSurface) {
    if (AppleWgpuContextHolder.wgpuContext == null) {
        runBlocking {
            initializeGpuContext(surface)
        }
    }
}

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = wgpuLayout

actual fun GpuBindGroup.unwrapHandle(): Any? = wgpuBindGroup

internal fun GpuBufferUsageFlags.toWgpuBufferUsage(): Set<GPUBufferUsage> = buildSet {
    if (this@toWgpuBufferUsage and GpuBufferUsage.MAP_READ.mask != 0) add(GPUBufferUsage.MapRead)
    if (this@toWgpuBufferUsage and GpuBufferUsage.MAP_WRITE.mask != 0) add(GPUBufferUsage.MapWrite)
    if (this@toWgpuBufferUsage and GpuBufferUsage.COPY_SRC.mask != 0) add(GPUBufferUsage.CopySrc)
    if (this@toWgpuBufferUsage and GpuBufferUsage.COPY_DST.mask != 0) add(GPUBufferUsage.CopyDst)
    if (this@toWgpuBufferUsage and GpuBufferUsage.INDEX.mask != 0) add(GPUBufferUsage.Index)
    if (this@toWgpuBufferUsage and GpuBufferUsage.VERTEX.mask != 0) add(GPUBufferUsage.Vertex)
    if (this@toWgpuBufferUsage and GpuBufferUsage.UNIFORM.mask != 0) add(GPUBufferUsage.Uniform)
    if (this@toWgpuBufferUsage and GpuBufferUsage.STORAGE.mask != 0) add(GPUBufferUsage.Storage)
    if (this@toWgpuBufferUsage and GpuBufferUsage.INDIRECT.mask != 0) add(GPUBufferUsage.Indirect)
}

internal fun GpuTextureUsageFlags.toWgpuTextureUsage(): Set<GPUTextureUsage> = buildSet {
    if (this@toWgpuTextureUsage and GpuTextureUsage.COPY_SRC.mask != 0) add(GPUTextureUsage.CopySrc)
    if (this@toWgpuTextureUsage and GpuTextureUsage.COPY_DST.mask != 0) add(GPUTextureUsage.CopyDst)
    if (this@toWgpuTextureUsage and GpuTextureUsage.TEXTURE_BINDING.mask != 0) add(GPUTextureUsage.TextureBinding)
    if (this@toWgpuTextureUsage and GpuTextureUsage.STORAGE_BINDING.mask != 0) add(GPUTextureUsage.StorageBinding)
    if (this@toWgpuTextureUsage and GpuTextureUsage.RENDER_ATTACHMENT.mask != 0) add(GPUTextureUsage.RenderAttachment)
}

internal fun GpuTextureDimension.toWgpu(): GPUTextureDimension = when (this) {
    GpuTextureDimension.D1 -> GPUTextureDimension.OneD
    GpuTextureDimension.D2 -> GPUTextureDimension.TwoD
    GpuTextureDimension.D3 -> GPUTextureDimension.ThreeD
}

internal fun GpuTextureFormat.toWgpu(): GPUTextureFormat = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> GPUTextureFormat.RGBA8Unorm
    GpuTextureFormat.BGRA8_UNORM -> GPUTextureFormat.BGRA8Unorm
    GpuTextureFormat.RGBA16_FLOAT -> GPUTextureFormat.RGBA16Float
    GpuTextureFormat.DEPTH24_PLUS -> GPUTextureFormat.Depth24Plus
}

internal fun GPUTextureFormat.toMateriaFormat(): GpuTextureFormat = when (this) {
    GPUTextureFormat.RGBA8Unorm -> GpuTextureFormat.RGBA8_UNORM
    GPUTextureFormat.RGBA8UnormSrgb -> GpuTextureFormat.RGBA8_UNORM
    GPUTextureFormat.BGRA8Unorm -> GpuTextureFormat.BGRA8_UNORM
    GPUTextureFormat.BGRA8UnormSrgb -> GpuTextureFormat.BGRA8_UNORM
    GPUTextureFormat.RGBA16Float -> GpuTextureFormat.RGBA16_FLOAT
    GPUTextureFormat.Depth24Plus -> GpuTextureFormat.DEPTH24_PLUS
    else -> GpuTextureFormat.BGRA8_UNORM
}

internal fun GpuAddressMode.toWgpu(): GPUAddressMode = when (this) {
    GpuAddressMode.CLAMP_TO_EDGE -> GPUAddressMode.ClampToEdge
    GpuAddressMode.REPEAT -> GPUAddressMode.Repeat
    GpuAddressMode.MIRROR_REPEAT -> GPUAddressMode.MirrorRepeat
}

internal fun GpuFilterMode.toWgpu(): GPUFilterMode = when (this) {
    GpuFilterMode.NEAREST -> GPUFilterMode.Nearest
    GpuFilterMode.LINEAR -> GPUFilterMode.Linear
}

internal fun GpuMipmapFilterMode.toWgpu(): io.ygdrasil.webgpu.GPUMipmapFilterMode = when (this) {
    GpuMipmapFilterMode.NEAREST -> io.ygdrasil.webgpu.GPUMipmapFilterMode.Nearest
    GpuMipmapFilterMode.LINEAR -> io.ygdrasil.webgpu.GPUMipmapFilterMode.Linear
}

internal fun Set<GpuShaderStage>.toWgpu(): Set<GPUShaderStage> = map { stage ->
    when (stage) {
        GpuShaderStage.VERTEX -> GPUShaderStage.Vertex
        GpuShaderStage.FRAGMENT -> GPUShaderStage.Fragment
        GpuShaderStage.COMPUTE -> GPUShaderStage.Compute
    }
}.toSet()

internal fun GpuVertexStepMode.toWgpu(): GPUVertexStepMode = when (this) {
    GpuVertexStepMode.VERTEX -> GPUVertexStepMode.Vertex
    GpuVertexStepMode.INSTANCE -> GPUVertexStepMode.Instance
}

internal fun GpuVertexFormat.toWgpu(): GPUVertexFormat = when (this) {
    GpuVertexFormat.FLOAT32 -> GPUVertexFormat.Float32
    GpuVertexFormat.FLOAT32x2 -> GPUVertexFormat.Float32x2
    GpuVertexFormat.FLOAT32x3 -> GPUVertexFormat.Float32x3
    GpuVertexFormat.FLOAT32x4 -> GPUVertexFormat.Float32x4
    GpuVertexFormat.UINT32 -> GPUVertexFormat.Uint32
    GpuVertexFormat.UINT32x2 -> GPUVertexFormat.Uint32x2
    GpuVertexFormat.UINT32x3 -> GPUVertexFormat.Uint32x3
    GpuVertexFormat.UINT32x4 -> GPUVertexFormat.Uint32x4
    GpuVertexFormat.SINT32 -> GPUVertexFormat.Sint32
    GpuVertexFormat.SINT32x2 -> GPUVertexFormat.Sint32x2
    GpuVertexFormat.SINT32x3 -> GPUVertexFormat.Sint32x3
    GpuVertexFormat.SINT32x4 -> GPUVertexFormat.Sint32x4
}

internal fun GpuPrimitiveTopology.toWgpu(): GPUPrimitiveTopology = when (this) {
    GpuPrimitiveTopology.POINT_LIST -> GPUPrimitiveTopology.PointList
    GpuPrimitiveTopology.LINE_LIST -> GPUPrimitiveTopology.LineList
    GpuPrimitiveTopology.LINE_STRIP -> GPUPrimitiveTopology.LineStrip
    GpuPrimitiveTopology.TRIANGLE_LIST -> GPUPrimitiveTopology.TriangleList
    GpuPrimitiveTopology.TRIANGLE_STRIP -> GPUPrimitiveTopology.TriangleStrip
}

internal fun GpuFrontFace.toWgpu(): GPUFrontFace = when (this) {
    GpuFrontFace.CCW -> GPUFrontFace.CCW
    GpuFrontFace.CW -> GPUFrontFace.CW
}

internal fun GpuCullMode.toWgpu(): GPUCullMode = when (this) {
    GpuCullMode.NONE -> GPUCullMode.None
    GpuCullMode.FRONT -> GPUCullMode.Front
    GpuCullMode.BACK -> GPUCullMode.Back
}

internal fun GpuCompareFunction.toWgpu(): GPUCompareFunction = when (this) {
    GpuCompareFunction.ALWAYS -> GPUCompareFunction.Always
    GpuCompareFunction.LESS -> GPUCompareFunction.Less
    GpuCompareFunction.LESS_EQUAL -> GPUCompareFunction.LessEqual
}

internal fun GpuBlendMode.toWgpu(): BlendState? = when (this) {
    GpuBlendMode.DISABLED -> null
    GpuBlendMode.ALPHA -> BlendState(
        color = BlendComponent(
            srcFactor = GPUBlendFactor.SrcAlpha,
            dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
            operation = GPUBlendOperation.Add
        ),
        alpha = BlendComponent(
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
            operation = GPUBlendOperation.Add
        )
    )

    GpuBlendMode.ADDITIVE -> BlendState(
        color = BlendComponent(
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.One,
            operation = GPUBlendOperation.Add
        ),
        alpha = BlendComponent(
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.One,
            operation = GPUBlendOperation.Add
        )
    )
}

internal fun GpuCompositeAlphaMode.toWgpu(): CompositeAlphaMode = when (this) {
    GpuCompositeAlphaMode.AUTO -> CompositeAlphaMode.Auto
    GpuCompositeAlphaMode.OPAQUE -> CompositeAlphaMode.Opaque
    GpuCompositeAlphaMode.PREMULTIPLIED -> CompositeAlphaMode.Premultiplied
    GpuCompositeAlphaMode.UNPREMULTIPLIED -> CompositeAlphaMode.Unpremultiplied
    GpuCompositeAlphaMode.INHERIT -> CompositeAlphaMode.Inherit
}