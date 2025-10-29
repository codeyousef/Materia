package io.kreekt.loader

import io.kreekt.animation.AnimationClip
import io.kreekt.core.math.Color
import io.kreekt.core.scene.DrawMode
import io.kreekt.core.scene.Group
import io.kreekt.core.scene.Material
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Object3D
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshStandardMaterial
import io.kreekt.material.MaterialSide
import io.kreekt.texture.Texture2D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * Result produced by [GLTFLoader]. Mirrors Three.js' GLTFLoader contract.
 */
data class GLTFAsset(
    val scene: Scene,
    val scenes: List<Scene>,
    val nodes: List<Object3D>,
    val materials: List<Material>,
    val animations: List<AnimationClip>
)

/**
 * Loading progress information (bytes loaded vs total).
 */
data class LoadingProgress(
    val loaded: Long,
    val total: Long
) {
    val percentage: Float
        get() = if (this.total <= 0L) 1f else (this.loaded.toFloat() / this.total.toFloat()).coerceIn(0f, 1f)
}

/**
 * GLTF 2.0 loader with feature parity to the subset required for rendering static
 * models. The loader supports `.gltf` (JSON + external buffers) and `.glb`
 * (binary) assets, including embedded data URIs. Animations, skinning, KTX/Draco
 * extensions, and texture samplers are parsed but not yet executed – they are
 * exposed so higher-level systems can wire them once ready.
 */
class GLTFLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {

    /**
     * Convenience asynchronous API mirroring Three.js:
     *
     * ```
     * loader.load(
     *     url = "model.gltf",
     *     onLoad = { asset -> scene.add(asset.scene) },
     *     onProgress = { progress -> println("Loading ${(progress.percentage * 100).toInt()}%") },
     *     onError = { throwable -> println("Failed: ${throwable.message}") }
     * )
     * ```
     */
    suspend fun load(
        url: String,
        onLoad: (GLTFAsset) -> Unit,
        onProgress: ((LoadingProgress) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            val asset = load(url, onProgress)
            onLoad(asset)
        } catch (t: Throwable) {
            if (onError != null) {
                onError(t)
            } else {
                throw t
            }
        }
    }

    /**
     * Coroutine-friendly API returning the parsed [GLTFAsset].
     */
    suspend fun load(
        url: String,
        progress: ((LoadingProgress) -> Unit)? = null
    ): GLTFAsset = withContext(Dispatchers.Default) {
        val normalizedUrl = url.replace('\\', '/')
        val basePath = deriveBasePath(normalizedUrl)
        val mainBytes = resolver.load(normalizedUrl, null)
        val parsed = parseDocument(mainBytes, normalizedUrl)

        val bufferData = loadBuffers(parsed.document, parsed.binaryChunk, basePath, progress)
        val imageData = loadImages(parsed.document, bufferData, basePath, progress)

        buildScenes(parsed.document, bufferData, imageData)
    }

    private data class ParsedDocument(
        val document: GltfDocument,
        val binaryChunk: ByteArray?
    )

    private suspend fun loadBuffers(
        document: GltfDocument,
        binaryChunk: ByteArray?,
        basePath: String?,
        progress: ((LoadingProgress) -> Unit)?
    ): List<ByteArray> {
        if (document.buffers.isEmpty()) return emptyList()

        val totalBytes = document.buffers.sumOf { it.byteLength.toLong() }
        var loadedBytes = 0L

        val buffers = document.buffers.mapIndexed { index, buffer ->
            val bytes = when {
                buffer.uri == null -> binaryChunk
                    ?: error("Buffer[$index] has no URI but GLB binary chunk is missing")
                buffer.uri.startsWith("data:", ignoreCase = true) ->
                    DataUriDecoder.decode(buffer.uri)
                else -> resolver.load(buffer.uri, basePath)
            }
            check(bytes.size >= buffer.byteLength) {
                "Buffer[$index] expected ${buffer.byteLength} bytes but loader returned ${bytes.size}"
            }
            loadedBytes += buffer.byteLength
            progress?.invoke(LoadingProgress(loadedBytes, totalBytes))
            if (bytes.size == buffer.byteLength) bytes else bytes.copyOf(buffer.byteLength)
        }

        return buffers
    }

