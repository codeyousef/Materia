package io.materia.loader

import io.kreekt.core.scene.Mesh
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ColladaLoaderTest {

    @Test
    fun `load simple collada mesh`() = runTest {
        val collada = """
            <COLLADA>
              <library_geometries>
                <geometry id="Geom">
                  <mesh>
                    <source id="Geom-positions">
                      <float_array id="Geom-positions-array" count="9">0 0 0  1 0 0  0 1 0</float_array>
                      <technique_common>
                        <accessor source="#Geom-positions-array" count="3" stride="3" />
                      </technique_common>
                    </source>
                    <vertices id="Geom-vertices">
                      <input semantic="POSITION" source="#Geom-positions"/>
                    </vertices>
                    <triangles material="mat" count="1">
                      <input semantic="VERTEX" source="#Geom-vertices" offset="0"/>
                      <p>0 1 2</p>
                    </triangles>
                  </mesh>
                </geometry>
              </library_geometries>
            </COLLADA>
        """.trimIndent()

        val loader = ColladaLoader(InMemoryResolver(mapOf("mesh.dae" to collada.encodeToByteArray())))
        val asset = loader.load("mesh.dae")
        val mesh = assertIs<Mesh>(asset.scene.children.first())
        assertEquals(3, mesh.geometry.getAttribute("position")?.count)
    }

    private class InMemoryResolver(private val files: Map<String, ByteArray>) : AssetResolver {
        override suspend fun load(uri: String, basePath: String?): ByteArray {
            return files[uri] ?: error("Missing asset: $uri")
        }
    }
}
