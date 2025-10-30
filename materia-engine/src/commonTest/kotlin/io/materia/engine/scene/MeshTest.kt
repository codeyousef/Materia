package io.materia.engine.scene

import io.materia.engine.geometry.vertexCount

import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.math.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshTest {

    @Test
    fun fromInterleavedBuildsGeometryWithExpectedStride() {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val colors = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
        val mesh = Mesh.fromInterleaved(
            name = "triangle",
            positions = positions,
            colors = colors,
            indices = shortArrayOf(0, 1, 2),
            material = UnlitColorMaterial(label = "triangle", color = Color.White)
        )

        assertEquals(positions.size / 3, mesh.geometry.vertexCount())
        assertEquals(Float.SIZE_BYTES * 6, mesh.geometry.layout.stride)
        assertTrue(mesh.geometry.indexBuffer != null)
    }
}
