package io.materia.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals

private const val EPSILON = 1e-4f

class SceneTests {
    @Test
    fun worldTransformPropagatesToChildren() {
        val scene = Scene("Root")
        val parent = Node("Parent").apply {
            transform.setPosition(1f, 2f, 3f)
        }
        val child = Node("Child").apply {
            transform.setPosition(4f, 5f, 6f)
        }

        parent.add(child)
        scene.add(parent)

        scene.update(0f)

        val world = child.positionWorld()
        assertEquals(5f, world.x, EPSILON)
        assertEquals(7f, world.y, EPSILON)
        assertEquals(9f, world.z, EPSILON)
    }

    @Test
    fun removingChildClearsParentReference() {
        val parent = Node("Parent")
        val child = Node("Child")

        parent.add(child)
        parent.remove(child)

        assertEquals(null, child.parent)
    }
}
