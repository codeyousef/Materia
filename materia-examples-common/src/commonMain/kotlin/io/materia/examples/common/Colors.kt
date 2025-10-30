package io.materia.examples.common

import io.materia.core.math.Color

object Colors {
    val deepNavy = Color(0x0b1020)
    val maroon = Color(0x8c2641)
    val teal = Color(0x1fb8b5)
    val azure = Color(0x3c6dd5)
    val amber = Color(0xf0b35a)

    fun brandCycle(): List<Color> = listOf(maroon, teal, azure, amber)
}