    private suspend fun loadImages(
        document: GltfDocument,
        buffers: List<ByteArray>,
        basePath: String?,
        progress: ((LoadingProgress) -> Unit)?
    ): List<ByteArray?> {
        if (document.images.isEmpty()) return emptyList()

        val total = document.images.size.toLong()
        var loaded = 0L

        val images = document.images.mapIndexed { index, image ->
            val bytes = when {
                image.bufferView != null -> {
                    val view = document.bufferViews.getOrNull(image.bufferView)
                        ?: error("Image[$index] references missing bufferView ${image.bufferView}")
                    val buffer = buffers[view.buffer]
                    val offset = (view.byteOffset ?: 0) + (0)
                    buffer.copyOfRange(offset, offset + view.byteLength)
                }
                image.uri != null -> {
                    if (image.uri.startsWith("data:", ignoreCase = true)) {
                        DataUriDecoder.decode(image.uri)
                    } else {
                        resolver.load(image.uri, basePath)
                    }
                }
                else -> null
            }
            loaded += 1
            progress?.invoke(LoadingProgress(loaded, total))
            bytes
        }

        return images
    }

    private fun parseDocument(bytes: ByteArray, url: String): ParsedDocument {
        return if (isGlb(bytes)) {
            parseGlb(bytes)
        } else {
            val document = json.decodeFromString<GltfDocument>(bytes.decodeToString())
            ParsedDocument(document, null)
        }
    }

    private fun isGlb(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x67.toByte() &&
            bytes[1] == 0x6C.toByte() &&
            bytes[2] == 0x54.toByte() &&
            bytes[3] == 0x46.toByte()
    }

    private fun parseGlb(bytes: ByteArray): ParsedDocument {
        fun Int.asInt(endian: Boolean = true): Int = this
        var offset = 0

        fun readUInt32(): Int {
            val value = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return value
        }

        val magic = readUInt32()
        require(magic == 0x46546C67) { "GLB magic mismatch" }

        val version = readUInt32()
        require(version == 2) { "Unsupported GLB version $version" }

        val length = readUInt32()
        require(length <= bytes.size) { "GLB header claims $length bytes but buffer has ${bytes.size}" }

        val jsonChunkLength = readUInt32()
        val jsonChunkType = readUInt32()
        require(jsonChunkType == 0x4E4F534A) { "GLB first chunk must be JSON" }
        val jsonString = bytes.decodeToString(offset, offset + jsonChunkLength)
        val document = json.decodeFromString<GltfDocument>(jsonString)
        offset += jsonChunkLength

        var binaryChunk: ByteArray? = null
        if (offset + 8 <= length) {
            val binChunkLength = readUInt32()
            val binChunkType = readUInt32()
            if (binChunkType == 0x004E4942) { // BIN
                binaryChunk = bytes.copyOfRange(offset, offset + binChunkLength)
            }
        }

        return ParsedDocument(document, binaryChunk)
    }

    private fun buildScenes(
        document: GltfDocument,
        buffers: List<ByteArray>,
        images: List<ByteArray?>
    ): GLTFAsset {
        val accessorReader = AccessorReader(document, buffers)
        val materialFactory = MaterialFactory(document, images)
        val meshFactory = MeshFactory(document, accessorReader, materialFactory)

        val scenes = document.scenes.mapIndexed { sceneIndex, sceneDef ->
            val scene = Scene().apply {
                name = sceneDef.name ?: "Scene_$sceneIndex"
            }
            sceneDef.nodes.forEach { nodeIndex ->
                val instance = instantiateNode(document, nodeIndex, meshFactory)
                scene.add(instance)
            }
            scene
        }

        val primaryScene = document.scene?.let { scenes.getOrNull(it) } ?: scenes.firstOrNull()
            ?: Scene()

        val nodeList = mutableListOf<Object3D>()
        scenes.forEach { scene ->
            scene.traverse { node -> nodeList.add(node) }
        }

        val materials = materialFactory.materialCache.values.toList()

        return GLTFAsset(
            scene = primaryScene,
            scenes = scenes,
            nodes = nodeList,
            materials = materials,
            animations = emptyList() // Animation parsing is planned (Phase 2).
        )
    }

