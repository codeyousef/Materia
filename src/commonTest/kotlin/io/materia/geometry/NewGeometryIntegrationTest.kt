package io.materia.geometry

import io.materia.core.math.Vector3
import io.materia.curve.LineCurve3
import io.materia.geometry.primitives.BoxGeometry
import io.materia.geometry.primitives.SphereGeometry
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests for newly implemented geometry classes
 * Validates that all implementations work correctly
 */
class NewGeometryIntegrationTest {

    @Test
    fun testConeGeometry() {
        val cone = ConeGeometry(radius = 2f, height = 3f, radialSegments = 16)

        val position = cone.getAttribute("position")
        assertNotNull(position, "ConeGeometry should have position attribute")
        assertTrue(position.count > 0, "ConeGeometry should have vertices")

        val normal = cone.getAttribute("normal")
        assertNotNull(normal, "ConeGeometry should have normal attribute")

        val uv = cone.getAttribute("uv")
        assertNotNull(uv, "ConeGeometry should have UV attribute")
    }

    @Test
    fun testCircleGeometry() {
        val circle = CircleGeometry(radius = 5f, segments = 32)

        val position = circle.getAttribute("position")
        assertNotNull(position, "CircleGeometry should have position attribute")
        assertTrue(
            position.count >= 34,
            "CircleGeometry should have center + circumference vertices"
        )

        // Check that center vertex exists at origin
        assertEquals(0f, position.getX(0), 0.001f, "Center should be at origin")
        assertEquals(0f, position.getY(0), 0.001f, "Center should be at origin")
    }

    @Test
    fun testTetrahedronGeometry() {
        val tetra = TetrahedronGeometry(radius = 1f, detail = 0)

        val position = tetra.getAttribute("position")
        assertNotNull(position, "TetrahedronGeometry should have position attribute")
        assertTrue(position.count >= 4, "Tetrahedron should have at least 4 vertices")
    }

    @Test
    fun testOctahedronGeometry() {
        val octa = OctahedronGeometry(radius = 1f, detail = 0)

        val position = octa.getAttribute("position")
        assertNotNull(position, "OctahedronGeometry should have position attribute")
        assertTrue(position.count >= 6, "Octahedron should have at least 6 vertices")
    }

    @Test
    fun testIcosahedronGeometry() {
        val icosa = IcosahedronGeometry(radius = 1f, detail = 0)

        val position = icosa.getAttribute("position")
        assertNotNull(position, "IcosahedronGeometry should have position attribute")
        assertTrue(position.count >= 12, "Icosahedron should have at least 12 vertices")
    }

    @Test
    fun testDodecahedronGeometry() {
        val dodeca = DodecahedronGeometry(radius = 1f, detail = 0)

        val position = dodeca.getAttribute("position")
        assertNotNull(position, "DodecahedronGeometry should have position attribute")
        assertTrue(position.count >= 20, "Dodecahedron should have at least 20 vertices")
    }

    @Test
    fun testTorusKnotGeometry() {
        val knot = TorusKnotGeometry(radius = 1f, tube = 0.4f, p = 2, q = 3)

        val position = knot.getAttribute("position")
        assertNotNull(position, "TorusKnotGeometry should have position attribute")
        assertTrue(position.count > 0, "TorusKnotGeometry should have vertices")

        val normal = knot.getAttribute("normal")
        assertNotNull(normal, "TorusKnotGeometry should have normals")
    }

    @Test
    fun testCapsuleGeometry() {
        val capsule = CapsuleGeometry(radius = 1f, length = 2f, capSegments = 4, radialSegments = 8)

        val position = capsule.getAttribute("position")
        assertNotNull(position, "CapsuleGeometry should have position attribute")
        assertTrue(position.count > 0, "CapsuleGeometry should have vertices")
    }

    @Test
    fun testLatheGeometry() {
        val points = listOf(
            Vector3(0f, -1f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(0f, 1f, 0f)
        )
        val lathe = LatheGeometry(points, segments = 16)

        val position = lathe.getAttribute("position")
        assertNotNull(position, "LatheGeometry should have position attribute")
        assertTrue(position.count > 0, "LatheGeometry should have vertices")
    }

    @Test
    fun testTubeGeometry() {
        val path = LineCurve3(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 1f, 1f)
        )
        val tube = TubeGeometry(path, tubularSegments = 32, radius = 0.5f, radialSegments = 8)

        val position = tube.getAttribute("position")
        assertNotNull(position, "TubeGeometry should have position attribute")
        assertTrue(position.count > 0, "TubeGeometry should have vertices")
    }

    @Test
    fun testEdgesGeometry() {
        val box = BoxGeometry(1f, 1f, 1f)
        val edges = EdgesGeometry(box, thresholdAngle = 1f)

        val position = edges.getAttribute("position")
        assertNotNull(position, "EdgesGeometry should have position attribute")
        assertTrue(position.count > 0, "EdgesGeometry should have edge vertices")
    }

    @Test
    fun testWireframeGeometry() {
        val sphere = SphereGeometry(radius = 1f, widthSegments = 8, heightSegments = 6)
        val wireframe = WireframeGeometry(sphere)

        val position = wireframe.getAttribute("position")
        assertNotNull(position, "WireframeGeometry should have position attribute")
        assertTrue(position.count > 0, "WireframeGeometry should have edge vertices")
    }

    @Test
    fun testParametricGeometry() {
        val parametric = ParametricGeometry({ u, v, target ->
            target.x = u * 10f - 5f
            target.y = kotlin.math.sin(u * kotlin.math.PI.toFloat() * 4f)
            target.z = v * 10f - 5f
            target
        }, slices = 20, stacks = 20)

        val position = parametric.getAttribute("position")
        assertNotNull(position, "ParametricGeometry should have position attribute")
        assertTrue(position.count > 0, "ParametricGeometry should have vertices")

        val normal = parametric.getAttribute("normal")
        assertNotNull(normal, "ParametricGeometry should have normals")
    }

    @Test
    fun testPolyhedronSubdivision() {
        val icosa0 = IcosahedronGeometry(radius = 1f, detail = 0)
        val icosa1 = IcosahedronGeometry(radius = 1f, detail = 1)

        val count0 = icosa0.getAttribute("position")!!.count
        val count1 = icosa1.getAttribute("position")!!.count

        assertTrue(count1 > count0, "Subdivided geometry should have more vertices")
    }

    @Test
    fun testGeometryBoundingSphere() {
        val sphere = SphereGeometry(radius = 5f)
        val boundingSphere = sphere.computeBoundingSphere()

        assertNotNull(boundingSphere, "Geometry should compute bounding sphere")
        assertTrue(boundingSphere.radius >= 4.5f, "Bounding sphere should contain geometry")
    }
}
