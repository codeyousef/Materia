/**
 * Contract tests for GeometryGenerator interface
 * These tests define the required behavior before implementation
 */
package io.materia.geometry

import io.materia.core.math.Vector2
import kotlin.test.*

class GeometryGeneratorTest {

    private lateinit var generator: GeometryGenerator

    @BeforeTest
    fun setup() {
        generator = MockGeometryGenerator()
    }

    @Test
    fun testCreateBox() {
        val geometry = generator.createBox(2f, 4f, 6f, intArrayOf(2, 3, 4))

        assertNotNull(geometry, "Box geometry should not be null")
        assertTrue("Box should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Box should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Box should have uv attribute") { geometry.hasAttribute("uv") }

        val positionAttribute = geometry.getAttribute("position")!!
        assertTrue("Box should have vertices") { positionAttribute.count > 0 }
        assertEquals(3, positionAttribute.itemSize, "Position should be vec3")
    }

    @Test
    fun testCreateSphere() {
        val geometry = generator.createSphere(5f, 32, 16)

        assertNotNull(geometry, "Sphere geometry should not be null")
        assertTrue("Sphere should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Sphere should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Sphere should have uv attribute") { geometry.hasAttribute("uv") }

        val positionAttribute = geometry.getAttribute("position")!!
        val expectedVertices =
            (32 + 1) * (16 + 1) // Expected vertex count (widthSegments+1) * (heightSegments+1)
        assertTrue("Sphere should have reasonable vertex count") {
            positionAttribute.count >= expectedVertices / 2
        }
    }

    @Test
    fun testCreateCylinder() {
        val geometry = generator.createCylinder(3f, 2f, 8f, 16)

        assertNotNull(geometry, "Cylinder geometry should not be null")
        assertTrue("Cylinder should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Cylinder should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Cylinder should have uv attribute") { geometry.hasAttribute("uv") }

        // Test that cylinder has reasonable triangle count
        val indices = geometry.index
        assertNotNull(indices, "Cylinder should have index buffer")
        assertTrue("Cylinder should have triangles") { indices.count >= 6 }
    }

    @Test
    fun testCreateCone() {
        val geometry = generator.createCone(4f, 10f, 8)

        assertNotNull(geometry, "Cone geometry should not be null")
        assertTrue("Cone should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Cone should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Cone should have uv attribute") { geometry.hasAttribute("uv") }
    }

    @Test
    fun testCreateTorus() {
        val geometry = generator.createTorus(3f, 1f, 8, 16)

        assertNotNull(geometry, "Torus geometry should not be null")
        assertTrue("Torus should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Torus should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Torus should have uv attribute") { geometry.hasAttribute("uv") }

        val positionAttribute = geometry.getAttribute("position")!!
        val expectedVertices = 8 * 16 // radial * tubular (vertex count)
        assertTrue("Torus should have expected vertex count") {
            positionAttribute.count >= expectedVertices
        }
    }

    @Test
    fun testCreatePlane() {
        val geometry = generator.createPlane(10f, 5f, 4, 2)

        assertNotNull(geometry, "Plane geometry should not be null")
        assertTrue("Plane should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Plane should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Plane should have uv attribute") { geometry.hasAttribute("uv") }

        val positionAttribute = geometry.getAttribute("position")!!
        val expectedVertices =
            (4 + 1) * (2 + 1) // (widthSegments+1) * (heightSegments+1) - vertex count
        assertEquals(
            expectedVertices,
            positionAttribute.count,
            "Plane should have exact vertex count"
        )
    }

    @Test
    fun testCreateFromExtrusion() {
        val shape = createTestShape()
        val options = ExtrudeOptions(
            depth = 2f,
            bevelEnabled = true,
            bevelThickness = 0.5f,
            bevelSize = 0.2f,
            bevelSegments = 3
        )

        val geometry = generator.createFromExtrusion(shape, options)

        assertNotNull(geometry, "Extruded geometry should not be null")
        assertTrue("Extruded geometry should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Extruded geometry should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Extruded geometry should have uv attribute") { geometry.hasAttribute("uv") }
    }

    @Test
    fun testCreateFromLathe() {
        val points = listOf(
            Vector2(0f, 0f),
            Vector2(1f, 0f),
            Vector2(1f, 1f),
            Vector2(0f, 1f)
        )

        val geometry = generator.createFromLathe(points, 16, 0f, PI * 2)

        assertNotNull(geometry, "Lathe geometry should not be null")
        assertTrue("Lathe geometry should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Lathe geometry should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Lathe geometry should have uv attribute") { geometry.hasAttribute("uv") }
    }

    @Test
    fun testCreateFromText() {
        val font = createTestFont()
        val options = TextOptions(
            size = 24f,
            height = 2f,
            curveSegments = 12,
            bevelEnabled = true
        )

        val geometry = generator.createFromText("Test", font, options)

        assertNotNull(geometry, "Text geometry should not be null")
        assertTrue("Text geometry should have position attribute") { geometry.hasAttribute("position") }
        assertTrue("Text geometry should have normal attribute") { geometry.hasAttribute("normal") }
        assertTrue("Text geometry should have uv attribute") { geometry.hasAttribute("uv") }
    }

    @Test
    fun testInvalidParametersThrowExceptions() {
        assertFailsWith<GeometryException.InvalidParameters> {
            generator.createBox(-1f, 2f, 3f) // Negative width
        }

        assertFailsWith<GeometryException.InvalidParameters> {
            generator.createSphere(5f, 2, 16) // Too few width segments
        }

        assertFailsWith<GeometryException.InvalidParameters> {
            generator.createCylinder(3f, -2f, 8f, 8) // Negative bottom radius
        }
    }