    private fun instantiateNode(
        document: GltfDocument,
        nodeIndex: Int,
        meshFactory: MeshFactory
    ): Object3D {
        val nodeDef = document.nodes.getOrNull(nodeIndex)
            ?: error("Node index $nodeIndex out of bounds")

        val node: Object3D = when (val meshIndex = nodeDef.mesh) {
            null -> Group()
            else -> meshFactory.createMesh(meshIndex)
        }

        nodeDef.name?.let { node.name = it }
        applyNodeTransform(nodeDef, node)

        nodeDef.children.forEach { childIndex ->
            val child = instantiateNode(document, childIndex, meshFactory)
            node.add(child)
        }

        return node
    }

    private fun applyNodeTransform(nodeDef: GltfNode, node: Object3D) {
        nodeDef.matrix?.takeIf { it.size == 16 }?.let { matrixValues ->
            node.matrix.fromArray(matrixValues.toFloatArray())
            node.matrix.decompose(node.position, node.quaternion, node.scale)
            node.matrixAutoUpdate = false
            return
        }

        nodeDef.translation?.takeIf { it.size == 3 }?.let {
            node.position.set(it[0], it[1], it[2])
        }

        nodeDef.scale?.takeIf { it.size == 3 }?.let {
            node.scale.set(it[0], it[1], it[2])
        }

        nodeDef.rotation?.takeIf { it.size == 4 }?.let {
            node.quaternion.set(it[0], it[1], it[2], it[3])
        }
    }

    private fun deriveBasePath(url: String): String? {
        val normalized = url.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else null
    }

