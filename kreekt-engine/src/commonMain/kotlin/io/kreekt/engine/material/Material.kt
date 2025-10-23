package io.kreekt.engine.material

import io.kreekt.engine.math.Color

sealed class Material(
    val label: String,
    val renderState: RenderState = RenderState()
)

class UnlitColorMaterial(
    label: String,
    val color: Color = Color.White,
    renderState: RenderState = RenderState()
) : Material(label, renderState)

class UnlitPointsMaterial(
    label: String,
    val baseColor: Color = Color.White,
    val size: Float = 1f,
    renderState: RenderState = RenderState(blendMode = BlendMode.Additive, depthWrite = false)
) : Material(label, renderState)
