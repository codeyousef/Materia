package io.kreekt.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals

class InstancedPointsTest {

    @Test
    fun createBuildsInstanceData() {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f
        )
        val colors = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val sizes = floatArrayOf(1f, 2f)
        val extras = floatArrayOf(0f, 0f, 0f, 1f, 0.1f, 0.2f, 0.3f, 0.4f)

        val points = InstancedPoints.create(
            name = "stars",
            positions = positions,
            colors = colors,
            sizes = sizes,
            extras = extras
        )

        assertEquals(2, points.instanceCount())
        assertEquals(InstancedPoints.COMPONENTS_PER_INSTANCE * 2, points.instanceData.size)

        points.updateInstance(
            index = 1,
            position = Vec3Components(2f, 0f, 0f),
            color = Vec3Components(0f, 0f, 1f),
            size = 1.5f,
            extra = Vec4Components(0.5f, 0.6f, 0.7f, 0.8f)
        )

        val base = InstancedPoints.COMPONENTS_PER_INSTANCE
        assertEquals(2f, points.instanceData[base])
        assertEquals(0f, points.instanceData[base + 1])
        assertEquals(0f, points.instanceData[base + 2])
        assertEquals(0f, points.instanceData[base + 3])
        assertEquals(0f, points.instanceData[base + 4])
        assertEquals(1f, points.instanceData[base + 5])
        assertEquals(1.5f, points.instanceData[base + 6])
        assertEquals(0.5f, points.instanceData[base + 7])
        assertEquals(0.6f, points.instanceData[base + 8])
        assertEquals(0.7f, points.instanceData[base + 9])
        assertEquals(0.8f, points.instanceData[base + 10])
    }
}
