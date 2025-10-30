package io.materia.renderer.webgpu

/**
 * Kotlin external interfaces for WebGPU API.
 * Maps to @webgpu/types TypeScript definitions.
 *
 * References:
 * - https://www.w3.org/TR/webgpu/
 * - https://gpuweb.github.io/gpuweb/
 */

// ============================================================================
// Core WebGPU Types
// ============================================================================

external interface GPU {
    fun requestAdapter(options: GPURequestAdapterOptions = definedExternally): dynamic /* Promise<GPUAdapter?> */
    val wgslLanguageFeatures: dynamic /* WGSLLanguageFeatures */
}

external interface GPURequestAdapterOptions {
    var powerPreference: String? /* "low-power" | "high-performance" */
        get() = definedExternally
        set(value) = definedExternally
    var forceFallbackAdapter: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUAdapter {
    val features: dynamic /* GPUSupportedFeatures */
    val limits: GPUSupportedLimits
    val isFallbackAdapter: Boolean
    fun requestDevice(descriptor: GPUDeviceDescriptor = definedExternally): dynamic /* Promise<GPUDevice> */
}

external interface GPUDeviceDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var requiredFeatures: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var requiredLimits: dynamic
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUSupportedLimits {
    val maxTextureDimension1D: Int
    val maxTextureDimension2D: Int
    val maxTextureDimension3D: Int
    val maxTextureArrayLayers: Int
    val maxBindGroups: Int
    val maxDynamicUniformBuffersPerPipelineLayout: Int
    val maxDynamicStorageBuffersPerPipelineLayout: Int
    val maxSampledTexturesPerShaderStage: Int
    val maxSamplersPerShaderStage: Int
    val maxStorageBuffersPerShaderStage: Int
    val maxStorageTexturesPerShaderStage: Int
    val maxUniformBuffersPerShaderStage: Int
    val maxUniformBufferBindingSize: Int
    val maxStorageBufferBindingSize: Int
    val maxVertexBuffers: Int
    val maxVertexAttributes: Int
    val maxVertexBufferArrayStride: Int
    val maxInterStageShaderComponents: Int
    val maxComputeWorkgroupStorageSize: Int
    val maxComputeInvocationsPerWorkgroup: Int
    val maxComputeWorkgroupSizeX: Int
    val maxComputeWorkgroupSizeY: Int
    val maxComputeWorkgroupSizeZ: Int
    val maxComputeWorkgroupsPerDimension: Int
}

external interface GPUDevice {
    val features: dynamic /* GPUSupportedFeatures */
    val limits: GPUSupportedLimits
    val queue: GPUQueue
    val lost: dynamic /* Promise<GPUDeviceLostInfo> */

    fun destroy()
    fun createBuffer(descriptor: GPUBufferDescriptor): GPUBuffer
    fun createTexture(descriptor: GPUTextureDescriptor): GPUTexture
    fun createSampler(descriptor: GPUSamplerDescriptor = definedExternally): GPUSampler
    fun createBindGroupLayout(descriptor: GPUBindGroupLayoutDescriptor): GPUBindGroupLayout
    fun createPipelineLayout(descriptor: GPUPipelineLayoutDescriptor): GPUPipelineLayout
    fun createBindGroup(descriptor: GPUBindGroupDescriptor): GPUBindGroup
    fun createShaderModule(descriptor: GPUShaderModuleDescriptor): GPUShaderModule
    fun createRenderPipeline(descriptor: GPURenderPipelineDescriptor): GPURenderPipeline
    fun createCommandEncoder(descriptor: GPUCommandEncoderDescriptor = definedExternally): GPUCommandEncoder
}

// ============================================================================
// Buffer Types
// ============================================================================

external interface GPUBufferDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var size: Int
    var usage: Int /* GPUBufferUsageFlags */
    var mappedAtCreation: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUBuffer {
    val size: Int
    val usage: Int
    val mapState: String /* "unmapped" | "pending" | "mapped" */

    fun destroy()
    fun mapAsync(
        mode: Int,
        offset: Int = definedExternally,
        size: Int = definedExternally
    ): dynamic /* Promise<void> */

    fun getMappedRange(
        offset: Int = definedExternally,
        size: Int = definedExternally
    ): dynamic /* ArrayBuffer */

    fun unmap()
}

// ============================================================================
// Texture Types
// ============================================================================

external interface GPUTextureDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var size: dynamic /* GPUExtent3D */
    var mipLevelCount: Int?
        get() = definedExternally
        set(value) = definedExternally
    var sampleCount: Int?
        get() = definedExternally
        set(value) = definedExternally
    var dimension: String? /* "1d" | "2d" | "3d" */
        get() = definedExternally
        set(value) = definedExternally
    var format: String /* GPUTextureFormat */
    var usage: Int /* GPUTextureUsageFlags */
}

