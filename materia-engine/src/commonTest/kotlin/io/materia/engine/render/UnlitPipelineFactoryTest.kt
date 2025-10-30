package io.materia.engine.render

import io.materia.engine.material.RenderState
import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.material.UnlitPointsMaterial
import io.materia.gpu.GpuPrimitiveTopology
import io.materia.gpu.GpuVertexFormat
import io.materia.gpu.GpuVertexStepMode
import kotlin.test.Test
import kotlin.test.assertEquals

class UnlitPipelineFactoryTest {
    @Test
    fun instancedPointsLayoutMatchesExpectedOffsets() {
        val layout = UnlitPipelineFactory.instancedPointsLayout()

        assertEquals(Float.SIZE_BYTES * 11, layout.arrayStride)
        assertEquals(GpuVertexStepMode.INSTANCE, layout.stepMode)
        assertEquals(4, layout.attributes.size)

        val positionAttr = layout.attributes[0]
        val colorAttr = layout.attributes[1]
        val sizeAttr = layout.attributes[2]
        val extraAttr = layout.attributes[3]

        assertEquals(0, positionAttr.offset)
        assertEquals(GpuVertexFormat.FLOAT32x3, positionAttr.format)

        assertEquals(Float.SIZE_BYTES * 3, colorAttr.offset)
        assertEquals(GpuVertexFormat.FLOAT32x3, colorAttr.format)

        assertEquals(Float.SIZE_BYTES * 6, sizeAttr.offset)
        assertEquals(GpuVertexFormat.FLOAT32, sizeAttr.format)

        assertEquals(Float.SIZE_BYTES * 7, extraAttr.offset)
        assertEquals(GpuVertexFormat.FLOAT32x4, extraAttr.format)
    }

    @Test
    fun materialBlueprintsExposeExpectedLayouts() {
        val colorBlueprint =
            UnlitColorMaterial(label = "color", renderState = RenderState()).toBindingBlueprint()
        val pointsBlueprint = UnlitPointsMaterial(label = "points").toBindingBlueprint()

        assertEquals(GpuVertexStepMode.VERTEX, colorBlueprint.vertexLayout.stepMode)
        assertEquals(GpuVertexStepMode.INSTANCE, pointsBlueprint.vertexLayout.stepMode)
        assertEquals(GpuPrimitiveTopology.TRIANGLE_LIST, colorBlueprint.primitiveTopology)
        assertEquals(GpuPrimitiveTopology.POINT_LIST, pointsBlueprint.primitiveTopology)
        assertEquals(Float.SIZE_BYTES * 6, colorBlueprint.vertexLayout.arrayStride)
        assertEquals(Float.SIZE_BYTES * 11, pointsBlueprint.vertexLayout.arrayStride)
    }
}
