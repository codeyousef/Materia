package io.kreekt.examples.triangle

import io.kreekt.engine.camera.PerspectiveCamera
import io.kreekt.engine.geometry.AttributeSemantic
import io.kreekt.engine.geometry.AttributeType
import io.kreekt.engine.geometry.Geometry
import io.kreekt.engine.geometry.GeometryAttribute
import io.kreekt.engine.geometry.GeometryLayout
import io.kreekt.engine.material.BlendMode
import io.kreekt.engine.material.RenderState
import io.kreekt.engine.material.UnlitColorMaterial
import io.kreekt.engine.material.UnlitPointsMaterial
import io.kreekt.engine.math.Color
import io.kreekt.engine.math.Vec3
import io.kreekt.engine.math.mat4
import io.kreekt.engine.render.PostProcessPipelineFactory
import io.kreekt.engine.render.SceneRenderer
import io.kreekt.engine.scene.Mesh
import io.kreekt.engine.scene.Scene
import io.kreekt.engine.scene.VertexBuffer
import io.kreekt.gpu.GpuBackend
import io.kreekt.gpu.GpuCommandEncoderDescriptor
import io.kreekt.gpu.GpuDeviceDescriptor
import io.kreekt.gpu.GpuFilterMode
import io.kreekt.gpu.GpuInstanceDescriptor
import io.kreekt.gpu.GpuLoadOp
import io.kreekt.gpu.GpuPowerPreference
import io.kreekt.gpu.GpuRenderPassColorAttachment
import io.kreekt.gpu.GpuRenderPassDescriptor
import io.kreekt.gpu.GpuRequestAdapterOptions
import io.kreekt.gpu.GpuSamplerDescriptor
import io.kreekt.gpu.GpuSurfaceConfiguration
import io.kreekt.gpu.GpuSurfaceFactory
import io.kreekt.gpu.GpuTextureDescriptor
import io.kreekt.gpu.GpuTextureDimension
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.GpuTextureUsage
import io.kreekt.gpu.GpuTextureView
import io.kreekt.gpu.createGpuInstance
import io.kreekt.gpu.gpuTextureUsage
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.RenderSurface
import kotlin.math.max
import kotlin.math.roundToInt