external interface GPUTexture {
    fun createView(descriptor: GPUTextureViewDescriptor = definedExternally): GPUTextureView
    fun destroy()
}

external interface GPUTextureViewDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var format: String?
        get() = definedExternally
        set(value) = definedExternally
    var dimension: String?
        get() = definedExternally
        set(value) = definedExternally
    var aspect: String?
        get() = definedExternally
        set(value) = definedExternally
    var baseMipLevel: Int?
        get() = definedExternally
        set(value) = definedExternally
    var mipLevelCount: Int?
        get() = definedExternally
        set(value) = definedExternally
    var baseArrayLayer: Int?
        get() = definedExternally
        set(value) = definedExternally
    var arrayLayerCount: Int?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUTextureView

external interface GPUSamplerDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var addressModeU: String?
        get() = definedExternally
        set(value) = definedExternally
    var addressModeV: String?
        get() = definedExternally
        set(value) = definedExternally
    var addressModeW: String?
        get() = definedExternally
        set(value) = definedExternally
    var magFilter: String?
        get() = definedExternally
        set(value) = definedExternally
    var minFilter: String?
        get() = definedExternally
        set(value) = definedExternally
    var mipmapFilter: String?
        get() = definedExternally
        set(value) = definedExternally
    var lodMinClamp: Float?
        get() = definedExternally
        set(value) = definedExternally
    var lodMaxClamp: Float?
        get() = definedExternally
        set(value) = definedExternally
    var compare: String?
        get() = definedExternally
        set(value) = definedExternally
    var maxAnisotropy: Int?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUSampler

// ============================================================================
// Shader Types
// ============================================================================

external interface GPUShaderModuleDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var code: String
}

external interface GPUShaderModule {
    fun getCompilationInfo(): dynamic /* Promise<GPUCompilationInfo> */
}

// ============================================================================
// Pipeline Types
// ============================================================================

