package io.materia.loader

import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshStandardMaterial
import kotlin.math.max

private data class ObjVertexKey(val positionIndex: Int, val uvIndex: Int?, val normalIndex: Int?)

/**
 * Minimal Wavefront OBJ loader supporting triangular and quad faces with
 * position/normal/uv attributes. Designed for runtime asset loading without
 * relying on platform-specific tooling.
 */
class OBJLoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<ModelAsset> {

    override suspend fun load(path: String): ModelAsset {
        val raw = resolver.load(path, deriveBasePath(path))
        val content = raw.decodeToString()
        val model = parse(content, path.substringAfterLast('/'))
        return model
    }

    private fun parse(content: String, name: String): ModelAsset {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()

        val finalPositions = ArrayList<Float>()
        val finalNormals = ArrayList<Float>()
        val finalUvs = ArrayList<Float>()
        val indices = ArrayList<Int>()
        val vertexCache = HashMap<ObjVertexKey, Int>()

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                when {
                    line.startsWith("v ") -> {
                        val tokens = line.substring(2).trim().split(Regex("\\s+"))
                        positions.add(tokens.getOrNull(0)?.toFloatOrNull() ?: 0f)
                        positions.add(tokens.getOrNull(1)?.toFloatOrNull() ?: 0f)
                        positions.add(tokens.getOrNull(2)?.toFloatOrNull() ?: 0f)
                    }
                    line.startsWith("vt ") -> {
                        val tokens = line.substring(3).trim().split(Regex("\\s+"))
                        uvs.add(tokens.getOrNull(0)?.toFloatOrNull() ?: 0f)
                        // OBJ V coordinates start at bottom
                        val v = tokens.getOrNull(1)?.toFloatOrNull()?.let { 1f - it } ?: 0f
                        uvs.add(v)
                    }
                    line.startsWith("vn ") -> {
                        val tokens = line.substring(3).trim().split(Regex("\\s+"))
                        normals.add(tokens.getOrNull(0)?.toFloatOrNull() ?: 0f)
                        normals.add(tokens.getOrNull(1)?.toFloatOrNull() ?: 0f)
                        normals.add(tokens.getOrNull(2)?.toFloatOrNull() ?: 0f)
                    }
                    line.startsWith("f ") -> {
                        val faceTokens = line.substring(2).trim().split(Regex("\\s+"))
                        if (faceTokens.size < 3) return@forEach
                        val vertexRefs = faceTokens.map { token ->
                            val elements = token.split('/')
                            val positionIndex = elements.getOrNull(0)?.toIntOrNull() ?: 0
                            val uvIndex = elements.getOrNull(1)?.takeIf { it.isNotBlank() }?.toIntOrNull()
                            val normalIndex = elements.getOrNull(2)?.toIntOrNull()
                            ObjVertexKey(positionIndex, uvIndex, normalIndex)
                        }

                        for (i in 1 until vertexRefs.size - 1) {
                            val a = emitVertex(vertexRefs[0], positions, uvs, normals, vertexCache, finalPositions, finalUvs, finalNormals)
                            val b = emitVertex(vertexRefs[i], positions, uvs, normals, vertexCache, finalPositions, finalUvs, finalNormals)
                            val c = emitVertex(vertexRefs[i + 1], positions, uvs, normals, vertexCache, finalPositions, finalUvs, finalNormals)
                            indices.add(a)
                            indices.add(b)
                            indices.add(c)
                        }
                    }
                }
            }

        val geometry = BufferGeometry()
        if (finalPositions.isEmpty()) {
            throw IllegalArgumentException("OBJ file contains no vertex positions")
        }
        geometry.setAttribute("position", BufferAttribute(finalPositions.toFloatArray(), 3))

        if (finalNormals.isNotEmpty()) {
            geometry.setAttribute("normal", BufferAttribute(finalNormals.toFloatArray(), 3))
        }

        if (finalUvs.isNotEmpty()) {
            geometry.setAttribute("uv", BufferAttribute(finalUvs.toFloatArray(), 2))
        }

        if (indices.isNotEmpty()) {
            val indexArray = FloatArray(indices.size) { idx -> indices[idx].toFloat() }
            geometry.setIndex(BufferAttribute(indexArray, 1))
        }

        val material = MeshStandardMaterial()
        val mesh = Mesh(geometry, material).apply {
            this.name = name.ifBlank { "OBJMesh" }
        }

        val scene = Scene().apply { add(mesh) }
        return ModelAsset(scene = scene, materials = listOf(material))
    }

    private fun deriveBasePath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else null
    }

    private fun emitVertex(
        key: ObjVertexKey,
        positions: List<Float>,
        uvs: List<Float>,
        normals: List<Float>,
        cache: MutableMap<ObjVertexKey, Int>,
        finalPositions: MutableList<Float>,
        finalUvs: MutableList<Float>,
        finalNormals: MutableList<Float>
    ): Int {
        cache[key]?.let { return it }

        val positionIndex = wrapIndex(key.positionIndex, positions.size / 3)
        val vertexOffset = positionIndex * 3
        finalPositions.add(positions[vertexOffset])
        finalPositions.add(positions[vertexOffset + 1])
        finalPositions.add(positions[vertexOffset + 2])

        if (key.uvIndex != null && uvs.isNotEmpty()) {
            val uvIndex = wrapIndex(key.uvIndex, uvs.size / 2)
            val uvOffset = uvIndex * 2
            finalUvs.add(uvs[uvOffset])
            finalUvs.add(uvs[uvOffset + 1])
        }

        if (key.normalIndex != null && normals.isNotEmpty()) {
            val normalIndex = wrapIndex(key.normalIndex, normals.size / 3)
            val normalOffset = normalIndex * 3
            finalNormals.add(normals[normalOffset])
            finalNormals.add(normals[normalOffset + 1])
            finalNormals.add(normals[normalOffset + 2])
        }

        val index = finalPositions.size / 3 - 1
        cache[key] = index
        return index
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (size == 0) return 0
        return when {
            index > 0 -> max(0, index - 1)
            index < 0 -> max(0, size + index)
            else -> 0
        }
    }
}
