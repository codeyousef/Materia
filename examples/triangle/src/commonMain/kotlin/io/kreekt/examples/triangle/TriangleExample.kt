package io.kreekt.examples.triangle

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.Color
import io.kreekt.core.math.Vector3
import io.kreekt.core.scene.Background
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.engine.render.UnlitPipelineFactory
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.RenderSurface
import io.kreekt.gpu.GpuBackend
import io.kreekt.gpu.GpuBufferDescriptor
import io.kreekt.gpu.GpuBufferUsage
import io.kreekt.gpu.GpuCommandEncoderDescriptor
import io.kreekt.gpu.GpuDeviceDescriptor
import io.kreekt.gpu.GpuInstanceDescriptor
import io.kreekt.gpu.GpuLoadOp
import io.kreekt.gpu.GpuPowerPreference
import io.kreekt.gpu.GpuRenderPassColorAttachment
import io.kreekt.gpu.GpuRenderPassDescriptor
import io.kreekt.gpu.GpuRequestAdapterOptions
import io.kreekt.gpu.GpuSurfaceConfiguration
import io.kreekt.gpu.GpuSurfaceFactory
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.GpuTextureUsage
import io.kreekt.gpu.createGpuInstance
import io.kreekt.gpu.gpuBufferUsage
import io.kreekt.gpu.gpuTextureUsage
import kotlin.math.max
import kotlin.math.roundToInt

data class TriangleBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val meshCount: Int,
    val cameraPosition: Vector3,
    val frameTimeMs: Double
) {
    fun pretty(): String = buildString {
        appendLine("🎯 Triangle MVP bootstrap complete")
        appendLine("  Backend : $backend")
        appendLine("  Device  : $deviceName")
        appendLine("  Driver  : $driverVersion")
        appendLine("  Meshes  : $meshCount")
        appendLine("  Frame   : ${frameTimeMs.asMsString()} ms")
        appendLine(
            "  Camera  : (${cameraPosition.x.format2f()}, " +
                "${cameraPosition.y.format2f()}, ${cameraPosition.z.format2f()})"
        )
    }

    private fun Double.asMsString(): String = ((this * 100).roundToInt() / 100.0).toString()
    private fun Float.format2f(): String = ((this * 100f).roundToInt() / 100f).toString()
}

