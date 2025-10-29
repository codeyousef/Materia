package io.kreekt.texture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoTextureContractTest {

    @Test
    fun `playback updates time`() {
        val texture = VideoTexture.fromUrl("clip_10s.mp4", width = 640, height = 360)
        assertTrue(texture.canPlay())
        assertEquals(640, texture.width)
        assertEquals(360, texture.height)

        texture.play()
        texture.update(1f)
        assertTrue(texture.isPlaying)
        assertTrue(texture.currentTime > 0f)

        texture.pause()
        val pausedTime = texture.currentTime
        texture.update(1f)
        assertEquals(pausedTime, texture.currentTime)
    }

    @Test
    fun `looping restarts when reaching end`() {
        val texture = VideoTexture(width = 320, height = 180, textureName = "loop")
        texture.loadFromUrl("loop_5s.mp4")
        texture.loop = true
        texture.play()
        texture.seekTo(texture.duration - 0.1f)
        texture.update(0.2f)
        assertTrue(texture.currentTime < 0.5f)
    }

    @Test
    fun `progress helpers`() {
        val texture = VideoTexture.fromUrl("clip_8s.mp4", width = 256, height = 256)
        texture.play()
        texture.update(2f)
        val progress = texture.getProgress()
        assertTrue(progress > 0f)
        texture.setProgress(0.5f)
        assertEquals(0.5f, texture.getProgress(), 1e-3f)
    }

    @Test
    fun `dispose clears binding`() {
        val texture = VideoTexture.fromData(ByteArray(1024), 64, 64)
        texture.disposeVideo()
        assertFalse(texture.isPlaying)
        assertEquals(null, texture.video)
    }
}
