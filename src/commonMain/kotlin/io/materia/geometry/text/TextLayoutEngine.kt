package io.materia.geometry.text

import io.materia.core.math.Vector2
import io.materia.geometry.*
import kotlin.math.max

/**
 * Text layout engine for arranging glyphs
 */
object TextLayoutEngine {

    /**
     * Layout text into lines with alignment and wrapping
     */
    fun layout(text: String, font: Font, options: TextOptions): TextLayout {
        val lines = if (options.wordWrap && options.maxWidth != null) {
            wrapText(text, font, options)
        } else {
            text.split('\n').map { it }
        }

        val textLines = mutableListOf<TextLine>()
        var maxWidth = 0f
        val lineHeight = options.size * options.lineHeight

        for ((lineIndex, lineText) in lines.withIndex()) {
            val line = layoutLine(lineText, font, options)
            val offsetY = -lineIndex * lineHeight

            // Apply text alignment
            val offsetX = when (options.textAlign) {
                TextAlign.LEFT -> 0f
                TextAlign.CENTER -> -line.width / 2f
                TextAlign.RIGHT -> -line.width
                TextAlign.JUSTIFY -> 0f // Justify will be handled in layoutLine
            }

            val adjustedLine = line.copy(offsetX = offsetX, offsetY = offsetY)
            textLines.add(adjustedLine)
            maxWidth = max(maxWidth, line.width)
        }

        val totalHeight = textLines.size * lineHeight
        val baseline = calculateBaseline(font, options)

        return TextLayout(textLines, maxWidth, totalHeight, baseline)
    }

    /**
     * Layout a single line of text
     */
    fun layoutLine(text: String, font: Font, options: TextOptions): TextLine {
        val glyphs = mutableListOf<PositionedGlyph>()
        var currentX = 0f
        val scale = options.size / font.unitsPerEm

        // First pass: layout normally to measure width
        for (i in text.indices) {
            val char = text[i]
            val glyph = font.getGlyph(char) ?: continue

            // Apply kerning if not the first character
            if (i > 0) {
                val kerning = font.getKerning(text[i - 1], char)
                currentX += (kerning * scale)
            }

            val positionedGlyph = PositionedGlyph(
                glyph = glyph,
                x = currentX,
                y = 0f,
                scale = scale
            )

            glyphs.add(positionedGlyph)

            // Advance position
            currentX += (glyph.width + options.letterSpacing) * scale
        }

        val lineWidth = currentX - options.letterSpacing * scale
        val lineHeight = options.size

        // Apply justify alignment if needed
        if (options.textAlign == TextAlign.JUSTIFY && options.maxWidth != null && glyphs.size > 1) {
            applyJustification(
                glyphs,
                text,
                options.maxWidth,
                lineWidth,
                scale,
                options.letterSpacing
            )
        }

        return TextLine(text, lineWidth, lineHeight, 0f, 0f, glyphs)
    }

    /**
     * Apply justify alignment
     */
    private fun applyJustification(
        glyphs: MutableList<PositionedGlyph>,
        text: String,
        targetWidth: Float,
        currentWidth: Float,
        scale: Float,
        letterSpacing: Float
    ) {
        val widthDiff = targetWidth - currentWidth
        val spaceCount = text.count { it == ' ' }

        if (spaceCount > 0) {
            // Distribute extra width among spaces
            val extraSpaceWidth = widthDiff / spaceCount
            var adjustedX = 0f
            var spacesSeen = 0

            for (i in glyphs.indices) {
                val glyph = glyphs[i]
                val char = text.getOrNull(i) ?: continue

                // Update position with justify adjustment
                glyphs[i] = glyph.copy(x = adjustedX)

                // Calculate next position
                adjustedX += glyph.glyph.width * scale + letterSpacing * scale

                // Add extra space after space characters
                if (char == ' ' && spacesSeen < spaceCount) {
                    adjustedX += extraSpaceWidth
                    spacesSeen++
                }
            }
        } else {
            // No spaces, distribute width between all characters
            // Check for division by zero - need at least 2 glyphs to distribute space
            if (glyphs.size <= 1) return

            val extraCharWidth = widthDiff / (glyphs.size - 1)
            for (i in glyphs.indices) {
                val glyph = glyphs[i]
                glyphs[i] = glyph.copy(x = glyph.x + i * extraCharWidth)
            }
        }
    }

    /**
     * Wrap text to fit within max width
     */
    fun wrapText(text: String, font: Font, options: TextOptions): List<String> {
        val maxWidth = options.maxWidth ?: return listOf(text)
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val metrics = font.measureText(testLine, options.size)

            if (metrics.width <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Word is too long, break it
                    lines.add(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    /**
     * Calculate baseline offset
     */
    fun calculateBaseline(font: Font, options: TextOptions): Float {
        val scale = options.size / font.unitsPerEm

        return when (options.textBaseline) {
            TextBaseline.ALPHABETIC -> 0f
            TextBaseline.TOP -> -font.ascender * scale
            TextBaseline.HANGING -> -font.ascender * scale * 0.8f
            TextBaseline.MIDDLE -> -(font.ascender - font.descender) * scale / 2f
            TextBaseline.IDEOGRAPHIC -> font.descender * scale
            TextBaseline.BOTTOM -> font.descender * scale
        }
    }
}