class TriangleExample(
    private val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE
) {

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): TriangleBootLog {
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val targetHeight = heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val aspect = targetWidth.toFloat() / max(1, targetHeight).toFloat()

        val (scene, camera, mesh) = buildScene(aspect)
        val meshCount = scene.children.count { it is Mesh }

        if (renderSurface == null) {
            return TriangleBootLog(
                backend = preferredBackends.firstOrNull()?.toBackendType() ?: BackendType.WEBGPU,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                meshCount = meshCount,
                cameraPosition = camera.position.clone(),
                frameTimeMs = 0.0
            )
        }
        val instance = createGpuInstance(
            GpuInstanceDescriptor(
                preferredBackends = preferredBackends,
                label = "triangle-instance"
            )
        )

        val adapter = instance.requestAdapter(
            GpuRequestAdapterOptions(
                powerPreference = powerPreference,
                label = "triangle-adapter"
            )
        )

        val device = adapter.requestDevice(
            GpuDeviceDescriptor(
                label = "triangle-device"
            )
        )

        val surface = GpuSurfaceFactory.create(
            device = device,
            renderSurface = renderSurface,
            label = "triangle-surface",
            configuration = GpuSurfaceConfiguration(
                format = GpuTextureFormat.BGRA8_UNORM,
                usage = gpuTextureUsage(GpuTextureUsage.RENDER_ATTACHMENT, GpuTextureUsage.COPY_SRC),
                width = targetWidth,
                height = targetHeight
            )
        )

        val surfaceFormat = surface.getPreferredFormat(adapter)

        val pipelineResources = UnlitPipelineFactory.createUnlitColorPipeline(device, surfaceFormat)
        val uniformBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "triangle-mvp",
                size = IDENTITY_MATRIX.size * Float.SIZE_BYTES.toLong(),
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM)
            )
        )
        uniformBuffer.writeFloats(IDENTITY_MATRIX)

        val bindGroup = UnlitPipelineFactory.createUniformBindGroup(
            device = device,
            layout = pipelineResources.bindGroupLayout,
            uniformBuffer = uniformBuffer,
            label = "triangle-bind-group"
        )

        val vertexBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "triangle-vertex-buffer",
                size = GPU_TRIANGLE_VERTEX_DATA.size * Float.SIZE_BYTES.toLong(),
                usage = gpuBufferUsage(GpuBufferUsage.VERTEX)
            )
        )
        vertexBuffer.writeFloats(GPU_TRIANGLE_VERTEX_DATA)

        val frame = surface.acquireFrame()
        val encoder = device.createCommandEncoder(
            GpuCommandEncoderDescriptor(label = "triangle-encoder")
        )

        val pass = encoder.beginRenderPass(
            GpuRenderPassDescriptor(
                colorAttachments = listOf(
                    GpuRenderPassColorAttachment(
                        view = frame.view,
                        loadOp = GpuLoadOp.CLEAR,
                        clearColor = floatArrayOf(0.05f, 0.05f, 0.1f, 1f)
                    )
                ),
                label = "triangle-pass"
            )
        )
        pass.setPipeline(pipelineResources.pipeline)
        pass.setVertexBuffer(0, vertexBuffer)
        pass.setBindGroup(0, bindGroup)
        pass.draw(GPU_TRIANGLE_VERTEX_COUNT)
        pass.end()

        val commandBuffer = encoder.finish()
        device.queue.submit(listOf(commandBuffer))
        surface.present(frame)

        uniformBuffer.destroy()
        vertexBuffer.destroy()
        instance.dispose()

        scene.updateMatrixWorld(true)

        return TriangleBootLog(
            backend = adapter.backend.toBackendType(),
            deviceName = adapter.info.name,
            driverVersion = adapter.info.driverVersion ?: "unknown",
            meshCount = meshCount,
            cameraPosition = camera.position.clone(),
            frameTimeMs = 0.0
        )
    }

    private fun buildScene(aspect: Float): Triple<Scene, PerspectiveCamera, Mesh> {
        val geometry = BufferGeometry().apply {
            setAttribute("position", BufferAttribute(TRIANGLE_VERTICES.copyOf(), TRIANGLE_COMPONENTS))
        }

        val material = MeshBasicMaterial().apply {
            color = Color(1f, 0.4f, 0.2f)
            toneMapped = false
        }

        val mesh = Mesh(geometry, material).apply {
            name = "TriangleMesh"
        }

        val scene = Scene().apply {
            name = "TriangleScene"
            background = Background.Color(Color(0.05f, 0.05f, 0.1f, 1f))
            add(mesh)
        }

        val camera = PerspectiveCamera(fov = 60f, aspect = aspect).apply {
            position.set(0f, 0f, 2f)
            lookAt(Vector3(0f, 0f, 0f))
        }
        scene.add(camera)
        scene.updateMatrixWorld(true)
        camera.updateMatrixWorld(true)

        return Triple(scene, camera, mesh)
    }

    fun renderFrame(@Suppress("UNUSED_PARAMETER") deltaSeconds: Float = 0f) = Unit

    fun resize(@Suppress("UNUSED_PARAMETER") width: Int, @Suppress("UNUSED_PARAMETER") height: Int) = Unit

    fun shutdown() = Unit

    private fun GpuBackend.toBackendType(): BackendType = when (this) {
        GpuBackend.WEBGPU -> BackendType.WEBGPU
        GpuBackend.VULKAN -> BackendType.VULKAN
        GpuBackend.MOLTENVK -> BackendType.VULKAN
    }

    companion object {
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
        private const val TRIANGLE_COMPONENTS = 3
        private const val GPU_TRIANGLE_COMPONENTS = 6
        private val GPU_TRIANGLE_VERTEX_DATA = floatArrayOf(
            0f, 0.5f, 0f, 1f, 0.4f, 0.2f,
            -0.5f, -0.5f, 0f, 1f, 0.4f, 0.2f,
            0.5f, -0.5f, 0f, 1f, 0.4f, 0.2f
        )
        private val GPU_TRIANGLE_VERTEX_COUNT = GPU_TRIANGLE_VERTEX_DATA.size / GPU_TRIANGLE_COMPONENTS

        private val TRIANGLE_VERTICES = floatArrayOf(
            0f, 0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f
        )

        private val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}
