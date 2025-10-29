package io.kreekt.loader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal glTF 2.0 schema used by the GLTFLoader. This intentionally captures
 * only the structures that are required to build renderable scenes. The schema
 * is deliberately partial – unknown fields are ignored by the JSON parser so
 * the loader stays forward compatible with new extensions.
 */
@Serializable
internal data class GltfDocument(
    val asset: GltfAssetMeta,
    val scene: Int? = null,
    val scenes: List<GltfScene> = emptyList(),
    val nodes: List<GltfNode> = emptyList(),
    val meshes: List<GltfMesh> = emptyList(),
    val accessors: List<GltfAccessor> = emptyList(),
    @SerialName("bufferViews")
    val bufferViews: List<GltfBufferView> = emptyList(),
    val buffers: List<GltfBuffer> = emptyList(),
    val materials: List<GltfMaterial> = emptyList(),
    val images: List<GltfImage> = emptyList(),
    val textures: List<GltfTexture> = emptyList(),
    val samplers: List<GltfSampler> = emptyList(),
    val animations: List<GltfAnimation> = emptyList()
)

@Serializable
internal data class GltfAssetMeta(
    val version: String,
    val generator: String? = null
)

@Serializable
internal data class GltfScene(
    val name: String? = null,
    val nodes: List<Int> = emptyList()
)

@Serializable
internal data class GltfNode(
    val name: String? = null,
    val mesh: Int? = null,
    val children: List<Int> = emptyList(),
    val translation: List<Float>? = null,
    val rotation: List<Float>? = null,
    val scale: List<Float>? = null,
    val matrix: List<Float>? = null
)

@Serializable
internal data class GltfMesh(
    val name: String? = null,
    val primitives: List<GltfPrimitive> = emptyList()
)

@Serializable
internal data class GltfPrimitive(
    val attributes: Map<String, Int> = emptyMap(),
    val indices: Int? = null,
    val material: Int? = null,
    val mode: Int? = null
)

@Serializable
internal data class GltfAccessor(
    val bufferView: Int? = null,
    val byteOffset: Int? = null,
    val componentType: Int,
    val normalized: Boolean = false,
    val count: Int,
    val type: String,
    val max: List<Float>? = null,
    val min: List<Float>? = null
)

@Serializable
internal data class GltfBufferView(
    val buffer: Int,
    val byteOffset: Int? = null,
    val byteLength: Int,
    val byteStride: Int? = null
)

@Serializable
internal data class GltfBuffer(
    val uri: String? = null,
    val byteLength: Int
)

@Serializable
internal data class GltfMaterial(
    val name: String? = null,
    @SerialName("pbrMetallicRoughness")
    val pbr: GltfPbr? = null,
    val doubleSided: Boolean = false,
    val alphaMode: String? = null,
    val alphaCutoff: Float? = null,
    val emissiveFactor: List<Float>? = null,
    val emissiveTexture: GltfTextureInfo? = null
)

@Serializable
internal data class GltfPbr(
    val baseColorFactor: List<Float>? = null,
    val baseColorTexture: GltfTextureInfo? = null,
    val metallicFactor: Float? = null,
    val roughnessFactor: Float? = null,
    val metallicRoughnessTexture: GltfTextureInfo? = null
)

@Serializable
internal data class GltfTextureInfo(
    val index: Int,
    val texCoord: Int? = null
)

@Serializable
internal data class GltfImage(
    val uri: String? = null,
    val mimeType: String? = null,
    val bufferView: Int? = null,
    val name: String? = null
)

@Serializable
internal data class GltfTexture(
    val sampler: Int? = null,
    val source: Int? = null,
    val name: String? = null
)

@Serializable
internal data class GltfSampler(
    val magFilter: Int? = null,
    val minFilter: Int? = null,
    val wrapS: Int? = null,
    val wrapT: Int? = null
)

@Serializable
internal data class GltfAnimation(
    val name: String? = null
    // Channels and samplers are currently ignored – animation support is planned.
)
