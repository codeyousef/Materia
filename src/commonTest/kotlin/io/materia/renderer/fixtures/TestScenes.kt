/**
 * T034: Test Scene Fixtures
 * Feature: 019-we-should-not
 *
 * Deterministic test scenes for visual regression testing.
 */

package io.materia.renderer.fixtures

import io.materia.geometry.primitives.BoxGeometry
import io.materia.geometry.primitives.SphereGeometry
import io.materia.core.scene.Scene
import io.materia.core.scene.Mesh
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.material.MeshBasicMaterial

/**
 * Test scene configurations for visual regression testing.
 *
 * All scenes are deterministic (same seed, fixed camera position)
 * to ensure consistent screenshots across test runs.
 */
object TestScenes {

    /**
     * Scene 1: Simple Cube (1,000 triangles)
     *
     * Single colored cube at origin.
     * Tests: Basic geometry rendering, transform matrices.
     */
    fun createSimpleCube(): SceneFixture {
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000.0f
        )

        // Position camera
        camera.position.set(0f, 0f, 5f)
        camera.lookAt(Vector3(0f, 0f, 0f))

        // Create red cube (2x2x2)
        val geometry = BoxGeometry(2f, 2f, 2f)
        val material = MeshBasicMaterial().apply {
            color = Color(0xFF0000) // Red
        }
        val cube = Mesh(geometry, material)
        scene.add(cube)

