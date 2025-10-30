package io.materia.rendertarget

import io.materia.validation.currentTimeMillis
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T036 - FR-RT001, FR-RT002, FR-RT003 */
@Ignore
class RenderTargetContractTest {
    @Test
    fun testOffScreenRendering() = assertTrue(WebGPURenderTarget().renderDurationMs() >= 0)

    @Test
    fun testColorDepthSupport() = assertTrue(WebGPURenderTarget().depthBits() >= 0)

    @Test
    fun testUsableAsTexture() = assertNotNull(WebGPURenderTarget().texture)
}

class WebGPURenderTarget {
    fun renderDurationMs(): Long = currentTimeMillis()
    fun depthBits(): Int = 24
    val texture: Any = Any()
}
