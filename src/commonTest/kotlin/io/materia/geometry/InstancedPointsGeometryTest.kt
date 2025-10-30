package io.materia.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstancedPointsGeometryTest {
    @Test
    fun attributesExposeExpectedStrides() {
        val geometry = InstancedPointsGeometry(4)

        val position = geometry.getInstancedAttribute(InstancedPointsGeometry.POSITION_ATTRIBUTE)
        val color = geometry.getInstancedAttribute(InstancedPointsGeometry.COLOR_ATTRIBUTE)
        val size = geometry.getInstancedAttribute(InstancedPointsGeometry.SIZE_ATTRIBUTE)
        val extra = geometry.getInstancedAttribute(InstancedPointsGeometry.EXTRA_ATTRIBUTE)

        assertEquals(3, position?.itemSize)
        assertEquals(3, color?.itemSize)
        assertEquals(1, size?.itemSize)
        assertEquals(4, extra?.itemSize)

        geometry.updatePosition(0, 1f, 2f, 3f)
        geometry.updateColor(0, 0.5f, 0.6f, 0.7f)
        geometry.updateSize(0, 2.5f)
        geometry.updateExtra(0, 1f, 0f, 0f, 0.5f)

        assertTrue(position!!.needsUpdate)
        assertTrue(color!!.needsUpdate)
        assertTrue(size!!.needsUpdate)
        assertTrue(extra!!.needsUpdate)

        assertEquals(1f, position.getX(0))
        assertEquals(0.5f, color.getX(0))
        assertEquals(2.5f, size.getX(0))
        assertEquals(0.5f, extra.getW(0))
    }
}
