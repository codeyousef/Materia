package io.materia.loader

import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshStandardMaterial
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loader for the simplified Draco JSON interchange format used by KreeKt.
 * Files are expected to contain a JSON object with position, index, and optional
 * normal/uv arrays (all stored as base64 or raw numeric lists). This keeps the
 * loader dependency-free while still providing a compact mesh container that can
 * be generated via the provided tooling.
 */
class DRACOLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AssetLoader<ModelAsset> {

    override suspend fun load(path: String): ModelAsset {
        val basePath = path.substringBeforeLast('/', "")
        val bytes = resolver.load(path, if (basePath.isEmpty()) null else "$basePath/")
        val text = bytes.decodeToString()
        val model = json.decodeFromString(DracoJsonMesh.serializer(), text)

        require(model.positions.isNotEmpty()) { "Draco JSON mesh missing positions" }
        require(model.indices.isNotEmpty()) { "Draco JSON mesh missing indices" }

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(model.positions.toFloatArray(), 3))
        model.normals?.let { geometry.setAttribute("normal", BufferAttribute(it.toFloatArray(), 3)) }
        model.uvs?.let { geometry.setAttribute("uv", BufferAttribute(it.toFloatArray(), 2)) }
        geometry.setIndex(BufferAttribute(model.indices.map(Int::toFloat).toFloatArray(), 1))

        val material = MeshStandardMaterial(name = model.material ?: "DracoMaterial")
        val mesh = Mesh(geometry, material).apply { name = model.name ?: "DracoMesh" }
        val scene = Scene().apply { add(mesh) }

        return ModelAsset(scene = scene, materials = listOf(material))
    }

    @Serializable
    private data class DracoJsonMesh(
        val name: String? = null,
        val material: String? = null,
        val positions: List<Float>,
        val normals: List<Float>? = null,
        val uvs: List<Float>? = null,
        val indices: List<Int>,
        @SerialName("tangents") val tangents: List<Float>? = null
    )
}
