package io.kreekt.engine.render

import io.kreekt.engine.material.BlendMode
import io.kreekt.engine.material.CullMode
import io.kreekt.engine.material.RenderState
import io.kreekt.gpu.GpuBindGroup
import io.kreekt.gpu.GpuBindGroupDescriptor
import io.kreekt.gpu.GpuBindGroupEntry
import io.kreekt.gpu.GpuBindGroupLayout
import io.kreekt.gpu.GpuBindGroupLayoutDescriptor
import io.kreekt.gpu.GpuBindGroupLayoutEntry
import io.kreekt.gpu.GpuBindingResource
import io.kreekt.gpu.GpuBindingResourceType
import io.kreekt.gpu.GpuBlendMode
import io.kreekt.gpu.GpuBuffer
import io.kreekt.gpu.GpuCompareFunction
import io.kreekt.gpu.GpuCullMode
import io.kreekt.gpu.GpuDepthState
import io.kreekt.gpu.GpuDevice
import io.kreekt.gpu.GpuPrimitiveTopology
import io.kreekt.gpu.GpuRenderPipeline
import io.kreekt.gpu.GpuRenderPipelineDescriptor
import io.kreekt.gpu.GpuShaderModuleDescriptor
import io.kreekt.gpu.GpuShaderStage
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.GpuVertexAttribute
import io.kreekt.gpu.GpuVertexBufferLayout
import io.kreekt.gpu.GpuVertexFormat
import io.kreekt.gpu.GpuVertexStepMode

/**
 * Builds GPU pipelines and bind-group layouts for unlit materials used by the MVP demos.
 *
 * The builders load WGSL shaders from `resources/shaders` and configure vertex state so both
 * WebGPU and Vulkan share the same pipeline definition.
 */
object UnlitPipelineFactory {

    data class PipelineResources(
        val pipeline: GpuRenderPipeline,
        val bindGroupLayout: GpuBindGroupLayout
    )

    /**
     * Create the pipeline used by [io.kreekt.engine.material.UnlitColorMaterial].
     */
    fun createUnlitColorPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        renderState: RenderState = RenderState()
    ): PipelineResources {
        val layout = createUniformLayout(device, label = "unlit-color-layout")
        val vertexModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "unlit_color.vert",
                code = ShaderSource.UNLIT_COLOR_VERT
            )
        )
        val fragmentModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "unlit_color.frag",
                code = ShaderSource.UNLIT_COLOR_FRAG
            )
        )

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "unlit-color-pipeline",
                vertexShader = vertexModule,
                fragmentShader = fragmentModule,
                colorFormats = listOf(colorFormat),
                vertexBuffers = listOf(vertexLayoutWithColor()),
                bindGroupLayouts = listOf(layout),
                cullMode = renderState.toCullMode(),
                depthState = renderState.toDepthState(),
                blendMode = renderState.toBlendMode()
            )
        )

        return PipelineResources(pipeline, layout)
    }

    /**
     * Create the pipeline used by [io.kreekt.engine.material.UnlitPointsMaterial].
     */
    fun createUnlitPointsPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        renderState: RenderState = RenderState()
    ): PipelineResources {
        val layout = createUniformLayout(device, label = "unlit-points-layout")
        val vertexModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "unlit_points.vert",
                code = ShaderSource.UNLIT_POINTS_VERT
            )
        )
        val fragmentModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "unlit_points.frag",
                code = ShaderSource.UNLIT_POINTS_FRAG
            )
        )

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "unlit-points-pipeline",
                vertexShader = vertexModule,
                fragmentShader = fragmentModule,
                colorFormats = listOf(colorFormat),
                vertexBuffers = listOf(instancedPointsLayout()),
                primitiveTopology = GpuPrimitiveTopology.POINT_LIST,
                bindGroupLayouts = listOf(layout),
                cullMode = renderState.toCullMode(),
                depthState = renderState.toDepthState(),
                blendMode = renderState.toBlendMode()
            )
        )

        return PipelineResources(pipeline, layout)
    }

    /**
     * Convenience helper to create a bind group referencing a single uniform buffer
     * that contains the model-view-projection matrix for the current draw.
     */
    fun createUniformBindGroup(
        device: GpuDevice,
        layout: GpuBindGroupLayout,
        uniformBuffer: GpuBuffer,
        label: String = "unlit-uniforms"
    ): GpuBindGroup {
        return device.createBindGroup(
            GpuBindGroupDescriptor(
                label = label,
                layout = layout,
                entries = listOf(
                    GpuBindGroupEntry(
                        binding = 0,
                        resource = GpuBindingResource.Buffer(uniformBuffer)
                    )
                )
            )
        )
    }

    private fun createUniformLayout(device: GpuDevice, label: String): GpuBindGroupLayout =
        device.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                label = label,
                entries = listOf(
                    GpuBindGroupLayoutEntry(
                        binding = 0,
                        visibility = setOf(GpuShaderStage.VERTEX),
                        resourceType = GpuBindingResourceType.UNIFORM_BUFFER
                    )
                )
            )
        )

    internal fun vertexLayoutWithColor(): GpuVertexBufferLayout =
        GpuVertexBufferLayout(
            arrayStride = Float.SIZE_BYTES * 6,
            stepMode = GpuVertexStepMode.VERTEX,
            attributes = listOf(
                GpuVertexAttribute(
                    shaderLocation = 0,
                    format = GpuVertexFormat.FLOAT32x3,
                    offset = 0
                ),
                GpuVertexAttribute(
                    shaderLocation = 1,
                    format = GpuVertexFormat.FLOAT32x3,
                    offset = Float.SIZE_BYTES * 3
                )
            )
        )

    internal fun instancedPointsLayout(): GpuVertexBufferLayout =
        GpuVertexBufferLayout(
            arrayStride = Float.SIZE_BYTES * 11,
            stepMode = GpuVertexStepMode.INSTANCE,
            attributes = listOf(
                GpuVertexAttribute(
                    shaderLocation = 0,
                    format = GpuVertexFormat.FLOAT32x3,
                    offset = 0
                ),
                GpuVertexAttribute(
                    shaderLocation = 1,
                    format = GpuVertexFormat.FLOAT32x3,
                    offset = Float.SIZE_BYTES * 3
                ),
                GpuVertexAttribute(
                    shaderLocation = 2,
                    format = GpuVertexFormat.FLOAT32,
                    offset = Float.SIZE_BYTES * 6
                ),
                GpuVertexAttribute(
                    shaderLocation = 3,
                    format = GpuVertexFormat.FLOAT32x4,
                    offset = Float.SIZE_BYTES * 7
                )
            )
        )
}

