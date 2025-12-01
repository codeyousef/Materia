package io.materia.engine.render

import io.materia.engine.material.BlendMode
import io.materia.engine.material.CullMode
import io.materia.engine.material.RenderState
import io.materia.gpu.GpuBindGroup
import io.materia.gpu.GpuBindGroupDescriptor
import io.materia.gpu.GpuBindGroupEntry
import io.materia.gpu.GpuBindGroupLayout
import io.materia.gpu.GpuBindGroupLayoutDescriptor
import io.materia.gpu.GpuBindGroupLayoutEntry
import io.materia.gpu.GpuBindingResource
import io.materia.gpu.GpuBindingResourceType
import io.materia.gpu.GpuBlendMode
import io.materia.gpu.GpuBuffer
import io.materia.gpu.GpuCompareFunction
import io.materia.gpu.GpuCullMode
import io.materia.gpu.GpuDepthState
import io.materia.gpu.GpuDevice
import io.materia.gpu.GpuPrimitiveTopology
import io.materia.gpu.GpuRenderPipeline
import io.materia.gpu.GpuRenderPipelineDescriptor
import io.materia.gpu.GpuShaderModuleDescriptor
import io.materia.gpu.GpuShaderStage
import io.materia.gpu.GpuTextureFormat
import io.materia.gpu.GpuVertexAttribute
import io.materia.gpu.GpuVertexBufferLayout
import io.materia.gpu.GpuVertexFormat
import io.materia.gpu.GpuVertexStepMode

/**
 * Builds GPU pipelines and bind-group layouts for unlit materials used by the MVP demos.
 *
 * The builders load WGSL shaders from `resources/shaders` and configure vertex state so both
 * WebGPU and Vulkan share the same pipeline definition.
 */
object UnlitPipelineFactory {

    /**
     * Controls whether points are rendered using POINT_LIST topology (native points) or
     * as TRIANGLE_LIST quads (6 vertices per point). The quad fallback is needed for
     * platforms that don't properly support point primitives (e.g., Android emulators).
     */
    var useQuadPointsFallback: Boolean = true

    /**
     * Number of vertices per point instance when using quad fallback.
     */
    const val VERTICES_PER_QUAD_POINT = 6

    data class PipelineResources(
        val pipeline: GpuRenderPipeline,
        val bindGroupLayout: GpuBindGroupLayout
    )