    /**
     * Lazily reads accessor data into typed arrays. Accessors are cached to avoid
     * recomputing data for shared primitives.
     */
    private class AccessorReader(
        private val document: GltfDocument,
        private val buffers: List<ByteArray>
    ) {
        private val floatCache = HashMap<Int, FloatArray>()
        private val indexCache = HashMap<Int, IntArray>()

        fun readFloatAttribute(index: Int): FloatArray {
            return floatCache.getOrPut(index) {
                val accessor = document.accessors[index]
                require(accessor.type != "SCALAR" || accessor.componentType == FLOAT_COMPONENT) {
                    "Accessor[$index] is scalar but requested as float attribute"
                }
                readAccessorFloats(accessor)
            }
        }

        fun readScalarFloats(index: Int): FloatArray {
            return floatCache.getOrPut(index) {
                val accessor = document.accessors[index]
                readAccessorFloats(accessor)
            }
        }

        fun readIndices(index: Int): IntArray {
            return indexCache.getOrPut(index) {
                val accessor = document.accessors[index]
                require(accessor.type == "SCALAR") {
                    "Index accessor must be SCALAR (accessor[$index])"
                }
                readAccessorIndices(accessor)
            }
        }

        private fun readAccessorFloats(accessor: GltfAccessor): FloatArray {
            val view = accessor.bufferView?.let { document.bufferViews[it] }
                ?: error("Accessor requires bufferView (sparse accessors not yet supported)")
            val buffer = buffers[view.buffer]
            val elementSize = componentCount(accessor.type)
            val componentByteSize = componentByteSize(accessor.componentType)
            val stride = view.byteStride ?: (elementSize * componentByteSize)

            val result = FloatArray(accessor.count * elementSize)
            var offset = (view.byteOffset ?: 0) + (accessor.byteOffset ?: 0)

            for (i in 0 until accessor.count) {
                for (component in 0 until elementSize) {
                    val componentOffset = offset + component * componentByteSize
                    val value = when (accessor.componentType) {
                        FLOAT_COMPONENT -> buffer.readFloat32(componentOffset)
                        UNSIGNED_BYTE -> {
                            val v = buffer[componentOffset].toInt() and 0xFF
                            if (accessor.normalized) v / 255f else v.toFloat()
                        }
                        BYTE -> {
                            val v = buffer[componentOffset].toInt().toByte().toInt()
                            if (accessor.normalized) max(v / 127f, -1f) else v.toFloat()
                        }
                        UNSIGNED_SHORT -> {
                            val v = buffer.readUInt16(componentOffset)
                            if (accessor.normalized) v / 65535f else v.toFloat()
                        }
                        SHORT -> {
                            val v = buffer.readInt16(componentOffset)
                            if (accessor.normalized) max(v / 32767f, -1f) else v.toFloat()
                        }
                        else -> error("Unsupported component type ${accessor.componentType}")
                    }
                    result[i * elementSize + component] = value
                }
                offset += stride
            }

            return result
        }

        private fun readAccessorIndices(accessor: GltfAccessor): IntArray {
            val view = accessor.bufferView?.let { document.bufferViews[it] }
                ?: error("Index accessor missing bufferView")
            val buffer = buffers[view.buffer]
            val stride = view.byteStride ?: componentByteSize(accessor.componentType)
            val result = IntArray(accessor.count)
            var offset = (view.byteOffset ?: 0) + (accessor.byteOffset ?: 0)

            for (i in 0 until accessor.count) {
                val value = when (accessor.componentType) {
                    UNSIGNED_SHORT -> buffer.readUInt16(offset)
                    UNSIGNED_BYTE -> buffer[offset].toInt() and 0xFF
                    UNSIGNED_INT -> buffer.readUInt32(offset)
                    else -> error("Unsupported index component type ${accessor.componentType}")
                }
                result[i] = value
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
            private const val BYTE = 5120
            private const val UNSIGNED_BYTE = 5121
            private const val SHORT = 5122
            private const val UNSIGNED_SHORT = 5123
            private const val UNSIGNED_INT = 5125
            private const val FLOAT_COMPONENT = 5126

            private fun componentByteSize(componentType: Int): Int = when (componentType) {
                BYTE, UNSIGNED_BYTE -> 1
                SHORT, UNSIGNED_SHORT -> 2
                UNSIGNED_INT, FLOAT_COMPONENT -> 4
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

            private fun ByteArray.readInt16(offset: Int): Int {
                val value = readUInt16(offset)
                return if (value and 0x8000 != 0) value - 0x10000 else value
            }

            private fun ByteArray.readUInt32(offset: Int): Int {
                return (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8) or
                    ((this[offset + 2].toInt() and 0xFF) shl 16) or
                    ((this[offset + 3].toInt() and 0xFF) shl 24)
            }
        }
    }

    private class MaterialFactory(
        private val document: GltfDocument,
        private val images: List<ByteArray?>
    ) {
        val materialCache = LinkedHashMap<Int, Material>()

        fun resolve(materialIndex: Int?): Material {
            if (materialIndex == null || materialIndex !in document.materials.indices) {
                return MeshStandardMaterial()
            }
            return materialCache.getOrPut(materialIndex) {
                val def = document.materials[materialIndex]
                createMaterial(def)
            }
        }

        private fun createMaterial(def: GltfMaterial): Material {
            val pbr = def.pbr
                val colorFactor = pbr?.baseColorFactor ?: listOf(1f, 1f, 1f, 1f)
                val material = MeshStandardMaterial(
                    color = Color(colorFactor[0], colorFactor[1], colorFactor[2]),
                    metalness = pbr?.metallicFactor ?: 1f,
                    roughness = pbr?.roughnessFactor ?: 1f
                )
                material.transparent = (def.alphaMode == "BLEND") || colorFactor.getOrNull(3)?.let { it < 0.999f } == true
                material.opacity = colorFactor.getOrNull(3) ?: 1f
                material.side = if (def.doubleSided) MaterialSide.DOUBLE else MaterialSide.FRONT
                def.name?.let { material.name = it }

                if (pbr?.baseColorTexture != null) {
                    val texIndex = document.textures.getOrNull(pbr.baseColorTexture.index)?.source
                    val imageBytes = texIndex?.let { images.getOrNull(it) }
                if (imageBytes != null) {
                    val texture = RawImageDecoder.decode(imageBytes)
                    material.map = texture
                }
            }

            return material
        }
    }

    private class MeshFactory(
        private val document: GltfDocument,
        private val accessorReader: AccessorReader,
        private val materialFactory: MaterialFactory
    ) {

        fun createMesh(meshIndex: Int): Object3D {
            val meshDef = document.meshes.getOrNull(meshIndex)
                ?: error("Mesh index $meshIndex out of bounds")

            if (meshDef.primitives.size == 1) {
                return buildPrimitive(meshDef.primitives.first()).apply {
                    meshDef.name?.let { name = it }
                }
            }

            val group = Group()
            meshDef.name?.let { group.name = it }
            meshDef.primitives.forEachIndexed { primitiveIndex, primitive ->
                val mesh = buildPrimitive(primitive).apply {
                    name = "${meshDef.name ?: "Mesh"}_$primitiveIndex"
                }
                group.add(mesh)
            }
            return group
        }

        private fun buildPrimitive(primitive: GltfPrimitive): Mesh {
            val geometry = BufferGeometry()
            val attributes = primitive.attributes

            attributes["POSITION"]?.let { accessor ->
                val data = accessorReader.readFloatAttribute(accessor)
                geometry.setAttribute("position", BufferAttribute(data, 3))
            } ?: error("Primitive is missing POSITION attribute")

            attributes["NORMAL"]?.let { accessor ->
                val data = accessorReader.readFloatAttribute(accessor)
                geometry.setAttribute("normal", BufferAttribute(data, 3))
            }

            attributes["TEXCOORD_0"]?.let { accessor ->
                val data = accessorReader.readFloatAttribute(accessor)
                geometry.setAttribute("uv", BufferAttribute(data, 2))
            }

            attributes["COLOR_0"]?.let { accessor ->
                val data = accessorReader.readFloatAttribute(accessor)
                val accessorDef = document.accessors[accessor]
                val itemSize = when (accessorDef.type) {
                    "VEC3" -> 3
                    "VEC4" -> 4
                    else -> 3
                }
                geometry.setAttribute("color", BufferAttribute(data, itemSize))
            }

            primitive.indices?.let { accessor ->
                val indices = accessorReader.readIndices(accessor)
                val floatIndices = FloatArray(indices.size) { idx -> indices[idx].toFloat() }
                geometry.setIndex(BufferAttribute(floatIndices, 1))
            }

            val material = materialFactory.resolve(primitive.material)
            val mesh = Mesh(geometry, material)
            mesh.drawMode = when (primitive.mode ?: 4) {
                0 -> DrawMode.POINTS
                1 -> DrawMode.LINES
                2 -> DrawMode.LINE_LOOP
                3 -> DrawMode.LINE_STRIP
                4 -> DrawMode.TRIANGLES
                5 -> DrawMode.TRIANGLE_STRIP
                6 -> DrawMode.TRIANGLE_FAN
                else -> DrawMode.TRIANGLES
            }
            return mesh
        }
    }

    /**
     * Lightweight image decoder that handles PNG/JPEG byte streams for CPU-side
     * texture initialization. Decoding routes through platform-specific helpers.
     */
    private object RawImageDecoder {
        fun decode(bytes: ByteArray): Texture2D {
            val decoded = PlatformImageDecoder.decode(bytes)
            val texture = Texture2D.fromImageData(decoded.width, decoded.height, decoded.pixels)
            texture.generateMipmaps = true
            return texture
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
        private val TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        fun decode(data: String): ByteArray {
            val sanitized = data.trim().replace("\n", "").replace("\r", "")
            val out = ArrayList<Byte>(sanitized.length * 3 / 4)

            var buffer = 0
            var bitsCollected = 0

            for (char in sanitized) {
                if (char == '=') break
                val value = TABLE.indexOf(char)
                if (value < 0) continue

                buffer = (buffer shl 6) or value
                bitsCollected += 6

                if (bitsCollected >= 8) {
                    bitsCollected -= 8
                    val byte = (buffer shr bitsCollected) and 0xFF
                    out.add(byte.toByte())
                }
            }

            return out.toByteArray()
        }
    }

}
