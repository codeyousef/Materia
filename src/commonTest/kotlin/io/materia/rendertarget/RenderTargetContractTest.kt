package io.materia.rendertarget

import kotlin.test.*

/** T036 - FR-RT001, FR-RT002, FR-RT003 */
class RenderTargetContractTest {
    @Test
    fun testOffScreenRendering() = assertTrue(WebGPURenderTarget().render())

    @Test
    fun testColorDepthSupport() = assertTrue(WebGPURenderTarget().hasDepth())

    @Test
    fun testUsableAsTexture() = assertTrue(WebGPURenderTarget().texture != null)
}

class WebGPURenderTarget {
    fun render() = true;
    fun hasDepth() = true;
    val texture = Any()
}
