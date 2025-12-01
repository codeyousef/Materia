package integration

import io.materia.core.Scene
import io.materia.core.Mesh
import io.materia.geometry.BoxGeometry
import io.materia.materials.MeshStandardMaterial
import io.materia.cameras.PerspectiveCamera
import io.materia.lights.DirectionalLight
import io.materia.lights.AmbientLight
import io.materia.animation.AnimationMixer
import io.materia.animation.AnimationClip
import io.materia.animation.tracks.NumberKeyframeTrack
import io.materia.math.Vector3
import io.materia.math.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Three.js API compatibility test
 * T024 - Validates Three.js-compatible API patterns
 */
class ThreeJsCompatibilityTest {

    @Test
    fun testThreeJsSceneCreation() {
        // Three.js-style scene creation
        val scene = Scene()
        val geometry = BoxGeometry(1f, 1f, 1f)
        val material = MeshStandardMaterial().apply {
            color = Color(0x00ff00)
            metalness = 0.5f
            roughness = 0.5f
        }
        val mesh = Mesh(geometry, material)
        scene.add(mesh)

        // Verify Three.js-compatible API
        assertEquals(1, scene.children.size, "Scene should contain mesh")
        assertNotNull(mesh.geometry, "Mesh should have geometry")
        assertNotNull(mesh.material, "Mesh should have material")
    }

    @Test
    fun testThreeJsCamera() {
        // Three.js-style camera setup
        val camera = PerspectiveCamera(
            fov = 75f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000f
        )

        camera.position.set(5f, 5f, 5f)
        camera.lookAt(Vector3(0f, 0f, 0f))

        // Verify camera properties
        assertEquals(75f, camera.fov, "FOV should match")
        assertEquals(0.1f, camera.near, "Near plane should match")
        assertEquals(1000f, camera.far, "Far plane should match")
        assertNotNull(camera.projectionMatrix, "Should have projection matrix")
    }

    @Test
    fun testThreeJsLighting() {
        // Three.js-style lighting setup
        val scene = Scene()

        val directionalLight = DirectionalLight(Color(0xffffff), 1.0f)
        directionalLight.position.set(5f, 10f, 7.5f)
        directionalLight.castShadow = true
        scene.add(directionalLight)

        val ambientLight = AmbientLight(Color(0x404040), 0.5f)
        scene.add(ambientLight)

        // Verify lights added to scene
        assertEquals(2, scene.children.size, "Scene should have two lights")
        assertTrue(directionalLight.castShadow, "Directional light should cast shadows")
    }

    @Test
    fun testThreeJsAnimation() {
        // Three.js-style animation setup
        val mesh = Mesh(BoxGeometry(1f, 1f, 1f), MeshStandardMaterial())
        val mixer = AnimationMixer(mesh)

        // Create rotation animation clip
        val times = floatArrayOf(0f, 1f, 2f)
        val values = floatArrayOf(0f, 3.14159f, 6.28318f)
        val track = NumberKeyframeTrack(".rotation[y]", times, values)
        val clip = AnimationClip("rotate", 2.0, listOf(track))

        // Play animation
        val action = mixer.clipAction(clip)
        action.play()

        // Verify animation state
        assertTrue(action.isRunning, "Animation should be running")
        assertNotNull(mixer.getRoot(), "Mixer should have root object")
    }
}