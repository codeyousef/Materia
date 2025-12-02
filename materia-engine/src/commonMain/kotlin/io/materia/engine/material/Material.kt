package io.materia.engine.material

import io.materia.engine.math.Color

/**
 * Base class for all materials defining how geometry is rendered.
 *
 * Materials encapsulate shader configuration and render state. The engine
 * uses the material type to select the appropriate pipeline and bind uniforms.
 *
 * @property label Debug identifier for this material.
 * @property renderState Depth, blend, and culling settings.
 */
sealed class Material(
    val label: String,
    val renderState: RenderState = RenderState()
)

/**
 * Simple unlit material rendering geometry with a solid color.
 *
 * Ignores lighting calculations, making it suitable for debug visualization,
 * UI elements, and stylized rendering.
 *
 * @param label Debug identifier.
 * @param color The solid color to apply.
 * @param renderState Optional render state overrides.
 */
class UnlitColorMaterial(
    label: String,
    val color: Color = Color.White,
    renderState: RenderState = RenderState()
) : Material(label, renderState)

/**
 * Material for rendering point primitives with per-instance color and size.
 *
 * Used with [InstancedPoints] for efficient point cloud and particle rendering.
 *
 * @param label Debug identifier.
 * @param baseColor Default color when per-instance color is not provided.
 * @param size Default point size in world units.
 * @param renderState Optional render state overrides.
 */
class UnlitPointsMaterial(
    label: String,
    val baseColor: Color = Color.White,
    val size: Float = 1f,
    renderState: RenderState = RenderState(blendMode = BlendMode.Opaque, depthWrite = true)
) : Material(label, renderState)

/**
 * Material for rendering line primitives.
 *
 * @param label Debug identifier.
 * @param color Line color.
 * @param renderState Optional render state overrides.
 */
class UnlitLineMaterial(
    label: String,
    val color: Color = Color.White,
    renderState: RenderState = RenderState(blendMode = BlendMode.Alpha, depthWrite = false)
) : Material(label, renderState)
