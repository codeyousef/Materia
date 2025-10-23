package io.kreekt.examples.triangle

import io.kreekt.engine.camera.OrbitController
import io.kreekt.engine.camera.PerspectiveCamera
import io.kreekt.engine.geometry.AttributeSemantic
import io.kreekt.engine.geometry.AttributeType
import io.kreekt.engine.geometry.Geometry
import io.kreekt.engine.geometry.GeometryAttribute
import io.kreekt.engine.geometry.GeometryLayout
import io.kreekt.engine.material.UnlitColorMaterial
import io.kreekt.engine.math.Vector3f
import io.kreekt.engine.scene.Mesh
import io.kreekt.engine.scene.Scene
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
import io.kreekt.gpu.GpuRenderPipelineDescriptor
import io.kreekt.gpu.GpuRequestAdapterOptions
import io.kreekt.gpu.GpuShaderModuleDescriptor
import io.kreekt.gpu.GpuSurface
import io.kreekt.gpu.GpuSurfaceConfiguration
import io.kreekt.gpu.GpuSurfaceFactory
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.GpuTextureUsage
import io.kreekt.gpu.gpuBufferUsage
import io.kreekt.gpu.gpuTextureUsage
import io.kreekt.gpu.createGpuInstance
import io.kreekt.gpu.gpuBufferUsage
import io.kreekt.renderer.RenderSurface

data class TriangleBootLog(
    val backend: GpuBackend,
    val adapterName: String,
    val deviceLabel: String?,
    val pipelineLabel: String?,
    val meshCount: Int,
    val cameraPosition: Vector3f
) {
    fun pretty(): String = buildString {
        appendLine("🎯 Triangle MVP bootstrap complete")
        appendLine("  Backend : $backend")
        appendLine("  Adapter : $adapterName")
        appendLine("  Device  : ${deviceLabel ?: "n/a"}")
        appendLine("  Pipeline: ${pipelineLabel ?: "n/a"}")
        appendLine("  Meshes  : $meshCount")
        appendLine("  Camera  : (${cameraPosition.x}, ${cameraPosition.y}, ${cameraPosition.z})")
    }
}

class TriangleExample(
    private val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE
) {
    private var orbitController: OrbitController? = null
    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): TriangleBootLog {
        if (renderSurface == null) {
            val (scene, camera) = buildScene()
            scene.update(0f)
            return TriangleBootLog(
                backend = preferredBackends.firstOrNull() ?: GpuBackend.WEBGPU,
                adapterName = "Stub Adapter",
                deviceLabel = "Stub Device",
                pipelineLabel = "triangle-pipeline",
                meshCount = scene.children.count { it is Mesh },
                cameraPosition = camera.transform.position.copy()
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

        val vertexShader = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "triangle.vert",
                code = loadShaderResource("shaders/triangle.vert.wgsl")
            )
        )

        val fragmentShader = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "triangle.frag",
                code = loadShaderResource("shaders/triangle.frag.wgsl")
            )
        )

        val targetWidth = widthOverride ?: renderSurface.width.takeIf { it > 0 } ?: 640
        val targetHeight = heightOverride ?: renderSurface.height.takeIf { it > 0 } ?: 480
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

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "triangle-pipeline",
                vertexShader = vertexShader,
                fragmentShader = fragmentShader,
                colorFormats = listOf(surfaceFormat)
            )
        )
        val frame = surface.acquireFrame()

        val vertexBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "triangle-vertex-buffer",
                size = TRIANGLE_VERTICES.size * Float.SIZE_BYTES.toLong(),
                usage = gpuBufferUsage(GpuBufferUsage.VERTEX)
            )
        )
        vertexBuffer.writeFloats(TRIANGLE_VERTICES)

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
        pass.setPipeline(pipeline)
        pass.setVertexBuffer(0, vertexBuffer)
        val triangleVertexCount = TRIANGLE_VERTICES.size / TRIANGLE_COMPONENTS
        pass.draw(vertexCount = triangleVertexCount)
        pass.end()
        val commandBuffer = encoder.finish()
        device.queue.submit(listOf(commandBuffer))
        surface.present(frame)

        val (scene, camera) = buildScene()
        scene.update(0f)

        return TriangleBootLog(
            backend = adapter.backend,
            adapterName = adapter.info.name,
            deviceLabel = device.descriptor.label,
            pipelineLabel = pipeline.descriptor.label,
            meshCount = scene.children.count { it is Mesh },
            cameraPosition = camera.transform.position.copy()
        )
    }

    private fun buildScene(): Pair<Scene, PerspectiveCamera> {
        val scene = Scene(name = "TriangleScene").apply {
            backgroundColor = floatArrayOf(0.05f, 0.05f, 0.1f, 1f)
        }

        val geometry = Geometry(
            vertexBuffer = io.kreekt.engine.scene.VertexBuffer(
                data = TRIANGLE_VERTICES,
                strideBytes = TRIANGLE_COMPONENTS * Float.SIZE_BYTES
            ),
            layout = GeometryLayout(
                stride = TRIANGLE_COMPONENTS * Float.SIZE_BYTES,
                attributes = mapOf(
                    AttributeSemantic.POSITION to GeometryAttribute(
                        offset = 0,
                        components = 3,
                        type = AttributeType.FLOAT32
                    )
                )
            ),
            indexBuffer = null
        )

        val material = UnlitColorMaterial(
            label = "TriangleMaterial",
            color = Vector3f(1f, 0.4f, 0.2f)
        )

        val mesh = Mesh(
            name = "TriangleMesh",
            geometry = geometry,
            material = material
        )
        scene.add(mesh)

        val camera = PerspectiveCamera(fovDegrees = 60f, aspect = 16f / 9f).apply {
            transform.setPosition(0f, 0f, 2f)
            lookAt(Vector3f(0f, 0f, 0f))
        }
        scene.add(camera)

        orbitController = OrbitController(camera, target = Vector3f(0f, 0f, 0f), radius = 2f)

        return scene to camera
    }

    companion object {
        private val TRIANGLE_VERTICES = floatArrayOf(
            0f, 0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f
        )

        private const val TRIANGLE_COMPONENTS = 3
    }
}
