package io.materia.engine.render

import io.materia.engine.camera.PerspectiveCamera
import io.materia.engine.math.mat4
import io.materia.engine.scene.InstancedPoints
import io.materia.engine.scene.Mesh
import io.materia.engine.scene.Scene
import io.materia.gpu.GpuAdapter
import io.materia.gpu.GpuAdapterInfo
import io.materia.gpu.GpuBackend
import io.materia.gpu.GpuBindGroup
import io.materia.gpu.GpuCommandEncoderDescriptor
import io.materia.gpu.GpuDevice
import io.materia.gpu.GpuDeviceDescriptor
import io.materia.gpu.GpuFilterMode
import io.materia.gpu.GpuInstance
import io.materia.gpu.GpuInstanceDescriptor
import io.materia.gpu.GpuLoadOp
import io.materia.gpu.GpuPowerPreference
import io.materia.gpu.GpuRenderPassColorAttachment
import io.materia.gpu.GpuRenderPassDepthStencilAttachment
import io.materia.gpu.GpuRenderPassDescriptor
import io.materia.gpu.GpuStoreOp
import io.materia.gpu.GpuRequestAdapterOptions
import io.materia.gpu.GpuSampler
import io.materia.gpu.GpuSamplerDescriptor
import io.materia.gpu.GpuSurface
import io.materia.gpu.GpuSurfaceFactory
import io.materia.gpu.GpuTexture
import io.materia.gpu.GpuTextureDescriptor
import io.materia.gpu.GpuTextureDimension
import io.materia.gpu.GpuTextureFormat
import io.materia.gpu.GpuTextureUsage
import io.materia.gpu.GpuTextureView
import io.materia.gpu.createGpuInstance
import io.materia.gpu.gpuTextureUsage
import io.materia.renderer.BackendType
import io.materia.renderer.RenderSurface
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.RendererInitializationException
import io.materia.renderer.PowerPreference
import io.materia.core.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Thin renderer implementation that delegates draw submission to [SceneRenderer].
 *
 * This provides a minimal, engine-focused render loop that bypasses the legacy
 * renderer stack and speaks directly to the multiplatform GPU layer.
 */
interface EngineRenderer {
    suspend fun initialize(): Result<Unit>
    fun render(scene: Scene, camera: PerspectiveCamera)
    fun resize(width: Int, height: Int)
    fun dispose()
    val backend: BackendType
    val deviceName: String
    val driverVersion: String
    var fxaaEnabled: Boolean
}

data class EngineRendererOptions(
    val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    val clearColor: FloatArray = floatArrayOf(0.05f, 0.05f, 0.1f, 1f),
    val enableFxaa: Boolean = false
)

suspend fun RendererFactory.createEngineRenderer(
    surface: RenderSurface,
    config: RendererConfig = RendererConfig(),
    options: EngineRendererOptions = EngineRendererOptions()
): Result<EngineRenderer> {
    val preferredBackends = config.preferredBackend?.let { listOf(it.toGpuBackend()) }
        ?: options.preferredBackends
    val resolvedOptions = options.copy(
        preferredBackends = preferredBackends,
        powerPreference = config.powerPreference.toGpuPowerPreference()
    )
    val renderer = EngineRendererImpl(surface, config, resolvedOptions)
    return renderer.initialize().map { renderer }
}

