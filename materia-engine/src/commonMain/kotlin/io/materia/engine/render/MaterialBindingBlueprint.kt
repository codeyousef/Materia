package io.materia.engine.render

import io.materia.engine.material.Material
import io.materia.engine.material.RenderState
import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.material.UnlitLineMaterial
import io.materia.engine.material.UnlitPointsMaterial
import io.materia.gpu.GpuDevice
import io.materia.gpu.GpuPrimitiveTopology
import io.materia.gpu.GpuTextureFormat
import io.materia.gpu.GpuVertexBufferLayout

sealed class MaterialBindingBlueprint(
    val renderState: RenderState
) {
    abstract val vertexLayout: GpuVertexBufferLayout
    abstract val primitiveTopology: GpuPrimitiveTopology

    fun createPipeline(
        device: GpuDevice,
        colorFormat: GpuTextureFormat
    ): UnlitPipelineFactory.PipelineResources =
        when (this) {
            is UnlitColor -> UnlitPipelineFactory.createUnlitColorPipeline(
                device,
                colorFormat,
                renderState,
                primitiveTopology
            )

            is UnlitLines -> UnlitPipelineFactory.createUnlitColorPipeline(
                device,
                colorFormat,
                renderState,
                primitiveTopology
            )

            is UnlitPoints -> UnlitPipelineFactory.createUnlitPointsPipeline(
                device,
                colorFormat,
                renderState
            )
        }

    class UnlitColor(renderState: RenderState) : MaterialBindingBlueprint(renderState) {
        override val vertexLayout: GpuVertexBufferLayout =
            UnlitPipelineFactory.vertexLayoutWithColor()
        override val primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.TRIANGLE_LIST
    }

    class UnlitLines(renderState: RenderState) : MaterialBindingBlueprint(renderState) {
        override val vertexLayout: GpuVertexBufferLayout =
            UnlitPipelineFactory.vertexLayoutWithColor()
        override val primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.LINE_LIST
    }

    class UnlitPoints(renderState: RenderState) : MaterialBindingBlueprint(renderState) {
        override val vertexLayout: GpuVertexBufferLayout =
            UnlitPipelineFactory.instancedPointsLayout()
        override val primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.POINT_LIST
    }
}

fun Material.toBindingBlueprint(): MaterialBindingBlueprint = when (this) {
    is UnlitColorMaterial -> MaterialBindingBlueprint.UnlitColor(renderState)
    is UnlitLineMaterial -> MaterialBindingBlueprint.UnlitLines(renderState)
    is UnlitPointsMaterial -> MaterialBindingBlueprint.UnlitPoints(renderState)
}
