package io.materia.loader

import io.materia.core.scene.Mesh
import io.materia.util.Base64Compat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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

    @Test
    fun `top-level relative model url is fetched as provided`() = runTest {
        val resolver = RecordingAssetResolver(
            responses = mapOf(
                "models/example.gltf" to minimalDocument("buffer.bin").encodeToByteArray(),
                "models/buffer.bin" to triangleBufferBytes()
            )
        )

        GLTFLoader(resolver = resolver).load("models/example.gltf")

        assertEquals(
            RecordingAssetResolver.Request("models/example.gltf", null),
            resolver.requestSnapshot().firstOrNull()
        )
    }

    @Test
    fun `top-level rooted model url is fetched as provided`() = runTest {
        val resolver = RecordingAssetResolver(
            responses = mapOf(
                "/models/example.gltf" to minimalDocument("buffer.bin").encodeToByteArray(),
                "/models/buffer.bin" to triangleBufferBytes()
            )
        )

        GLTFLoader(resolver = resolver).load("/models/example.gltf")

        assertEquals(
            RecordingAssetResolver.Request("/models/example.gltf", null),
            resolver.requestSnapshot().firstOrNull()
        )
    }

    @Test
    fun `same url loads share cached source and return independent scene instances`() = runTest {
        val cache = GLTFAssetCache()
        val resolver = RecordingAssetResolver(
            responses = mapOf(
                "models/shared.gltf" to minimalDocument("buffer.bin").encodeToByteArray(),
                "models/buffer.bin" to triangleBufferBytes()
            )
        )

        val first = GLTFLoader(
            resolver = resolver,
            cache = cache,
            cacheScope = "shared-fixture"
        ).load("models/shared.gltf")
        val second = GLTFLoader(
            resolver = resolver,
            cache = cache,
            cacheScope = "shared-fixture"
        ).load("models/shared.gltf")

        val requests = resolver.requestSnapshot()
        assertEquals(1, requests.count { it.uri == "models/shared.gltf" && it.basePath == null })
        assertEquals(1, requests.count { resolveAssetUri(it.uri, it.basePath) == "models/buffer.bin" })
        assertNotSame(first.scene, second.scene)

        val firstMesh = assertIs<Mesh>(first.scene.children.first())
        val secondMesh = assertIs<Mesh>(second.scene.children.first())
        assertNotSame(firstMesh, secondMesh)
        assertSame(firstMesh.geometry, secondMesh.geometry)
        assertSame(firstMesh.material, secondMesh.material)

        firstMesh.position.x = 42f
        assertEquals(0f, secondMesh.position.x)
    }

    @Test
    fun `concurrent same url loads share in-flight work`() = runTest {
        val cache = GLTFAssetCache()
        val resolver = RecordingAssetResolver(
            responses = mapOf(
                "models/concurrent.gltf" to minimalDocument("buffer.bin").encodeToByteArray(),
                "models/buffer.bin" to triangleBufferBytes()
            ),
            delayedResolvedUris = setOf("models/concurrent.gltf", "models/buffer.bin")
        )

        val loads = List(3) {
            async {
                GLTFLoader(
                    resolver = resolver,
                    cache = cache,
                    cacheScope = "concurrent-fixture"
                ).load("models/concurrent.gltf")
            }
        }.awaitAll()

        assertEquals(3, loads.map { it.scene }.toSet().size)
        val requests = resolver.requestSnapshot()
        assertEquals(1, requests.count { it.uri == "models/concurrent.gltf" && it.basePath == null })
        assertEquals(1, requests.count { resolveAssetUri(it.uri, it.basePath) == "models/buffer.bin" })
    }

    @Test
    fun `failed cache load is evicted so later load can retry`() = runTest {
        val cache = GLTFAssetCache()
        val resolver = RecordingAssetResolver(
            responses = mapOf(
                "models/retry.gltf" to minimalDocument("buffer.bin").encodeToByteArray(),
                "models/buffer.bin" to triangleBufferBytes()
            ),
            failResolvedUrisOnce = setOf("models/retry.gltf")
        )

        assertFailsWith<IllegalStateException> {
            GLTFLoader(
                resolver = resolver,
                cache = cache,
                cacheScope = "retry-fixture"
            ).load("models/retry.gltf")
        }

        val asset = GLTFLoader(
            resolver = resolver,
            cache = cache,
            cacheScope = "retry-fixture"
        ).load("models/retry.gltf")

        assertTrue(asset.scenes.isNotEmpty())
        assertEquals(
            2,
            resolver.requestSnapshot().count { it.uri == "models/retry.gltf" && it.basePath == null }
        )
    }

    @Test
    fun `load binary glb scene with embedded buffer`() = runTest {
        val bufferBytes = triangleBufferBytes()
        val glbBytes = minimalGlbDocument(bufferBytes)
        val resolver = RecordingAssetResolver(
            responses = mapOf("models/example.glb" to glbBytes)
        )

        val asset = GLTFLoader(resolver = resolver).load("models/example.glb")

        val mesh = assertIs<Mesh>(asset.scene.children.firstOrNull())
        val positionAttribute = mesh.geometry.getAttribute("position")
        assertNotNull(positionAttribute)
        assertEquals(3, positionAttribute.count)
        assertEquals(
            listOf(RecordingAssetResolver.Request("models/example.glb", null)),
            resolver.requestSnapshot()
        )
    }

    @Test
    fun `dependent relative uri resolves under base path`() {
        assertEquals("models/buffer.bin", resolveAssetUri("buffer.bin", "models"))
    }

    @Test
    fun `dependent rooted uri stays rooted`() {
        assertEquals("/textures/a.png", resolveAssetUri("/textures/a.png", "models"))
    }

    private class RecordingAssetResolver(
        private val responses: Map<String, ByteArray>,
        private val delayedResolvedUris: Set<String> = emptySet(),
        failResolvedUrisOnce: Set<String> = emptySet()
    ) : AssetResolver {
        data class Request(val uri: String, val basePath: String?)

        private val requestMutex = Mutex()
        private val requests = mutableListOf<Request>()
        private val oneShotFailures = failResolvedUrisOnce.toMutableSet()

        override suspend fun load(uri: String, basePath: String?): ByteArray {
            val resolvedUri = resolveAssetUri(uri, basePath)
            requestMutex.withLock {
                requests += Request(uri, basePath)
                if (oneShotFailures.remove(resolvedUri)) {
                    throw IllegalStateException("Intentional transient failure for $resolvedUri")
                }
            }
            if (resolvedUri in delayedResolvedUris) delay(25)
            return responses[resolvedUri] ?: error("Unexpected asset request for $resolvedUri")
        }

        suspend fun requestSnapshot(): List<Request> =
            requestMutex.withLock { requests.toList() }
    }

    private fun minimalDocument(bufferUri: String): String {
        val bufferBytes = triangleBufferBytes()
        return """
          {
            "asset": { "version": "2.0" },
            "buffers": [
            { "uri": "$bufferUri", "byteLength": ${bufferBytes.size} }
            ],
            "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": ${bufferBytes.size} }
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
    }

    private fun minimalGlbDocument(bufferBytes: ByteArray): ByteArray {
        val json = """
          {
            "asset": { "version": "2.0" },
            "buffers": [
            { "byteLength": ${bufferBytes.size} }
            ],
            "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": ${bufferBytes.size} }
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
        """.trimIndent().encodeToByteArray()

        val jsonChunk = padToFourBytes(json, 0x20)
        val binaryChunk = padToFourBytes(bufferBytes, 0)
        val totalLength = 12 + 8 + jsonChunk.size + 8 + binaryChunk.size
        val result = ByteArray(totalLength)
        var offset = 0
        offset = writeUInt32Le(result, offset, 0x46546C67)
        offset = writeUInt32Le(result, offset, 2)
        offset = writeUInt32Le(result, offset, totalLength)
        offset = writeUInt32Le(result, offset, jsonChunk.size)
        offset = writeUInt32Le(result, offset, 0x4E4F534A)
        jsonChunk.copyInto(result, offset)
        offset += jsonChunk.size
        offset = writeUInt32Le(result, offset, binaryChunk.size)
        offset = writeUInt32Le(result, offset, 0x004E4942)
        binaryChunk.copyInto(result, offset)
        return result
    }

    private fun padToFourBytes(source: ByteArray, padByte: Int): ByteArray {
        val padding = (4 - source.size % 4) % 4
        if (padding == 0) return source
        return source + ByteArray(padding) { padByte.toByte() }
    }

    private fun writeUInt32Le(target: ByteArray, offset: Int, value: Int): Int {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
        return offset + 4
    }

    private fun triangleBufferBytes(): ByteArray {
        return FloatArrayEncoder.encode(
            floatArrayOf(
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            )
        )
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
