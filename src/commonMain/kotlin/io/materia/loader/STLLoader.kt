package io.materia.loader

import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshStandardMaterial

/**
 * Loader for STL meshes. Supports both binary and ASCII encodings.
 */
class STLLoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<ModelAsset> {

    override suspend fun load(path: String): ModelAsset {
        val bytes = resolver.load(path, deriveBasePath(path))
        val geometry = if (isBinary(bytes)) {
            parseBinary(bytes)
        } else {
            parseAscii(bytes.decodeToString())
        }

        val material = MeshStandardMaterial()
        val mesh = Mesh(geometry, material).apply {
            name = path.substringAfterLast('/').ifBlank { "STLMesh" }
        }
        val scene = Scene().apply { add(mesh) }
        return ModelAsset(scene = scene, materials = listOf(material))
    }

    private fun parseBinary(data: ByteArray): BufferGeometry {
        if (data.size < 84) throw IllegalArgumentException("Invalid binary STL")
        val triangleCount = data.readUInt32(80)
        val expectedSize = 84 + triangleCount * 50
        require(data.size >= expectedSize) { "Binary STL truncated" }

        val positions = FloatArray(triangleCount * 9)
        val normals = FloatArray(triangleCount * 9)

        var offset = 84
        for (i in 0 until triangleCount) {
            val nx = data.readFloat32(offset); offset += 4
            val ny = data.readFloat32(offset); offset += 4
            val nz = data.readFloat32(offset); offset += 4

            for (vertex in 0 until 3) {
                val px = data.readFloat32(offset); offset += 4
                val py = data.readFloat32(offset); offset += 4
                val pz = data.readFloat32(offset); offset += 4

                val base = i * 9 + vertex * 3
                positions[base] = px
                positions[base + 1] = py
                positions[base + 2] = pz

                normals[base] = nx
                normals[base + 1] = ny
                normals[base + 2] = nz
            }

            offset += 2 // attribute byte count
        }

        return buildGeometry(positions, normals)
    }

    private fun parseAscii(content: String): BufferGeometry {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        var currentNormal = floatArrayOf(0f, 0f, 1f)

        content.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("facet normal") -> {
                    val tokens = line.split(Regex("\\s+"))
                    if (tokens.size >= 5) {
                        currentNormal = floatArrayOf(
                            tokens[2].toFloatOrNull() ?: 0f,
                            tokens[3].toFloatOrNull() ?: 0f,
                            tokens[4].toFloatOrNull() ?: 0f
                        )
                    }
                }
                line.startsWith("vertex") -> {
                    val tokens = line.split(Regex("\\s+"))
                    if (tokens.size >= 4) {
                        positions.add(tokens[1].toFloatOrNull() ?: 0f)
                        positions.add(tokens[2].toFloatOrNull() ?: 0f)
                        positions.add(tokens[3].toFloatOrNull() ?: 0f)
                        normals.add(currentNormal[0])
                        normals.add(currentNormal[1])
                        normals.add(currentNormal[2])
                    }
                }
            }
        }

        if (positions.isEmpty()) {
            throw IllegalArgumentException("ASCII STL contains no vertices")
        }

        val positionArray = positions.toFloatArray()
        val normalArray = normals.toFloatArray()
        return buildGeometry(positionArray, normalArray)
    }

    private fun buildGeometry(positions: FloatArray, normals: FloatArray): BufferGeometry {
        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        if (normals.isNotEmpty()) {
            geometry.setAttribute("normal", BufferAttribute(normals, 3))
        }
        return geometry
    }

    private fun isBinary(data: ByteArray): Boolean {
        if (data.size < 84) return false
        val triangleCount = data.readUInt32(80)
        val expectedSize = 84 + triangleCount * 50
        return expectedSize == data.size
    }

    private fun ByteArray.readUInt32(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArray.readFloat32(offset: Int): Float {
        val bits = (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }

    private fun deriveBasePath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else null
    }
}
