package integration

import io.materia.core.Scene
import io.materia.core.Mesh
import io.materia.geometry.BoxGeometry
import io.materia.materials.MeshStandardMaterial
import io.materia.cameras.PerspectiveCamera
import io.materia.math.Vector3
import io.materia.math.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Basic spinning cube scenario test
 * T023 - Validates basic scene functionality
 */
class BasicSceneTest {

    @Test
    fun testBasicSpinningCubeScenario() {
        // Create a basic scene with a spinning cube
        val scene = Scene()
        val geometry = BoxGeometry(1f, 1f, 1f)
        val material = MeshStandardMaterial().apply {
            color = Color(0x00ff00)
        }
        val cube = Mesh(geometry, material)
        scene.add(cube)

        // Verify scene setup
        assertEquals(1, scene.children.size, "Scene should have one child")
        assertNotNull(cube.geometry, "Cube should have geometry")
        assertNotNull(cube.material, "Cube should have material")

        // Verify geometry properties
        assertTrue(geometry.getAttribute("position") != null, "Geometry should have position attribute")
    }

    @Test
    fun testSceneHierarchy() {
        // Create scene with hierarchy
        val scene = Scene()
        val parentMesh = Mesh(BoxGeometry(1f, 1f, 1f), MeshStandardMaterial())
        val childMesh = Mesh(BoxGeometry(0.5f, 0.5f, 0.5f), MeshStandardMaterial())

        parentMesh.add(childMesh)
        scene.add(parentMesh)

        // Verify hierarchy
        assertEquals(1, scene.children.size, "Scene should have one direct child")
        assertEquals(1, parentMesh.children.size, "Parent should have one child")
        assertEquals(parentMesh, childMesh.parent, "Child's parent should be parentMesh")
    }

    @Test
    fun testBasicRenderingSetup() {
        // Create rendering components
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000f
        )

        camera.position.set(0f, 0f, 5f)
        camera.lookAt(Vector3(0f, 0f, 0f))

        // Verify camera setup
        assertEquals(75f, camera.fov, "Camera FOV should be 75")
        assertEquals(5f, camera.position.z, "Camera should be at z=5")
        assertNotNull(camera.projectionMatrix, "Camera should have projection matrix")
    }
}