package io.materia.texture

import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeTextureSamplerTest {

    @Test
    fun sampleLocalPositionReadsNearestByteVoxel() {
        val texture = Data3DTexture(
            data = byteArrayOf(
                -1, 0, 0, 127,
                0, 0, -1, -1
            ),
            width = 2,
            height = 1,
            depth = 1
        )

        val sampler = VolumeTextureSampler.from(texture)
        val left = sampler.sampleLocalPosition(-1f, 0f, 0f)
        val right = sampler.sampleLocalPosition(1f, 0f, 0f)

        assertEquals(1f, left.r, 0.001f)
        assertEquals(0f, left.g, 0.001f)
        assertEquals(0f, left.b, 0.001f)
        assertEquals(127f / 255f, left.a, 0.001f)

        assertEquals(0f, right.r, 0.001f)
        assertEquals(0f, right.g, 0.001f)
        assertEquals(1f, right.b, 0.001f)
        assertEquals(1f, right.a, 0.001f)
    }

    @Test
    fun sampleNormalizedReadsFloatVoxelData() {
        val texture = Data3DTexture.fromFloatArray(
            data = floatArrayOf(
                0.25f, 0.5f, 0.75f, 1f,
                1f, 0f, 0.5f, 0.25f
            ),
            width = 1,
            height = 2,
            depth = 1
        )

        val sampler = VolumeTextureSampler.from(texture)
        val bottom = sampler.sampleNormalized(0f, 0f, 0f)
        val top = sampler.sampleNormalized(0f, 1f, 0f)

        assertEquals(0.25f, bottom.r, 0.001f)
        assertEquals(0.5f, bottom.g, 0.001f)
        assertEquals(0.75f, bottom.b, 0.001f)
        assertEquals(1f, bottom.a, 0.001f)

        assertEquals(1f, top.r, 0.001f)
        assertEquals(0f, top.g, 0.001f)
        assertEquals(0.5f, top.b, 0.001f)
        assertEquals(0.25f, top.a, 0.001f)
    }
}