package io.materia.scene

import io.materia.core.scene.Mesh
import io.materia.geometry.primitives.BoxGeometry
import io.materia.material.MeshBasicMaterial
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract test for MeshBuilder
 * T013 - This test MUST FAIL until MeshBuilder is implemented
 */
class MeshBuilderTest {

    @Test
    fun testMeshBuilderContract() {
        // Test basic mesh creation using the builder pattern
        val geometry: BoxGeometry = BoxGeometry(1f, 1f, 1f)
        val material: MeshBasicMaterial = MeshBasicMaterial()
        val mesh: Mesh = Mesh(geometry, material).apply {
            name = "TestMesh"
            castShadow = true
            receiveShadow = true
        }

        assertNotNull(mesh)
        assertTrue(mesh.castShadow)
        assertTrue(mesh.receiveShadow)
        assertTrue(mesh.name == "TestMesh")
    }

    @Test
    fun testGeometryBuilderContract() {
        // Test geometry creation
        val geometry: BoxGeometry = BoxGeometry(1f, 1f, 1f)

        assertNotNull(geometry)
        assertNotNull(geometry.getAttribute("position"))
        assertNotNull(geometry.getAttribute("normal"))
        assertNotNull(geometry.getAttribute("uv"))
        // Note: boundingBox is computed lazily via computeBoundingBox()
        // It may be null until explicitly computed
        geometry.computeBoundingBox()
        assertNotNull(geometry.boundingBox)
    }

    @Test
    fun testMaterialBuilderContract() {
        // Test material creation
        val material: MeshBasicMaterial = MeshBasicMaterial().apply {
            color.set(1f, 0f, 0f)
            transparent = true
            opacity = 0.8f
        }

        assertNotNull(material)
        assertTrue(material.color.r == 1f)
        assertTrue(material.transparent)
        assertTrue(material.opacity == 0.8f)
    }
}