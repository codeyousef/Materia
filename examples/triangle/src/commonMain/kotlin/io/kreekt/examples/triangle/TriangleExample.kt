package io.kreekt.examples.triangle

import io.kreekt.engine.camera.PerspectiveCamera
import io.kreekt.engine.math.Vector3f
import io.kreekt.engine.scene.Mesh
import io.kreekt.engine.scene.Scene
import io.kreekt.engine.scene.VertexBuffer
import io.kreekt.gpu.GpuBackend
import io.kreekt.gpu.GpuCommandEncoderDescriptor
import io.kreekt.gpu.GpuDeviceDescriptor
import io.kreekt.gpu.GpuInstanceDescriptor
import io.kreekt.gpu.GpuPowerPreference
import io.kreekt.gpu.GpuRenderPipelineDescriptor
import io.kreekt.gpu.GpuRequestAdapterOptions
import io.kreekt.gpu.GpuShaderModuleDescriptor
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.createGpuInstance

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
    suspend fun boot(): TriangleBootLog {
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
                label = "triangle-vertex",
                code = WGSL_VERTEX
            )
        )

        val fragmentShader = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "triangle-fragment",
                code = WGSL_FRAGMENT
            )
        )

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "triangle-pipeline",
                vertexShader = vertexShader,
                fragmentShader = fragmentShader,
                colorFormats = listOf(GpuTextureFormat.BGRA8_UNORM)
            )
        )

        val encoder = device.createCommandEncoder(
            GpuCommandEncoderDescriptor(label = "triangle-encoder")
        )
        val commandBuffer = encoder.finish()
        device.queue.submit(listOf(commandBuffer))

        val (scene, camera) = buildScene()
        scene.updateWorldMatrix()

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

        val vertices = floatArrayOf(
            0f, 0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f
        )

        val mesh = Mesh(
            name = "TriangleMesh",
            vertices = VertexBuffer(data = vertices, stride = 3)
        )
        scene.add(mesh)

        val camera = PerspectiveCamera(fovDegrees = 60f, aspect = 16f / 9f).apply {
            transform.setPosition(0f, 0f, 2f)
            lookAt(Vector3f(0f, 0f, 0f))
        }
        scene.add(camera)

        return scene to camera
    }

    companion object {
        private const val WGSL_VERTEX = """
            @vertex
            fn main(@builtin(vertex_index) vertexIndex : u32) -> @builtin(position) vec4<f32> {
                var positions = array<vec2<f32>, 3>(
                    vec2<f32>(0.0, 0.5),
                    vec2<f32>(-0.5, -0.5),
                    vec2<f32>(0.5, -0.5)
                );
                let pos = positions[vertexIndex];
                return vec4<f32>(pos, 0.0, 1.0);
            }
        """

        private const val WGSL_FRAGMENT = """
            @fragment
            fn main() -> @location(0) vec4<f32> {
                return vec4<f32>(1.0, 0.4, 0.2, 1.0);
            }
        """
    }
}
