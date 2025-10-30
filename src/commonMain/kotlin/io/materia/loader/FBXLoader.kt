package io.materia.loader

import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.MeshStandardMaterial

/**
 * Minimal ASCII FBX loader supporting static meshes (positions, normals, UVs).
 * The parser focuses on the subset produced by common exporters (Autodesk FBX
 * SDK, Blender) for triangle geometry.
 */
class FBXLoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<ModelAsset> {

    override suspend fun load(path: String): ModelAsset {
        val basePath = path.substringBeforeLast('/', "")
        val bytes = resolver.load(path, if (basePath.isEmpty()) null else "$basePath/")
        val text = bytes.decodeToString()
        val mesh = FbxAsciiMeshParser(text).parse()

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(mesh.positions, 3))
        mesh.normals?.let { geometry.setAttribute("normal", BufferAttribute(it, 3)) }
        mesh.uvs?.let { geometry.setAttribute("uv", BufferAttribute(it, 2)) }
        if (mesh.indices.isNotEmpty()) {
            val indexArray = FloatArray(mesh.indices.size) { mesh.indices[it].toFloat() }
            geometry.setIndex(BufferAttribute(indexArray, 1))
        }

        val material = MeshStandardMaterial(name = mesh.materialName)
        val meshNode = Mesh(geometry, material).apply { this.name = mesh.name }
        val scene = Scene().apply { add(meshNode) }

        return ModelAsset(scene = scene, materials = listOf(material))
    }

    private class FbxAsciiMeshParser(private val content: String) {

        fun parse(): ParsedMesh {
            val vertices = readFloatArray("Vertices")
                ?: throw IllegalArgumentException("FBX file missing Vertices array")
            val polygonIndices = readIntArray("PolygonVertexIndex")
                ?: throw IllegalArgumentException("FBX file missing PolygonVertexIndex array")
            val normalsSource = readLayerFloatArray("LayerElementNormal", "Normals")
            val uvsSource = readLayerFloatArray("LayerElementUV", "UV")

            val triangles = triangulate(polygonIndices)
            val finalPositions = FloatArray(triangles.size * 3)
            val finalNormals = normalsSource?.let { FloatArray(triangles.size * 3) }
            val finalUVs = uvsSource?.let { FloatArray(triangles.size * 2) }

            for (i in triangles.indices) {
                val vertexIndex = triangles[i]
                val posBase = vertexIndex * 3
                val dstPosBase = i * 3
                finalPositions[dstPosBase] = vertices[posBase]
                finalPositions[dstPosBase + 1] = vertices[posBase + 1]
                finalPositions[dstPosBase + 2] = vertices[posBase + 2]

                finalNormals?.let { normalsTarget ->
                    normalsSource?.let { normals ->
                        val normalBase = vertexIndex * 3
                        if (normalBase + 2 < normals.size) {
                            normalsTarget[dstPosBase] = normals[normalBase]
                            normalsTarget[dstPosBase + 1] = normals[normalBase + 1]
                            normalsTarget[dstPosBase + 2] = normals[normalBase + 2]
                        }
                    }
                }

                finalUVs?.let { uvsTarget ->
                    uvsSource?.let { uvs ->
                        val uvBase = vertexIndex * 2
                        if (uvBase + 1 < uvs.size) {
                            val dstUvBase = i * 2
                            uvsTarget[dstUvBase] = uvs[uvBase]
                            uvsTarget[dstUvBase + 1] = 1f - uvs[uvBase + 1] // FBX V axis is flipped
                        }
                    }
                }
            }

            val indices = IntArray(triangles.size) { it }
            return ParsedMesh(
                name = guessMeshName(),
                materialName = "FBXMaterial",
                positions = finalPositions,
                normals = finalNormals,
                uvs = finalUVs,
                indices = indices
            )
        }

        private fun triangulate(indices: IntArray): IntArray {
            val triangles = mutableListOf<Int>()
            val polygon = mutableListOf<Int>()

            for (raw in indices) {
                val endOfPolygon = raw < 0
                val vertexIndex = if (endOfPolygon) -raw - 1 else raw
                polygon.add(vertexIndex)

                if (endOfPolygon) {
                    if (polygon.size >= 3) {
                        val base = polygon[0]
                        for (i in 1 until polygon.size - 1) {
                            triangles.add(base)
                            triangles.add(polygon[i])
                            triangles.add(polygon[i + 1])
                        }
                    }
                    polygon.clear()
                }
            }

            return triangles.toIntArray()
        }

        private fun readFloatArray(name: String): FloatArray? {
            val regex = Regex("""(?s)$name:\s*\*\d+\s*\{(.*?)\}""")
            val match = regex.find(content) ?: return null
            return match.groupValues[1]
                .split(',', '\n', '\r', '\t', ' ')
                .filter { it.isNotBlank() }
                .map { it.toFloat() }
                .toFloatArray()
        }

        private fun readIntArray(name: String): IntArray? {
            val regex = Regex("""(?s)$name:\s*\*\d+\s*\{(.*?)\}""")
            val match = regex.find(content) ?: return null
            return match.groupValues[1]
                .split(',', '\n', '\r', '\t', ' ')
                .filter { it.isNotBlank() }
                .map { it.toInt() }
                .toIntArray()
        }

        private fun readLayerFloatArray(layerName: String, arrayName: String): FloatArray? {
            val layerRegex = Regex("""(?s)$layerName:[^{]+\{(.*?)\}""")
            val layerMatch = layerRegex.find(content) ?: return null
            val body = layerMatch.groupValues[1]
            val arrayRegex = Regex("""(?s)$arrayName:\s*\*\d+\s*\{(.*?)\}""")
            val arrayMatch = arrayRegex.find(body) ?: return null
            return arrayMatch.groupValues[1]
                .split(',', '\n', '\r', '\t', ' ')
                .filter { it.isNotBlank() }
                .map { it.toFloat() }
                .toFloatArray()
        }

        private fun guessMeshName(): String {
            val regex = Regex("Model: \"Model::([^\"]+)\"")
            return regex.find(content)?.groupValues?.getOrNull(1) ?: "FBXMesh"
        }

        data class ParsedMesh(
            val name: String,
            val materialName: String,
            val positions: FloatArray,
            val normals: FloatArray?,
            val uvs: FloatArray?,
            val indices: IntArray
        )
    }
}
