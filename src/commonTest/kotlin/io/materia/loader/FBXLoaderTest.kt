package io.materia.loader

import io.kreekt.core.scene.Mesh
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FBXLoaderTest {

    @Test
    fun `load minimal ASCII fbx`() = runTest {
        val content = """
            ; FBX 7.4.0 project file
            Objects:  {
                Geometry: 1, "Geometry::TestMesh", "Mesh" {
                    Vertices: *9 {0,0,0, 1,0,0, 0,1,0}
                    PolygonVertexIndex: *3 {0, 1, -3}
                    LayerElementNormal: 0 {
                        Normals: *9 {0,0,1, 0,0,1, 0,0,1}
                    }
                    LayerElementUV: 0 {
                        UV: *6 {0,0, 1,0, 0,1}
                    }
                }
            }
            Model: "Model::TestMesh", "Mesh" {
            }
        """.trimIndent()

        val loader = FBXLoader(InMemoryResolver(mapOf("mesh.fbx" to content.encodeToByteArray())))
        val asset = loader.load("mesh.fbx")
        val mesh = assertIs<Mesh>(asset.scene.children.first())
        assertEquals("TestMesh", mesh.name)
        val positions = mesh.geometry.getAttribute("position")
        assertEquals(3, positions?.count)
    }

    private class InMemoryResolver(private val files: Map<String, ByteArray>) : AssetResolver {
        override suspend fun load(uri: String, basePath: String?): ByteArray {
            return files[uri] ?: error("Missing asset: $uri")
        }
    }
}
