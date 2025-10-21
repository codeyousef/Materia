package io.kreekt.examples.triangle

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
    val pipelineLabel: String?
) {
    fun pretty(): String = buildString {
        appendLine("🎯 Triangle MVP bootstrap complete")
        appendLine("  Backend : $backend")
        appendLine("  Adapter : $adapterName")
        appendLine("  Device  : ${deviceLabel ?: "n/a"}")
        appendLine("  Pipeline: ${pipelineLabel ?: "n/a"}")
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

        return TriangleBootLog(
            backend = adapter.backend,
            adapterName = adapter.info.name,
            deviceLabel = device.descriptor.label,
            pipelineLabel = pipeline.descriptor.label
        )
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
