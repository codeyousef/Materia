package io.materia.loader

import io.materia.util.Base64Compat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GLTFLoaderTest {
    @Test
    fun testGLTFLoaderCreation() = runTest {
        val loader = GLTFLoader()
        assertNotNull(loader)
    }

    @Test
    fun `load embedded glTF scene`() = runTest {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val positionBytes = FloatArrayEncoder.encode(positions)
        val bufferBase64 = Base64Compat.encode(positionBytes)

        val gltfJson = """
            {
              "asset": { "version": "2.0" },
              "buffers": [
                { "uri": "data:application/octet-stream;base64,$bufferBase64", "byteLength": ${positionBytes.size} }
              ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": ${positionBytes.size} }
              ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" }
              ],
              "meshes": [
                { "primitives": [ { "attributes": { "POSITION": 0 } } ] }
              ],
              "nodes": [
                { "mesh": 0 }
              ],
              "scenes": [
                { "nodes": [ 0 ] }
              ],
              "scene": 0
            }
        """.trimIndent()

        val documentUri = "data:application/json;base64," + Base64Compat.encode(gltfJson.encodeToByteArray())

        val loader = GLTFLoader()
        val asset = loader.load(documentUri)

        assertTrue(asset.scenes.isNotEmpty(), "Expected at least one scene")
        assertNotNull(asset.scene, "Primary scene should not be null")
        val rootChild = asset.scene.children.firstOrNull()
        val mesh = assertIs<io.materia.core.scene.Mesh>(rootChild)
        val positionAttribute = mesh.geometry.getAttribute("position")
        assertNotNull(positionAttribute, "Position attribute must be present")
        assertEquals(3, positionAttribute.count)
    }

    private object FloatArrayEncoder {
        fun encode(values: FloatArray): ByteArray {
            val bytes = ByteArray(values.size * 4)
            var offset = 0
            for (value in values) {
                val bits = value.toRawBits()
                bytes[offset] = (bits and 0xFF).toByte()
                bytes[offset + 1] = ((bits shr 8) and 0xFF).toByte()
                bytes[offset + 2] = ((bits shr 16) and 0xFF).toByte()
                bytes[offset + 3] = ((bits shr 24) and 0xFF).toByte()
                offset += 4
            }
            return bytes
        }
    }
}
