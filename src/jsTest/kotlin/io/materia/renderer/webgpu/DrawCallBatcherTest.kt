package io.materia.renderer.webgpu

import io.materia.core.scene.Mesh
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute
import io.materia.material.MeshBasicMaterial
import kotlin.test.Test
import kotlin.test.assertNotEquals

class DrawCallBatcherTest {
    @Test
    fun renderStateReflectsMaterialChanges() {
        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f, 0f,
                        1f, 0f, 0f,
                        0f, 1f, 0f
                    ),
                    3
                )
            )
        }

        val material = MeshBasicMaterial()
        val mesh = Mesh(geometry, material)

        val initialKey = DrawCallBatcher.BatchKey.fromMesh(mesh)

        material.depthWrite = false
        material.transparent = true
        material.blending = io.materia.material.Blending.AdditiveBlending

        val modifiedKey = DrawCallBatcher.BatchKey.fromMesh(mesh)

        assertNotEquals(
            initialKey.renderState,
            modifiedKey.renderState,
            "Render state should change when material blending configuration changes"
        )
    }
}
