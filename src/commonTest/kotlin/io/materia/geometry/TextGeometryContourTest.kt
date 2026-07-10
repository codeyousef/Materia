package io.materia.geometry

import io.materia.geometry.text.PathConverter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TextGeometryContourTest {
    @Test
    fun pathConverterGroupsInnerContoursAsHoles() {
        val shapes = PathConverter.convert(compoundGlyph('O').path, TransformMatrix3())

        assertEquals(1, shapes.size)
        assertEquals(1, shapes.single().holes.size)
    }

    @Test
    fun flatTextTriangulatesConcaveGlyphs() {
        val geometry = geometryFor(concaveGlyph('L'))

        assertEquals(5f, triangleArea(geometry), 0.001f)
    }

    @Test
    fun flatTextTriangulationPreservesGlyphHoles() {
        val geometry = geometryFor(compoundGlyph('O'))

        assertEquals(84f, triangleArea(geometry), 0.001f)
    }

    @Test
    fun extrudedTextCapsPreserveGlyphHoles() {
        val geometry = geometryFor(compoundGlyph('O'), height = 2f)

        assertEquals(84f, triangleArea(geometry, requiredZ = 2f), 0.001f)
    }

    private fun geometryFor(glyph: Glyph, height: Float = 0f): TextGeometry {
        val font = SimpleFont(
            familyName = "Contour Test",
            unitsPerEm = 10,
            ascender = 8f,
            descender = -2f,
            glyphs = mapOf(glyph.unicode to glyph)
        )
        return TextGeometry(
            text = glyph.unicode.toString(),
            font = font,
            options = TextOptions(size = 10f, height = height, curveSegments = 3)
        )
    }

    private fun triangleArea(geometry: TextGeometry, requiredZ: Float? = null): Float {
        val positions = assertNotNull(geometry.getAttribute("position"))
        val indices = assertNotNull(geometry.index)
        var area = 0f

        for (offset in indices.array.indices step 3) {
            val a = indices.array[offset].toInt()
            val b = indices.array[offset + 1].toInt()
            val c = indices.array[offset + 2].toInt()
            if (requiredZ != null && listOf(a, b, c).any { abs(positions.getZ(it) - requiredZ) > 0.001f }) {
                continue
            }
            area += abs(
                (positions.getX(b) - positions.getX(a)) * (positions.getY(c) - positions.getY(a)) -
                    (positions.getY(b) - positions.getY(a)) * (positions.getX(c) - positions.getX(a))
            ) * 0.5f
        }

        return area
    }

    private fun concaveGlyph(char: Char): Glyph = Glyph(
        unicode = char,
        width = 4f,
        leftSideBearing = 0f,
        rightSideBearing = 0f,
        path = GlyphPath(
            commands = listOf(
                PathCommand.MoveTo(0f, 0f),
                PathCommand.LineTo(0f, 3f),
                PathCommand.LineTo(1f, 3f),
                PathCommand.LineTo(1f, 1f),
                PathCommand.LineTo(3f, 1f),
                PathCommand.LineTo(3f, 0f),
                PathCommand.ClosePath
            ),
            boundingBox = BoundingBox2D(0f, 0f, 3f, 3f)
        )
    )

    private fun compoundGlyph(char: Char): Glyph = Glyph(
        unicode = char,
        width = 12f,
        leftSideBearing = 0f,
        rightSideBearing = 0f,
        path = GlyphPath(
            commands = listOf(
                PathCommand.MoveTo(0f, 0f),
                PathCommand.LineTo(0f, 10f),
                PathCommand.LineTo(10f, 10f),
                PathCommand.LineTo(10f, 0f),
                PathCommand.ClosePath,
                PathCommand.MoveTo(3f, 3f),
                PathCommand.LineTo(7f, 3f),
                PathCommand.LineTo(7f, 7f),
                PathCommand.LineTo(3f, 7f),
                PathCommand.ClosePath
            ),
            boundingBox = BoundingBox2D(0f, 0f, 10f, 10f)
        )
    )
}
