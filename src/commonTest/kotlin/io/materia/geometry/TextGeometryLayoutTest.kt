package io.materia.geometry.layout

import io.materia.geometry.BoundingBox2D
import io.materia.geometry.Font
import io.materia.geometry.Glyph
import io.materia.geometry.GlyphPath
import io.materia.geometry.PathCommand
import io.materia.geometry.SimpleFont
import io.materia.geometry.TextAlign
import io.materia.geometry.TextBaseline
import io.materia.geometry.TextGeometry
import io.materia.geometry.TextMetrics
import io.materia.geometry.TextOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class TextGeometryLayoutTest {
    private val font = SimpleFont(
        familyName = "Layout Test",
        unitsPerEm = 1000,
        ascender = 800f,
        descender = -200f,
        glyphs = mapOf(
            'A' to rectangularGlyph('A'),
            ' ' to Glyph(
                unicode = ' ',
                width = 300f,
                leftSideBearing = 0f,
                rightSideBearing = 0f,
                path = GlyphPath(emptyList(), BoundingBox2D(0f, 0f, 0f, 0f))
            )
        )
    )

    @Test
    fun glyphAdvanceIsAppliedInOutputUnits() {
        val bounds = geometry("AA").getTextBounds()

        assertEquals(0f, bounds.min.x, 0.001f)
        assertEquals(110f, bounds.max.x, 0.001f)
    }

    @Test
    fun multilineOffsetUsesConfiguredLineHeight() {
        val bounds = geometry("A\nA", lineHeight = 1.2f).getTextBounds()

        assertEquals(-120f, bounds.min.y, 0.001f)
        assertEquals(70f, bounds.max.y, 0.001f)
    }

    @Test
    fun centerAlignmentUsesTheFullLineWidth() {
        val bounds = geometry("AA", textAlign = TextAlign.CENTER).getTextBounds()

        assertEquals(-60f, bounds.min.x, 0.001f)
        assertEquals(50f, bounds.max.x, 0.001f)
    }

    @Test
    fun kerningIsAppliedInOutputUnits() {
        val kerningFont = KerningFont(font, -100f)
        val bounds = geometry("AA", font = kerningFont).getTextBounds()

        assertEquals(100f, bounds.max.x, 0.001f)
    }

    @Test
    fun wordWrapUsesTheConfiguredOutputWidth() {
        val bounds = geometry("AA AA", wordWrap = true, maxWidth = 150f).getTextBounds()

        assertEquals(-120f, bounds.min.y, 0.001f)
        assertEquals(110f, bounds.max.x, 0.001f)
    }

    @Test
    fun baselineOffsetIsAppliedToGeneratedGeometry() {
        val alphabetic = geometry("A", textBaseline = TextBaseline.ALPHABETIC).getTextBounds()
        val top = geometry("A", textBaseline = TextBaseline.TOP).getTextBounds()

        assertEquals(-80f, top.min.y - alphabetic.min.y, 0.001f)
        assertEquals(-80f, top.max.y - alphabetic.max.y, 0.001f)
    }

    private fun geometry(
        text: String,
        font: Font = this.font,
        lineHeight: Float = 1.2f,
        textAlign: TextAlign = TextAlign.LEFT,
        textBaseline: TextBaseline = TextBaseline.ALPHABETIC,
        maxWidth: Float? = null,
        wordWrap: Boolean = false
    ): TextGeometry = TextGeometry(
        text = text,
        font = font,
        options = TextOptions(
            size = 100f,
            height = 0f,
            curveSegments = 3,
            lineHeight = lineHeight,
            textAlign = textAlign,
            textBaseline = textBaseline,
            maxWidth = maxWidth,
            wordWrap = wordWrap
        )
    )

    private fun rectangularGlyph(char: Char): Glyph = Glyph(
        unicode = char,
        width = 600f,
        leftSideBearing = 0f,
        rightSideBearing = 100f,
        path = GlyphPath(
            commands = listOf(
                PathCommand.MoveTo(0f, 0f),
                PathCommand.LineTo(500f, 0f),
                PathCommand.LineTo(500f, 700f),
                PathCommand.LineTo(0f, 700f),
                PathCommand.ClosePath
            ),
            boundingBox = BoundingBox2D(0f, 0f, 500f, 700f)
        )
    )

    private class KerningFont(
        private val delegate: Font,
        private val kerning: Float
    ) : Font by delegate {
        override fun getKerning(leftChar: Char, rightChar: Char): Float = kerning

        override fun measureText(text: String, size: Float): TextMetrics =
            delegate.measureText(text, size)
    }
}
