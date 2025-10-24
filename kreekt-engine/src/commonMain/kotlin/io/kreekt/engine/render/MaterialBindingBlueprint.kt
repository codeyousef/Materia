package io.kreekt.engine.render

import io.kreekt.engine.material.Material
import io.kreekt.engine.material.RenderState
import io.kreekt.engine.material.UnlitColorMaterial
import io.kreekt.engine.material.UnlitPointsMaterial
import io.kreekt.gpu.GpuDevice
import io.kreekt.gpu.GpuPrimitiveTopology
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.GpuVertexBufferLayout

sealed class MaterialBindingBlueprint(
    val renderState: RenderState
) {
    abstract val vertexLayout: GpuVertexBufferLayout
    abstract val primitiveTopology: GpuPrimitiveTopology

    fun createPipeline(device: GpuDevice, colorFormat: GpuTextureFormat): UnlitPipelineFactory.PipelineResources =
        when (this) {
            is UnlitColor -> UnlitPipelineFactory.createUnlitColorPipeline(device, colorFormat, renderState)
            is UnlitPoints -> UnlitPipelineFactory.createUnlitPointsPipeline(device, colorFormat, renderState)
        }

    class UnlitColor(renderState: RenderState) : MaterialBindingBlueprint(renderState) {
        override val vertexLayout: GpuVertexBufferLayout = UnlitPipelineFactory.vertexLayoutWithColor()
        override val primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.TRIANGLE_LIST
    }

    class UnlitPoints(renderState: RenderState) : MaterialBindingBlueprint(renderState) {
        override val vertexLayout: GpuVertexBufferLayout = UnlitPipelineFactory.instancedPointsLayout()
        override val primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.POINT_LIST
    }
}

fun Material.toBindingBlueprint(): MaterialBindingBlueprint = when (this) {
    is UnlitColorMaterial -> MaterialBindingBlueprint.UnlitColor(renderState)
    is UnlitPointsMaterial -> MaterialBindingBlueprint.UnlitPoints(renderState)
}
