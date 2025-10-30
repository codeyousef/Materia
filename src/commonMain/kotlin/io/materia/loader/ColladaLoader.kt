package io.materia.loader

import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshStandardMaterial

/**
 * Lightweight COLLADA (.dae) loader that supports meshes exported by Blender
 * and similar tools. The implementation focuses on triangle meshes using the
 * common `<triangles>` element with POSITION/NORMAL/TEXCOORD inputs.
 */
class ColladaLoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<ModelAsset> {

    override suspend fun load(path: String): ModelAsset {
        val basePath = path.substringBeforeLast('/', "")
        val bytes = resolver.load(path, if (basePath.isEmpty()) null else "$basePath/")
        val text = bytes.decodeToString()
        val parser = ColladaMeshParser(text)
        val meshData = parser.parse()

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(meshData.positions, 3))
        meshData.normals?.let { geometry.setAttribute("normal", BufferAttribute(it, 3)) }
        meshData.uvs?.let { geometry.setAttribute("uv", BufferAttribute(it, 2)) }
        geometry.setIndex(BufferAttribute(meshData.indices.map(Int::toFloat).toFloatArray(), 1))

        val material = MeshStandardMaterial(name = meshData.materialName)
        val mesh = Mesh(geometry, material).apply { name = meshData.name }
        val scene = Scene().apply { add(mesh) }
        return ModelAsset(scene = scene, materials = listOf(material))
    }

    private class ColladaMeshParser(private val xml: String) {
        private val floatArrayRegex =
            Regex("""<float_array[^>]*id="([^"]+)"[^>]*>(.*?)</float_array>""", RegexOption.DOT_MATCHES_ALL)
        private val accessorRegex =
            Regex("""<accessor[^>]*source="#([^"]+)"[^>]*stride="(\d+)"[^>]*/?>""")
        private val verticesRegex =
            Regex("""<vertices[^>]*id="([^"]+)"[^>]*>(.*?)</vertices>""", RegexOption.DOT_MATCHES_ALL)
        private val sourceRegex =
            Regex("""<source[^>]*id="([^"]+)"[^>]*>(.*?)</source>""", RegexOption.DOT_MATCHES_ALL)
        private val nestedFloatArrayRegex =
            Regex("""<float_array[^>]*id="([^"]+)"""")

        fun parse(): ParsedMesh {
            val floatArrays = parseFloatArrays()
            val strides = parseStrides()
            val sourceMap = parseSourceMap()
            val verticesMap = parseVertices()

            val trianglesMatch = Regex(
                """<triangles[^>]*material="?([^" ]*)"?[^>]*>(.*?)</triangles>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(xml)
                ?: throw IllegalArgumentException("COLLADA file missing <triangles> element")
            val trianglesBody = trianglesMatch.groupValues[2]
            val materialName = trianglesMatch.groupValues[1].ifBlank { "ColladaMaterial" }

            val inputs = Regex(
                """<input[^>]*semantic="([^"]+)"[^>]*source="#([^"]+)"[^>]*offset="(\d+)"[^>]*/?>"""
            )
                .findAll(trianglesBody)
                .map {
                    InputEntry(semantic = it.groupValues[1], source = it.groupValues[2], offset = it.groupValues[3].toInt())
                }
                .toList()

            val stride = inputs.maxOfOrNull { it.offset }?.plus(1) ?: 1
            val indexData = Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(trianglesBody)
                ?.groupValues?.get(1)
                ?: throw IllegalArgumentException("COLLADA triangles missing <p> element")
            val rawIndices = indexData.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }.map { it.toInt() }

            val positionsSource = resolveSource(inputs, "VERTEX", verticesMap)
            val normalsSource = resolveSource(inputs, "NORMAL", emptyMap())
            val uvSource = resolveSource(inputs, "TEXCOORD", emptyMap())

            val positionArrayId = positionsSource?.let { sourceMap[it] ?: it }
                ?: throw IllegalArgumentException("COLLADA missing VERTEX input")
            val positionsArray = floatArrays[positionArrayId]
                ?: throw IllegalArgumentException("COLLADA missing position array: $positionArrayId")
            val positionStride = strides[positionArrayId] ?: 3

            val normalsArrayId = normalsSource?.let { sourceMap[it] ?: it }
            val normalsArray = normalsArrayId?.let { floatArrays[it] }
            val normalStride = normalsArrayId?.let { strides[it] ?: 3 } ?: 3

            val uvArrayId = uvSource?.let { sourceMap[it] ?: it }
            val uvArray = uvArrayId?.let { floatArrays[it] }
            val uvStride = uvArrayId?.let { strides[it] ?: 2 } ?: 2

            val vertexCount = if (stride > 0) rawIndices.size / stride else rawIndices.size
            val positions = FloatArray(vertexCount * 3)
            val normals = normalsArray?.let { FloatArray(vertexCount * 3) }
            val uvs = uvArray?.let { FloatArray(vertexCount * 2) }
            val indices = IntArray(vertexCount) { it }

            for (vertex in 0 until vertexCount) {
                val base = vertex * stride
                val posIndex = rawIndices[base + (inputs.find { it.semantic == "VERTEX" }?.offset ?: 0)]
                val posBase = posIndex * positionStride
                val dstPos = vertex * 3
                positions[dstPos] = positionsArray[posBase]
                positions[dstPos + 1] = positionsArray[posBase + 1]
                positions[dstPos + 2] = positionsArray[posBase + 2]

                normals?.let {
                    val normalOffset = inputs.find { it.semantic == "NORMAL" }?.offset
                    if (normalOffset != null && normalsArray != null) {
                        val normalIndex = rawIndices[base + normalOffset]
                        val normalBase = normalIndex * normalStride
                        val dstNormal = vertex * 3
                        it[dstNormal] = normalsArray[normalBase]
                        it[dstNormal + 1] = normalsArray[normalBase + 1]
                        it[dstNormal + 2] = normalsArray[normalBase + 2]
                    }
                }

                uvs?.let {
                    val uvOffset = inputs.find { it.semantic == "TEXCOORD" }?.offset
                    if (uvOffset != null && uvArray != null) {
                        val uvIndex = rawIndices[base + uvOffset]
                        val uvBase = uvIndex * uvStride
                        val dstUv = vertex * 2
                        it[dstUv] = uvArray[uvBase]
                        it[dstUv + 1] = 1f - uvArray[uvBase + 1]
                    }
                }
            }

            return ParsedMesh(
                name = guessMeshName(),
                materialName = materialName,
                positions = positions,
                normals = normals,
                uvs = uvs,
                indices = indices
            )
        }

        private fun parseFloatArrays(): Map<String, FloatArray> = buildMap {
            for (match in floatArrayRegex.findAll(xml)) {
                val id = match.groupValues[1]
                val data = match.groupValues[2]
                val floats = data.split(Regex("""[\s,]+"""))
                    .filter { it.isNotBlank() }
                    .map { it.toFloat() }
                    .toFloatArray()
                put(id, floats)
            }
        }

        private fun parseStrides(): Map<String, Int> = buildMap {
            for (match in accessorRegex.findAll(xml)) {
                val source = match.groupValues[1]
                val stride = match.groupValues[2].toInt()
                put(source, stride)
            }
        }

        private fun parseVertices(): Map<String, String> = buildMap {
            for (match in verticesRegex.findAll(xml)) {
                val verticesId = match.groupValues[1]
                val body = match.groupValues[2]
                val inputMatch = Regex("""<input[^>]*semantic="POSITION"[^>]*source="#([^"]+)"[^>]*/?>""")
                    .find(body)
                if (inputMatch != null) {
                    put(verticesId, inputMatch.groupValues[1])
                }
            }
        }

        private fun parseSourceMap(): Map<String, String> = buildMap {
            for (match in sourceRegex.findAll(xml)) {
                val sourceId = match.groupValues[1]
                val body = match.groupValues[2]
                val floatMatch = nestedFloatArrayRegex.find(body)
                if (floatMatch != null) {
                    put(sourceId, floatMatch.groupValues[1])
                }
            }
        }

        private fun resolveSource(inputs: List<InputEntry>, semantic: String, vertices: Map<String, String>): String? {
            val entry = inputs.find { it.semantic == semantic } ?: return null
            val source = entry.source
            return vertices[source] ?: source
        }

        private fun guessMeshName(): String {
            val match = Regex("""<geometry[^>]*id="([^"]+)"""").find(xml)
            return match?.groupValues?.get(1) ?: "ColladaMesh"
        }

        data class InputEntry(val semantic: String, val source: String, val offset: Int)

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