    /**
     * Create the pipeline used by [io.materia.engine.material.UnlitColorMaterial].
     */
    fun createUnlitColorPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        renderState: RenderState = RenderState(),
        primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.TRIANGLE_LIST,
        depthFormat: GpuTextureFormat? = null
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
                depthStencilFormat = depthFormat,
                vertexBuffers = listOf(vertexLayoutWithColor()),
                primitiveTopology = primitiveTopology,
                bindGroupLayouts = listOf(layout),
                cullMode = renderState.toCullMode(),
                depthState = renderState.toDepthState(depthFormat),
                blendMode = renderState.toBlendMode()
            )
        )

        return PipelineResources(pipeline, layout)
    }

    /**
     * Create the pipeline used by [io.materia.engine.material.UnlitPointsMaterial].
     * 
     * When [useQuadPointsFallback] is true, this creates a pipeline that renders each point
     * as 6 vertices (2 triangles forming a quad) instead of using native POINT_LIST topology.
     * This is necessary for platforms that don't support point primitives properly.
     */
    fun createUnlitPointsPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat,
        renderState: RenderState = RenderState(),
        depthFormat: GpuTextureFormat? = null
    ): PipelineResources {
        val layout = createUniformLayout(device, label = "unlit-points-layout")
        
        // Use quad shaders and TRIANGLE_LIST topology for fallback mode
        val (vertexShaderLabel, fragmentShaderLabel, topology) = if (useQuadPointsFallback) {
            Triple("unlit_points_quad.vert", "unlit_points_quad.frag", GpuPrimitiveTopology.TRIANGLE_LIST)
        } else {
            Triple("unlit_points.vert", "unlit_points.frag", GpuPrimitiveTopology.POINT_LIST)
        }
        
        val vertexModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = vertexShaderLabel,
                code = if (useQuadPointsFallback) ShaderSource.UNLIT_POINTS_QUAD_VERT else ShaderSource.UNLIT_POINTS_VERT
            )
        )
        val fragmentModule = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = fragmentShaderLabel,
                code = if (useQuadPointsFallback) ShaderSource.UNLIT_POINTS_QUAD_FRAG else ShaderSource.UNLIT_POINTS_FRAG
            )
        )

        // Use instance vertex buffers for both quad fallback and native points
        val vertexBuffers = listOf(instancedPointsLayout())
        
        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "unlit-points-pipeline",
                vertexShader = vertexModule,
                fragmentShader = fragmentModule,
                colorFormats = listOf(colorFormat),
                depthStencilFormat = depthFormat,
                vertexBuffers = vertexBuffers,
                primitiveTopology = topology,
                bindGroupLayouts = listOf(layout),
                cullMode = renderState.toCullMode(),
                depthState = renderState.toDepthState(depthFormat),
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

    // Quad-based point shaders for platforms without proper point primitive support
    // These render each point as 2 triangles (6 vertices) forming a quad
    val UNLIT_POINTS_QUAD_VERT = """
        struct VertexInput {
            @location(0) instancePosition : vec3<f32>,
            @location(1) instanceColor : vec3<f32>,
            @location(2) instanceSize : f32,
            @location(3) instanceExtra : vec4<f32>,
        };

        struct VertexOutput {
            @builtin(position) position : vec4<f32>,
            @location(0) color : vec3<f32>,
        };

        @group(0) @binding(0)
        var<uniform> uModelViewProjection : mat4x4<f32>;

        @vertex
        fn main(@builtin(vertex_index) vertexIndex : u32, @builtin(instance_index) instanceIndex : u32, input : VertexInput) -> VertexOutput {
            var output : VertexOutput;
            
            // Quad offsets for 6 vertices (2 triangles)
            var quadOffsets = array<vec2<f32>, 6>(
                vec2<f32>(-1.0, -1.0),
                vec2<f32>( 1.0, -1.0),
                vec2<f32>(-1.0,  1.0),
                vec2<f32>(-1.0,  1.0),
                vec2<f32>( 1.0, -1.0),
                vec2<f32>( 1.0,  1.0)
            );
            
            // Transform the point center to clip space
            let clipPos = uModelViewProjection * vec4<f32>(input.instancePosition, 1.0);
            
            // Get the quad corner offset
            let offset = quadOffsets[vertexIndex];
            
            // Scale point size in clip space
            let pointSize = max(input.instanceSize * 0.012, 0.004);
            
            // Apply offset in clip space (multiply by w for perspective-correct sizing)
            var finalPos = clipPos;
            finalPos.x = finalPos.x + offset.x * pointSize * clipPos.w;
            finalPos.y = finalPos.y + offset.y * pointSize * clipPos.w;
            
            output.position = finalPos;
            output.color = input.instanceColor;
            
            return output;
        }
    """.trimIndent()

    val UNLIT_POINTS_QUAD_FRAG = """
        @fragment
        fn main(@location(0) color : vec3<f32>) -> @location(0) vec4<f32> {
            return vec4<f32>(color, 1.0);
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

private fun RenderState.toDepthState(depthFormat: GpuTextureFormat? = null) = if (depthFormat != null) {
    // When we have a depth attachment, we must provide depth state even if depth testing is disabled
    io.materia.gpu.GpuDepthState(
        depthWriteEnabled = if (depthTest) depthWrite else false,
        depthCompare = if (depthTest) io.materia.gpu.GpuCompareFunction.LESS_EQUAL else io.materia.gpu.GpuCompareFunction.ALWAYS
    )
} else {
    null
}
