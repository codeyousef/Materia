package io.materia.engine.math

import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/**
 * Immutable RGBA color packed into a single 32-bit unsigned integer.
 *
 * Components are stored as 8-bit values in RGBA order (high to low bits).
 * Use [fromFloats] or [fromBytes] to create instances and access normalized
 * [0, 1] float values via [r], [g], [b], [a] properties.
 *
 * @property rgba The packed color value with R in the highest byte.
 */
@JvmInline
value class Color(val rgba: UInt) {
    /** Red component normalized to [0, 1]. */
    val r: Float
        get() = ((rgba shr 24) and 0xFFu).toFloat() / 255f

    /** Green component normalized to [0, 1]. */
    val g: Float
        get() = ((rgba shr 16) and 0xFFu).toFloat() / 255f

    /** Blue component normalized to [0, 1]. */
    val b: Float
        get() = ((rgba shr 8) and 0xFFu).toFloat() / 255f

    /** Alpha component normalized to [0, 1]. */
    val a: Float
        get() = (rgba and 0xFFu).toFloat() / 255f

    /**
     * Creates a new color with the specified alpha, preserving RGB.
     *
     * @param alpha The new alpha value in [0, 1].
     * @return A new color with the modified alpha.
     */
    fun withAlpha(alpha: Float): Color = fromFloats(r, g, b, alpha)

    /** Converts this color to a 3-component RGB vector. */
    fun toVec3(): Vec3 = vec3(r, g, b)

    /** Converts this color to a 4-component RGBA vector. */
    fun toVec4(): Vec4 = vec4(r, g, b, a)

    companion object {
        /**
         * Creates a color from byte component values.
         *
         * @param r Red component [0, 255].
         * @param g Green component [0, 255].
         * @param b Blue component [0, 255].
         * @param a Alpha component [0, 255], defaults to fully opaque.
         * @return A new packed color.
         */
        fun fromBytes(r: Int, g: Int, b: Int, a: Int = 255): Color {
            val rb = r.coerceIn(0, 255)
            val gb = g.coerceIn(0, 255)
            val bb = b.coerceIn(0, 255)
            val ab = a.coerceIn(0, 255)
            val packed = (rb shl 24) or (gb shl 16) or (bb shl 8) or ab
            return Color(packed.toUInt())
        }

        /**
         * Creates a color from normalized float component values.
         *
         * @param r Red component [0, 1].
         * @param g Green component [0, 1].
         * @param b Blue component [0, 1].
         * @param a Alpha component [0, 1], defaults to fully opaque.
         * @return A new packed color.
         */
        fun fromFloats(r: Float, g: Float, b: Float, a: Float = 1f): Color {
            fun component(value: Float): Int =
                (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)

            return fromBytes(
                component(r),
                component(g),
                component(b),
                component(a)
            )
        }

        /** Opaque white (1, 1, 1, 1). */
        val White: Color = fromFloats(1f, 1f, 1f)

        /** Opaque black (0, 0, 0, 1). */
        val Black: Color = fromFloats(0f, 0f, 0f)
    }
}

/**
 * Convenience function to create a [Color] from normalized float values.
 *
 * @param r Red component [0, 1].
 * @param g Green component [0, 1].
 * @param b Blue component [0, 1].
 * @param a Alpha component [0, 1], defaults to fully opaque.
 * @return A new packed color.
 */
fun colorOf(r: Float, g: Float, b: Float, a: Float = 1f): Color = Color.fromFloats(r, g, b, a)
