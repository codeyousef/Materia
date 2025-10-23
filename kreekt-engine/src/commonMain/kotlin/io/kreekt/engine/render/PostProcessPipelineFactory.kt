package io.kreekt.engine.render

import io.kreekt.gpu.GpuBindGroup
import io.kreekt.gpu.GpuBindGroupDescriptor
import io.kreekt.gpu.GpuBindGroupEntry
import io.kreekt.gpu.GpuBindGroupLayout
import io.kreekt.gpu.GpuBindGroupLayoutDescriptor
import io.kreekt.gpu.GpuBindGroupLayoutEntry
import io.kreekt.gpu.GpuBindingResource
import io.kreekt.gpu.GpuBindingResourceType
import io.kreekt.gpu.GpuDevice
import io.kreekt.gpu.GpuRenderPipeline
import io.kreekt.gpu.GpuRenderPipelineDescriptor
import io.kreekt.gpu.GpuShaderModuleDescriptor
import io.kreekt.gpu.GpuShaderStage
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.io.readTextResource

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
                code = readTextResource("shaders/fullscreen_fxaa.vert.wgsl")
            )
        )
        val fragment = device.createShaderModule(
            GpuShaderModuleDescriptor(
                label = "fullscreen_fxaa.frag",
                code = readTextResource("shaders/fullscreen_fxaa.frag.wgsl")
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
        textureView: io.kreekt.gpu.GpuTextureView,
        sampler: io.kreekt.gpu.GpuSampler
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
}
