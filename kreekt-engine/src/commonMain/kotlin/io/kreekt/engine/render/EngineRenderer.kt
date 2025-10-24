package io.kreekt.engine.render

import io.kreekt.engine.camera.PerspectiveCamera
import io.kreekt.engine.math.mat4
import io.kreekt.engine.scene.Mesh
import io.kreekt.engine.scene.Scene
import io.kreekt.gpu.GpuBackend
import io.kreekt.gpu.GpuCommandEncoderDescriptor
import io.kreekt.gpu.GpuDevice
import io.kreekt.gpu.GpuDeviceDescriptor
import io.kreekt.gpu.GpuInstance
import io.kreekt.gpu.GpuInstanceDescriptor
import io.kreekt.gpu.GpuLoadOp
import io.kreekt.gpu.GpuPowerPreference
import io.kreekt.gpu.GpuRenderPassColorAttachment
import io.kreekt.gpu.GpuRenderPassDescriptor
import io.kreekt.gpu.GpuRequestAdapterOptions
import io.kreekt.gpu.GpuSurface
import io.kreekt.gpu.GpuSurfaceFactory
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.createGpuInstance
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.RenderSurface
import io.kreekt.renderer.RendererConfig
import io.kreekt.renderer.RendererFactory
import io.kreekt.renderer.RendererInitializationException
import io.kreekt.core.Result
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
}

data class EngineRendererOptions(
    val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    val clearColor: FloatArray = floatArrayOf(0.05f, 0.05f, 0.1f, 1f)
)

suspend fun RendererFactory.createEngineRenderer(
    surface: RenderSurface,
    config: RendererConfig = RendererConfig(),
    options: EngineRendererOptions = EngineRendererOptions()
): Result<EngineRenderer> {
    val renderer = EngineRendererImpl(surface, config, options)
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
    private lateinit var device: GpuDevice
    private lateinit var gpuSurface: GpuSurface
    private lateinit var sceneRenderer: SceneRenderer
    private lateinit var surfaceFormat: GpuTextureFormat

    private var width: Int = max(surface.width, 1)
    private var height: Int = max(surface.height, 1)

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

            val adapter = gpuInstance.requestAdapter(
                GpuRequestAdapterOptions(
                    powerPreference = options.powerPreference,
                    label = "engine-renderer-adapter"
                )
            )

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
            initialized = true
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

        val meshes = collectMeshes(scene)
        sceneRenderer.prepareMeshesBlocking(meshes)

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

        val pass = encoder.beginRenderPass(
            GpuRenderPassDescriptor(
                colorAttachments = listOf(
                    GpuRenderPassColorAttachment(
                        view = frame.view,
                        loadOp = GpuLoadOp.CLEAR,
                        clearColor = clearColor
                    )
                ),
                label = "engine-renderer-pass"
            )
        )

        val viewProjection = mat4().multiply(
            camera.projectionMatrix(),
            camera.viewMatrix()
        )

        sceneRenderer.record(pass, meshes, viewProjection)
        pass.end()

        val commandBuffer = encoder.finish()
        device.queue.submit(listOf(commandBuffer))
        gpuSurface.present(frame)
    }

    override fun resize(width: Int, height: Int) {
        if (!initialized) return
        this.width = max(width, 1)
        this.height = max(height, 1)
        gpuSurface.resize(this.width, this.height)
    }

    override fun dispose() {
        if (!initialized) return
        sceneRenderer.dispose()
        device.destroy()
        gpuInstance.dispose()
        initialized = false
    }

    private fun collectMeshes(scene: Scene): List<Mesh> {
        val meshes = mutableListOf<Mesh>()
        scene.traverse { node ->
            if (node is Mesh) {
                meshes += node
            }
        }
        return meshes
    }

}

private fun BackendType.toGpuBackend(): GpuBackend = when (this) {
    BackendType.WEBGPU -> GpuBackend.WEBGPU
    BackendType.VULKAN -> GpuBackend.VULKAN
    BackendType.WEBGL -> GpuBackend.WEBGPU
}
