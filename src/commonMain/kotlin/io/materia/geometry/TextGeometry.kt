/**
 * TextGeometry with comprehensive font loading and text rendering support
 * Converts text strings into 3D geometry using vector fonts with advanced typography features
 */
package io.materia.geometry

import io.materia.core.math.Box3
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.text.GeometryMerger
import io.materia.geometry.text.PathConverter
import io.materia.geometry.text.ShapeTriangulator
import io.materia.geometry.text.TextLayoutEngine

/**
 * Options for text geometry generation
 */
data class TextOptions(
    val size: Float = 12f,
    val height: Float = 1f,
    val curveSegments: Int = 12,
    val bevelEnabled: Boolean = false,
    val bevelThickness: Float = 0.1f,
    val bevelSize: Float = 0.05f,
    val bevelOffset: Float = 0f,
    val bevelSegments: Int = 3,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val textAlign: TextAlign = TextAlign.LEFT,
    val textBaseline: TextBaseline = TextBaseline.ALPHABETIC,
    val maxWidth: Float? = null,
    val wordWrap: Boolean = false
) {
    init {
        require(size > 0f) { "Text size must be positive" }
        require(height >= 0f) { "Text height must be non-negative" }
        require(curveSegments >= 3) { "Curve segments must be at least 3" }
        require(lineHeight > 0f) { "Line height must be positive" }
        if (bevelEnabled) {
            require(bevelThickness >= 0f) { "Bevel thickness must be non-negative" }
            require(bevelSize >= 0f) { "Bevel size must be non-negative" }
            require(bevelSegments >= 0) { "Bevel segments must be non-negative" }
        }
    }
}

/**
 * Text alignment options
 */
enum class TextAlign {
    LEFT, CENTER, RIGHT, JUSTIFY
}

/**
 * Text baseline options
 */
enum class TextBaseline {
    ALPHABETIC, TOP, HANGING, MIDDLE, IDEOGRAPHIC, BOTTOM
}

/**
 * Font interface for vector font data
 */
interface Font {
    val familyName: String
    val styleName: String
    val unitsPerEm: Int
    val ascender: Float
    val descender: Float
    val lineGap: Float
    val glyphs: Map<Char, Glyph>

    fun getGlyph(char: Char): Glyph?
    fun getKerning(leftChar: Char, rightChar: Char): Float
    fun measureText(text: String, size: Float): TextMetrics
}

/**
 * Glyph data for individual characters
 */
data class Glyph(
    val unicode: Char,
    val width: Float,
    val leftSideBearing: Float,
    val rightSideBearing: Float,
    val path: GlyphPath
)

/**
 * Vector path data for glyph outlines
 */
data class GlyphPath(
    val commands: List<PathCommand>,
    val boundingBox: BoundingBox2D
)

/**
 * Path commands for vector glyph outlines
 */
sealed class PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand()
    data class LineTo(val x: Float, val y: Float) : PathCommand()
    data class QuadraticCurveTo(val cpx: Float, val cpy: Float, val x: Float, val y: Float) :
        PathCommand()

    data class BezierCurveTo(
        val cp1x: Float,
        val cp1y: Float,
        val cp2x: Float,
        val cp2y: Float,
        val x: Float,
        val y: Float
    ) : PathCommand()

    object ClosePath : PathCommand()
}

/**
 * Text measurement results
 */
data class TextMetrics(
    val width: Float,
    val height: Float,
    val actualBoundingBoxLeft: Float,
    val actualBoundingBoxRight: Float,
    val actualBoundingBoxAscent: Float,
    val actualBoundingBoxDescent: Float,
    val fontBoundingBoxAscent: Float,
    val fontBoundingBoxDescent: Float
)

/**
 * 2D bounding box for glyph bounds
 */
data class BoundingBox2D(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
}

/**
 * Font loading result
 */
