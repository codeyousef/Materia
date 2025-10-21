package io.kreekt.engine.material

import io.kreekt.engine.math.Vector3f

sealed class Material(
    val label: String,
    val renderState: RenderState = RenderState()
)

class UnlitColorMaterial(
    label: String,
    val color: Vector3f = Vector3f(1f, 1f, 1f),
    renderState: RenderState = RenderState()
) : Material(label, renderState)

class UnlitPointsMaterial(
    label: String,
    val baseColor: Vector3f = Vector3f(1f, 1f, 1f),
    val size: Float = 1f,
    renderState: RenderState = RenderState(blendMode = BlendMode.Additive, depthWrite = false)
) : Material(label, renderState)
