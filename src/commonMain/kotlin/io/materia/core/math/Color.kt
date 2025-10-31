package io.materia.core.math

import kotlin.math.abs

/**
 * Color representation supporting RGB and HSL color spaces.
 * Compatible with Three.js Color API.
 *
 * Colors are stored internally as RGB floating point values in the range [0, 1].
 */
data class Color(
    var r: Float = 1f,
    var g: Float = 1f,
    var b: Float = 1f,
    var a: Float = 1f
) {

    companion object {
        // Common colors
        val WHITE: Color
            get() = Color(1f, 1f, 1f, 1f)
        val BLACK: Color
            get() = Color(0f, 0f, 0f, 1f)
        val RED: Color
            get() = Color(1f, 0f, 0f, 1f)
        val GREEN: Color
            get() = Color(0f, 1f, 0f, 1f)
        val BLUE: Color
            get() = Color(0f, 0f, 1f, 1f)
        val YELLOW: Color
            get() = Color(1f, 1f, 0f, 1f)
        val CYAN: Color
            get() = Color(0f, 1f, 1f, 1f)
        val MAGENTA: Color
            get() = Color(1f, 0f, 1f, 1f)
        val GRAY: Color
            get() = Color(0.5f, 0.5f, 0.5f, 1f)
        val ORANGE: Color
            get() = Color(1f, 0.5f, 0f, 1f)
        val PURPLE: Color
            get() = Color(0.5f, 0f, 1f, 1f)

        /**
         * Creates a color from HSL values
         */
        fun fromHSL(h: Float, s: Float, l: Float): Color =
            Color().setHSL(h, s, l)

        /**
         * Creates a color from hex integer
         */
        fun fromHex(hex: Int): Color =
            Color().setHex(hex)

        /**
         * Creates a color from hex string
         */
        fun fromHexString(hex: String): Color =
            Color().setHexString(hex)

        /**
         * Creates a color by name
         */
        fun fromName(name: String): Color =
            Color().setName(name)

        /**
         * Linear interpolation between two colors
         */
        fun lerp(color1: Color, color2: Color, alpha: Float): Color =
            color1.clone().lerp(color2, alpha)
    }

    constructor(color: Color) : this(color.r, color.g, color.b, color.a)

    constructor(r: Float, g: Float, b: Float) : this(r, g, b, 1f)

    constructor(hex: Int) : this() {
        setHex(hex)
    }

    constructor(hexString: String) : this() {
        setHexString(hexString)
    }

    /**
     * Sets RGB values
     */
    fun set(r: Float, g: Float, b: Float): Color {
        this.r = r
        this.g = g
        this.b = b
        return this
    }

    /**
     * Creates a copy of this color
     */
    fun clone(): Color {
        return Color(r, g, b, a)
    }

    /**
     * Copies values from another color
     */
    fun copy(color: Color): Color {
        r = color.r
        g = color.g
        b = color.b
        a = color.a
        return this
    }

    /**
     * Sets color from hex integer (0x000000 to 0xffffff)
     */
    fun setHex(hex: Int): Color {
        r = ((hex shr 16) and 255) / 255f
        g = ((hex shr 8) and 255) / 255f
        b = (hex and 255) / 255f
        return this
    }

    /**
     * Gets color as hex integer
     */
    fun getHex(): Int {
        return (((r * 255)).toInt() shl 16) or (((g * 255)).toInt() shl 8) or ((b * 255)).toInt()
    }

    /**
     * Sets color from hex string ("#ffffff", "ffffff", "#fff", "fff")
     */
    fun setHexString(hex: String): Color {
        val cleanHex = hex.removePrefix("#")
        val hexValue = when (cleanHex.length) {
            3 -> {
                // Short form: "abc" -> "aabbcc"
                val r = cleanHex[0]
                val g = cleanHex[1]
                val b = cleanHex[2]
                "$r$r$g$g$b$b"
            }

            6 -> cleanHex
            else -> throw IllegalArgumentException("Invalid hex color: $hex")
        }
        return setHex(hexValue.toInt(16))
    }

    /**
     * Gets color as hex string
     */
    fun getHexString(): String {
        val hex = getHex()
        return hex.toString(16).padStart(6, '0')
    }

    /**
     * Sets color from HSL values (hue: 0-1, saturation: 0-1, lightness: 0-1)
     */
    fun setHSL(h: Float, s: Float, l: Float): Color {
        val hue = ((h % 1f) + 1f) % 1f // Ensure hue is in [0, 1)

        if (kotlin.math.abs(s) < 0.000001f) {
            // Achromatic (gray)
            r = l
            g = l
            b = l
        } else {
            val hue2rgb = { p: Float, q: Float, t: Float ->
                var tt = t
                if (tt < 0f) tt = tt + 1f
                if (tt > 1f) tt = tt - 1f
                when {
                    tt < 1f / 6f -> p + (q - p) * 6f * tt
                    tt < 1f / 2f -> q
                    tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
                    else -> p
                }
            }

            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q

            r = hue2rgb(p, q, hue + 1f / 3f)
            g = hue2rgb(p, q, hue)
            b = hue2rgb(p, q, hue - 1f / 3f)
        }

        return this
    }

    /**
     * Gets HSL values as Vector3 (h, s, l)
     */
    fun getHSL(): Vector3 {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val diff = max - min
        val sum = max + min

        var h = 0f
        var s = 0f
        val l = sum / 2f

        if (kotlin.math.abs(diff) >= 0.000001f) {
            s = if (l < 0.5f) diff / sum else diff / (2f - sum)

            h = when (max) {
                r -> (g - b) / diff + (if (g < b) 6f else 0f)
                g -> (b - r) / diff + 2f
                b -> (r - g) / diff + 4f
                else -> 0f
            }
            h /= 6f
        }

        return Vector3(h, s, l)
    }

    /**
     * Sets color by CSS color name
     */
    fun setName(name: String): Color {
        val colorHex = when (name.lowercase()) {
            "aliceblue" -> 0xf0f8ff
            "antiquewhite" -> 0xfaebd7
            "aqua" -> 0x00ffff
            "aquamarine" -> 0x7fffd4
            "azure" -> 0xf0ffff
            "beige" -> 0xf5f5dc
            "bisque" -> 0xffe4c4
            "black" -> 0x000000
            "blanchedalmond" -> 0xffebcd
            "blue" -> 0x0000ff
            "blueviolet" -> 0x8a2be2
            "brown" -> 0xa52a2a
            "burlywood" -> 0xdeb887
            "cadetblue" -> 0x5f9ea0
            "chartreuse" -> 0x7fff00
            "chocolate" -> 0xd2691e
            "coral" -> 0xff7f50
            "cornflowerblue" -> 0x6495ed
            "cornsilk" -> 0xfff8dc
            "crimson" -> 0xdc143c
            "cyan" -> 0x00ffff
            "darkblue" -> 0x00008b
            "darkcyan" -> 0x008b8b
            "darkgoldenrod" -> 0xb8860b
            "darkgray", "darkgrey" -> 0xa9a9a9
            "darkgreen" -> 0x006400
            "darkkhaki" -> 0xbdb76b
            "darkmagenta" -> 0x8b008b
            "darkolivegreen" -> 0x556b2f
            "darkorange" -> 0xff8c00
            "darkorchid" -> 0x9932cc
            "darkred" -> 0x8b0000
            "darksalmon" -> 0xe9967a
            "darkseagreen" -> 0x8fbc8f
            "darkslateblue" -> 0x483d8b
            "darkslategray", "darkslategrey" -> 0x2f4f4f
            "darkturquoise" -> 0x00ced1
            "darkviolet" -> 0x9400d3
            "deeppink" -> 0xff1493
            "deepskyblue" -> 0x00bfff
            "dimgray", "dimgrey" -> 0x696969
            "dodgerblue" -> 0x1e90ff
            "firebrick" -> 0xb22222
            "floralwhite" -> 0xfffaf0
            "forestgreen" -> 0x228b22
            "fuchsia" -> 0xff00ff
            "gainsboro" -> 0xdcdcdc
            "ghostwhite" -> 0xf8f8ff
            "gold" -> 0xffd700
            "goldenrod" -> 0xdaa520
            "gray", "grey" -> 0x808080
            "green" -> 0x008000
            "greenyellow" -> 0xadff2f
            "honeydew" -> 0xf0fff0
            "hotpink" -> 0xff69b4
            "indianred" -> 0xcd5c5c
            "indigo" -> 0x4b0082
            "ivory" -> 0xfffff0
            "khaki" -> 0xf0e68c
            "lavender" -> 0xe6e6fa
            "lavenderblush" -> 0xfff0f5
            "lawngreen" -> 0x7cfc00
            "lemonchiffon" -> 0xfffacd
            "lightblue" -> 0xadd8e6
            "lightcoral" -> 0xf08080
            "lightcyan" -> 0xe0ffff
            "lightgoldenrodyellow" -> 0xfafad2
            "lightgray", "lightgrey" -> 0xd3d3d3
            "lightgreen" -> 0x90ee90
            "lightpink" -> 0xffb6c1
            "lightsalmon" -> 0xffa07a
            "lightseagreen" -> 0x20b2aa
            "lightskyblue" -> 0x87cefa
            "lightslategray", "lightslategrey" -> 0x778899
            "lightsteelblue" -> 0xb0c4de
            "lightyellow" -> 0xffffe0
            "lime" -> 0x00ff00
            "limegreen" -> 0x32cd32
            "linen" -> 0xfaf0e6
            "magenta" -> 0xff00ff
            "maroon" -> 0x800000
            "mediumaquamarine" -> 0x66cdaa
            "mediumblue" -> 0x0000cd
            "mediumorchid" -> 0xba55d3
            "mediumpurple" -> 0x9370db
            "mediumseagreen" -> 0x3cb371
            "mediumslateblue" -> 0x7b68ee
            "mediumspringgreen" -> 0x00fa9a
            "mediumturquoise" -> 0x48d1cc
            "mediumvioletred" -> 0xc71585
            "midnightblue" -> 0x191970
            "mintcream" -> 0xf5fffa
            "mistyrose" -> 0xffe4e1
            "moccasin" -> 0xffe4b5
            "navajowhite" -> 0xffdead
            "navy" -> 0x000080
            "oldlace" -> 0xfdf5e6
            "olive" -> 0x808000
            "olivedrab" -> 0x6b8e23
            "orange" -> 0xffa500
            "orangered" -> 0xff4500
            "orchid" -> 0xda70d6
            "palegoldenrod" -> 0xeee8aa
            "palegreen" -> 0x98fb98
            "paleturquoise" -> 0xafeeee
            "palevioletred" -> 0xdb7093
            "papayawhip" -> 0xffefd5
            "peachpuff" -> 0xffdab9
            "peru" -> 0xcd853f
            "pink" -> 0xffc0cb
            "plum" -> 0xdda0dd
            "powderblue" -> 0xb0e0e6
            "purple" -> 0x800080
            "red" -> 0xff0000
            "rosybrown" -> 0xbc8f8f
            "royalblue" -> 0x4169e1
            "saddlebrown" -> 0x8b4513
            "salmon" -> 0xfa8072
            "sandybrown" -> 0xf4a460
            "seagreen" -> 0x2e8b57
            "seashell" -> 0xfff5ee
            "sienna" -> 0xa0522d
            "silver" -> 0xc0c0c0
            "skyblue" -> 0x87ceeb
            "slateblue" -> 0x6a5acd
            "slategray", "slategrey" -> 0x708090
            "snow" -> 0xfffafa
            "springgreen" -> 0x00ff7f
            "steelblue" -> 0x4682b4
            "tan" -> 0xd2b48c
            "teal" -> 0x008080
            "thistle" -> 0xd8bfd8
            "tomato" -> 0xff6347
            "turquoise" -> 0x40e0d0
            "violet" -> 0xee82ee
            "wheat" -> 0xf5deb3
            "white" -> 0xffffff
            "whitesmoke" -> 0xf5f5f5
            "yellow" -> 0xffff00
            "yellowgreen" -> 0x9acd32
            else -> throw IllegalArgumentException("Unknown color name: $name")
        }
        return setHex(colorHex)
    }

    /**
     * Adds another color to this color
     */
    fun add(color: Color): Color {
        r = r + color.r
        g = g + color.g
        b = b + color.b
        return this
    }

    /**
     * Adds a scalar to each component
     */
    fun addScalar(scalar: Float): Color {
        r = r + scalar
        g = g + scalar
        b = b + scalar
        return this
    }

    /**
     * Subtracts another color from this color
     */
    fun sub(color: Color): Color {
        r = r - color.r
        g = g - color.g
        b = b - color.b
        return this
    }

    /**
     * Multiplies this color by another color (component-wise)
     */
    fun multiply(color: Color): Color {
        r = r * color.r
        g = g * color.g
        b = b * color.b
        return this
    }

    /**
     * Multiplies this color by a scalar
     */
    fun multiplyScalar(scalar: Float): Color {
        r = r * scalar
        g = g * scalar
        b = b * scalar
        return this
    }

    /**
     * Linear interpolation to another color
     */
    fun lerp(color: Color, alpha: Float): Color {
        r += (color.r - r) * alpha
        g += (color.g - g) * alpha
        b += (color.b - b) * alpha
        return this
    }

    /**
     * Linear interpolation between two colors
     */
    fun lerpColors(color1: Color, color2: Color, alpha: Float): Color {
        r = color1.r + (color2.r - color1.r) * alpha
        g = color1.g + (color2.g - color1.g) * alpha
        b = color1.b + (color2.b - color1.b) * alpha
        return this
    }

    /**
     * Checks if this color equals another color within tolerance
     */
    fun equals(color: Color, tolerance: Float = 1e-6f): Boolean {
        return abs(r - color.r) < tolerance &&
                abs(g - color.g) < tolerance &&
                abs(b - color.b) < tolerance
    }

    /**
     * Converts to array [r, g, b]
     */
    fun toArray(): FloatArray {
        return floatArrayOf(r, g, b)
    }

    /**
     * Sets from array
     */
    fun fromArray(array: FloatArray, offset: Int = 0): Color {
        r = array[offset]
        g = array[offset + 1]
        b = array[offset + 2]
        return this
    }

    /**
     * Converts to CSS rgb string
     */
    fun toRgbString(): String {
        val rInt = ((r * 255)).toInt().coerceIn(0, 255)
        val gInt = ((g * 255)).toInt().coerceIn(0, 255)
        val bInt = ((b * 255)).toInt().coerceIn(0, 255)
        return "rgb($rInt, $gInt, $bInt)"
    }

    /**
     * Converts to CSS hex string
     */
    fun toCssHexString(): String {
        return "#${getHexString()}"
    }

    override fun toString(): String {
        return "Color(r=$r, g=$g, b=$b)"
    }

    // Operator overloading for arithmetic
    operator fun plus(other: Color): Color = Color(r + other.r, g + other.g, b + other.b)
    operator fun plus(scalar: Float): Color = Color(r + scalar, g + scalar, b + scalar)
    operator fun minus(other: Color): Color = Color(r - other.r, g - other.g, b - other.b)
    operator fun minus(scalar: Float): Color = Color(r - scalar, g - scalar, b - scalar)
    operator fun times(other: Color): Color = Color(r * other.r, g * other.g, b * other.b)
    operator fun times(scalar: Float): Color = Color((r * scalar), (g * scalar), (b * scalar))
    operator fun div(other: Color): Color = Color(r / other.r, g / other.g, b / other.b)
    operator fun div(scalar: Float): Color = Color(r / scalar, g / scalar, b / scalar)
    operator fun unaryMinus(): Color = Color(-r, -g, -b)

    // Mutable operators
    operator fun plusAssign(other: Color) {
        r = r + other.r
        g = g + other.g
        b = b + other.b
    }

    operator fun minusAssign(other: Color) {
        r = r - other.r
        g = g - other.g
        b = b - other.b
    }

    operator fun timesAssign(scalar: Float) {
        r = r * scalar
        g = g * scalar
        b = b * scalar
    }
}