private class EngineRendererImpl(
    private val surface: RenderSurface,
    private val config: RendererConfig,
    private val options: EngineRendererOptions
) : EngineRenderer {

    private val initMutex = Mutex()
    private var initialized = false

    private lateinit var gpuInstance: GpuInstance
    private lateinit var adapter: GpuAdapter
    private lateinit var adapterInfo: GpuAdapterInfo
    private lateinit var device: GpuDevice
    private lateinit var gpuSurface: GpuSurface
    private lateinit var sceneRenderer: SceneRenderer
    private lateinit var surfaceFormat: GpuTextureFormat
    private var backendType: BackendType = BackendType.WEBGPU
    private var fxaaResources: PostProcessPipelineFactory.PipelineResources? = null
    private var fxaaSampler: GpuSampler? = null
    private var fxaaBindGroup: GpuBindGroup? = null
    private var fxaaBindGroupTexture: GpuTexture? = null
    private var offscreenTexture: GpuTexture? = null
    private var offscreenView: GpuTextureView? = null
    private var fxaaWidth: Int = 0
    private var fxaaHeight: Int = 0

    private var depthTexture: GpuTexture? = null
    private var depthView: GpuTextureView? = null
    private var depthWidth: Int = 0
    private var depthHeight: Int = 0

    private var width: Int = max(surface.width, 1)
    private var height: Int = max(surface.height, 1)

    override val backend: BackendType
        get() = backendType

    override val deviceName: String
        get() = adapterInfo.name

    override val driverVersion: String
        get() = adapterInfo.driverVersion ?: "unknown"

    override var fxaaEnabled: Boolean = options.enableFxaa
        set(value) {
            if (field == value) return
            field = value
            if (!initialized) return
            if (value) {
                recreateOffscreenTargets(width, height)
            } else {
                releaseFxaaTargets()
            }
        }

    override suspend fun initialize(): Result<Unit> = initMutex.withLock {
        if (initialized) {
            return Result.Success(Unit)
        }

        return try {
            val preferredBackends = config.preferredBackend?.let { listOf(it.toGpuBackend()) }
                ?: options.preferredBackends

            gpuInstance = createGpuInstance(
                GpuInstanceDescriptor(
                    preferredBackends = preferredBackends,
                    label = "engine-renderer-instance"
                )
            )

            adapter = gpuInstance.requestAdapter(
                GpuRequestAdapterOptions(
                    powerPreference = options.powerPreference,
                    label = "engine-renderer-adapter"
                )
            )
            adapterInfo = adapter.info
            backendType = adapter.backend.toBackendType()

            device = adapter.requestDevice(
                GpuDeviceDescriptor(
                    label = "engine-renderer-device"
                )
            )

            gpuSurface = GpuSurfaceFactory.create(
                device = device,
                renderSurface = surface,
                label = "engine-surface"
            )
            surfaceFormat = gpuSurface.getPreferredFormat(adapter)
            sceneRenderer = SceneRenderer(device, surfaceFormat)
            setupFxaaResources()
            initialized = true
            recreateDepthTexture(width, height)
            if (fxaaEnabled) {
                recreateOffscreenTargets(width, height)
            }
            Result.Success(Unit)
        } catch (error: RendererInitializationException) {
            Result.Error(error.message ?: "Failed to initialize engine renderer", error)
        } catch (error: Exception) {
            Result.Error(error.message ?: "Failed to initialize engine renderer", error)
        }
    }

    override fun render(scene: Scene, camera: PerspectiveCamera) {
        check(initialized) { "EngineRenderer not initialized. Call initialize() first." }

        scene.updateWorldMatrix(force = true)
        camera.updateProjection()
        camera.updateWorldMatrix(force = true)

        val (meshes, points) = collectRenderables(scene)
        sceneRenderer.prepareBlocking(meshes, points)

        var useFxaa = fxaaEnabled && fxaaResources != null
        if (useFxaa) {
            if (width != fxaaWidth || height != fxaaHeight || offscreenTexture == null || offscreenView == null) {
                if (width > 0 && height > 0) {
                    recreateOffscreenTargets(width, height)
                }
            }
            if (offscreenView == null) {
                useFxaa = false
            }
        }

        val frame = try {
            gpuSurface.acquireFrame()
        } catch (error: Exception) {
            gpuSurface.resize(width, height)
            return
        }
        val encoder = device.createCommandEncoder(
            GpuCommandEncoderDescriptor(label = "engine-renderer-encoder")
        )

        val clearColor = if (scene.backgroundColor.size >= 4) {
            scene.backgroundColor
        } else {
            options.clearColor
        }

        val targetView = if (useFxaa) offscreenView ?: frame.view else frame.view

        val pass = encoder.beginRenderPass(
            GpuRenderPassDescriptor(
                colorAttachments = listOf(
                    GpuRenderPassColorAttachment(
                        view = targetView,
                        loadOp = GpuLoadOp.CLEAR,
                        clearColor = clearColor
                    )
                ),
                depthStencilAttachment = depthView?.let {
                    GpuRenderPassDepthStencilAttachment(
                        view = it,
                        depthLoadOp = GpuLoadOp.CLEAR,
                        depthStoreOp = GpuStoreOp.DISCARD,
                        depthClearValue = 1.0f
                    )
                },
                label = "engine-renderer-pass"
            )
        )

        // Compute view-projection matrix (no Y-flip needed for Vulkan)
        val projectionMatrix = camera.projectionMatrix()
        val viewProjection = mat4().multiply(projectionMatrix, camera.viewMatrix())

        sceneRenderer.record(pass, meshes, points, viewProjection)
        pass.end()

        if (useFxaa) {
            ensureFxaaBindGroup()
            val resources = fxaaResources
            val bindGroup = fxaaBindGroup
            if (resources != null && bindGroup != null) {
                val fxaaPass = encoder.beginRenderPass(
                    GpuRenderPassDescriptor(
                        colorAttachments = listOf(
                            GpuRenderPassColorAttachment(
                                view = frame.view,
                                loadOp = GpuLoadOp.CLEAR,
                                clearColor = clearColor
                            )
                        ),
                        label = "engine-renderer-fxaa-pass"
                    )
                )
                fxaaPass.setPipeline(resources.pipeline)
                fxaaPass.setBindGroup(0, bindGroup)
                fxaaPass.draw(3)
                fxaaPass.end()
            }
        }

        val commandBuffer = encoder.finish()
        device.queue.submit(listOf(commandBuffer))
        gpuSurface.present(frame)
    }

    override fun resize(width: Int, height: Int) {
        if (!initialized) return
        this.width = max(width, 1)
        this.height = max(height, 1)
        gpuSurface.resize(this.width, this.height)
        recreateDepthTexture(this.width, this.height)
        if (fxaaEnabled) {
            recreateOffscreenTargets(this.width, this.height)
        }
    }

    override fun dispose() {
        if (!initialized) return
        sceneRenderer.dispose()
        device.destroy()
        gpuInstance.dispose()
        releaseFxaaTargets()
        depthTexture?.destroy()
        depthTexture = null
        depthView = null
        fxaaSampler = null
        fxaaBindGroup = null
        fxaaBindGroupTexture = null
        initialized = false
    }

    private suspend fun setupFxaaResources() {
        fxaaResources = PostProcessPipelineFactory.createFxaaPipeline(device, surfaceFormat)
        fxaaSampler = device.createSampler(
            GpuSamplerDescriptor(
                label = "engine-fxaa-sampler",
                magFilter = GpuFilterMode.LINEAR,
                minFilter = GpuFilterMode.LINEAR
            )
        )
    }

    private fun recreateOffscreenTargets(width: Int, height: Int) {
        if (!fxaaEnabled) return
        releaseFxaaTargets()
        if (width <= 0 || height <= 0) return
        val texture = device.createTexture(
            GpuTextureDescriptor(
                label = "engine-fxaa-offscreen",
                size = Triple(width, height, 1),
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = surfaceFormat,
                usage = gpuTextureUsage(
                    GpuTextureUsage.RENDER_ATTACHMENT,
                    GpuTextureUsage.TEXTURE_BINDING,
                    GpuTextureUsage.COPY_SRC
                )
            )
        )
        offscreenTexture = texture
        offscreenView = texture.createView()
        fxaaBindGroupTexture = null
        fxaaWidth = width
        fxaaHeight = height
    }

    private fun ensureFxaaBindGroup() {
        val resources = fxaaResources ?: return
        val sampler = fxaaSampler ?: return
        val texture = offscreenTexture ?: return
        val view = offscreenView ?: return
        if (fxaaBindGroupTexture === texture && fxaaBindGroup != null) return
        fxaaBindGroup = PostProcessPipelineFactory.createFxaaBindGroup(
            device,
            resources.bindGroupLayout,
            view,
            sampler
        )
        fxaaBindGroupTexture = texture
    }

    private fun recreateDepthTexture(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (depthTexture != null && depthWidth == width && depthHeight == height) return

        depthTexture?.destroy()

        val texture = device.createTexture(
            GpuTextureDescriptor(
                label = "engine-depth-texture",
                size = Triple(width, height, 1),
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = GpuTextureFormat.DEPTH24_PLUS,
                usage = gpuTextureUsage(GpuTextureUsage.RENDER_ATTACHMENT)
            )
        )
        depthTexture = texture
        depthView = texture.createView()
        depthWidth = width
        depthHeight = height
    }

    private fun releaseFxaaTargets() {
        offscreenTexture?.destroy()
        offscreenTexture = null
        offscreenView = null
        fxaaBindGroup = null
        fxaaBindGroupTexture = null
        fxaaWidth = 0
        fxaaHeight = 0
    }

    private fun collectRenderables(scene: Scene): Pair<List<Mesh>, List<InstancedPoints>> {
        val meshes = mutableListOf<Mesh>()
        val points = mutableListOf<InstancedPoints>()
        scene.traverse { node ->
            if (node is Mesh) {
                meshes += node
            } else if (node is InstancedPoints) {
                points += node
            }
        }
        return meshes to points
    }

}

private fun BackendType.toGpuBackend(): GpuBackend = when (this) {
    BackendType.WEBGPU -> GpuBackend.WEBGPU
    BackendType.VULKAN -> GpuBackend.VULKAN
    BackendType.WEBGL -> GpuBackend.WEBGPU
}

private fun GpuBackend.toBackendType(): BackendType = when (this) {
    GpuBackend.WEBGPU -> BackendType.WEBGPU
    GpuBackend.VULKAN, GpuBackend.MOLTENVK -> BackendType.VULKAN
}

private fun PowerPreference.toGpuPowerPreference(): GpuPowerPreference = when (this) {
    PowerPreference.LOW_POWER -> GpuPowerPreference.LOW_POWER
    PowerPreference.HIGH_PERFORMANCE -> GpuPowerPreference.HIGH_PERFORMANCE
}
