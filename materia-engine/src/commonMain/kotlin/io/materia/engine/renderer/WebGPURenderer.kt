/**
 * WebGPURenderer - Unified Cross-Platform Renderer
 *
 * A high-level renderer that owns GPUDevice and GPUQueue, providing
 * a Three.js-style render API across all platforms.
 */
package io.materia.engine.renderer

import io.materia.camera.Camera
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.scene.Object3D
import io.materia.core.scene.Scene
import io.materia.engine.core.Disposable
import io.materia.engine.core.DisposableContainer
import io.materia.engine.material.EngineMaterial
import io.materia.engine.scene.EngineMesh
import io.materia.gpu.*
import io.materia.renderer.RenderSurface

/**
 * Renderer configuration options.
 */
data class WebGPURendererConfig(
    /**
     * Whether to enable depth testing.
     */
    val depthTest: Boolean = true,

    /**
     * Clear color for the render target.
     */
    val clearColor: Color = Color(0f, 0f, 0f),

    /**
     * Clear alpha value.
     */
    val clearAlpha: Float = 1f,

    /**
     * Power preference for GPU adapter selection.
     */
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,

    /**
     * Whether to automatically resize when the surface changes.
     */
    val autoResize: Boolean = true,

    /**
     * Preferred texture format (auto-detected if null).
     */
    val preferredFormat: GpuTextureFormat? = null,

    /**
     * Number of MSAA samples (1 = disabled).
     */
    val antialias: Int = 1,

    /**
     * Enable debug labels and validation (development only).
     */
    val debug: Boolean = false
)

/**
 * Render statistics for performance monitoring.
 */
data class WebGPURenderStats(
    var frameCount: Long = 0,
    var drawCalls: Int = 0,
    var triangles: Int = 0,
    var textureBinds: Int = 0,
    var pipelineSwitches: Int = 0,
    var frameTime: Float = 0f
)

/**
 * WebGPU-based renderer providing a unified rendering API.
 *
 * This renderer:
 * - Owns and manages GPUDevice and GPUQueue
 * - Implements resource management via Disposable interface
 * - Works identically on JS (WebGPU) and JVM (Vulkan)
 * - Uses queue.writeBuffer for portable data uploads
 *
 * ## Usage
 *
 * ```kotlin
 * // Create renderer
 * val renderer = WebGPURenderer(surface, config)
 * renderer.initialize()
 *
 * // Main loop
 * renderLoop.start { deltaTime ->
 *     renderer.render(scene, camera)
 * }
 *
 * // Cleanup
 * renderer.dispose()
 * ```
 *
 * ## Resource Management
 *
 * All GPU resources created by this renderer are tracked and disposed
 * when the renderer is disposed. Scene objects (meshes, materials) should
 * implement Disposable for their own resources.
 */
