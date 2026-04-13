package io.materia.texture

import io.materia.core.math.Color
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Data3DTextureContractTest {

    @Test
    fun testCreateFromRawData() {
        val rgbaData = ByteArray(16 * 16 * 8 * 4) { (it % 256).toByte() }
        val texture = Data3DTexture(
            data = rgbaData,
            width = 16,
            height = 16,
            depth = 8,
            format = TextureFormat.RGBA8
        )

        assertEquals(16, texture.width)
        assertEquals(16, texture.height)
        assertEquals(8, texture.depth)
        assertEquals(rgbaData.size, texture.getData().size)
    }

    @Test
    fun testCreateFromFloatArray() {
        val values = FloatArray(8 * 8 * 4 * 4) { index -> (index % 7) / 6f }
        val texture = Data3DTexture.fromFloatArray(
            data = values,
            width = 8,
            height = 8,
            depth = 4,
            format = TextureFormat.RGBA32F
        )

        assertEquals(8, texture.width)
        assertEquals(8, texture.height)
        assertEquals(4, texture.depth)
        assertNotNull(texture.getFloatData())
        assertEquals(values.size * 4, texture.getDataSize())
    }

    @Test
    fun testVoxelAccess() {
        val texture = Data3DTexture.solidColor(
            color = Color.BLACK,
            width = 4,
            height = 4,
            depth = 4
        )

        texture.setVoxel(1, 2, 3, Color.RED)
        val voxel = texture.getVoxel(1, 2, 3)

        assertTrue(voxel.r > 0.9f)
        assertTrue(voxel.g < 0.1f)
        assertTrue(voxel.b < 0.1f)
    }

    @Test
    fun testClearAndMapVoxels() {
        val texture = Data3DTexture(
            data = ByteArray(4 * 4 * 4 * 4),
            width = 4,
            height = 4,
            depth = 4
        )

        texture.clear(Color.BLUE)
        val cleared = texture.getVoxel(2, 2, 2)
        assertTrue(cleared.b > 0.9f)

        texture.mapVoxels { _, _, _, _ -> Color.GREEN }
        val mapped = texture.getVoxel(0, 0, 0)
        assertTrue(mapped.g > 0.9f)
    }

    @Test
    fun testClonePreservesDepthAndWrapR() {
        val original = Data3DTexture.createNoise(
            width = 8,
            height = 8,
            depth = 8,
            seed = 42
        ).apply {
            wrapR = TextureWrap.MIRRORED_REPEAT
        }

        val clone = original.clone()

        assertEquals(original.width, clone.width)
        assertEquals(original.height, clone.height)
        assertEquals(original.depth, clone.depth)
        assertEquals(TextureWrap.MIRRORED_REPEAT, clone.wrapR)
        assertNotNull(clone.getData())
    }
}