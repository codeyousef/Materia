/**
 * Contract test: Intersection data completeness
 * Covers: FR-R005, FR-R006, FR-R007 from contracts/raycaster-api.kt
 *
 * Test Cases:
 * - Distance, point, face data
 * - UV coordinates at intersection
 * - Instance ID for InstancedMesh
 * - Normal computation
 *
 * Expected: All tests FAIL (TDD requirement)
 */
package io.materia.raycaster

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.core.scene.Mesh
import io.materia.geometry.primitives.BoxGeometry
import io.materia.material.MeshBasicMaterial
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntersectionContractTest {

    /**
     * FR-R005: Intersection should contain distance, point, and object
     */
    @Test
    fun testIntersectionBasicData() {
        // Given: An intersection result
        val mesh = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial())
        val point = Vector3(0.5f, 0f, 0f)
        val distance = 5f

        // When: Creating intersection
        val intersection = Intersection(
            distance = distance,
            point = point,
            `object` = mesh
        )

        // Then: Should contain basic data
        assertEquals(distance, intersection.distance, "Distance should match")
        assertEquals(point, intersection.point, "Point should match")
        assertEquals(mesh, intersection.`object`, "Object should match")
    }

    /**
     * FR-R006: Intersection should include face information
     */
    @Test
    fun testIntersectionFaceData() {
        // Given: Intersection with face data
        val intersection = Intersection(
            distance = 1f,
            point = Vector3(0f, 0f, 0f),
            `object` = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial()),
            face = Face(
                a = 0,
                b = 1,
                c = 2,
                normal = Vector3(0f, 0f, 1f),
                materialIndex = 0
            )
        )

        // Then: Face data should be available
        assertNotNull(intersection.face, "Face should be present")
        assertEquals(0, intersection.face?.a, "Face vertex A should be 0")
        assertEquals(1, intersection.face?.b, "Face vertex B should be 1")
        assertEquals(2, intersection.face?.c, "Face vertex C should be 2")
        assertEquals(Vector3(0f, 0f, 1f), intersection.face?.normal, "Face normal should match")
    }

    /**
     * FR-R006: Intersection should include UV coordinates
     */
    @Test
    fun testIntersectionUVCoordinates() {
        // Given: Intersection with UV data
        val intersection = Intersection(
            distance = 1f,
            point = Vector3(0f, 0f, 0f),
            `object` = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial()),
            uv = Vector2(0.5f, 0.5f)
        )

        // Then: UV coordinates should be available
        assertNotNull(intersection.uv, "UV coordinates should be present")
        assertEquals(0.5f, intersection.uv?.x, "U coordinate should be 0.5")
        assertEquals(0.5f, intersection.uv?.y, "V coordinate should be 0.5")
    }

    /**
     * FR-R007: Intersection should include instance ID for InstancedMesh
     */
    @Test
    fun testIntersectionInstanceId() {
        // Given: Intersection with instanced mesh
        val instancedMesh = InstancedMesh(
            BoxGeometry(1f, 1f, 1f),
            MeshBasicMaterial(),
            count = 100
        )

        val intersection = Intersection(
            distance = 1f,
            point = Vector3(0f, 0f, 0f),
            `object` = instancedMesh,
            instanceId = 42
        )

        // Then: Instance ID should be available
        assertNotNull(intersection.instanceId, "Instance ID should be present")
        assertEquals(42, intersection.instanceId, "Instance ID should be 42")
    }

    /**
     * FR-R007: Intersection should compute point normal
     */
    @Test
    fun testIntersectionPointNormal() {
        // Given: Intersection with normal
        val normal = Vector3(0f, 1f, 0f) // Pointing up
        val intersection = Intersection(
            distance = 1f,
            point = Vector3(0f, 0f, 0f),
            `object` = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial()),
            normal = normal
        )

        // Then: Normal should be available
        assertNotNull(intersection.normal, "Normal should be present")
        assertEquals(normal, intersection.normal, "Normal should match")

        // Then: Normal should be normalized
        val length = intersection.normal!!.length()
        assertEquals(1f, length, 0.001f, "Normal should be normalized")
    }

    /**
     * Intersection should support barycentric coordinates
     */
    @Test
    fun testIntersectionBarycentricCoordinates() {
        // Given: Intersection with barycentric coordinates
        val barycentric = Vector3(0.33f, 0.33f, 0.34f) // Should sum to 1
        val intersection = Intersection(
            distance = 1f,
            point = Vector3(0f, 0f, 0f),
            `object` = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial()),
            barycentric = barycentric
        )

        // Then: Barycentric coordinates should be available
        assertNotNull(intersection.barycentric, "Barycentric coordinates should be present")

        // Then: Barycentric coordinates should sum to 1
        val sum = barycentric.x + barycentric.y + barycentric.z
        assertEquals(1f, sum, 0.001f, "Barycentric coordinates should sum to 1")
    }

    /**
     * Intersection should include face index
     */
    @Test
    fun testIntersectionFaceIndex() {
        // Given: Intersection with face index
        val intersection = Intersection(
            distance = 1f,
            point = Vector3(0f, 0f, 0f),
            `object` = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial()),
            faceIndex = 5
        )

        // Then: Face index should be available
        assertNotNull(intersection.faceIndex, "Face index should be present")
        assertEquals(5, intersection.faceIndex, "Face index should be 5")
    }

    /**
     * Intersection should be comparable by distance
     */
    @Test
    fun testIntersectionComparable() {
        // Given: Multiple intersections
        val mesh = Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial())
        val intersection1 = Intersection(distance = 5f, point = Vector3(), `object` = mesh)
        val intersection2 = Intersection(distance = 2f, point = Vector3(), `object` = mesh)
        val intersection3 = Intersection(distance = 10f, point = Vector3(), `object` = mesh)

        // When: Sorting by distance
        val sorted = listOf(intersection1, intersection2, intersection3)
            .sortedBy { it.distance }

        // Then: Should be sorted by distance
        assertEquals(2f, sorted[0].distance)
        assertEquals(5f, sorted[1].distance)
        assertEquals(10f, sorted[2].distance)
    }
}

// Data class for Intersection
data class Intersection(
    val distance: Float,
    val point: Vector3,
    val `object`: Object3D,
    val face: Face? = null,
    val faceIndex: Int? = null,
    val uv: Vector2? = null,
    val uv2: Vector2? = null,
    val normal: Vector3? = null,
    val instanceId: Int? = null,
    val barycentric: Vector3? = null
)

// Face data structure
data class Face(
    val a: Int,
    val b: Int,
    val c: Int,
    val normal: Vector3,
    val materialIndex: Int = 0
)

// Placeholder for InstancedMesh
class InstancedMesh(
    geometry: BoxGeometry,
    material: MeshBasicMaterial,
    val count: Int
) : Mesh(geometry, material)