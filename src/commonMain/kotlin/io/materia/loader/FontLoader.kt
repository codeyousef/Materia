package io.materia.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loader for typeface fonts used with TextGeometry.
 *
 * FontLoader loads JSON font files (typeface.js format) for use with
 * [TextGeometry] to render 3D text.
 *
 * ## Usage
 *
 * ```kotlin
 * val loader = FontLoader()
 * val font = loader.load("fonts/helvetiker_regular.typeface.json")
 *
 * val textGeometry = TextGeometry("Hello World", TextGeometryParameters(
 *     font = font,
 *     size = 1f,
 *     depth = 0.2f
 * ))
 * ```
 *
 * ## Font Format
 *
 * The loader expects fonts in typeface.js JSON format:
 * - Convert fonts using https://gero3.github.io/facetype.js/
 * - Supports TrueType (.ttf) and OpenType (.otf) sources
 *
 * @param resolver Asset resolver for loading font files.
 * @param manager Optional loading manager.
 */
class FontLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val manager: LoadingManager? = null
) : AssetLoader<Font> {

    private val cache = mutableMapOf<String, Font>()

    /**
     * Loads a font from the given path.
     *
     * @param path Path to the typeface.js JSON font file.
     * @return The loaded font.
     */
    override suspend fun load(path: String): Font = withContext(Dispatchers.Default) {
        // Check cache
        cache[path]?.let { return@withContext it }

        manager?.itemStart(path)

        try {
            val bytes = resolver.load(path)
            val json = bytes.decodeToString()
            val font = parseFont(json)

            cache[path] = font
            manager?.itemEnd(path)

            font
        } catch (e: Exception) {
            manager?.itemError(path, e)
            throw e
        }
    }

    /**
     * Loads a font with callbacks (Three.js-style API).
     */
    suspend fun load(
        path: String,
        onLoad: (Font) -> Unit,
        onProgress: ((LoadingProgress) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            val font = load(path)
            onLoad(font)
        } catch (e: Exception) {
            onError?.invoke(e) ?: throw e
        }
    }

    /**
     * Clears the font cache.
     */
    fun clearCache() {
        cache.clear()
    }

    companion object {
        /**
         * Parses a typeface.js JSON font.
         */
        internal fun parseFont(json: String): Font {
            // Simple JSON parsing for font data
            // In production, use kotlinx.serialization

            val glyphs = mutableMapOf<Char, FontGlyph>()
            var familyName = ""
            var resolution = 1000
            var boundingBox = FontBoundingBox()

            // Parse basic properties
            val familyMatch = Regex(""""familyName"\s*:\s*"([^"]+)"""").find(json)
            familyName = familyMatch?.groupValues?.get(1) ?: "Unknown"

            val resolutionMatch = Regex(""""resolution"\s*:\s*(\d+)""").find(json)
            resolution = resolutionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1000

            // Parse bounding box
            val yMinMatch = Regex(""""yMin"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json)
            val yMaxMatch = Regex(""""yMax"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json)
            val xMinMatch = Regex(""""xMin"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json)
            val xMaxMatch = Regex(""""xMax"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json)

            boundingBox = FontBoundingBox(
                xMin = xMinMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
                xMax = xMaxMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
                yMin = yMinMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
                yMax = yMaxMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            )

            // Parse glyphs section
            val glyphsMatch = Regex(""""glyphs"\s*:\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""").find(json)
            val glyphsSection = glyphsMatch?.groupValues?.get(1) ?: ""

            // Extract individual glyphs
            val glyphPattern = Regex(""""([^"]+)"\s*:\s*\{([^}]*(?:\[[^\]]*\][^}]*)*)\}""")
            for (match in glyphPattern.findAll(glyphsSection)) {
                val charStr = match.groupValues[1]
                val glyphData = match.groupValues[2]

                val char = if (charStr.length == 1) charStr[0] else continue

                val haMatch = Regex(""""ha"\s*:\s*(\d+(?:\.\d+)?)""").find(glyphData)
                val horizontalAdvance = haMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

                val oMatch = Regex(""""o"\s*:\s*"([^"]*)"""").find(glyphData)
                val pathCommands = oMatch?.groupValues?.get(1) ?: ""

                glyphs[char] = FontGlyph(
                    char = char,
                    horizontalAdvance = horizontalAdvance,
                    pathCommands = pathCommands
                )
            }

            return Font(
                familyName = familyName,
                resolution = resolution,
                boundingBox = boundingBox,
                glyphs = glyphs
            )
        }
    }
}

/**
 * Represents a loaded font for use with TextGeometry.
 */
data class Font(
    /** Font family name. */
    val familyName: String,
    /** Font resolution (units per em). */
    val resolution: Int,
    /** Font bounding box. */
    val boundingBox: FontBoundingBox,
    /** Map of characters to glyph data. */
    val glyphs: Map<Char, FontGlyph>
) {
    /**
     * Gets a glyph for a character.
     *
     * @param char The character to look up.
     * @return The glyph, or null if not found.
     */
    fun getGlyph(char: Char): FontGlyph? = glyphs[char]

    /**
     * Checks if the font contains a character.
     */
    fun hasGlyph(char: Char): Boolean = glyphs.containsKey(char)

    /**
     * Gets the text width for a string.
     *
     * @param text The text to measure.
     * @param size Font size.
     * @return Width of the text.
     */
    fun getTextWidth(text: String, size: Float = 1f): Float {
        val scale = size / resolution
        return text.sumOf { char ->
            (glyphs[char]?.horizontalAdvance ?: 0f).toDouble()
        }.toFloat() * scale
    }
}

/**
 * Bounding box for a font.
 */
data class FontBoundingBox(
    val xMin: Float = 0f,
    val xMax: Float = 0f,
    val yMin: Float = 0f,
    val yMax: Float = 0f
)

/**
 * Represents a single glyph (character) in a font.
 */
data class FontGlyph(
    /** The character this glyph represents. */
    val char: Char,
    /** Horizontal advance (spacing to next character). */
    val horizontalAdvance: Float,
    /** SVG-style path commands for the glyph outline. */
    val pathCommands: String
) {
    /**
     * Parses path commands into a list of shape operations.
     */
    fun getPath(): List<GlyphPathCommand> {
        if (pathCommands.isEmpty()) return emptyList()

        val commands = mutableListOf<GlyphPathCommand>()
        val tokens = pathCommands.split(" ").filter { it.isNotEmpty() }

        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "m" -> {
                    // Move to
                    if (i + 2 < tokens.size) {
                        val x = tokens[i + 1].toFloatOrNull() ?: 0f
                        val y = tokens[i + 2].toFloatOrNull() ?: 0f
                        commands.add(GlyphPathCommand.MoveTo(x, y))
                        i += 3
                    } else break
                }
                "l" -> {
                    // Line to
                    if (i + 2 < tokens.size) {
                        val x = tokens[i + 1].toFloatOrNull() ?: 0f
                        val y = tokens[i + 2].toFloatOrNull() ?: 0f
                        commands.add(GlyphPathCommand.LineTo(x, y))
                        i += 3
                    } else break
                }
                "q" -> {
                    // Quadratic curve
                    if (i + 4 < tokens.size) {
                        val cpX = tokens[i + 1].toFloatOrNull() ?: 0f
                        val cpY = tokens[i + 2].toFloatOrNull() ?: 0f
                        val x = tokens[i + 3].toFloatOrNull() ?: 0f
                        val y = tokens[i + 4].toFloatOrNull() ?: 0f
                        commands.add(GlyphPathCommand.QuadraticCurveTo(cpX, cpY, x, y))
                        i += 5
                    } else break
                }
                "b" -> {
                    // Bezier curve
                    if (i + 6 < tokens.size) {
                        val cp1X = tokens[i + 1].toFloatOrNull() ?: 0f
                        val cp1Y = tokens[i + 2].toFloatOrNull() ?: 0f
                        val cp2X = tokens[i + 3].toFloatOrNull() ?: 0f
                        val cp2Y = tokens[i + 4].toFloatOrNull() ?: 0f
                        val x = tokens[i + 5].toFloatOrNull() ?: 0f
                        val y = tokens[i + 6].toFloatOrNull() ?: 0f
                        commands.add(GlyphPathCommand.BezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, x, y))
                        i += 7
                    } else break
                }
                else -> i++
            }
        }

        return commands
    }
}

/**
 * Path commands for glyph outlines.
 */
sealed class GlyphPathCommand {
    data class MoveTo(val x: Float, val y: Float) : GlyphPathCommand()
    data class LineTo(val x: Float, val y: Float) : GlyphPathCommand()
    data class QuadraticCurveTo(val cpX: Float, val cpY: Float, val x: Float, val y: Float) : GlyphPathCommand()
    data class BezierCurveTo(
        val cp1X: Float, val cp1Y: Float,
        val cp2X: Float, val cp2Y: Float,
        val x: Float, val y: Float
    ) : GlyphPathCommand()
}
