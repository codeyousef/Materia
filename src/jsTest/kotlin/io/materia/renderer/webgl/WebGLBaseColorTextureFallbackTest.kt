package io.materia.renderer.webgl

import io.materia.camera.PerspectiveCamera
import io.materia.core.Result
import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.core.scene.Background
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.MeshStandardMaterial
import io.materia.renderer.BackendType
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.webgpu.WebGPUSurface
import io.materia.texture.Texture2D
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.RGBA
import org.khronos.webgl.WebGLRenderingContext.Companion.UNSIGNED_BYTE
import org.w3c.dom.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebGLBaseColorTextureFallbackTest {

    @Test
    fun meshStandardMaterialMapSamplesTextureWithoutImplicitColor0Tint() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 128
        canvas.height = 128

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

        val scene = Scene().apply {
            background = Background.Color(Color.BLACK)
            add(Mesh(createTexturedQuadGeometry(), createRedTextureMaterial()))
        }
        val camera = PerspectiveCamera(60f, 1f, 0.1f, 10f).apply {
            position.set(0f, 0f, 3f)
            lookAt(Vector3(0f, 0f, 0f))
            updateMatrixWorld(true)
            updateProjectionMatrix()
        }

        try {
            renderer.render(scene, camera)
            val pixel = readCenterPixel(canvas)

            assertTrue(pixel[0] > 180, "Expected sampled texture red channel, got ${pixel[0]}")
            assertTrue(pixel[1] < 80, "COLOR_0 should not tint when vertexColors is false, got ${pixel[1]}")
            assertTrue(pixel[2] < 80, "Expected low blue channel from red texture, got ${pixel[2]}")
            assertTrue(renderer.stats.textureMemory >= 16L, "Expected WebGL fallback to track uploaded texture memory")
        } finally {
            renderer.dispose()
        }
    }

    private fun createTexturedQuadGeometry(): BufferGeometry =
        BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        -1f, -1f, 0f,
                        1f, -1f, 0f,
                        1f, 1f, 0f,
                        -1f, -1f, 0f,
                        1f, 1f, 0f,
                        -1f, 1f, 0f
                    ),
                    3
                )
            )
            setAttribute(
                "uv",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f,
                        1f, 0f,
                        1f, 1f,
                        0f, 0f,
                        1f, 1f,
                        0f, 1f
                    ),
                    2
                )
            )
            setAttribute(
                "color",
                BufferAttribute(
                    FloatArray(6 * 3) { index ->
                        if (index % 3 == 1) 1f else 0f
                    },
                    3
                )
            )
        }

    private fun createRedTextureMaterial(): MeshStandardMaterial =
        MeshStandardMaterial().apply {
            color = Color.WHITE
            vertexColors = false
            map = Texture2D.fromImageData(
                width = 2,
                height = 2,
                data = ByteArray(2 * 2 * 4) { index ->
                    when (index % 4) {
                        0 -> 255.toByte()
                        3 -> 255.toByte()
                        else -> 0
                    }
                }
            )
        }

    private fun readCenterPixel(canvas: HTMLCanvasElement): IntArray {
        val raw = canvas.asDynamic().getContext("webgl2")
            ?: canvas.asDynamic().getContext("webgl")
            ?: canvas.asDynamic().getContext("experimental-webgl")
        val gl = raw.unsafeCast<WebGLRenderingContext>()
        val pixel = Uint8Array(4)
        gl.readPixels(canvas.width / 2, canvas.height / 2, 1, 1, RGBA, UNSIGNED_BYTE, pixel)
        return IntArray(4) { index -> (pixel.asDynamic()[index] as Number).toInt() }
    }
}
