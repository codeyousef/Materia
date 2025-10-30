/**
 * Contract test: ArrayCamera multi-view rendering
 * Covers: FR-C005, FR-C006 from contracts/camera-api.kt
 *
 * Test Cases:
 * - Multiple cameras with viewport regions
 * - Render scene once per camera
 * - Correct viewport bounds
 *
 * Expected: All tests FAIL (TDD requirement)
 */
package io.materia.camera

import io.materia.core.math.Vector4
import io.materia.core.scene.Scene
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArrayCameraContractTest {

    /**
     * FR-C005: ArrayCamera should support multiple cameras with viewports
     */
    @Test
    fun testArrayCameraSupportsMultipleCameras() {
        // Given: Array of cameras with viewports
        val cameras = listOf(
            PerspectiveCamera(50f, 1f, 0.1f, 100f),
            PerspectiveCamera(75f, 1f, 0.1f, 100f),
            OrthographicCamera(-10f, 10f, 10f, -10f, 0.1f, 100f)
        )

        // When: Creating array camera
        val arrayCamera = ArrayCamera(cameras.toTypedArray())

        // Then: Should contain all cameras
        assertEquals(3, arrayCamera.cameras.size, "ArrayCamera should contain 3 cameras")

        // Then: Each camera should be accessible
        cameras.forEachIndexed { index, camera ->
            assertEquals(
                camera, arrayCamera.cameras[index],
                "Camera at index $index should match"
            )
        }
    }

    /**
     * FR-C005: ArrayCamera should assign viewport regions to each camera
     */
    @Test
    fun testArrayCameraAssignsViewports() {
        // Given: Array camera with multiple views
        val arrayCamera = ArrayCamera()

        // When: Adding cameras with viewports
        arrayCamera.addCamera(
            PerspectiveCamera(50f, 1f, 0.1f, 100f),
            Vector4(0f, 0f, 0.5f, 1f) // Left half
        )
        arrayCamera.addCamera(
            PerspectiveCamera(50f, 1f, 0.1f, 100f),
            Vector4(0.5f, 0f, 0.5f, 1f) // Right half
        )

        // Then: Each camera should have a viewport
        val viewport1 = arrayCamera.getViewport(0)
        val viewport2 = arrayCamera.getViewport(1)

        assertNotNull(viewport1, "First camera should have viewport")
        assertNotNull(viewport2, "Second camera should have viewport")

        // Then: Viewports should not overlap
        assertEquals(0f, viewport1.x, "First viewport should start at x=0")
        assertEquals(0.5f, viewport1.z, "First viewport should have width=0.5")
        assertEquals(0.5f, viewport2.x, "Second viewport should start at x=0.5")
        assertEquals(0.5f, viewport2.z, "Second viewport should have width=0.5")
    }

    /**
     * FR-C006: ArrayCamera should support grid layouts
     */
    @Test
    fun testArrayCameraSupportsGridLayout() {
        // Given: 2x2 grid of cameras
        val arrayCamera = ArrayCamera()

        // When: Setting up 2x2 grid
        arrayCamera.setupGrid(2, 2)

        // Then: Should have 4 cameras
        assertEquals(4, arrayCamera.cameras.size, "2x2 grid should have 4 cameras")

        // Then: Viewports should form a grid
        val viewports = (0 until 4).map { arrayCamera.getViewport(it) }

        // Top-left
        assertEquals(0f, viewports[0]?.x)
        assertEquals(0.5f, viewports[0]?.y)

        // Top-right
        assertEquals(0.5f, viewports[1]?.x)
        assertEquals(0.5f, viewports[1]?.y)

        // Bottom-left
        assertEquals(0f, viewports[2]?.x)
        assertEquals(0f, viewports[2]?.y)

        // Bottom-right
        assertEquals(0.5f, viewports[3]?.x)
        assertEquals(0f, viewports[3]?.y)
    }

    /**
     * FR-C006: ArrayCamera should render each viewport
     */
    @Test
    fun testArrayCameraRendersEachViewport() {
        // Given: Array camera with multiple views
        val arrayCamera = ArrayCamera()
        arrayCamera.addCamera(PerspectiveCamera(), Vector4(0f, 0f, 0.5f, 1f))
        arrayCamera.addCamera(OrthographicCamera(), Vector4(0.5f, 0f, 0.5f, 1f))

        // Given: Scene to render
        val scene = Scene()
        val renderer = MockRenderer()

        // When: Rendering through array camera
        arrayCamera.render(renderer, scene)

        // Then: Should have rendered each camera
        assertEquals(
            2, renderer.renderCalls.size,
            "Should render once per camera"
        )
    }

    /**
     * ArrayCamera should support different aspect ratios per viewport
     */
    @Test
    fun testArrayCameraSupportsPerViewportAspect() {
        // Given: Array camera
        val arrayCamera = ArrayCamera()

        // When: Adding cameras with different aspects
        val cam1 = PerspectiveCamera(50f, 16f / 9f, 0.1f, 100f)
        val cam2 = PerspectiveCamera(50f, 4f / 3f, 0.1f, 100f)

        arrayCamera.addCamera(cam1, Vector4(0f, 0f, 0.5f, 1f))
        arrayCamera.addCamera(cam2, Vector4(0.5f, 0f, 0.5f, 1f))

        // Then: Each camera should maintain its aspect ratio
        assertEquals(16f / 9f, (arrayCamera.cameras[0] as PerspectiveCamera).aspect)
        assertEquals(4f / 3f, (arrayCamera.cameras[1] as PerspectiveCamera).aspect)
    }

    /**
     * ArrayCamera should support enabling/disabling individual cameras
     */
    @Test
    fun testArrayCameraEnableDisable() {
        // Given: Array camera with multiple views
        val arrayCamera = ArrayCamera()
        arrayCamera.setupGrid(1, 2) // Two cameras side by side

        // When: Disabling first camera
        arrayCamera.setEnabled(0, false)

        // Then: First camera should be disabled
        assertEquals(false, arrayCamera.isEnabled(0))
        assertEquals(true, arrayCamera.isEnabled(1))

        // When: Rendering
        val renderer = MockRenderer()
        arrayCamera.render(renderer, Scene())

        // Then: Should only render enabled camera
        assertEquals(1, renderer.renderCalls.size, "Should only render enabled cameras")
    }

    /**
     * ArrayCamera should handle viewport pixel coordinates
     */
    @Test
    fun testArrayCameraPixelCoordinates() {
        // Given: Array camera with known viewport size
        val arrayCamera = ArrayCamera()
        val screenWidth = 1920f
        val screenHeight = 1080f

        arrayCamera.setSize(screenWidth, screenHeight)

        // When: Adding camera with normalized viewport
        arrayCamera.addCamera(
            PerspectiveCamera(),
            Vector4(0.25f, 0.25f, 0.5f, 0.5f) // Center quarter
        )

        // Then: Should calculate pixel coordinates
        val pixelViewport = arrayCamera.getPixelViewport(0)
        assertNotNull(pixelViewport)

        assertEquals(480f, pixelViewport.x, 1f) // 0.25 * 1920
        assertEquals(270f, pixelViewport.y, 1f) // 0.25 * 1080
        assertEquals(960f, pixelViewport.z, 1f) // 0.5 * 1920
        assertEquals(540f, pixelViewport.w, 1f) // 0.5 * 1080
    }
}

