package io.kreekt.renderer.webgl

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.renderer.webgpu.WebGPURendererFactory
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue

/**
 * Contract Tests: WebGLRenderer Geometry Rendering (Contracts 2 & 3)
 *
 * Validates that the renderer correctly handles both indexed and non-indexed geometry.
 *
 * From: specs/017-in-voxelcraft-example/contracts/renderer-contract.md
 */
class WebGLRendererGeometryTest {

    @Test
    fun testIndexedGeometryRendering() = runTest {
        // Setup
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600

        val renderer = WebGPURendererFactory.create(canvas)
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)
        camera.position.set(0f, 5f, 10f)
        camera.updateMatrixWorld(true)
        camera.updateProjectionMatrix()

        // Create indexed geometry (cube with 8 vertices, 12 triangles)
        val positions = floatArrayOf(
            // Front face
            -1f, -1f, 1f,  // 0
            1f, -1f, 1f,   // 1
            1f, 1f, 1f,    // 2
            -1f, 1f, 1f,   // 3
            // Back face
            -1f, -1f, -1f, // 4
            1f, -1f, -1f,  // 5
            1f, 1f, -1f,   // 6
            -1f, 1f, -1f   // 7
        )

        val indices = floatArrayOf(
            // Front
            0f, 1f, 2f,  0f, 2f, 3f,
            // Right
            1f, 5f, 6f,  1f, 6f, 2f,
            // Back
            5f, 4f, 7f,  5f, 7f, 6f,
            // Left
            4f, 0f, 3f,  4f, 3f, 7f,
            // Top
            3f, 2f, 6f,  3f, 6f, 7f,
            // Bottom
            4f, 5f, 1f,  4f, 1f, 0f
        )

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        geometry.setIndex(BufferAttribute(indices, 1))

        val material = MeshBasicMaterial()
        val mesh = Mesh(geometry, material)
        mesh.name = "indexed_cube"
        scene.add(mesh)

        // Render and validate no errors
        var renderSuccessful = false
        try {
            renderer.render(scene, camera)
            renderSuccessful = true
        } catch (e: Exception) {
            console.error("Indexed geometry rendering failed: ${e.message}")
        }

        assertTrue(renderSuccessful, "Indexed geometry should render without errors")

        val stats = renderer.stats
        assertTrue(stats.triangles == 12, "Expected 12 triangles from indexed cube, got ${stats.triangles}")

        console.log("✅ Indexed geometry test passed: 12 triangles rendered")
    }

    @Test
    fun testNonIndexedGeometryRendering() = runTest {
        // Setup
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600

        val renderer = WebGPURendererFactory.create(canvas)
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)
        camera.position.set(0f, 5f, 10f)
        camera.updateMatrixWorld(true)
        camera.updateProjectionMatrix()

        // Create non-indexed geometry (2 triangles = quad)
        val positions = floatArrayOf(
            // Triangle 1
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            // Triangle 2
            -1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f
        )

        val geometry = BufferGeometry()
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        // No indices - non-indexed rendering

        val material = MeshBasicMaterial()
        val mesh = Mesh(geometry, material)
        mesh.name = "non_indexed_quad"
        scene.add(mesh)

        // Render and validate no errors
        var renderSuccessful = false
        try {
            renderer.render(scene, camera)
            renderSuccessful = true
        } catch (e: Exception) {
            console.error("Non-indexed geometry rendering failed: ${e.message}")
        }

        assertTrue(renderSuccessful, "Non-indexed geometry should render without errors")

        val stats = renderer.stats
        assertTrue(stats.triangles == 2, "Expected 2 triangles from non-indexed quad, got ${stats.triangles}")

        console.log("✅ Non-indexed geometry test passed: 2 triangles rendered")
    }

    @Test
    fun testEmptySceneRendering() = runTest {
        // Setup
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600

        val renderer = WebGPURendererFactory.create(canvas)
        val scene = Scene()  // Empty scene
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)
        camera.updateMatrixWorld(true)
        camera.updateProjectionMatrix()

        // Render empty scene - should not crash
        var renderSuccessful = false
        try {
            renderer.render(scene, camera)
            renderSuccessful = true
        } catch (e: Exception) {
            console.error("Empty scene rendering failed: ${e.message}")
        }

        assertTrue(renderSuccessful, "Empty scene should render without errors")

        val stats = renderer.stats
        assertTrue(stats.triangles == 0, "Expected 0 triangles from empty scene, got ${stats.triangles}")

        console.log("✅ Empty scene test passed: 0 triangles rendered")
    }
}
