package io.materia.renderer.webgl

import io.materia.camera.PerspectiveCamera
import io.materia.core.Result
import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.core.scene.Background
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.primitives.BoxGeometry
import io.materia.material.MeshBasicMaterial
import io.materia.renderer.BackendType
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.webgpu.WebGPUSurface
import io.materia.texture.Data3DTexture
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebGLVolumeTextureFallbackSmokeTest {

    @Test
    fun rendererFactoryRendersData3dTextureWithWebglFallback() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 640
        canvas.height = 480

        val rendererResult = RendererFactory.create(
            surface = WebGPUSurface(canvas),
            config = RendererConfig(
                preferredBackend = BackendType.WEBGL,
                enableValidation = false,
                msaaSamples = 1
            )
        )

        assertTrue(
            rendererResult is Result.Success,
            "RendererFactory should create a WebGL fallback renderer when requested explicitly"
        )

        val renderer = rendererResult.getOrThrow()
        assertEquals(BackendType.WEBGL, renderer.backend)

        val scene = Scene().apply {
            background = Background.Color(Color(0x05070C))
        }
        val camera = PerspectiveCamera(60f, canvas.width.toFloat() / canvas.height.toFloat(), 0.1f, 100f).apply {
            position.set(0f, 0f, 5f)
            lookAt(Vector3(0f, 0f, 0f))
            updateMatrixWorld(true)
            updateProjectionMatrix()
        }

        val material = MeshBasicMaterial().apply {
            color = Color(0xFFFFFF)
            map = createTestVolumeTexture()
        }
        scene.add(Mesh(BoxGeometry(2f, 2f, 2f), material))

        var renderSuccessful = false
        var renderedTriangles = 0
        try {
            renderer.render(scene, camera)
            renderSuccessful = true
            renderedTriangles = renderer.stats.triangles
        } finally {
            renderer.dispose()
        }

        assertTrue(renderSuccessful, "WebGL fallback should render a Data3DTexture-mapped mesh without throwing")
        assertTrue(
            renderedTriangles >= 12,
            "Expected WebGL fallback to draw the box geometry, got $renderedTriangles triangles"
        )
    }

    private fun createTestVolumeTexture(): Data3DTexture {
        val size = 4
        val data = ByteArray(size * size * size * 4)
        var offset = 0
        val maxIndex = (size - 1).toFloat()

        for (z in 0 until size) {
            val nz = z.toFloat() / maxIndex
            for (y in 0 until size) {
                val ny = y.toFloat() / maxIndex
                for (x in 0 until size) {
                    val nx = x.toFloat() / maxIndex
                    data[offset++] = (nx * 255f).toInt().toByte()
                    data[offset++] = (ny * 255f).toInt().toByte()
                    data[offset++] = (nz * 255f).toInt().toByte()
                    data[offset++] = 255.toByte()
                }
            }
        }

        return Data3DTexture(
            data = data,
            width = size,
            height = size,
            depth = size,
            textureName = "WebGLFallbackSmokeVolume"
        )
    }
}