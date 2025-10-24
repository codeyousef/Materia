package io.kreekt.engine.math

import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

@JvmInline
value class Color(val rgba: UInt) {
    val r: Float
        get() = ((rgba shr 24) and 0xFFu).toFloat() / 255f

    val g: Float
        get() = ((rgba shr 16) and 0xFFu).toFloat() / 255f

    val b: Float
        get() = ((rgba shr 8) and 0xFFu).toFloat() / 255f

    val a: Float
        get() = (rgba and 0xFFu).toFloat() / 255f

    fun withAlpha(alpha: Float): Color = fromFloats(r, g, b, alpha)

    fun toVec3(): Vec3 = vec3(r, g, b)

    fun toVec4(): Vec4 = vec4(r, g, b, a)

    companion object {
        fun fromBytes(r: Int, g: Int, b: Int, a: Int = 255): Color {
            val rb = r.coerceIn(0, 255)
            val gb = g.coerceIn(0, 255)
            val bb = b.coerceIn(0, 255)
            val ab = a.coerceIn(0, 255)
            val packed = (rb shl 24) or (gb shl 16) or (bb shl 8) or ab
            return Color(packed.toUInt())
        }

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

        val White: Color = fromFloats(1f, 1f, 1f)
        val Black: Color = fromFloats(0f, 0f, 0f)
    }
}

fun colorOf(r: Float, g: Float, b: Float, a: Float = 1f): Color = Color.fromFloats(r, g, b, a)
