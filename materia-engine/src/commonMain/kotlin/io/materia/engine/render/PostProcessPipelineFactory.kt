package io.materia.engine.render

import io.materia.gpu.GpuBindGroup
import io.materia.gpu.GpuBindGroupDescriptor
import io.materia.gpu.GpuBindGroupEntry
import io.materia.gpu.GpuBindGroupLayout
import io.materia.gpu.GpuBindGroupLayoutDescriptor
import io.materia.gpu.GpuBindGroupLayoutEntry
import io.materia.gpu.GpuBindingResource
import io.materia.gpu.GpuBindingResourceType
import io.materia.gpu.GpuDevice
import io.materia.gpu.GpuRenderPipeline
import io.materia.gpu.GpuRenderPipelineDescriptor
import io.materia.gpu.GpuShaderModuleDescriptor
import io.materia.gpu.GpuShaderStage
import io.materia.gpu.GpuTextureFormat

object PostProcessPipelineFactory {

    data class PipelineResources(
        val pipeline: GpuRenderPipeline,
        val bindGroupLayout: GpuBindGroupLayout
    )

    suspend fun createFxaaPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat
    ): PipelineResources {
        val layout = device.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                label = "fxaa-layout",
                entries = listOf(
                    GpuBindGroupLayoutEntry(
                        binding = 0,
                        visibility = setOf(GpuShaderStage.FRAGMENT),
                        resourceType = GpuBindingResourceType.TEXTURE
                    ),
                    GpuBindGroupLayoutEntry(
                        binding = 1,
                        visibility = setOf(GpuShaderStage.FRAGMENT),
                        resourceType = GpuBindingResourceType.SAMPLER
                    )
                )
            )
        )

        val vertex = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "fullscreen_fxaa.vert",
                code = FXAA_VERT_SHADER
            )
        )
        val fragment = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "fullscreen_fxaa.frag",
                code = FXAA_FRAG_SHADER
            )
        )

        val pipeline = device.createRenderPipeline(
            GpuRenderPipelineDescriptor(
                label = "fxaa-pipeline",
                vertexShader = vertex,
                fragmentShader = fragment,
                colorFormats = listOf(colorFormat),
                bindGroupLayouts = listOf(layout),
                depthState = null
            )
        )

        return PipelineResources(pipeline, layout)
    }

    fun createFxaaBindGroup(
        device: GpuDevice,
        layout: GpuBindGroupLayout,
        textureView: io.materia.gpu.GpuTextureView,
        sampler: io.materia.gpu.GpuSampler
    ): GpuBindGroup = device.createBindGroup(
        GpuBindGroupDescriptor(
            label = "fxaa-bind-group",
            layout = layout,
            entries = listOf(
                GpuBindGroupEntry(
                    binding = 0,
                    resource = GpuBindingResource.Texture(textureView)
                ),
                GpuBindGroupEntry(
                    binding = 1,
                    resource = GpuBindingResource.Sampler(sampler)
                )
            )
        )
    )

    private val FXAA_VERT_SHADER = """
        struct VertexOutput {
            @builtin(position) position : vec4<f32>,
            @location(0) uv : vec2<f32>,
        };

        const FULLSCREEN_POSITIONS : array<vec2<f32>, 3> = array<vec2<f32>, 3>(
            vec2<f32>(-1.0, -3.0),
            vec2<f32>(-1.0, 1.0),
            vec2<f32>(3.0, 1.0),
        );

        const FULLSCREEN_UVS : array<vec2<f32>, 3> = array<vec2<f32>, 3>(
            vec2<f32>(0.0, 2.0),
            vec2<f32>(0.0, 0.0),
            vec2<f32>(2.0, 0.0),
        );

        @vertex
        fn main(@builtin(vertex_index) vertexIndex : u32) -> VertexOutput {
            var output : VertexOutput;
            let pos = FULLSCREEN_POSITIONS[vertexIndex];
            output.position = vec4<f32>(pos, 0.0, 1.0);
            output.uv = FULLSCREEN_UVS[vertexIndex] * 0.5;
            return output;
        }
    """.trimIndent()

    private val FXAA_FRAG_SHADER = """
        struct FragmentInput {
            @location(0) uv : vec2<f32>,
        };

        struct FragmentOutput {
            @location(0) color : vec4<f32>,
        };

        @group(0) @binding(0)
        var uColorTexture : texture_2d<f32>;

        @group(0) @binding(1)
        var uColorSampler : sampler;

        @fragment
        fn main(input : FragmentInput) -> FragmentOutput {
            var output : FragmentOutput;
            output.color = textureSample(uColorTexture, uColorSampler, input.uv);
            return output;
        }
    """.trimIndent()
}
