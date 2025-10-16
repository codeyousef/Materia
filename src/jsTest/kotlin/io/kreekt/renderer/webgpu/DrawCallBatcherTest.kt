package io.kreekt.renderer.webgpu

import io.kreekt.core.scene.Mesh
import io.kreekt.geometry.BufferGeometry
import io.kreekt.geometry.BufferAttribute
import io.kreekt.material.MeshBasicMaterial
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
        material.blending = io.kreekt.material.Blending.AdditiveBlending

        val modifiedKey = DrawCallBatcher.BatchKey.fromMesh(mesh)

        assertNotEquals(
            initialKey.renderState,
            modifiedKey.renderState,
            "Render state should change when material blending configuration changes"
        )
    }
}
