package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.math.Vector2
import io.materia.core.scene.Scene

/** A scene/camera pair rendered after the primary world scene in the same frame. */
data class RenderOverlayLayer(
    val scene: Scene,
    val camera: Camera,
    val clearDepth: Boolean = true
)

/** Browser renderer capability for orthographic HUD and other in-canvas layers. */
interface LayeredRenderer {
    fun renderLayers(
        scene: Scene,
        camera: Camera,
        overlays: List<RenderOverlayLayer> = emptyList()
    )
}

/** Render overlays when supported and preserve normal rendering as a safe fallback. */
fun Renderer.renderWithOverlays(
    scene: Scene,
    camera: Camera,
    overlays: List<RenderOverlayLayer>
) {
    val layered = this as? LayeredRenderer
    if (layered != null) {
        layered.renderLayers(scene, camera, overlays)
    } else {
        render(scene, camera)
    }
}

data class LayeredHit<T>(
    val value: T,
    val overlayIndex: Int? = null
)

/** Apply an engine-specific hit test to topmost screen layers before the world scene. */
class OverlayFirstPicker {
    fun <T> pick(
        normalizedPointer: Vector2,
        scene: Scene,
        camera: Camera,
        overlays: List<RenderOverlayLayer>,
        hitTest: (scene: Scene, camera: Camera, pointer: Vector2) -> T?
    ): LayeredHit<T>? {
        for (index in overlays.indices.reversed()) {
            val overlay = overlays[index]
            val hit = hitTest(overlay.scene, overlay.camera, normalizedPointer)
            if (hit != null) return LayeredHit(hit, overlayIndex = index)
        }

        return hitTest(scene, camera, normalizedPointer)?.let(::LayeredHit)
    }
}