sealed class FontResult<T> {
    data class Success<T>(val value: T) : FontResult<T>()
    data class Error<T>(val exception: Exception) : FontResult<T>()
}

/**
 * Font loader interface for different font formats
 */
interface FontLoader {
    suspend fun loadFont(path: String): FontResult<Font>
    suspend fun loadFont(data: ByteArray): FontResult<Font>
    fun getSupportedFormats(): List<String>
}

/**
 * Text layout information
 */
data class TextLayout(
    val lines: List<TextLine>,
    val totalWidth: Float,
    val totalHeight: Float,
    val baseline: Float
)

/**
 * Individual text line information
 */
data class TextLine(
    val text: String,
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float,
    val glyphs: List<PositionedGlyph>
)

/**
 * Positioned glyph with transform information
 */
data class PositionedGlyph(
    val glyph: Glyph,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float = 0f
)

/**
 * TextGeometry class for generating 3D text
 */
class TextGeometry(
    val text: String,
    val font: Font,
    val options: TextOptions = TextOptions()
) : BufferGeometry() {

    private val textLayout: TextLayout

    init {
        require(text.isNotEmpty()) { "Text cannot be empty" }
        textLayout = TextLayoutEngine.layout(text, font, options)
        generate()
    }

    private fun generate() {
        val vertices = mutableListOf<Vector3>()
        val normals = mutableListOf<Vector3>()
        val uvs = mutableListOf<Vector2>()
        val indices = mutableListOf<Int>()

        var vertexOffset = 0

        // Generate geometry for each line
        for (line in textLayout.lines) {
            for (positionedGlyph in line.glyphs) {
                generateGlyphGeometry(
                    positionedGlyph,
                    line.offsetX,
                    line.offsetY,
                    vertices,
                    normals,
                    uvs,
                    indices,
                    vertexOffset
                )
                vertexOffset = vertices.size
            }
        }

        // Set geometry attributes
        setAttribute(
            "position",
            BufferAttribute(vertices.flatMap { listOf(it.x, it.y, it.z) }.toFloatArray(), 3)
        )
        setAttribute(
            "normal",
            BufferAttribute(normals.flatMap { listOf(it.x, it.y, it.z) }.toFloatArray(), 3)
        )
        setAttribute("uv", BufferAttribute(uvs.flatMap { listOf(it.x, it.y) }.toFloatArray(), 2))
        setIndex(BufferAttribute(indices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    private fun generateGlyphGeometry(
        positionedGlyph: PositionedGlyph,
        lineOffsetX: Float,
        lineOffsetY: Float,
        vertices: MutableList<Vector3>,
        normals: MutableList<Vector3>,
        uvs: MutableList<Vector2>,
        indices: MutableList<Int>,
        vertexOffset: Int
    ) {
        val glyph = positionedGlyph.glyph
        val path = glyph.path
        val transform = TransformMatrix3()
            .scale(positionedGlyph.scale, positionedGlyph.scale)
            .translate(positionedGlyph.x + lineOffsetX, positionedGlyph.y + lineOffsetY)

        // Convert glyph path to 2D shape
        val shapes = PathConverter.convert(path, transform)

        // Generate 3D geometry for each shape
        for (shape in shapes) {
            if (options.height > 0f) {
                // Create extruded geometry
                val extrudeOptions = ExtrudeOptions(
                    depth = options.height,
                    bevelEnabled = options.bevelEnabled,
                    bevelThickness = options.bevelThickness,
                    bevelSize = options.bevelSize,
                    bevelOffset = options.bevelOffset,
                    bevelSegments = options.bevelSegments,
                    steps = 1
                )

                val extrudeGeometry = ExtrudeGeometry(shape, extrudeOptions)
                GeometryMerger.merge(extrudeGeometry, vertices, normals, uvs, indices)
            } else {
                // Create flat geometry
                ShapeTriangulator.triangulate(shape, vertices, normals, uvs, indices)
            }
        }
    }

    /**
     * Get text layout information
     */
    fun getTextLayout(): TextLayout = textLayout

    /**
     * Get text bounds in 3D space
     */
    fun getTextBounds(): Box3 {
        return computeBoundingBox()
    }
}

/**
 * Simple font implementation for testing
 */
class SimpleFont(
    override val familyName: String,
    override val styleName: String = "Regular",
    override val unitsPerEm: Int = 1000,
    override val ascender: Float = 800f,
    override val descender: Float = -200f,
    override val lineGap: Float = 100f,
    override val glyphs: Map<Char, Glyph> = emptyMap()
) : Font {

    override fun getGlyph(char: Char): Glyph? = glyphs[char]

    override fun getKerning(leftChar: Char, rightChar: Char): Float = 0f

    override fun measureText(text: String, size: Float): TextMetrics {
        val scale = size / unitsPerEm
        var width = 0f

        for (char in text) {
            val glyph = getGlyph(char)
            if (glyph != null) {
                width = width + glyph.width * scale
            }
        }

        return TextMetrics(
            width = width,
            height = size,
            actualBoundingBoxLeft = 0f,
            actualBoundingBoxRight = width,
            actualBoundingBoxAscent = (ascender * scale),
            actualBoundingBoxDescent = -(descender * scale),
            fontBoundingBoxAscent = (ascender * scale),
            fontBoundingBoxDescent = -(descender * scale)
        )
    }
}

/**
 * Helper class for 3x3 matrix transformations
 */
class TransformMatrix3 {
    private val elements = FloatArray(9) { 0f }

    init {
        identity()
    }

    fun identity(): TransformMatrix3 {
        elements[0] = 1f; elements[1] = 0f; elements[2] = 0f
        elements[3] = 0f; elements[4] = 1f; elements[5] = 0f
        elements[6] = 0f; elements[7] = 0f; elements[8] = 1f
        return this
    }

    fun scale(sx: Float, sy: Float): TransformMatrix3 {
        elements[0] *= sx; elements[3] *= sx; elements[6] *= sx
        elements[1] *= sy; elements[4] *= sy; elements[7] *= sy
        return this
    }

    fun translate(tx: Float, ty: Float): TransformMatrix3 {
        elements[6] += elements[0] * tx + elements[3] * ty
        elements[7] += elements[1] * tx + elements[4] * ty
        return this
    }

    fun transformPoint(point: Vector2): Vector2 {
        val x = point.x
        val y = point.y
        return Vector2(
            elements[0] * x + elements[3] * y + elements[6],
            elements[1] * x + elements[4] * y + elements[7]
        )
    }
}

/**
 * Utility functions for text geometry
 */
object TextGeometryHelper {

    /**
     * Create a simple rectangular font for testing
     */
    fun createTestFont(): Font {
        val glyphs = mutableMapOf<Char, Glyph>()

        // Simple rectangular glyphs for basic ASCII characters
        for (c in 'A'..'Z') {
            val path = GlyphPath(
                commands = listOf(
                    PathCommand.MoveTo(0f, 0f),
                    PathCommand.LineTo(500f, 0f),
                    PathCommand.LineTo(500f, 700f),
                    PathCommand.LineTo(0f, 700f),
                    PathCommand.ClosePath
                ),
                boundingBox = BoundingBox2D(0f, 0f, 500f, 700f)
            )

            glyphs[c] = Glyph(
                unicode = c,
                width = 600f,
                leftSideBearing = 50f,
                rightSideBearing = 50f,
                path = path
            )
        }

        return SimpleFont("TestFont", "Regular", 1000, 800f, -200f, 100f, glyphs)
    }

    /**
     * Create text geometry with default settings
     */
    fun createText(text: String, font: Font? = null): TextGeometry {
        val usedFont = font ?: createTestFont()
        return TextGeometry(text, usedFont, TextOptions())
    }
}