package io.materia.loader

import io.materia.core.scene.Mesh
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DRACOLoaderTest {

    @Test
    fun `load draco json mesh`() = runTest {
        val json = """
            {
              "name": "Quad",
              "material": "DracoMaterial",
              "positions": [0,0,0, 1,0,0, 1,1,0, 0,1,0],
              "normals": [0,0,1, 0,0,1, 0,0,1, 0,0,1],
              "uvs": [0,0, 1,0, 1,1, 0,1],
              "indices": [0,1,2, 0,2,3]
            }
        """.trimIndent()

        val loader = DRACOLoader(InMemoryResolver(mapOf("quad.drc" to json.encodeToByteArray())))
        val asset = loader.load("quad.drc")
        val mesh = assertIs<Mesh>(asset.scene.children.first())
        assertEquals(6, mesh.geometry.index?.count)
    }

    private class InMemoryResolver(private val files: Map<String, ByteArray>) : AssetResolver {
        override suspend fun load(uri: String, basePath: String?): ByteArray {
            return files[uri] ?: error("Missing asset: $uri")
        }
    }
}
