package io.materia.postprocessing

import io.materia.camera.Camera
import io.materia.material.Material
import io.materia.core.math.Color
import io.materia.renderer.Renderer
import io.materia.renderer.RenderTarget
import io.materia.core.scene.Scene

/**
 * RenderPass renders a scene with a camera to a render target.
 * This is typically the first pass in a post-processing pipeline.
 *
 * @property scene The scene to render
 * @property camera The camera to render from
 * @property overrideMaterial Optional material to override all scene materials
 * @property clearColor The color to clear the render target with
 * @property clearAlpha The alpha value for the clear color
 * @property clearDepth Whether to clear the depth buffer
 */
class RenderPass(
    var scene: Scene,
    var camera: Camera,
    var overrideMaterial: Material? = null,
    var clearColor: Color? = null,
    var clearAlpha: Float = 0.0f,
    var clearDepth: Boolean = false
) : Pass() {

    init {
        needsSwap = false
        clear = false
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Store current renderer state
        val oldAutoClear = renderer.autoClear
        val oldAutoClearColor = renderer.autoClearColor
        val oldAutoClearDepth = renderer.autoClearDepth
        val oldAutoClearStencil = renderer.autoClearStencil
        val oldOverrideMaterial = scene.overrideMaterial
        val oldClearColor = renderer.getClearColor().copy()
        val oldClearAlpha = renderer.getClearAlpha()
        val oldRenderTarget = renderer.getRenderTarget()

        // Apply override material if specified
        if (overrideMaterial != null) {
            scene.overrideMaterial = overrideMaterial
        }

        // Set clear color if specified
        clearColor?.let { color ->
            renderer.setClearColor(color, clearAlpha)
        }

        // Configure auto-clear settings
        renderer.autoClear = false

        // Set render target
        renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)

        // Clear if requested
        if (clear) {
            renderer.clear(
                renderer.autoClearColor,
                renderer.autoClearDepth,
                renderer.autoClearStencil
            )
        }

        // Clear depth if requested
        if (clearDepth) {
            renderer.clearDepth()
        }

        // Render the scene
        renderer.render(scene, camera)

        // Restore renderer state
        renderer.autoClear = oldAutoClear
        renderer.autoClearColor = oldAutoClearColor
        renderer.autoClearDepth = oldAutoClearDepth
        renderer.autoClearStencil = oldAutoClearStencil
        renderer.setClearColor(oldClearColor, oldClearAlpha)
        renderer.setRenderTarget(oldRenderTarget)

        // Restore scene state
        scene.overrideMaterial = oldOverrideMaterial
    }
}