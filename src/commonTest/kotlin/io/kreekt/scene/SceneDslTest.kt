package io.kreekt.scene

import io.kreekt.core.scene.Group
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.primitives.BoxGeometry
import io.kreekt.material.MeshBasicMaterial
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for Scene DSL and hierarchy
 */
class SceneDslTest {

    @Test
    fun testSceneBuilderContract() {
        // Test basic scene construction without DSL (DSL is a future enhancement)
        val scene: Scene = Scene()
        val geometry: BoxGeometry = BoxGeometry(1f, 1f, 1f)
        val material: MeshBasicMaterial = MeshBasicMaterial()
        val mesh: Mesh = Mesh(geometry, material)
        scene.add(mesh)

        assertEquals(1, scene.children.size)
        assertTrue(scene.children.first() is Mesh)
    }

    @Test
    fun testSceneHierarchyContract() {
        // Test Object3D hierarchy
        val scene: Scene = Scene()
        // Use Group which is a concrete implementation of Object3D
        val group: Group = Group()
        val geometry: BoxGeometry = BoxGeometry(1f, 1f, 1f)
        val material: MeshBasicMaterial = MeshBasicMaterial()
        val mesh: Mesh = Mesh(geometry, material)

        group.add(mesh)
        scene.add(group)

        assertEquals(1, scene.children.size)
        assertEquals(1, group.children.size)
        assertTrue(group.children.first() is Mesh)
    }

    @Test
    fun testSceneTraversalContract() {
        // Test scene traversal
        val scene: Scene = Scene()
        // Use Group which is a concrete implementation of Object3D
        val group: Group = Group()
        val geometry1: BoxGeometry = BoxGeometry(1f, 1f, 1f)
        val material1: MeshBasicMaterial = MeshBasicMaterial()
        val mesh1: Mesh = Mesh(geometry1, material1)

        val geometry2: BoxGeometry = BoxGeometry(2f, 2f, 2f)
        val material2: MeshBasicMaterial = MeshBasicMaterial()
        val mesh2: Mesh = Mesh(geometry2, material2)

        group.add(mesh1)
        group.add(mesh2)
        scene.add(group)

        var count = 0
        scene.traverse { object3d ->
            count++
        }

        // Should count scene, group, mesh1, and mesh2
        assertEquals(4, count)
    }
}