data class TriangleBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val meshCount: Int,
    val cameraPosition: Vec3,
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
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    private val enableFxaa: Boolean = true
) {

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): TriangleBootLog {
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val targetHeight = heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val aspect = targetWidth.toFloat() / max(1, targetHeight).toFloat()

        val (scene, camera, meshes) = buildScene(aspect)
        scene.updateWorldMatrix(true)
        camera.updateProjection()

        if (renderSurface == null) {
            return TriangleBootLog(
                backend = preferredBackends.firstOrNull()?.asBackendType() ?: BackendType.WEBGPU,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                meshCount = meshes.size,
                cameraPosition = camera.transform.position.copy(),
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
        val sceneRenderer = SceneRenderer(device, surfaceFormat)
        sceneRenderer.prepareMeshesBlocking(meshes)

        val fxaaResources = if (enableFxaa) {
            PostProcessPipelineFactory.createFxaaPipeline(device, surfaceFormat)
        } else null

        val frame = surface.acquireFrame()
        val offscreenTexture = if (enableFxaa) {
            device.createTexture(
                GpuTextureDescriptor(
                    label = "triangle-offscreen",
                    size = Triple(targetWidth, targetHeight, 1),
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
        } else null
        val offscreenView = offscreenTexture?.createView()

        val encoder = device.createCommandEncoder(
            GpuCommandEncoderDescriptor(label = "triangle-encoder")
        )

        val pass = encoder.beginRenderPass(
            GpuRenderPassDescriptor(
                colorAttachments = listOf(
                    GpuRenderPassColorAttachment(
                        view = offscreenView ?: frame.view,
                        loadOp = GpuLoadOp.CLEAR,
                        clearColor = floatArrayOf(0.05f, 0.05f, 0.1f, 1f)
                    )
                ),
                label = "triangle-pass"
            )
        )

        val viewProjection = computeViewProjection(camera)
        sceneRenderer.record(pass, meshes, viewProjection)
        pass.end()

        if (enableFxaa && fxaaResources != null && offscreenView != null) {
            val sampler = device.createSampler(
                GpuSamplerDescriptor(
                    label = "fxaa-sampler",
                    magFilter = GpuFilterMode.LINEAR,
                    minFilter = GpuFilterMode.LINEAR
                )
            )
            val bindGroup = PostProcessPipelineFactory.createFxaaBindGroup(
                device,
                fxaaResources.bindGroupLayout,
                offscreenView,
                sampler
            )

            val fxaaPass = encoder.beginRenderPass(
                GpuRenderPassDescriptor(
                    colorAttachments = listOf(
                        GpuRenderPassColorAttachment(
                            view = frame.view,
                            loadOp = GpuLoadOp.CLEAR,
                            clearColor = floatArrayOf(0f, 0f, 0f, 1f)
                        )
                    ),
                    label = "fxaa-pass"
                )
            )
            fxaaPass.setPipeline(fxaaResources.pipeline)
            fxaaPass.setBindGroup(0, bindGroup)
            fxaaPass.draw(3)
            fxaaPass.end()
        }

        val commandBuffer = encoder.finish()
        device.queue.submit(listOf(commandBuffer))
        surface.present(frame)

        offscreenTexture?.destroy()
        sceneRenderer.dispose()
        instance.dispose()

        return TriangleBootLog(
            backend = adapter.backend.asBackendType(),
            deviceName = adapter.info.name,
            driverVersion = adapter.info.driverVersion ?: "unknown",
            meshCount = meshes.size,
            cameraPosition = camera.transform.position.copy(),
            frameTimeMs = 0.0
        )
    }

    private fun buildScene(aspect: Float): Triple<Scene, PerspectiveCamera, List<Mesh>> {
        val triangleGeometry = Geometry(
            vertexBuffer = VertexBuffer(
                data = GPU_TRIANGLE_VERTEX_DATA,
                strideBytes = Float.SIZE_BYTES * GPU_TRIANGLE_COMPONENTS
            ),
            layout = GeometryLayout(
                stride = Float.SIZE_BYTES * GPU_TRIANGLE_COMPONENTS,
                attributes = mapOf(
                    AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32),
                    AttributeSemantic.COLOR to GeometryAttribute(Float.SIZE_BYTES * 3, 3, AttributeType.FLOAT32)
                )
            )
        )

        val triangleMaterial = UnlitColorMaterial(
            label = "triangle",
            color = Color.fromFloats(1f, 0.4f, 0.2f),
            renderState = RenderState(depthTest = false, depthWrite = false)
        )

        val triangleMesh = Mesh(
            name = "triangle",
            geometry = triangleGeometry,
            material = triangleMaterial
        )

        val pointsGeometry = Geometry(
            vertexBuffer = VertexBuffer(
                data = GPU_POINTS_VERTEX_DATA,
                strideBytes = Float.SIZE_BYTES * GPU_POINTS_COMPONENTS
            ),
            layout = GeometryLayout(
                stride = Float.SIZE_BYTES * GPU_POINTS_COMPONENTS,
                attributes = mapOf(
                    AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32)
                )
            )
        )

        val pointsMaterial = UnlitPointsMaterial(
            label = "triangle-points",
            renderState = RenderState(
                depthTest = false,
                depthWrite = false,
                blendMode = BlendMode.Additive
            )
        )

        val pointsMesh = Mesh(
            name = "triangle-stars",
            geometry = pointsGeometry,
            material = pointsMaterial
        )

        val scene = Scene("triangle-scene").apply {
            add(triangleMesh)
            add(pointsMesh)
        }

        val camera = PerspectiveCamera(fovDegrees = 60f, aspect = aspect, near = 0.1f, far = 100f)
        camera.transform.setPosition(0f, 0f, 2.5f)
        camera.lookAt(Vec3.Zero)

        return Triple(scene, camera, listOf(triangleMesh, pointsMesh))
    }

    private fun computeViewProjection(camera: PerspectiveCamera) = mat4().multiply(
        camera.projectionMatrix(),
        camera.viewMatrix()
    )

    companion object {
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720

        private const val GPU_TRIANGLE_COMPONENTS = 6
        private const val GPU_POINTS_COMPONENTS = 11

        private val GPU_TRIANGLE_VERTEX_DATA = floatArrayOf(
            0f, 0.5f, 0f, 1f, 0.4f, 0.2f,
            -0.5f, -0.5f, 0f, 1f, 0.4f, 0.2f,
            0.5f, -0.5f, 0f, 1f, 0.4f, 0.2f
        )

        private val GPU_POINTS_VERTEX_DATA = floatArrayOf(
            -0.75f, 0.6f, 0f, 0.3f, 0.9f, 0.5f, 1f, 0f, 0f, 0f, 0f,
            0.0f, 0.85f, 0f, 0.6f, 0.9f, 0.2f, 1.2f, 0.1f, 0f, 0f, 0f,
            0.65f, 0.55f, 0f, 0.2f, 0.8f, 1.0f, 0.9f, 0.2f, 0f, 0f, 0f,
            -0.6f, -0.65f, 0f, 0.95f, 0.4f, 0.2f, 1.1f, 0.3f, 0f, 0f, 0f,
            0.7f, -0.7f, 0f, 0.8f, 0.3f, 0.9f, 1.4f, 0.4f, 0f, 0f, 0f
        )
    }
}

private fun GpuBackend.asBackendType(): BackendType = when (this) {
    GpuBackend.WEBGPU -> BackendType.WEBGPU
    GpuBackend.VULKAN, GpuBackend.MOLTENVK -> BackendType.VULKAN
}
