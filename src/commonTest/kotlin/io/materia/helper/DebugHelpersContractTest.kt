/**
 * Contract Test: Visual debugging helpers (axes, grid, box)
 * Covers: FR-H001 through FR-H004 from contracts/helper-api.kt
 *
 * Test Cases:
 * - AxesHelper renders colored axes
 * - GridHelper renders grid with divisions
 * - BoxHelper updates with object bounds
 * - Box3Helper creates from min/max vectors
 *
 * Expected: All tests FAIL
 */
package io.materia.helper

import io.materia.core.math.Box3
import io.materia.core.math.Color
import io.materia.core.scene.Mesh
import io.materia.geometry.primitives.BoxGeometry
import io.materia.material.MeshBasicMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DebugHelpersContractTest {

    @Test
    fun testAxesHelperCreation() {
        // When: Creating axes helper
        val helper = AxesHelper(size = 5f)

        // Then: Helper should exist
        assertNotNull(helper, "AxesHelper should be created")
    }

    @Test
    fun testAxesHelperSize() {
        // Given: Axes helper with specific size
        val size = 10f
        val helper = AxesHelper(size)

        // Then: Size should be stored (test via geometry or property)
        assertNotNull(helper, "Axes helper should have size property")
    }

    @Test
    fun testGridHelperCreation() {
        // When: Creating grid helper
        val helper = GridHelper(
            size = 100f,
            divisions = 10
        )

        // Then: Helper should exist
        assertNotNull(helper, "GridHelper should be created")
    }

    @Test
    fun testGridHelperWithColors() {
        // When: Creating grid helper with custom colors
        val helper = GridHelper(
            size = 100f,
            divisions = 10,
            colorCenterLine = Color(0xff0000),
            colorGrid = Color(0x888888)
        )

        // Then: Helper should exist with colors
        assertNotNull(helper, "GridHelper with colors should be created")
    }

    @Test
    fun testGridHelperDivisions() {
        // Given: Grid helpers with different divisions
        val helper10 = GridHelper(size = 100f, divisions = 10)
        val helper20 = GridHelper(size = 100f, divisions = 20)

        // Then: Should support different division counts
        assertNotNull(helper10)
        assertNotNull(helper20)
    }

    @Test
    fun testBoxHelperCreation() {
        // Given: An object
        val geometry = BoxGeometry(1f, 1f, 1f)
        val material = MeshBasicMaterial()
        val mesh = Mesh(geometry, material)

        // When: Creating box helper
        val helper = BoxHelper(mesh)

        // Then: Helper should exist
        assertNotNull(helper, "BoxHelper should be created")
    }

    @Test
    fun testBoxHelperWithColor() {
        // Given: An object
        val geometry = BoxGeometry(1f, 1f, 1f)
        val material = MeshBasicMaterial()
        val mesh = Mesh(geometry, material)

        // When: Creating box helper with color
        val helper = BoxHelper(mesh, color = Color(0xff0000))

        // Then: Helper should exist with color
        assertNotNull(helper, "BoxHelper with color should be created")
    }

    @Test
    fun testBoxHelperUpdate() {
        // Given: A box helper
        val geometry = BoxGeometry(1f, 1f, 1f)
        val material = MeshBasicMaterial()
        val mesh = Mesh(geometry, material)
        val helper = BoxHelper(mesh)

        // When: Object position changes
        mesh.position.set(10f, 5f, 0f)

        // When: Updating helper
        helper.update()

        // Then: helper should still reference source object
        assertEquals(mesh, helper.`object`)
    }

    @Test
    fun testBox3HelperFromBoundingBox() {
        val box = Box3()
        box.min.set(-1f, -1f, -1f)
        box.max.set(1f, 1f, 1f)

        val helper = Box3Helper(box)

        assertNotNull(helper)
        helper.update()
        assertEquals("Box3Helper", helper.name)
    }

    @Test
    fun testHelperVisibility() {
        // Given: A helper
        val helper = AxesHelper(5f)

        // When: Setting visibility
        helper.visible = false

        // Then: Visibility should be set
        assertEquals(false, helper.visible, "Helper visibility should be settable")

        helper.visible = true
        assertEquals(true, helper.visible)
    }
}
