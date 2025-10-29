package io.kreekt.lighting

import io.kreekt.core.math.Color
import io.kreekt.renderer.CubeFace
import io.kreekt.texture.Texture2D
import io.kreekt.texture.CubeTexture as RuntimeCubeTexture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class IBLProcessorSimpleTest {

    @Test
    fun `generate equirectangular from solid cube`() = runTest {
        val cube = RuntimeCubeTexture.solidColor(Color(1f, 0f, 0f), 4)
        val processor = IBLProcessorSimple()

        val result = processor.generateEquirectangularMap(cube, width = 8, height = 4)
        assertTrue(result is Texture2D)
        val data = result.getFloatData()
        requireNotNull(data)
        assertEquals(1f, data[0], 1e-3f)
        assertEquals(0f, data[1], 1e-3f)
        assertEquals(0f, data[2], 1e-3f)
    }

    @Test
    fun `generate irradiance map retains energy`() = runTest {
        val cube = RuntimeCubeTexture.solidColor(Color(0.5f, 0.25f, 0.75f), 4)
        val processor = IBLProcessorSimple()
        val irradiance = processor.generateIrradianceMap(cube, size = 2)
        val impl = irradiance as? io.kreekt.renderer.CubeTextureImpl
        requireNotNull(impl)
        val faceData = impl.getFaceFloatData(CubeFace.POSITIVE_Z)
        requireNotNull(faceData)
        assertEquals(0.5f, faceData[0], 1e-2f)
        assertEquals(0.25f, faceData[1], 1e-2f)
        assertEquals(0.75f, faceData[2], 1e-2f)
    }

    @Test
    fun `generate BRDF LUT has expected dimensions`() = runTest {
        val processor = IBLProcessorSimple()
        val lut = processor.generateBRDFLUT(16)
        assertTrue(lut is Texture2D)
        assertEquals(16, lut.width)
        assertEquals(16, lut.height)
    }
}