    @Test
    fun testParameterEdgeCases() {
        // Test with minimum valid parameters
        val minBox = generator.createBox(0.1f, 0.1f, 0.1f, intArrayOf(1, 1, 1))
        assertNotNull(minBox, "Minimum box should be valid")

        val minSphere = generator.createSphere(0.1f, 3, 2)
        assertNotNull(minSphere, "Minimum sphere should be valid")
    }

    // Helper methods to create test objects
    private fun createTestShape(): Shape {
        return MockShape()
    }

    private fun createTestFont(): Font {
        return MockFont()
    }
}

// Mock implementations for testing
private class MockGeometryGenerator : GeometryGenerator {
    override fun createBox(
        width: Float,
        height: Float,
        depth: Float,
        segments: IntArray?
    ): BufferGeometry {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw GeometryException.InvalidParameters("Box dimensions must be positive")
        }
        return MockBufferGeometry()
    }

    override fun createSphere(
        radius: Float,
        widthSegments: Int,
        heightSegments: Int
    ): BufferGeometry {
        if (radius <= 0 || widthSegments < 3 || heightSegments < 2) {
            throw GeometryException.InvalidParameters("Invalid sphere parameters")
        }
        val vertexCount = (widthSegments + 1) * (heightSegments + 1)
        val triangleCount = widthSegments * heightSegments * 2
        return MockBufferGeometry(vertexCount, triangleCount)
    }

    override fun createCylinder(
        radiusTop: Float,
        radiusBottom: Float,
        height: Float,
        radialSegments: Int
    ): BufferGeometry {
        if (radiusTop < 0 || radiusBottom < 0 || height <= 0 || radialSegments < 3) {
            throw GeometryException.InvalidParameters("Invalid cylinder parameters")
        }
        return MockBufferGeometry()
    }

    override fun createCone(radius: Float, height: Float, radialSegments: Int): BufferGeometry {
        return MockBufferGeometry()
    }

    override fun createTorus(
        radius: Float,
        tube: Float,
        radialSegments: Int,
        tubularSegments: Int
    ): BufferGeometry {
        val vertexCount = radialSegments * tubularSegments
        val triangleCount = radialSegments * tubularSegments * 2
        return MockBufferGeometry(vertexCount, triangleCount)
    }

    override fun createPlane(
        width: Float,
        height: Float,
        widthSegments: Int,
        heightSegments: Int
    ): BufferGeometry {
        val vertexCount = (widthSegments + 1) * (heightSegments + 1)
        val triangleCount = widthSegments * heightSegments * 2
        return MockBufferGeometry(vertexCount, triangleCount)
    }

    override fun createFromExtrusion(shape: Shape, options: ExtrudeOptions): BufferGeometry {
        return MockBufferGeometry()
    }

    override fun createFromLathe(
        points: List<Vector2>,
        segments: Int,
        phiStart: Float,
        phiLength: Float
    ): BufferGeometry {
        return MockBufferGeometry()
    }

    override fun createFromText(text: String, font: Font, options: TextOptions): BufferGeometry {
        return MockBufferGeometry()
    }
}

private class MockBufferGeometry(
    private val vertexCount: Int = 8,
    private val triangleCount: Int = 12
) : BufferGeometry() {
    init {
        // Add default attributes with calculated sizes
        setAttribute(
            "position",
            io.materia.geometry.BufferAttribute(FloatArray(vertexCount * 3), 3)
        )
        setAttribute("normal", io.materia.geometry.BufferAttribute(FloatArray(vertexCount * 3), 3))
        setAttribute("uv", io.materia.geometry.BufferAttribute(FloatArray(vertexCount * 2), 2))
        setIndex(io.materia.geometry.BufferAttribute(FloatArray(triangleCount * 3), 1))
    }
}

private class MockShape : Shape

private class MockFont : Font

// BufferAttribute is already defined in io.materia.geometry package

// Mock interfaces
private interface GeometryGenerator {
    fun createBox(
        width: Float,
        height: Float,
        depth: Float,
        segments: IntArray? = null
    ): BufferGeometry

    fun createSphere(radius: Float, widthSegments: Int, heightSegments: Int): BufferGeometry
    fun createCylinder(
        radiusTop: Float,
        radiusBottom: Float,
        height: Float,
        radialSegments: Int
    ): BufferGeometry

    fun createCone(radius: Float, height: Float, radialSegments: Int): BufferGeometry
    fun createTorus(
        radius: Float,
        tube: Float,
        radialSegments: Int,
        tubularSegments: Int
    ): BufferGeometry

    fun createPlane(
        width: Float,
        height: Float,
        widthSegments: Int,
        heightSegments: Int
    ): BufferGeometry

    fun createFromExtrusion(shape: Shape, options: ExtrudeOptions): BufferGeometry
    fun createFromLathe(
        points: List<Vector2>,
        segments: Int,
        phiStart: Float,
        phiLength: Float
    ): BufferGeometry

    fun createFromText(text: String, font: Font, options: TextOptions): BufferGeometry
}

// Note: Using real BufferGeometry from io.materia.geometry.BufferGeometry

private interface Shape
private interface Font

private data class ExtrudeOptions(
    val depth: Float,
    val bevelEnabled: Boolean,
    val bevelThickness: Float,
    val bevelSize: Float,
    val bevelSegments: Int
)

private data class TextOptions(
    val size: Float,
    val height: Float,
    val curveSegments: Int,
    val bevelEnabled: Boolean
)

private sealed class GeometryException(message: String) : Exception(message) {
    class InvalidParameters(message: String) : GeometryException(message)
}

// Add missing PI constant
private const val PI = kotlin.math.PI.toFloat()