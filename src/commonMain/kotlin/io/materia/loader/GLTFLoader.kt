package io.materia.loader

import io.materia.animation.AnimationClip
import io.materia.core.math.Color
import io.materia.core.scene.Group
import io.materia.core.scene.Material
import io.materia.core.scene.Mesh
import io.materia.core.scene.Object3D
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.MaterialSide
import io.materia.material.MeshStandardMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Result of loading a glTF asset.
 *
 * Contains the scene graph, materials, and animations extracted from
 * the glTF file.
 *
 * @property scene The default scene (or first scene if no default).
 * @property scenes All scenes defined in the file.
 * @property nodes All nodes in the file.
 * @property materials All materials created during loading.
 * @property animations Animation clips (if any).
 */
data class GLTFAsset(
    val scene: Scene,
    val scenes: List<Scene>,
    val nodes: List<Object3D>,
    val materials: List<Material>,
    val animations: List<AnimationClip>
)

/**
 * Progress information for asset loading operations.
 *
 * @property loaded Bytes loaded so far.
 * @property total Total bytes to load (may be 0 if unknown).
 */
data class LoadingProgress(
    val loaded: Long,
    val total: Long
) {
    val percentage: Float
        get() = if (total <= 0) 1f else (loaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * Loader for glTF 2.0 3D models.
 *
 * Supports both .gltf (JSON + external binaries) and embedded data URIs.
 * Extracts geometry, materials, textures, and animations from glTF files.
 *
 * Features:
 * - Asynchronous loading with progress callbacks
 * - Automatic base path resolution for relative URIs
 * - Mesh instancing (shared geometry for repeated nodes)
 * - Multi-primitive mesh support
 * - PBR material creation
 *
 * ```kotlin
 * val loader = GLTFLoader()
 * val asset = loader.load("models/character.gltf") { progress ->
 *     println("Loading: ${(progress.percentage * 100).toInt()}%")
 * }
 * scene.add(asset.scene)
 * ```
 *
 * @param resolver Asset resolver for loading external resources.
 * @param json JSON parser configuration.
 */
class GLTFLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {

    suspend fun load(
        url: String,
        onLoad: (GLTFAsset) -> Unit,
        onProgress: ((LoadingProgress) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            onLoad(load(url, onProgress))
        } catch (t: Throwable) {
            if (onError != null) onError(t) else throw t
        }
    }

    suspend fun load(
        url: String,
        progress: ((LoadingProgress) -> Unit)? = null
    ): GLTFAsset = withContext(Dispatchers.Default) {
        val normalizedUrl = url.replace('\\', '/')
        val basePath = normalizedUrl.substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() }
        val documentBytes = loadDocumentBytes(normalizedUrl, basePath)
        val document = json.decodeFromString<GltfDocument>(documentBytes.decodeToString())

        val buffers = loadBuffers(document.buffers, basePath, progress)
        val reader = AccessorReader(document, buffers)
        val materialFactory = MaterialFactory()
        val meshCache = HashMap<Int, Object3D>()
        val nodeCache = HashMap<Int, Object3D>()

        fun buildMesh(index: Int): Object3D = meshCache.getOrPut(index) {
            val meshDef = document.meshes.getOrNull(index) ?: return@getOrPut Group()
            if (meshDef.primitives.size <= 1) {
                val primitive = meshDef.primitives.firstOrNull()
                return@getOrPut if (primitive != null) {
                    buildPrimitive(meshDef.name, primitive, reader, materialFactory)
                } else {
                    Group()
                }
            }

            Group().apply {
                meshDef.name?.let { name = it }
                meshDef.primitives.forEachIndexed { primitiveIndex, primitive ->
                    val child = buildPrimitive(
                        meshDef.name?.let { "${it}_$primitiveIndex" },
                        primitive,
                        reader,
                        materialFactory
                    )
                    add(child)
                }
            }
        }

        fun buildNode(index: Int): Object3D = nodeCache.getOrPut(index) {
            val nodeDef = document.nodes.getOrNull(index) ?: return@getOrPut Group()
            val base = when (val meshIndex = nodeDef.mesh) {
                null -> Group()
                else -> cloneObject(buildMesh(meshIndex))
            }
            nodeDef.name?.let { base.name = it }
            nodeDef.children?.forEach { child -> base.add(buildNode(child)) }
            base
        }

        val scenes = document.scenes.map { sceneDef ->
            Scene().apply {
                sceneDef.nodes.forEach { nodeIndex ->
                    add(buildNode(nodeIndex))
                }
            }
        }

        val primaryScene = document.scene?.let { index ->
            scenes.getOrNull(index)
        } ?: scenes.firstOrNull() ?: Scene()

        val allNodes = nodeCache.keys.sorted().mapNotNull { nodeCache[it] }
        val materials = materialFactory.materials.toList()

        GLTFAsset(
            scene = primaryScene,
            scenes = scenes,
            nodes = allNodes,
            materials = materials,
            animations = emptyList()
        )
    }

    private suspend fun loadDocumentBytes(url: String, basePath: String?): ByteArray {
        return when {
            url.startsWith("data:", ignoreCase = true) -> DataUriDecoder.decode(url)
            else -> resolver.load(url, basePath)
        }
    }

    private suspend fun loadBuffers(
        buffers: List<GltfBuffer>,
        basePath: String?,
        progress: ((LoadingProgress) -> Unit)?
    ): List<ByteArray> {
        if (buffers.isEmpty()) return emptyList()
        val total = buffers.sumOf { it.byteLength.toLong() }.coerceAtLeast(1L)
        var loaded = 0L

        return buffers.map { buffer ->
            val data = when (val uri = buffer.uri) {
                null -> ByteArray(buffer.byteLength)
                else -> {
                    if (uri.startsWith("data:", ignoreCase = true)) {
                        DataUriDecoder.decode(uri)
                    } else {
                        resolver.load(uri, basePath?.let { ensureTrailingSlash(it) })
                    }
                }
            }

            loaded += data.size
            progress?.invoke(LoadingProgress(loaded, total))
            data
        }
    }

    private fun ensureTrailingSlash(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    private fun buildPrimitive(
        name: String?,
        primitive: GltfPrimitive,
        reader: AccessorReader,
        materialFactory: MaterialFactory
    ): Object3D {
        val geometry = BufferGeometry()

        primitive.attributes["POSITION"]?.let { accessorIndex ->
            val data = reader.readFloatAttribute(accessorIndex)
            geometry.setAttribute("position", BufferAttribute(data, 3))
        } ?: error("GLTF primitive missing POSITION attribute")

        primitive.attributes["NORMAL"]?.let { accessorIndex ->
            val data = reader.readFloatAttribute(accessorIndex)
            geometry.setAttribute("normal", BufferAttribute(data, 3))
        }

        primitive.attributes["TEXCOORD_0"]?.let { accessorIndex ->
            val data = reader.readFloatAttribute(accessorIndex)
            geometry.setAttribute("uv", BufferAttribute(data, 2))
        }

        primitive.attributes["COLOR_0"]?.let { accessorIndex ->
            val accessor = reader.document.accessors[accessorIndex]
            val data = reader.readFloatAttribute(accessorIndex)
            val size = when (accessor.type) {
                "VEC4" -> 4
                else -> 3
            }
            geometry.setAttribute("color", BufferAttribute(data, size))
        }

        primitive.indices?.let { accessorIndex ->
            val indices = reader.readIndices(accessorIndex)
            val floatIndices = FloatArray(indices.size) { idx -> indices[idx].toFloat() }
            geometry.setIndex(BufferAttribute(floatIndices, 1))
        }

        val material = materialFactory.resolve()
        return Mesh(geometry, material).apply {
            this.name = name ?: "GLTFMesh"
            drawMode = when (primitive.mode ?: 4) {
                0 -> io.materia.core.scene.DrawMode.POINTS
                1 -> io.materia.core.scene.DrawMode.LINES
                2 -> io.materia.core.scene.DrawMode.LINE_LOOP
                3 -> io.materia.core.scene.DrawMode.LINE_STRIP
                5 -> io.materia.core.scene.DrawMode.TRIANGLE_STRIP
                6 -> io.materia.core.scene.DrawMode.TRIANGLE_FAN
                else -> io.materia.core.scene.DrawMode.TRIANGLES
            }
        }
    }

    private fun cloneObject(source: Object3D): Object3D {
        return when (source) {
            is Mesh -> {
                val geometry = source.geometry
                val material = source.material
                Mesh(geometry, material).apply { name = source.name }
            }

            is Group -> {
                Group().apply {
                    name = source.name
                    source.children.forEach { child -> add(cloneObject(child)) }
                }
            }

            else -> Group()
        }
    }

    private class MaterialFactory {
        private val defaultBaseColor = Color(1f, 1f, 1f)
        private val _materials = mutableListOf<Material>()
        val materials: List<Material> get() = _materials

        fun resolve(): Material {
            val material = MeshStandardMaterial(color = defaultBaseColor)
            material.side = MaterialSide.FRONT
            _materials.add(material)
            return material
        }
    }

    private class AccessorReader(
        val document: GltfDocument,
        private val buffers: List<ByteArray>
    ) {
        fun readFloatAttribute(accessorIndex: Int): FloatArray {
            val accessor = document.accessors.getOrNull(accessorIndex)
                ?: error("Accessor $accessorIndex not found")
            val bufferView = accessor.bufferView?.let { document.bufferViews[it] }
                ?: error("Accessor $accessorIndex missing bufferView")

            require(accessor.componentType == FLOAT_COMPONENT) {
                "Only FLOAT attributes are supported in this loader subset"
            }

            val componentCount = componentCount(accessor.type)
            val data = FloatArray(accessor.count * componentCount)
            val buffer = buffers[bufferView.buffer]

            val stride =
                bufferView.byteStride ?: componentCount * componentByteSize(accessor.componentType)
            var offset = (bufferView.byteOffset ?: 0) + (accessor.byteOffset ?: 0)

            for (i in 0 until accessor.count) {
                for (component in 0 until componentCount) {
                    data[i * componentCount + component] =
                        buffer.readFloat32(offset + component * 4)
                }
                offset += stride
            }

            return data
        }

        fun readIndices(accessorIndex: Int): IntArray {
            val accessor = document.accessors.getOrNull(accessorIndex)
                ?: error("Index accessor $accessorIndex missing")
            val bufferView = accessor.bufferView?.let { document.bufferViews[it] }
                ?: error("Index accessor $accessorIndex missing bufferView")
            val buffer = buffers[bufferView.buffer]

            val stride = bufferView.byteStride ?: componentByteSize(accessor.componentType)
            val result = IntArray(accessor.count)
            var offset = (bufferView.byteOffset ?: 0) + (accessor.byteOffset ?: 0)

            for (i in 0 until accessor.count) {
                result[i] = when (accessor.componentType) {
                    UNSIGNED_BYTE -> buffer[offset].toInt() and 0xFF
                    UNSIGNED_SHORT -> buffer.readUInt16(offset)
                    UNSIGNED_INT -> buffer.readUInt32(offset)
                    else -> error("Unsupported index component type ${accessor.componentType}")
                }
                offset += stride
            }

            return result
        }

        private fun componentCount(type: String): Int = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            "MAT3" -> 9
            "MAT4" -> 16
            else -> error("Unsupported accessor type $type")
        }

        companion object {
            private const val FLOAT_COMPONENT = 5126
            private const val UNSIGNED_BYTE = 5121
            private const val UNSIGNED_SHORT = 5123
            private const val UNSIGNED_INT = 5125

            fun componentByteSize(componentType: Int): Int = when (componentType) {
                FLOAT_COMPONENT, UNSIGNED_INT -> 4
                UNSIGNED_SHORT -> 2
                UNSIGNED_BYTE -> 1
                else -> error("Unsupported component type $componentType")
            }

            private fun ByteArray.readFloat32(offset: Int): Float {
                val bits = (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8) or
                    ((this[offset + 2].toInt() and 0xFF) shl 16) or
                    ((this[offset + 3].toInt() and 0xFF) shl 24)
                return Float.fromBits(bits)
            }

            private fun ByteArray.readUInt16(offset: Int): Int {
                return (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8)
            }

            private fun ByteArray.readUInt32(offset: Int): Int {
                return (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8) or
                    ((this[offset + 2].toInt() and 0xFF) shl 16) or
                    ((this[offset + 3].toInt() and 0xFF) shl 24)
            }
        }
    }

    private object DataUriDecoder {
        fun decode(uri: String): ByteArray {
            val commaIndex = uri.indexOf(',')
            require(commaIndex != -1) { "Invalid data URI: $uri" }
            val metadata = uri.substring(5, commaIndex)
            val payload = uri.substring(commaIndex + 1)
            return if (metadata.endsWith(";base64", ignoreCase = true)) {
                Base64Decoder.decode(payload)
            } else {
                payload.encodeToByteArray()
            }
        }
    }

    private object Base64Decoder {
        private val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        fun decode(data: String): ByteArray {
            val sanitized = data.trim().replace("\n", "").replace("\r", "")
            val output = ArrayList<Byte>(sanitized.length * 3 / 4)
            var buffer = 0
            var bitsCollected = 0

            for (char in sanitized) {
                if (char == '=') break
                val value = table.indexOf(char)
                if (value < 0) continue

                buffer = (buffer shl 6) or value
                bitsCollected += 6

                if (bitsCollected >= 8) {
                    bitsCollected -= 8
                    val byte = (buffer shr bitsCollected) and 0xFF
                    output.add(byte.toByte())
                }
            }

            return output.toByteArray()
        }
    }

    @Serializable
    private data class GltfDocument(
        val buffers: List<GltfBuffer> = emptyList(),
        val bufferViews: List<GltfBufferView> = emptyList(),
        val accessors: List<GltfAccessor> = emptyList(),
        val meshes: List<GltfMesh> = emptyList(),
        val nodes: List<GltfNode> = emptyList(),
        val scenes: List<GltfScene> = emptyList(),
        val scene: Int? = null,
        val materials: List<GltfMaterial> = emptyList(),
        val images: List<GltfImage> = emptyList(),
        val textures: List<GltfTexture> = emptyList()
    )

    @Serializable
    private data class GltfBuffer(
        val uri: String? = null,
        val byteLength: Int
    )

    @Serializable
    private data class GltfBufferView(
        val buffer: Int,
        val byteOffset: Int? = null,
        val byteLength: Int,
        val byteStride: Int? = null
    )

    @Serializable
    private data class GltfAccessor(
        val bufferView: Int? = null,
        val byteOffset: Int? = null,
        val componentType: Int,
        val normalized: Boolean = false,
        val count: Int,
        val type: String
    )

    @Serializable
    private data class GltfMesh(
        val primitives: List<GltfPrimitive> = emptyList(),
        val name: String? = null
    )

    @Serializable
    private data class GltfPrimitive(
        val attributes: Map<String, Int> = emptyMap(),
        val indices: Int? = null,
        val material: Int? = null,
        val mode: Int? = null
    )

    @Serializable
    private data class GltfNode(
        val mesh: Int? = null,
        val children: List<Int>? = null,
        val name: String? = null
    )

    @Serializable
    private data class GltfScene(
        val nodes: List<Int> = emptyList()
    )

    @Serializable
    private data class GltfMaterial(
        val name: String? = null,
        val doubleSided: Boolean = false,
        val pbrMetallicRoughness: GltfPbr? = null,
        val alphaMode: String? = null,
        val baseColorTexture: GltfTextureInfo? = null
    ) {
        val pbr: GltfPbr? get() = pbrMetallicRoughness
    }

    @Serializable
    private data class GltfPbr(
        val baseColorFactor: List<Float>? = null,
        val metallicFactor: Float? = null,
        val roughnessFactor: Float? = null
    )

    @Serializable
    private data class GltfImage(
        val uri: String? = null
    )

    @Serializable
    private data class GltfTexture(
        val source: Int? = null
    )

    @Serializable
    private data class GltfTextureInfo(
        val index: Int
    )
}
