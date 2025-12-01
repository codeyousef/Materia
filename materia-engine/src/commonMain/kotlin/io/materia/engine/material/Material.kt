package io.materia.engine.material

import io.materia.engine.math.Color

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
    renderState: RenderState = RenderState(blendMode = BlendMode.Opaque, depthWrite = true)
) : Material(label, renderState)

class UnlitLineMaterial(
    label: String,
    val color: Color = Color.White,
    renderState: RenderState = RenderState(blendMode = BlendMode.Alpha, depthWrite = false)
) : Material(label, renderState)
