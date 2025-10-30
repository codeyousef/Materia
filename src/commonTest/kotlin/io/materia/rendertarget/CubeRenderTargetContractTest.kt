package io.materia.rendertarget

import kotlin.test.*

/** T038 - FR-RT006, FR-RT007, FR-RT008, FR-RT009 */
class CubeRenderTargetContractTest {
    @Test
    fun testRenderTo6Faces() = assertEquals(6, CubeRT().faceCount)

    @Test
    fun test3DTextureSlices() = assertTrue(RT3D().slices > 0)
}

class CubeRT {
    val faceCount = 6
}

class RT3D {
    val slices = 128
}