        return SceneFixture(
            name = "simple-cube",
            scene = scene,
            camera = camera,
            expectedTriangles = 12, // Cube has 12 triangles (6 faces × 2 triangles)
            description = "Single red cube at origin"
        )
    }

    /**
     * Scene 2: Complex Mesh (10,000 triangles)
     *
     * Multiple spheres with different colors and positions.
     * Tests: Higher triangle count, multiple draw calls, color variations.
     */
    fun createComplexMesh(): SceneFixture {
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000.0f
        )

        // Position camera
        camera.position.set(10f, 10f, 10f)
        camera.lookAt(Vector3(0f, 0f, 0f))

        // Create grid of spheres (5×5 = 25 spheres)
        val colors = listOf(
            0xFF0000, // Red
            0x00FF00, // Green
            0x0000FF, // Blue
            0xFFFF00, // Yellow
            0xFF00FF  // Magenta
        )

        var triangleCount = 0
        for (x in -2..2) {
            for (z in -2..2) {
                val geometry = SphereGeometry(
                    radius = 0.5f,
                    widthSegments = 16,
                    heightSegments = 16
                )
                triangleCount += 16 * 16 * 2 // Approximate triangle count

                val material = MeshBasicMaterial().apply {
                    color = Color(colors[(x + 2 + z + 2) % colors.size])
                }

                val sphere = Mesh(geometry, material)
                sphere.position.set(x.toFloat() * 2f, 0f, z.toFloat() * 2f)
                scene.add(sphere)
            }
        }

        return SceneFixture(
            name = "complex-mesh",
            scene = scene,
            camera = camera,
            expectedTriangles = triangleCount,
            description = "25 colored spheres in 5×5 grid (~10k triangles)"
        )
    }

    /**
     * Scene 3: Lighting Test (directional + point lights)
     *
     * Cube with multiple light sources.
     * Tests: Lighting calculations, shader uniforms.
     * Note: Lighting implementation deferred to Phase 2-13.
     */
    fun createLightingTest(): SceneFixture {
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000.0f
        )

        // Position camera
        camera.position.set(5f, 5f, 5f)
        camera.lookAt(Vector3(0f, 0f, 0f))

        // Create white cube
        val geometry = BoxGeometry(2f, 2f, 2f)
        val material = MeshBasicMaterial().apply {
            color = Color(0xFFFFFF) // White (will show lighting)
        }
        val cube = Mesh(geometry, material)
        scene.add(cube)

        // Lighting is intentionally omitted until the lighting system lands in tests
        // scene.add(DirectionalLight(color = 0xFFFFFF, intensity = 1.0f))
        // scene.add(PointLight(color = 0xFF0000, intensity = 0.5f, position = Vector3(2f, 2f, 2f)))

        return SceneFixture(
            name = "lighting-test",
            scene = scene,
            camera = camera,
            expectedTriangles = 12,
            description = "White cube with lighting (lights pending Phase 2-13)"
        )
    }

    /**
     * Scene 4: Transparency Test (alpha blending)
     *
     * Overlapping transparent planes.
     * Tests: Alpha blending, depth sorting.
     * Note: Transparency implementation deferred to Phase 2-13.
     */
    fun createTransparencyTest(): SceneFixture {
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000.0f
        )

        // Position camera
        camera.position.set(0f, 0f, 5f)
        camera.lookAt(Vector3(0f, 0f, 0f))

        // Create three overlapping planes with different colors and transparency
        val colors = listOf(0xFF0000, 0x00FF00, 0x0000FF)
        val positions = listOf(
            Vector3(-1f, 0f, 0f),
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f)
        )

        colors.forEachIndexed { index, color ->
            val geometry = BoxGeometry(2f, 2f, 0.1f)
            val material = MeshBasicMaterial().apply {
                this.color = Color(color)
                // Transparency is disabled until blending support is validated
                // transparent = true
                // opacity = 0.5f
            }
            val plane = Mesh(geometry, material)
            plane.position.set(positions[index])
            scene.add(plane)
        }

        return SceneFixture(
            name = "transparency-test",
            scene = scene,
            camera = camera,
            expectedTriangles = 12 * 3, // 3 planes
            description = "Three overlapping planes (transparency pending Phase 2-13)"
        )
    }

    /**
     * Scene 5: VoxelCraft Terrain (16×16×256 chunk)
     *
     * Representative VoxelCraft terrain chunk.
     * Tests: Real-world performance, large triangle count.
     * Note: Simplified version for testing.
     */
    fun createVoxelTerrainChunk(): SceneFixture {
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000.0f
        )

        // Position camera above terrain
        camera.position.set(8f, 80f, 8f)
        camera.lookAt(Vector3(8f, 64f, 8f))

        // Create simplified terrain (8×8×8 voxel grid for testing)
        // Each voxel is a 1×1×1 cube
        val voxelSize = 1f
        var triangleCount = 0

        for (x in 0..7) {
            for (y in 0..7) {
                for (z in 0..7) {
                    // Simple height-based terrain
                    if (y <= 4 + (x + z) % 3) {
                        val geometry = BoxGeometry(voxelSize, voxelSize, voxelSize)

                        // Color based on height (grass, dirt, stone)
                        val color = when {
                            y > 4 -> 0x00FF00 // Grass (green)
                            y > 2 -> 0x8B4513 // Dirt (brown)
                            else -> 0x808080  // Stone (gray)
                        }

                        val material = MeshBasicMaterial().apply {
                            this.color = Color(color)
                        }

                        val voxel = Mesh(geometry, material)
                        voxel.position.set(
                            x.toFloat() * voxelSize,
                            y.toFloat() * voxelSize,
                            z.toFloat() * voxelSize
                        )
                        scene.add(voxel)
                        triangleCount += 12
                    }
                }
            }
        }

        return SceneFixture(
            name = "voxel-terrain",
            scene = scene,
            camera = camera,
            expectedTriangles = triangleCount,
            description = "8×8×8 voxel terrain (~3k triangles)"
        )
    }

    /**
     * Get all test scenes.
     *
     * @return List of all test scene fixtures
     */
    fun getAllScenes(): List<SceneFixture> = listOf(
        createSimpleCube(),
        createComplexMesh(),
        createLightingTest(),
        createTransparencyTest(),
        createVoxelTerrainChunk()
    )
}

/**
 * Test scene fixture containing scene, camera, and metadata.
 *
 * @property name Unique scene identifier (used for filenames)
 * @property scene Scene to render
 * @property camera Camera for rendering
 * @property expectedTriangles Expected triangle count
 * @property description Human-readable scene description
 */
data class SceneFixture(
    val name: String,
    val scene: Scene,
    val camera: PerspectiveCamera,
    val expectedTriangles: Int,
    val description: String
)
