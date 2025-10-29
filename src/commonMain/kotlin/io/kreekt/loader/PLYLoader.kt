package io.kreekt.loader

import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshStandardMaterial

/**
 * Simple ASCII PLY loader supporting position, normal, and UV properties.
 * Binary PLY variants are currently not supported.
 */
class PLYLoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<ModelAsset> {

    override suspend fun load(path: String): ModelAsset {
        val raw = resolver.load(path, deriveBasePath(path))
        val content = raw.decodeToString()
        val model = parse(content, path.substringAfterLast('/'))
        return model
    }

    private fun parse(content: String, name: String): ModelAsset {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != "ply") {
            throw IllegalArgumentException("PLY header missing")
        }

        var format = ""
        var vertexCount = 0
        var faceCount = 0
        val propertyOrder = mutableListOf<String>()

        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line == "end_header") break
            when {
                line.startsWith("format ") -> {
                    format = line.substringAfter("format ").substringBefore(' ')
                }
                line.startsWith("element vertex ") -> {
                    vertexCount = line.substringAfterLast(' ').toInt()
                }
                line.startsWith("element face ") -> {
                    faceCount = line.substringAfterLast(' ').toInt()
                }
                line.startsWith("property ") -> {
                    propertyOrder.add(line.substringAfterLast(' '))
                }
            }
        }

        require(format == "ascii") { "PLY binary formats are not supported" }

        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        var hasNormals = false
        var hasUvs = false

        val xIndex = propertyOrder.indexOf("x")
        val yIndex = propertyOrder.indexOf("y")
        val zIndex = propertyOrder.indexOf("z")
        val nxIndex = propertyOrder.indexOf("nx")
        val nyIndex = propertyOrder.indexOf("ny")
        val nzIndex = propertyOrder.indexOf("nz")
        val uIndex = propertyOrder.indexOfFirst { it == "u" || it == "s" }
        val vIndex = propertyOrder.indexOfFirst { it == "v" || it == "t" }

        for (i in 0 until vertexCount) {
            if (!lines.hasNext()) throw IllegalArgumentException("Unexpected EOF while reading vertices")
            val tokens = lines.next().trim().split(Regex("\\s+"))

            positions[i * 3] = tokens.getOrNull(xIndex)?.toFloatOrNull() ?: 0f
            positions[i * 3 + 1] = tokens.getOrNull(yIndex)?.toFloatOrNull() ?: 0f
            positions[i * 3 + 2] = tokens.getOrNull(zIndex)?.toFloatOrNull() ?: 0f

            if (nxIndex != -1 && nyIndex != -1 && nzIndex != -1) {
                normals[i * 3] = tokens.getOrNull(nxIndex)?.toFloatOrNull() ?: 0f
                normals[i * 3 + 1] = tokens.getOrNull(nyIndex)?.toFloatOrNull() ?: 0f
                normals[i * 3 + 2] = tokens.getOrNull(nzIndex)?.toFloatOrNull() ?: 0f
                hasNormals = true
            }

            if (uIndex != -1 && vIndex != -1) {
                uvs[i * 2] = tokens.getOrNull(uIndex)?.toFloatOrNull() ?: 0f
                uvs[i * 2 + 1] = tokens.getOrNull(vIndex)?.toFloatOrNull() ?: 0f
                hasUvs = true
            }
        }

        val indices = ArrayList<Int>(faceCount * 3)
        for (i in 0 until faceCount) {
            if (!lines.hasNext()) throw IllegalArgumentException("Unexpected EOF while reading faces")
            val tokens = lines.next().trim().split(Regex("\\s+"))
            val count = tokens.firstOrNull()?.toIntOrNull() ?: 0
            val verts = tokens.drop(1).mapNotNull { it.toIntOrNull() }
            if (verts.size < 3 || count < 3) continue
            for (j in 1 until count - 1) {
                indices.add(verts[0])
                indices.add(verts[j])
                indices.add(verts[j + 1])
            }
        }

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        if (hasNormals) {
            geometry.setAttribute("normal", BufferAttribute(normals, 3))
        }
        if (hasUvs) {
            geometry.setAttribute("uv", BufferAttribute(uvs, 2))
        }
        if (indices.isNotEmpty()) {
            val indexArray = FloatArray(indices.size) { idx -> indices[idx].toFloat() }
            geometry.setIndex(BufferAttribute(indexArray, 1))
        }

        val material = MeshStandardMaterial()
        val mesh = Mesh(geometry, material).apply {
            this.name = name.ifBlank { "PLYMesh" }
        }

        val scene = Scene().apply { add(mesh) }
        return ModelAsset(scene = scene, materials = listOf(material))
    }

    private fun deriveBasePath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else null
    }
}