class WebGPURenderer(
    private val config: WebGPURendererConfig = WebGPURendererConfig()
) : Disposable {

    private val resources = DisposableContainer()
    private var _disposed = false

    // GPU core objects
    private var instance: GpuInstance? = null
    private var adapter: GpuAdapter? = null
    private var device: GpuDevice? = null
    private var surface: GpuSurface? = null

    // Surface configuration
    private var surfaceFormat: GpuTextureFormat = GpuTextureFormat.BGRA8_UNORM
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    // Depth resources
    private var depthTexture: GpuTexture? = null
    private var depthTextureView: GpuTextureView? = null

    // Frame uniforms
    private var cameraUniformBuffer: GpuBuffer? = null
    private var lightUniformBuffer: GpuBuffer? = null
    private var frameBindGroup: GpuBindGroup? = null
    private var frameBindGroupLayout: GpuBindGroupLayout? = null

    // Pipeline cache
    private val pipelineCache = mutableMapOf<String, GpuRenderPipeline>()

    // Statistics
    val stats = WebGPURenderStats()

    // State
    private var isInitialized = false

    override val isDisposed: Boolean get() = _disposed

    /**
     * Initializes the renderer with the given render surface.
     *
     * This must be called before [render].
     */
    suspend fun initialize(renderSurface: RenderSurface) {
        if (isInitialized) return

        // Create GPU instance
        instance = createGpuInstance(
            GpuInstanceDescriptor(
                preferredBackends = listOf(GpuBackend.WEBGPU, GpuBackend.VULKAN),
                label = "materia-instance"
            )
        )

        // Request adapter
        adapter = instance!!.requestAdapter(
            GpuRequestAdapterOptions(
                powerPreference = config.powerPreference,
                label = "materia-adapter"
            )
        )

        // Request device
        device = adapter!!.requestDevice(
            GpuDeviceDescriptor(
                label = "materia-device"
            )
        )

        // Create and configure surface
        surfaceWidth = renderSurface.width.coerceAtLeast(1)
        surfaceHeight = renderSurface.height.coerceAtLeast(1)

        surface = GpuSurfaceFactory.create(
            device = device!!,
            renderSurface = renderSurface,
            label = "materia-surface",
            configuration = GpuSurfaceConfiguration(
                format = config.preferredFormat ?: GpuTextureFormat.BGRA8_UNORM,
                usage = gpuTextureUsage(GpuTextureUsage.RENDER_ATTACHMENT),
                width = surfaceWidth,
                height = surfaceHeight
            )
        )

        surfaceFormat = config.preferredFormat ?: surface!!.getPreferredFormat(adapter!!)

        // Create depth buffer
        createDepthResources()

        // Create frame uniform buffers
        createFrameUniforms()

        isInitialized = true
    }

    private fun createDepthResources() {
        if (!config.depthTest) return

        depthTexture?.destroy()
        depthTextureView = null

        depthTexture = device!!.createTexture(
            GpuTextureDescriptor(
                label = "depth-texture",
                size = Triple(surfaceWidth, surfaceHeight, 1),
                format = GpuTextureFormat.DEPTH24_PLUS,
                usage = gpuTextureUsage(GpuTextureUsage.RENDER_ATTACHMENT)
            )
        )

        depthTextureView = depthTexture!!.createView(
            GpuTextureViewDescriptor(label = "depth-view")
        )
    }

    private fun createFrameUniforms() {
        val dev = device ?: return

        // Camera uniforms: viewProj (64) + cameraPos (16) + near/far (8) + padding (8) = 96 bytes
        cameraUniformBuffer = dev.createBuffer(
            GpuBufferDescriptor(
                label = "camera-uniforms",
                size = 144, // Aligned to 16 bytes
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )

        // Directional light uniforms: direction (16) + color+intensity (16) = 32 bytes
        lightUniformBuffer = dev.createBuffer(
            GpuBufferDescriptor(
                label = "light-uniforms",
                size = 32,
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )

        // Create bind group layout for frame uniforms
        frameBindGroupLayout = dev.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                label = "frame-bind-group-layout",
                entries = listOf(
                    GpuBindGroupLayoutEntry(
                        binding = 0,
                        visibility = setOf(GpuShaderStage.VERTEX, GpuShaderStage.FRAGMENT),
                        resourceType = GpuBindingResourceType.UNIFORM_BUFFER
                    ),
                    GpuBindGroupLayoutEntry(
                        binding = 1,
                        visibility = setOf(GpuShaderStage.FRAGMENT),
                        resourceType = GpuBindingResourceType.UNIFORM_BUFFER
                    )
                )
            )
        )

        // Create bind group
        frameBindGroup = dev.createBindGroup(
            GpuBindGroupDescriptor(
                label = "frame-bind-group",
                layout = frameBindGroupLayout!!,
                entries = listOf(
                    GpuBindGroupEntry(0, GpuBindingResource.Buffer(cameraUniformBuffer!!)),
                    GpuBindGroupEntry(1, GpuBindingResource.Buffer(lightUniformBuffer!!))
                )
            )
        )
    }

    /**
     * Resizes the renderer to new dimensions.
     */
    fun setSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == surfaceWidth && height == surfaceHeight) return

        surfaceWidth = width
        surfaceHeight = height

        surface?.resize(width, height)
        createDepthResources()
    }

    /**
     * Renders the scene from the camera's viewpoint.
     */
    fun render(scene: Scene, camera: Camera) {
        if (!isInitialized || _disposed) return

        val dev = device ?: return
        val surf = surface ?: return

        stats.frameCount++
        stats.drawCalls = 0
        stats.triangles = 0
        stats.pipelineSwitches = 0

        // Update camera matrices
        camera.updateMatrixWorld()
        camera.updateProjectionMatrix()

        // Update scene matrices
        scene.updateMatrixWorld(true)

        // Update frame uniforms
        updateCameraUniforms(camera)
        updateLightUniforms(scene)

        // Acquire frame
        val frame = try {
            surf.acquireFrame()
        } catch (e: Exception) {
            println("Failed to acquire frame: ${e.message}")
            return
        }

        // Create command encoder
        val encoder = dev.createCommandEncoder(
            GpuCommandEncoderDescriptor(label = "frame-encoder")
        )

        // Begin render pass
        val colorAttachment = GpuRenderPassColorAttachment(
            view = frame.view,
            loadOp = GpuLoadOp.CLEAR,
            storeOp = GpuStoreOp.STORE,
            clearColor = floatArrayOf(
                config.clearColor.r,
                config.clearColor.g,
                config.clearColor.b,
                config.clearAlpha
            )
        )

        val depthAttachment = if (config.depthTest && depthTextureView != null) {
            GpuRenderPassDepthStencilAttachment(
                view = depthTextureView!!,
                depthLoadOp = GpuLoadOp.CLEAR,
                depthStoreOp = GpuStoreOp.STORE,
                depthClearValue = 1f
            )
        } else null

        val renderPass = encoder.beginRenderPass(
            GpuRenderPassDescriptor(
                label = "main-pass",
                colorAttachments = listOf(colorAttachment),
                depthStencilAttachment = depthAttachment
            )
        )

        // Render scene objects
        renderObject(renderPass, scene, camera)

        // End render pass
        renderPass.end()

        // Submit commands
        val commandBuffer = encoder.finish("frame-commands")
        dev.queue.submit(listOf(commandBuffer))

        // Present frame
        surf.present(frame)
    }

    private fun renderObject(pass: GpuRenderPassEncoder, obj: Object3D, camera: Camera) {
        if (!obj.visible) return

        // Render mesh objects
        if (obj is EngineMesh) {
            renderMesh(pass, obj, camera)
        }

        // Render children
        for (child in obj.children) {
            renderObject(pass, child, camera)
        }
    }

    private fun renderMesh(pass: GpuRenderPassEncoder, mesh: EngineMesh, camera: Camera) {
        val dev = device ?: return
        val material = mesh.engineMaterial ?: return

        if (!material.visible) return

        // Get or create pipeline for this material
        val pipeline = getOrCreatePipeline(material)

        // Update mesh uniforms
        mesh.updateModelUniform()

        // Get buffers
        val vertexBuffer = mesh.getVertexBuffer(dev)
        val indexBuffer = mesh.getIndexBuffer(dev)

        // Bind pipeline
        pass.setPipeline(pipeline)
        stats.pipelineSwitches++

        // Bind frame uniforms (group 0)
        pass.setBindGroup(0, frameBindGroup!!)

        // Bind model uniforms (group 1)
        val modelBindGroup = mesh.getBindGroup(dev, createModelBindGroupLayout())
        pass.setBindGroup(1, modelBindGroup)

        // Bind vertex buffer
        pass.setVertexBuffer(0, vertexBuffer)

        // Draw
        if (indexBuffer != null) {
            pass.setIndexBuffer(indexBuffer, mesh.indexFormat, 0)
            pass.drawIndexed(mesh.indexCount, 1, 0, 0, 0)
            stats.triangles += mesh.indexCount / 3
        } else {
            pass.draw(mesh.vertexCount, 1, 0, 0)
            stats.triangles += mesh.vertexCount / 3
        }

        stats.drawCalls++
    }

    private fun getOrCreatePipeline(material: EngineMaterial): GpuRenderPipeline {
        val key = material.name + material.getRequiredFeatures().hashCode()

        return pipelineCache.getOrPut(key) {
            material.createPipeline(
                device!!,
                surfaceFormat,
                if (config.depthTest) GpuTextureFormat.DEPTH24_PLUS else null
            )
        }
    }

    private var modelBindGroupLayout: GpuBindGroupLayout? = null

    private fun createModelBindGroupLayout(): GpuBindGroupLayout {
        if (modelBindGroupLayout != null) return modelBindGroupLayout!!

        modelBindGroupLayout = device!!.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                label = "model-bind-group-layout",
                entries = listOf(
                    GpuBindGroupLayoutEntry(
                        binding = 0,
                        visibility = setOf(GpuShaderStage.VERTEX),
                        resourceType = GpuBindingResourceType.UNIFORM_BUFFER
                    )
                )
            )
        )

        return modelBindGroupLayout!!
    }

    private fun updateCameraUniforms(camera: Camera) {
        val buffer = cameraUniformBuffer ?: return

        // Calculate view-projection matrix
        val viewProjection = Matrix4()
            .copy(camera.projectionMatrix)
            .multiply(camera.matrixWorldInverse)

        // CameraUniforms structure:
        // viewMatrix: mat4x4 (64 bytes)
        // projectionMatrix: mat4x4 (64 bytes) - but we'll use viewProj instead
        // viewProjectionMatrix: mat4x4 (64 bytes)
        // cameraPosition: vec3 (12 bytes) + padding (4 bytes)
        // near: f32, far: f32, padding: vec2

        val data = FloatArray(36) // 144 bytes / 4

        // View-projection matrix
        viewProjection.elements.copyInto(data, 0, 0, 16)

        // Camera position
        val worldPos = camera.getWorldPosition()
        data[16] = worldPos.x
        data[17] = worldPos.y
        data[18] = worldPos.z
        data[19] = 0f // padding

        // Near/far
        data[20] = camera.near
        data[21] = camera.far
        data[22] = 0f // padding
        data[23] = 0f // padding

        buffer.writeFloats(data)
    }

    private fun updateLightUniforms(scene: Scene) {
        val buffer = lightUniformBuffer ?: return

        // Default directional light (sun-like)
        val data = floatArrayOf(
            // direction (normalized)
            -0.5f, -1f, -0.5f, 0f,
            // color + intensity
            1f, 1f, 1f, 1f
        )

        buffer.writeFloats(data)
    }

    /**
     * Clears all cached pipelines.
     *
     * Call this when materials change significantly.
     */
    fun clearPipelineCache() {
        // Pipelines are managed by the device
        pipelineCache.clear()
    }

    override fun dispose() {
        if (_disposed) return
        _disposed = true

        pipelineCache.clear()

        depthTexture?.destroy()
        depthTexture = null
        depthTextureView = null

        cameraUniformBuffer?.destroy()
        lightUniformBuffer?.destroy()

        device?.destroy()
        instance?.dispose()

        resources.dispose()
        isInitialized = false
    }
}
