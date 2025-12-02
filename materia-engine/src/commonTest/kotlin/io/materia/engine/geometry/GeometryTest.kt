package io.materia.engine.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for geometry-related components.
 */
class GeometryTest {

    @Test
    fun geometryAttribute_storesCorrectValues() {
        val attr = GeometryAttribute(
            offset = 12,
            components = 3,
            type = AttributeType.FLOAT32
        )
        
        assertEquals(12, attr.offset)
        assertEquals(3, attr.components)
        assertEquals(AttributeType.FLOAT32, attr.type)
    }

    @Test
    fun geometryLayout_computesStrideCorrectly() {
        val layout = GeometryLayout(
            stride = 32, // position(12) + normal(12) + uv(8)
            attributes = mapOf(
                AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32),
                AttributeSemantic.NORMAL to GeometryAttribute(12, 3, AttributeType.FLOAT32),
                AttributeSemantic.UV to GeometryAttribute(24, 2, AttributeType.FLOAT32)
            )
        )
        
        assertEquals(32, layout.stride)
        assertEquals(3, layout.attributes.size)
        
        val posAttr = layout.attributes[AttributeSemantic.POSITION]
        assertNotNull(posAttr)
        assertEquals(0, posAttr.offset)
        assertEquals(3, posAttr.components)
    }

    @Test
    fun attributeSemantic_commonTypes() {
        // Verify common semantics exist
        val semantics = listOf(
            AttributeSemantic.POSITION,
            AttributeSemantic.NORMAL,
            AttributeSemantic.UV,
            AttributeSemantic.COLOR
        )
        
        assertEquals(4, semantics.size)
        assertTrue(semantics.all { it.name.isNotEmpty() })
    }

    @Test
    fun geometryLayout_emptyAttributes() {
        val layout = GeometryLayout(
            stride = 0,
            attributes = emptyMap()
        )
        
        assertEquals(0, layout.stride)
        assertTrue(layout.attributes.isEmpty())
    }

    @Test
    fun geometryAttribute_colorAttribute() {
        val colorAttr = GeometryAttribute(
            offset = 24,
            components = 4, // RGBA
            type = AttributeType.FLOAT32
        )
        
        assertEquals(24, colorAttr.offset)
        assertEquals(4, colorAttr.components)
    }
}
