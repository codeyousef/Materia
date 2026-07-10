package io.materia.renderer

import io.materia.camera.OrthographicCamera
import io.materia.core.math.Vector2
import io.materia.core.scene.Group
import io.materia.core.scene.Scene
import io.materia.renderer.webgl.WebGLRenderer
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayeredRendererTest {

    @Test
    fun overlayPickingTakesPriorityOverWorldPicking() {
        val world = sceneWithNamedNode("world")
        val overlay = sceneWithNamedNode("overlay")
        val camera = OrthographicCamera(-1f, 1f, 1f, -1f)

        val hit = OverlayFirstPicker().pick(
            normalizedPointer = Vector2(),
            scene = world,
            camera = camera,
            overlays = listOf(RenderOverlayLayer(overlay, camera)),
            hitTest = ::firstNodeName
        )

        assertEquals("overlay", hit?.value)
        assertEquals(0, hit?.overlayIndex)
    }

    @Test
    fun worldPickingRunsWhenNoOverlayNodeIsHit() {
        val world = sceneWithNamedNode("world")
        val camera = OrthographicCamera(-1f, 1f, 1f, -1f)

        val hit = OverlayFirstPicker().pick(
            Vector2(),
            world,
            camera,
            listOf(RenderOverlayLayer(Scene(), camera)),
            hitTest = ::firstNodeName
        )

        assertEquals("world", hit?.value)
        assertNull(hit?.overlayIndex)
    }

    @Test
    fun webglRendererExposesLayeredRenderCapability() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val renderer = WebGLRenderer(canvas)
        assertTrue(renderer is LayeredRenderer)
    }

    private fun sceneWithNamedNode(name: String): Scene = Scene().apply {
        add(Group().apply { this.name = name })
    }

    private fun firstNodeName(
        scene: Scene,
        camera: io.materia.camera.Camera,
        pointer: Vector2
    ): String? = scene.children.firstOrNull()?.name?.takeIf { it.isNotBlank() }
}
