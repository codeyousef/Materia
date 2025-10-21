package io.kreekt.engine.material

import io.kreekt.engine.math.Vector3f

sealed class Material(val label: String)

class UnlitColorMaterial(
    label: String,
    val color: Vector3f = Vector3f(1f, 1f, 1f)
) : Material(label)

class UnlitPointsMaterial(
    label: String,
    val baseColor: Vector3f = Vector3f(1f, 1f, 1f),
    val size: Float = 1f
) : Material(label)
