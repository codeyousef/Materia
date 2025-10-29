package io.kreekt.loader

import io.kreekt.core.scene.Mesh
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class OBJLoaderTest {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `load minimal OBJ`() = runTest {
        val objData = """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
        """.trimIndent()

        val objUri = "data:application/octet-stream;base64,${Base64.encode(objData.encodeToByteArray())}"
        val asset = OBJLoader().load(objUri)
        val mesh = assertIs<Mesh>(asset.scene.children.first())
        val positions = mesh.geometry.getAttribute("position")
        assertEquals(3, positions?.count)
    }
}
