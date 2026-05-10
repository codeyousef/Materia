package io.materia.renderer.webgl

import io.materia.camera.PerspectiveCamera
import io.materia.core.Result
import io.materia.core.scene.Scene
import io.materia.renderer.BackendType
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebGLReadPixelsGuardTest {

    @Test
    fun steadyWebGLRenderDoesNotReadPixels() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 64
        canvas.height = 64

        val counter = installReadPixelsCounter()
        val rendererResult = RendererFactory.create(
            surface = WebGPUSurface(canvas),
            config = RendererConfig(
                preferredBackend = BackendType.WEBGL,
                enableValidation = false,
                msaaSamples = 1
            )
        )

        assertTrue(rendererResult is Result.Success)
        val renderer = rendererResult.getOrThrow()
        assertEquals(BackendType.WEBGL, renderer.backend)

        val scene = Scene()
        val camera = PerspectiveCamera(60f, 1f, 0.1f, 10f).apply {
            position.set(0f, 0f, 3f)
            updateMatrixWorld(true)
            updateProjectionMatrix()
        }

        try {
            repeat(8) {
                renderer.render(scene, camera)
            }
            assertEquals(0, readPixelsCount(counter))
        } finally {
            renderer.dispose()
            restoreReadPixels(counter)
        }
    }

    private fun installReadPixelsCounter(): dynamic {
        val counter = js("({ count: 0, originals: [] })")
        js(
            """
            (function(counter) {
              var patch = function(ctor) {
                if (!ctor || !ctor.prototype || !ctor.prototype.readPixels) return;
                var proto = ctor.prototype;
                var original = proto.readPixels;
                counter.originals.push([proto, original]);
                proto.readPixels = function() {
                  counter.count += 1;
                  return original.apply(this, arguments);
                };
              };
              patch(typeof WebGLRenderingContext !== "undefined" ? WebGLRenderingContext : null);
              patch(typeof WebGL2RenderingContext !== "undefined" ? WebGL2RenderingContext : null);
            })(counter);
            """
        )
        return counter
    }

    private fun readPixelsCount(counter: dynamic): Int =
        (counter.count as Number).toInt()

    private fun restoreReadPixels(counter: dynamic) {
        js(
            """
            (function(counter) {
              for (var index = 0; index < counter.originals.length; index += 1) {
                var pair = counter.originals[index];
                pair[0].readPixels = pair[1];
              }
              counter.originals = [];
            })(counter);
            """
        )
    }
}