external interface GPURenderPipelineDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var layout: dynamic /* GPUPipelineLayout | "auto" */
    var vertex: GPUVertexState
    var primitive: GPUPrimitiveState?
        get() = definedExternally
        set(value) = definedExternally
    var depthStencil: GPUDepthStencilState?
        get() = definedExternally
        set(value) = definedExternally
    var multisample: GPUMultisampleState?
        get() = definedExternally
        set(value) = definedExternally
    var fragment: GPUFragmentState?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUVertexState {
    var module: GPUShaderModule
    var entryPoint: String
    var buffers: Array<GPUVertexBufferLayout?>?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUVertexBufferLayout {
    var arrayStride: Int
    var stepMode: String? /* "vertex" | "instance" */
        get() = definedExternally
        set(value) = definedExternally
    var attributes: Array<GPUVertexAttribute>
}

external interface GPUVertexAttribute {
    var format: String /* GPUVertexFormat */
    var offset: Int
    var shaderLocation: Int
}

external interface GPUPrimitiveState {
    var topology: String? /* "point-list" | "line-list" | "line-strip" | "triangle-list" | "triangle-strip" */
        get() = definedExternally
        set(value) = definedExternally
    var stripIndexFormat: String?
        get() = definedExternally
        set(value) = definedExternally
    var frontFace: String? /* "ccw" | "cw" */
        get() = definedExternally
        set(value) = definedExternally
    var cullMode: String? /* "none" | "front" | "back" */
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUDepthStencilState {
    var format: String /* GPUTextureFormat */
    var depthWriteEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var depthCompare: String? /* GPUCompareFunction */
        get() = definedExternally
        set(value) = definedExternally
    var stencilFront: GPUStencilFaceState?
        get() = definedExternally
        set(value) = definedExternally
    var stencilBack: GPUStencilFaceState?
        get() = definedExternally
        set(value) = definedExternally
    var stencilReadMask: Int?
        get() = definedExternally
        set(value) = definedExternally
    var stencilWriteMask: Int?
        get() = definedExternally
        set(value) = definedExternally
    var depthBias: Int?
        get() = definedExternally
        set(value) = definedExternally
    var depthBiasSlopeScale: Float?
        get() = definedExternally
        set(value) = definedExternally
    var depthBiasClamp: Float?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUStencilFaceState {
    var compare: String?
        get() = definedExternally
        set(value) = definedExternally
    var failOp: String?
        get() = definedExternally
        set(value) = definedExternally
    var depthFailOp: String?
        get() = definedExternally
        set(value) = definedExternally
    var passOp: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUMultisampleState {
    var count: Int?
        get() = definedExternally
        set(value) = definedExternally
    var mask: Int?
        get() = definedExternally
        set(value) = definedExternally
    var alphaToCoverageEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUFragmentState {
    var module: GPUShaderModule
    var entryPoint: String
    var targets: Array<GPUColorTargetState?>
}

external interface GPUColorTargetState {
    var format: String /* GPUTextureFormat */
    var blend: GPUBlendState?
        get() = definedExternally
        set(value) = definedExternally
    var writeMask: Int?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUBlendState {
    var color: GPUBlendComponent
    var alpha: GPUBlendComponent
}

external interface GPUBlendComponent {
    var operation: String?
        get() = definedExternally
        set(value) = definedExternally
    var srcFactor: String?
        get() = definedExternally
        set(value) = definedExternally
    var dstFactor: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPURenderPipeline

external interface GPUPipelineLayoutDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var bindGroupLayouts: Array<GPUBindGroupLayout>
}

external interface GPUPipelineLayout

// ============================================================================
// Bind Group Types
// ============================================================================

external interface GPUBindGroupLayoutDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var entries: Array<GPUBindGroupLayoutEntry>
}

external interface GPUBindGroupLayoutEntry {
    var binding: Int
    var visibility: Int /* GPUShaderStageFlags */
    var buffer: GPUBufferBindingLayout?
        get() = definedExternally
        set(value) = definedExternally
    var sampler: GPUSamplerBindingLayout?
        get() = definedExternally
        set(value) = definedExternally
    var texture: GPUTextureBindingLayout?
        get() = definedExternally
        set(value) = definedExternally
    var storageTexture: GPUStorageTextureBindingLayout?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUBufferBindingLayout {
    var type: String? /* "uniform" | "storage" | "read-only-storage" */
        get() = definedExternally
        set(value) = definedExternally
    var hasDynamicOffset: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var minBindingSize: Int?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUSamplerBindingLayout {
    var type: String? /* "filtering" | "non-filtering" | "comparison" */
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUTextureBindingLayout {
    var sampleType: String? /* "float" | "unfilterable-float" | "depth" | "sint" | "uint" */
        get() = definedExternally
        set(value) = definedExternally
    var viewDimension: String?
        get() = definedExternally
        set(value) = definedExternally
    var multisampled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUStorageTextureBindingLayout {
    var access: String? /* "write-only" | "read-only" | "read-write" */
        get() = definedExternally
        set(value) = definedExternally
    var format: String
    var viewDimension: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUBindGroupLayout

external interface GPUBindGroupDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var layout: GPUBindGroupLayout
    var entries: Array<GPUBindGroupEntry>
}

external interface GPUBindGroupEntry {
    var binding: Int
    var resource: dynamic /* GPUBufferBinding | GPUSampler | GPUTextureView */
}

external interface GPUBufferBinding {
    var buffer: GPUBuffer
    var offset: Int?
        get() = definedExternally
        set(value) = definedExternally
    var size: Int?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUBindGroup

// ============================================================================
// Command Encoding Types
// ============================================================================

external interface GPUCommandEncoderDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUCommandEncoder {
    fun beginRenderPass(descriptor: GPURenderPassDescriptor): GPURenderPassEncoder
    fun finish(descriptor: GPUCommandBufferDescriptor = definedExternally): GPUCommandBuffer
}

external interface GPURenderPassDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
    var colorAttachments: Array<GPURenderPassColorAttachment?>
    var depthStencilAttachment: GPURenderPassDepthStencilAttachment?
        get() = definedExternally
        set(value) = definedExternally
    var occlusionQuerySet: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var timestampWrites: dynamic
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPURenderPassColorAttachment {
    var view: GPUTextureView
    var resolveTarget: GPUTextureView?
        get() = definedExternally
        set(value) = definedExternally
    var clearValue: dynamic /* GPUColor */
        get() = definedExternally
        set(value) = definedExternally
    var loadOp: String /* "load" | "clear" */
    var storeOp: String /* "store" | "discard" */
}

external interface GPURenderPassDepthStencilAttachment {
    var view: GPUTextureView
    var depthClearValue: Float?
        get() = definedExternally
        set(value) = definedExternally
    var depthLoadOp: String? /* "load" | "clear" */
        get() = definedExternally
        set(value) = definedExternally
    var depthStoreOp: String? /* "store" | "discard" */
        get() = definedExternally
        set(value) = definedExternally
    var depthReadOnly: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var stencilClearValue: Int?
        get() = definedExternally
        set(value) = definedExternally
    var stencilLoadOp: String?
        get() = definedExternally
        set(value) = definedExternally
    var stencilStoreOp: String?
        get() = definedExternally
        set(value) = definedExternally
    var stencilReadOnly: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPURenderPassEncoder {
    fun setPipeline(pipeline: GPURenderPipeline)
    fun setBindGroup(
        index: Int,
        bindGroup: GPUBindGroup?,
        dynamicOffsets: IntArray = definedExternally
    )

    fun setVertexBuffer(
        slot: Int,
        buffer: GPUBuffer?,
        offset: Int = definedExternally,
        size: Int = definedExternally
    )

    fun setIndexBuffer(
        buffer: GPUBuffer,
        indexFormat: String,
        offset: Int = definedExternally,
        size: Int = definedExternally
    )

    fun draw(
        vertexCount: Int,
        instanceCount: Int = definedExternally,
        firstVertex: Int = definedExternally,
        firstInstance: Int = definedExternally
    )

    fun drawIndexed(
        indexCount: Int,
        instanceCount: Int = definedExternally,
        firstIndex: Int = definedExternally,
        baseVertex: Int = definedExternally,
        firstInstance: Int = definedExternally
    )

    fun end()
}

external interface GPUCommandBufferDescriptor {
    var label: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GPUCommandBuffer

// ============================================================================
// Queue Types
// ============================================================================

external interface GPUQueue {
    fun submit(commandBuffers: Array<GPUCommandBuffer>)
    fun writeBuffer(
        buffer: GPUBuffer,
        bufferOffset: Int,
        data: dynamic,
        dataOffset: Int = definedExternally,
        size: Int = definedExternally
    )

    fun writeTexture(destination: dynamic, data: dynamic, dataLayout: dynamic, size: dynamic)
}

// ============================================================================
// Canvas Context Types
// ============================================================================

external interface GPUCanvasContext {
    fun configure(configuration: GPUCanvasConfiguration)
    fun unconfigure()
    fun getCurrentTexture(): GPUTexture
}

external interface GPUCanvasConfiguration {
    var device: GPUDevice
    var format: String /* GPUTextureFormat */
    var usage: Int?
        get() = definedExternally
        set(value) = definedExternally
    var alphaMode: String?
        get() = definedExternally
        set(value) = definedExternally
}
