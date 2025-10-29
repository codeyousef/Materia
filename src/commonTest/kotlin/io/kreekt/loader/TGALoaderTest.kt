package io.kreekt.loader

import io.kreekt.texture.Texture2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class TGALoaderTest {
    @Test
    fun `load uncompressed 24-bit TGA`() = runTest {
        val tgaData = "AAACAAAAAAAAAAAAAgACABgAAAD/AP8A/wAA////"
        val uri = "data:image/x-tga;base64,$tgaData"
        val texture = TGALoader().load(uri)
        assertIs<Texture2D>(texture)
        assertEquals(2, texture.width)
        assertEquals(2, texture.height)
    }
}
