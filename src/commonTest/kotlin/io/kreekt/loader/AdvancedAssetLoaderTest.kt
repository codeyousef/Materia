package io.kreekt.loader

import io.kreekt.core.Result
import io.kreekt.core.scene.Mesh
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

class AdvancedAssetLoaderTest {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `load glTF via advanced loader`() = runTest {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val positionBytes = FloatArrayEncoder.encode(positions)
        val bufferBase64 = Base64.encode(positionBytes)

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

        val documentUri = "data:application/json;base64," + Base64.encode(gltfJson.encodeToByteArray())

        val loader = AdvancedAssetLoader()
        val result = loader.loadModel(documentUri)
        val asset = when (result) {
            is Result.Success -> result.value
            is Result.Error -> fail(
                "Expected success but got error: ${result.message}; cause=${result.exception?.message}"
            )
        }
        assertTrue(asset.scene.children.isNotEmpty())
        val mesh = assertIs<Mesh>(asset.scene.children.first())
        val positionAttribute = mesh.geometry.getAttribute("position")
        assertEquals(3, positionAttribute?.count)
    }
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
