package io.materia.core.scene

import kotlin.test.Test
import kotlin.test.assertEquals

class Object3DMatrixWorldTest {
    @Test
    fun forcedMatrixWorldUpdateReflectsDirectPositionMutation() {
        val scene = Scene()
        val node = Group()
        scene.add(node)

        scene.updateMatrixWorld(force = true)
        node.position.set(3f, 4f, 5f)

        scene.updateMatrixWorld(force = true)

        val worldPosition = node.matrixWorld.getPosition()
        assertEquals(3f, worldPosition.x)
        assertEquals(4f, worldPosition.y)
        assertEquals(5f, worldPosition.z)
    }

    @Test
    fun forcedMatrixWorldUpdateReflectsDirectScaleMutation() {
        val scene = Scene()
        val node = Group()
        scene.add(node)

        scene.updateMatrixWorld(force = true)
        node.scale.set(2f, 3f, 4f)

        scene.updateMatrixWorld(force = true)

        val worldScale = node.matrixWorld.getScale()
        assertEquals(2f, worldScale.x)
        assertEquals(3f, worldScale.y)
        assertEquals(4f, worldScale.z)
    }
}
