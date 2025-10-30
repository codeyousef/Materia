package io.materia.fog

import io.materia.core.math.Color

/**
 * Base interface for all fog types
 */
sealed interface FogBase {
    val color: Color
    val name: String
}
