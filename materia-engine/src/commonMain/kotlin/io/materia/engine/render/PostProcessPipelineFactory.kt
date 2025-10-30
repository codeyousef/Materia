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
import io.materia.io.readTextResource

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
}
