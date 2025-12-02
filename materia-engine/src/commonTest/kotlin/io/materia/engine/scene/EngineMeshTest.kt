package io.materia.engine.scene

import io.materia.core.math.Color
import io.materia.engine.material.BasicMaterial
import io.materia.engine.material.StandardMaterial
import io.materia.engine.material.Side
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for EngineMesh and related scene graph components.
 */
class EngineMeshTest {

    /**
     * Creates a simple triangle geometry for testing.
     */
    private fun createTestGeometry(): BufferGeometry {
        val geometry = BufferGeometry()
        
        // Triangle positions
        val positions = floatArrayOf(
            0f, 1f, 0f,   // top
            -1f, -1f, 0f, // bottom-left
            1f, -1f, 0f   // bottom-right
        )
        
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        return geometry
    }

    @Test
    fun engineMesh_canBeCreatedWithGeometry() {
        val geometry = createTestGeometry()
        val mesh = EngineMesh(geometry)
        
        assertTrue(mesh.visible)
        assertFalse(mesh.isDisposed)
    }

    @Test
    fun engineMesh_canAttachMaterial() {
        val geometry = createTestGeometry()
        val material = BasicMaterial(
            name = "test-material",
            color = Color(1f, 0f, 0f)
        )
        
        val mesh = EngineMesh(geometry, material)
        
        assertNotNull(mesh.engineMaterial)
        assertEquals("test-material", mesh.engineMaterial?.name)
    }

    @Test
    fun engineMesh_canChangeMaterial() {
        val geometry = createTestGeometry()
        val material1 = BasicMaterial(name = "material1")
        val material2 = StandardMaterial(name = "material2")
        
        val mesh = EngineMesh(geometry, material1)
        assertEquals("material1", mesh.engineMaterial?.name)
        
        mesh.engineMaterial = material2
        assertEquals("material2", mesh.engineMaterial?.name)
    }

    @Test
    fun engineMesh_canDetachMaterial() {
        val geometry = createTestGeometry()
        val material = BasicMaterial(name = "test-material")
        val mesh = EngineMesh(geometry, material)
        
        assertNotNull(mesh.engineMaterial)
        
        mesh.engineMaterial = null
        assertNull(mesh.engineMaterial)
    }

    @Test
    fun engineMesh_transformUpdatesPropagateToModelMatrix() {
        val geometry = createTestGeometry()
        val mesh = EngineMesh(geometry)
        
        // Set position
        mesh.position.set(1f, 2f, 3f)
        mesh.updateMatrix()
        
        // The model matrix should reflect the position
        val pos = mesh.matrix.getPosition()
        assertEquals(1f, pos.x, 0.001f)
        assertEquals(2f, pos.y, 0.001f)
        assertEquals(3f, pos.z, 0.001f)
    }

    @Test
    fun engineMesh_scaleAffectsModelMatrix() {
        val geometry = createTestGeometry()
        val mesh = EngineMesh(geometry)
        
        mesh.scale.set(2f, 2f, 2f)
        mesh.updateMatrix()
        
        val scale = mesh.matrix.getScale()
        assertEquals(2f, scale.x, 0.001f)
        assertEquals(2f, scale.y, 0.001f)
        assertEquals(2f, scale.z, 0.001f)
    }

    @Test
    fun engineMesh_disposeMarksAsDisposed() {
        val geometry = createTestGeometry()
        val mesh = EngineMesh(geometry)
        assertFalse(mesh.isDisposed)
        
        mesh.dispose()
        
        assertTrue(mesh.isDisposed)
    }

    @Test
    fun engineMesh_disposeIsIdempotent() {
        val geometry = createTestGeometry()
        val mesh = EngineMesh(geometry)
        
        mesh.dispose()
        mesh.dispose()
        mesh.dispose()
        
        assertTrue(mesh.isDisposed)
    }

    @Test
    fun engineMesh_childrenInheritTransforms() {
        val geometry = createTestGeometry()
        val parent = EngineMesh(geometry)
        val child = EngineMesh(geometry)
        
        parent.add(child)
        parent.position.set(10f, 0f, 0f)
        child.position.set(5f, 0f, 0f)
        
        parent.updateMatrixWorld(true)
        
        // Child world position should be parent + child local
        val childWorldPos = child.getWorldPosition()
        assertEquals(15f, childWorldPos.x, 0.001f)
    }

    @Test
    fun engineMesh_visibilityAffectsRendering() {
        val geometry = createTestGeometry()
        val parent = EngineMesh(geometry)
        val child = EngineMesh(geometry)
        
        parent.add(child)
        
        // Initially both visible
        assertTrue(parent.visible)
        assertTrue(child.visible)
        
        // Setting parent invisible doesn't change child's visible flag
        // but affects rendering (checked at render time)
        parent.visible = false
        assertFalse(parent.visible)
        assertTrue(child.visible) // Child still has visible=true locally
    }

    @Test
    fun engineMesh_canHaveMultipleChildren() {
        val geometry = createTestGeometry()
        val parent = EngineMesh(geometry)
        val child1 = EngineMesh(geometry)
        val child2 = EngineMesh(geometry)
        val child3 = EngineMesh(geometry)
        
        parent.add(child1)
        parent.add(child2)
        parent.add(child3)
        
        assertEquals(3, parent.children.size)
        assertTrue(parent.children.contains(child1))
        assertTrue(parent.children.contains(child2))
        assertTrue(parent.children.contains(child3))
    }

    @Test
    fun engineMesh_removeChildWorks() {
        val geometry = createTestGeometry()
        val parent = EngineMesh(geometry)
        val child = EngineMesh(geometry)
        
        parent.add(child)
        assertEquals(1, parent.children.size)
        
        parent.remove(child)
        assertEquals(0, parent.children.size)
        assertNull(child.parent)
    }

    @Test
    fun engineMesh_vertexCountReflectsGeometry() {
        val geometry = createTestGeometry()
        val mesh = EngineMesh(geometry)
        
        assertEquals(3, mesh.vertexCount)
    }

    @Test
    fun engineMesh_indexedGeometry() {
        val geometry = BufferGeometry()
        
        // Quad positions (4 vertices)
        val positions = floatArrayOf(
            -1f, 1f, 0f,  // top-left
            1f, 1f, 0f,   // top-right
            -1f, -1f, 0f, // bottom-left
            1f, -1f, 0f   // bottom-right
        )
        
        // 6 indices for 2 triangles
        val indices = floatArrayOf(0f, 2f, 1f, 1f, 2f, 3f)
        
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        geometry.setIndex(BufferAttribute(indices, 1))
        
        val mesh = EngineMesh(geometry)
        
        assertEquals(4, mesh.vertexCount)
        assertEquals(6, mesh.indexCount)
        assertTrue(mesh.isIndexed)
    }

    @Test
    fun engineMesh_disposeAlsoDisposeMaterial() {
        val geometry = createTestGeometry()
        val material = BasicMaterial(name = "test-material")
        val mesh = EngineMesh(geometry, material)
        
        assertFalse(material.isDisposed)
        
        mesh.dispose()
        
        assertTrue(mesh.isDisposed)
        assertTrue(material.isDisposed)
    }
}