private object ShaderSource {
    val UNLIT_COLOR_VERT = """
        struct VertexInput {
            @location(0) position : vec3<f32>,
            @location(1) color : vec3<f32>,
        };

        struct VertexOutput {
            @builtin(position) position : vec4<f32>,
            @location(0) color : vec3<f32>,
        };

        @group(0) @binding(0)
        var<uniform> uModelViewProjection : mat4x4<f32>;

        @vertex
        fn main(input : VertexInput) -> VertexOutput {
            var output : VertexOutput;
            output.position = uModelViewProjection * vec4<f32>(input.position, 1.0);
            output.color = input.color;
            return output;
        }
    """.trimIndent()

    val UNLIT_COLOR_FRAG = """
        struct FragmentInput {
            @location(0) color : vec3<f32>,
        };

        struct FragmentOutput {
            @location(0) color : vec4<f32>,
        };

        @fragment
        fn main(input : FragmentInput) -> FragmentOutput {
            var output : FragmentOutput;
            output.color = vec4<f32>(input.color, 1.0);
            return output;
        }
    """.trimIndent()

    val UNLIT_POINTS_VERT = """
        struct VertexInput {
            @location(0) instancePosition : vec3<f32>,
            @location(1) instanceColor : vec3<f32>,
            @location(2) instanceSize : f32,
            @location(3) instanceExtra : vec4<f32>,
        };

        struct VertexOutput {
            @builtin(position) position : vec4<f32>,
            @location(0) color : vec3<f32>,
            @location(1) size : f32,
            @location(2) extra : vec4<f32>,
        };

        @group(0) @binding(0)
        var<uniform> uModelViewProjection : mat4x4<f32>;

        @vertex
        fn main(input : VertexInput) -> VertexOutput {
            var output : VertexOutput;
            output.position = uModelViewProjection * vec4<f32>(input.instancePosition, 1.0);

            let glow = clamp(input.instanceExtra.x, 0.0, 1.0);
            let sizeFactor = clamp(input.instanceSize, 0.0, 10.0);

            output.color = input.instanceColor * (1.0 + glow * 0.3) * clamp(sizeFactor, 0.2, 1.5);
            output.size = input.instanceSize;
            output.extra = input.instanceExtra;
            return output;
        }
    """.trimIndent()

    val UNLIT_POINTS_FRAG = """
        struct FragmentInput {
            @location(0) color : vec3<f32>,
            @location(1) size : f32,
            @location(2) extra : vec4<f32>,
        };

        struct FragmentOutput {
            @location(0) color : vec4<f32>,
        };

        @fragment
        fn main(input : FragmentInput) -> FragmentOutput {
            var output : FragmentOutput;
            var alpha = clamp(input.extra.w, 0.0, 1.0);
            if (alpha == 0.0) {
                alpha = 1.0;
            }

            let glow = clamp(input.extra.x, 0.0, 1.0);
            let intensity = clamp(input.size * 0.5, 0.2, 1.5);
            let finalColor = input.color * (1.0 + glow * 0.5) * intensity;

            output.color = vec4<f32>(finalColor, alpha);
            return output;
        }
    """.trimIndent()
}

private fun RenderState.toCullMode(): GpuCullMode = when (cullMode) {
    CullMode.NONE -> GpuCullMode.NONE
    CullMode.FRONT -> GpuCullMode.FRONT
    CullMode.BACK -> GpuCullMode.BACK
}

private fun RenderState.toBlendMode(): GpuBlendMode = when (blendMode) {
    BlendMode.Opaque -> GpuBlendMode.DISABLED
    BlendMode.Alpha -> GpuBlendMode.ALPHA
    BlendMode.Additive -> GpuBlendMode.ADDITIVE
}

private fun RenderState.toDepthState() = if (depthTest) {
    io.kreekt.gpu.GpuDepthState(
        depthWriteEnabled = depthWrite,
        depthCompare = if (depthWrite) io.kreekt.gpu.GpuCompareFunction.LESS else io.kreekt.gpu.GpuCompareFunction.LESS_EQUAL
    )
} else {
    null
}