// Extension classes for ArrayCamera testing
class ArrayCamera(
    initialCameras: Array<Camera> = emptyArray()
) : Camera() {
    private val _cameras = mutableListOf<Camera>()
    private val viewports = mutableListOf<Vector4>()
    private val enabled = mutableListOf<Boolean>()
    private var screenWidth = 1f
    private var screenHeight = 1f

    val cameras: Array<Camera>
        get() = _cameras.toTypedArray()

    init {
        initialCameras.forEach { camera ->
            _cameras.add(camera)
            enabled.add(true)
        }
    }

    // Implement abstract Camera methods
    override fun updateProjectionMatrix() {
        // Update all sub-cameras
        _cameras.forEach { camera ->
            camera.updateProjectionMatrix()
        }
    }

    override fun setViewOffset(
        fullWidth: Int,
        fullHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        // Not used for array camera
    }

    override fun clearViewOffset() {
        // Not used for array camera
    }

    fun addCamera(camera: Camera, viewport: Vector4) {
        _cameras.add(camera)
        viewports.add(viewport)
        enabled.add(true)
    }

    fun getViewport(index: Int): Vector4? = viewports.getOrNull(index)

    fun setupGrid(rows: Int, cols: Int) {
        _cameras.clear()
        viewports.clear()
        enabled.clear()

        val cellWidth = 1f / cols
        val cellHeight = 1f / rows

        // Create cameras row by row, from top to bottom
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val camera = PerspectiveCamera()
                val x = col * cellWidth
                // Y coordinate: top row starts at (1 - cellHeight), bottom row at 0
                val y = (rows - 1 - row) * cellHeight
                val viewport = Vector4(x, y, cellWidth, cellHeight)

                _cameras.add(camera)
                viewports.add(viewport)
                enabled.add(true)
            }
        }
    }

    fun render(renderer: MockRenderer, scene: Scene) {
        _cameras.forEachIndexed { index, camera ->
            if (isEnabled(index)) {
                val viewport = viewports.getOrNull(index)
                if (viewport != null) {
                    renderer.setViewport(viewport.x, viewport.y, viewport.z, viewport.w)
                }
                renderer.render(scene, camera)
            }
        }
    }

    fun setEnabled(index: Int, isEnabled: Boolean) {
        if (index in enabled.indices) {
            enabled[index] = isEnabled
        }
    }

    fun isEnabled(index: Int): Boolean = enabled.getOrNull(index) ?: false

    fun setSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    fun getPixelViewport(index: Int): Vector4? {
        val viewport = viewports.getOrNull(index) ?: return null
        return Vector4(
            viewport.x * screenWidth,
            viewport.y * screenHeight,
            viewport.z * screenWidth,
            viewport.w * screenHeight
        )
    }
}

// Enhanced mock renderer for array camera testing
class MockRenderer {
    val renderCalls = mutableListOf<Pair<Scene, Camera>>()

    fun setViewport(x: Float, y: Float, width: Float, height: Float) {}

    fun render(scene: Scene, camera: Camera) {
        renderCalls.add(scene to camera)
    }
}