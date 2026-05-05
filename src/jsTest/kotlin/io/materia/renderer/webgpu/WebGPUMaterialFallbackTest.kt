package io.materia.renderer.webgpu

import io.materia.core.math.Color
import io.materia.material.BlendMode
import io.materia.material.MaterialSide
import io.materia.material.MeshStandardMaterial
import io.materia.material.Blending
import io.materia.material.Side
import io.materia.texture.Texture2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.HTMLCanvasElement
import kotlinx.browser.document

class WebGPUMaterialFallbackTest {
    @Test
    fun standardMaterialFallbackPreservesBaseColorTextureAndState() {
        val texture = Texture2D.fromImageData(
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
        val standard = MeshStandardMaterial().apply {
            name = "glb-material"
            color = Color(0.25f, 0.5f, 0.75f)
            map = texture
            transparent = true
            opacity = 0.4f
            vertexColors = false
            depthTest = false
            depthWrite = false
            colorWrite = false
            side = MaterialSide.DOUBLE
            blending = BlendMode.ADDITIVE
            wireframe = true
            wireframeLinewidth = 2f
        }
        val renderer = WebGPURenderer(document.createElement("canvas") as HTMLCanvasElement)

        val fallback = with(renderer) { standard.toWebGpuBasicFallback() }

        assertEquals("glb-material", fallback.name)
        assertNotSame(standard.color, fallback.color)
        assertEquals(standard.color.r, fallback.color.r)
        assertEquals(standard.color.g, fallback.color.g)
        assertEquals(standard.color.b, fallback.color.b)
        assertSame(texture, fallback.map)
        assertTrue(fallback.transparent)
        assertEquals(0.4f, fallback.opacity)
        assertFalse(fallback.vertexColors)
        assertFalse(fallback.depthTest)
        assertFalse(fallback.depthWrite)
        assertFalse(fallback.colorWrite)
        assertEquals(Side.DoubleSide, fallback.side)
        assertEquals(Blending.AdditiveBlending, fallback.blending)
        assertTrue(fallback.wireframe)
        assertEquals(2f, fallback.wireframeLinewidth)
    }
}
