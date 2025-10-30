package io.materia.engine.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeometryUtilsTest {

    @Test
    fun buildInterleavedGeometryProducesExpectedStrideAndData() {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f
        )
        val normals = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f
        )
        val uvs = floatArrayOf(
            0f, 0f,
            1f, 0f
        )
        val colors = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val indices = shortArrayOf(0, 1)

        val geometry = buildInterleavedGeometry(
            InterleavedGeometrySource(
                positions = positions,
                normals = normals,
                uvs = uvs,
                colors = colors,
                indices = indices
            )
        )

        val layout = geometry.layout
        assertEquals(Float.SIZE_BYTES * 11, layout.stride)
        assertEquals(2, geometry.vertexCount())
        assertEquals(indices.size, geometry.indexBuffer?.size)

        val positionAttr = layout.attributes[AttributeSemantic.POSITION]
        val normalAttr = layout.attributes[AttributeSemantic.NORMAL]
        val uvAttr = layout.attributes[AttributeSemantic.UV]
        val colorAttr = layout.attributes[AttributeSemantic.COLOR]

        assertNotNull(positionAttr)
        assertNotNull(normalAttr)
        assertNotNull(uvAttr)
        assertNotNull(colorAttr)

        val data = geometry.vertexBuffer.data
        val firstVertex = listOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        val secondVertex = listOf(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f)
        assertEquals(firstVertex, data.take(11))
        assertEquals(secondVertex, data.drop(11))
    }

    @Test
    fun buildInterleavedGeometryHandlesPositionsOnly() {
        val positions = floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f)
        val geometry = buildInterleavedGeometry(
            InterleavedGeometrySource(positions = positions)
        )

        assertEquals(Float.SIZE_BYTES * 3, geometry.layout.stride)
        assertTrue(geometry.layout.attributes.containsKey(AttributeSemantic.POSITION))
        assertEquals(2, geometry.vertexCount())
    }
}
