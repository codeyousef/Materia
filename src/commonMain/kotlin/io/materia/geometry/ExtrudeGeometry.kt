/**
 * ExtrudeGeometry implementation with advanced beveling support
 * Extrudes 2D shapes into 3D geometry with configurable beveling, holes, and UV mapping
 */
package io.materia.geometry

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.shape.Shape
import kotlin.math.*

/**
 * Options for extrusion geometry generation
 */
data class ExtrudeOptions(
    val depth: Float = 1f,
    val bevelEnabled: Boolean = false,
    val bevelThickness: Float = 0.2f,
    val bevelSize: Float = 0.1f,
    val bevelOffset: Float = 0f,
    val bevelSegments: Int = 3,
    val extrudePath: Curve? = null,
    val steps: Int = 1,
    val uvGenerator: ExtrudeUVGenerator? = null
) {
    init {
        require(depth > 0f) { "Extrude depth must be positive" }
        require(bevelSegments >= 0) { "Bevel segments must be non-negative" }
        require(steps >= 1) { "Steps must be at least 1" }
        if (bevelEnabled) {
            require(bevelThickness >= 0f) { "Bevel thickness must be non-negative" }
            require(bevelSize >= 0f) { "Bevel size must be non-negative" }
        }
    }
}

/**
 * UV generation strategy for extruded geometry
 */
interface ExtrudeUVGenerator {
    fun generateSideUV(
        geometry: ExtrudeGeometry,
        vertices: List<Vector3>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        indexD: Int
    ): List<Vector2>

    fun generateTopUV(
        geometry: ExtrudeGeometry,
        vertices: List<Vector3>,
        indexA: Int,
        indexB: Int,
        indexC: Int
    ): List<Vector2>

    fun generateBottomUV(
        geometry: ExtrudeGeometry,
        vertices: List<Vector3>,
        indexA: Int,
        indexB: Int,
        indexC: Int
    ): List<Vector2>
}

/**
 * Default UV generator for extruded geometry
 */
class DefaultExtrudeUVGenerator : ExtrudeUVGenerator {
    override fun generateSideUV(
        geometry: ExtrudeGeometry,
        vertices: List<Vector3>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        indexD: Int
    ): List<Vector2> {
        // Validate indices are within bounds
        require(
            indexA in vertices.indices && indexB in vertices.indices &&
                    indexC in vertices.indices && indexD in vertices.indices
        ) {
            "Invalid vertex indices for UV generation"
        }

        val a = vertices.getOrNull(indexA) ?: return emptyList()
        val b = vertices.getOrNull(indexB) ?: return emptyList()
        val c = vertices.getOrNull(indexC) ?: return emptyList()
        val d = vertices.getOrNull(indexD) ?: return emptyList()

        return if (abs(a.y - b.y) < abs(a.x - b.x)) {
            listOf(
                Vector2(a.x, 1f - a.z),
                Vector2(b.x, 1f - b.z),
                Vector2(c.x, 1f - c.z),
                Vector2(d.x, 1f - d.z)
            )
        } else {
            listOf(
                Vector2(a.y, 1f - a.z),
                Vector2(b.y, 1f - b.z),
                Vector2(c.y, 1f - c.z),
                Vector2(d.y, 1f - d.z)
            )
        }
    }

    override fun generateTopUV(
        geometry: ExtrudeGeometry,
        vertices: List<Vector3>,
        indexA: Int,
        indexB: Int,
        indexC: Int
    ): List<Vector2> {
        // Validate indices are within bounds
        require(indexA in vertices.indices && indexB in vertices.indices && indexC in vertices.indices) {
            "Invalid vertex indices for top UV generation"
        }

        val a = vertices.getOrNull(indexA) ?: return emptyList()
        val b = vertices.getOrNull(indexB) ?: return emptyList()
        val c = vertices.getOrNull(indexC) ?: return emptyList()

        return listOf(
            Vector2(a.x, a.y),
            Vector2(b.x, b.y),
            Vector2(c.x, c.y)
        )
    }

    override fun generateBottomUV(
        geometry: ExtrudeGeometry,
        vertices: List<Vector3>,
        indexA: Int,
        indexB: Int,
        indexC: Int
    ): List<Vector2> {
        return generateTopUV(geometry, vertices, indexA, indexB, indexC)
    }
}

/**
 * Simple curve interface for extrusion paths
 * Note: Shape and Path are imported from io.materia.shape and io.materia.curve
 */
interface Curve {
    fun getPoint(t: Float): Vector3
    fun getTangent(t: Float): Vector3
    fun getLength(): Float
}

/**
 * ExtrudeGeometry class for creating 3D geometry from 2D shapes
 */
class ExtrudeGeometry(
    val shapes: List<Shape>,
    val options: ExtrudeOptions = ExtrudeOptions()
) : BufferGeometry() {

    private val uvGenerator = options.uvGenerator ?: DefaultExtrudeUVGenerator()

    init {
        require(shapes.isNotEmpty()) { "At least one shape must be provided" }
        generate()
    }

    constructor(shape: Shape, options: ExtrudeOptions = ExtrudeOptions()) : this(
        listOf(shape),
        options
    )

    private fun generate() {
        val vertices = mutableListOf<Vector3>()
        val normals = mutableListOf<Vector3>()
        val uvs = mutableListOf<Vector2>()
        val indices = mutableListOf<Int>()

        var vertexCounter = 0

        for (shape in shapes) {
            val extractedPoints = shape.extractPoints(divisions = 12)
            val shapePoints = extractedPoints.shape
            val holes = extractedPoints.holes

            // Triangulate the shape
            val triangulatedShape = triangulateShape(shapePoints, holes)

            // Generate layers along the extrusion path
            val layers = generateLayers(shapePoints, holes)

            // Generate side faces
            generateSideFaces(shapePoints, layers, vertices, normals, uvs, indices, vertexCounter)
            vertexCounter = vertices.size

            // Generate top and bottom faces
            generateCapFaces(
                triangulatedShape,
                layers,
                vertices,
                normals,
                uvs,
                indices,
                vertexCounter,
                true
            ) // top
            vertexCounter = vertices.size
            generateCapFaces(
                triangulatedShape,
                layers,
                vertices,
                normals,
                uvs,
                indices,
                vertexCounter,
                false
            ) // bottom
            vertexCounter = vertices.size

            // Generate hole faces
            for (hole in holes) {
                generateSideFaces(hole, layers, vertices, normals, uvs, indices, vertexCounter)
                vertexCounter = vertices.size
            }
        }

        // Set geometry attributes
        setAttribute(
            "position",
            BufferAttribute(vertices.flatMap { listOf(it.x, it.y, it.z) }.toFloatArray(), 3)
        )
        setAttribute(
            "normal",
            BufferAttribute(normals.flatMap { listOf(it.x, it.y, it.z) }.toFloatArray(), 3)
        )
        setAttribute("uv", BufferAttribute(uvs.flatMap { listOf(it.x, it.y) }.toFloatArray(), 2))
        setIndex(BufferAttribute(indices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    private fun generateLayers(
        shapePoints: List<Vector2>,
        holes: List<List<Vector2>>
    ): List<ExtrusionLayer> {
        val layers = mutableListOf<ExtrusionLayer>()

        if (options.extrudePath != null) {
            // Extrude along curve
            generateCurveLayers(shapePoints, holes, layers)
        } else {
            // Straight extrusion
            generateStraightLayers(shapePoints, holes, layers)
        }

        return layers
    }

    private fun generateStraightLayers(
        shapePoints: List<Vector2>,
        holes: List<List<Vector2>>,
        layers: MutableList<ExtrusionLayer>
    ) {
        val steps = options.steps
        val depth = options.depth
        val bevelEnabled = options.bevelEnabled
        val bevelSegments = options.bevelSegments
        val bevelThickness = options.bevelThickness
        val bevelSize = options.bevelSize
        val bevelOffset = options.bevelOffset

        // Calculate layer positions
        val layerPositions = mutableListOf<Float>()

        if (bevelEnabled) {
            // Bottom bevel
            for (i in 0..bevelSegments) {
                val t = if (bevelSegments > 0) i.toFloat() / bevelSegments else 0f
                val bevelZ = -bevelThickness + (1f - cos(t * PI.toFloat() / 2f)) * bevelThickness
                layerPositions.add(bevelZ)
            }

            // Middle straight section
            val straightSteps = steps - 2 * bevelSegments
            for (i in 1 until straightSteps) {
                val t = if (straightSteps > 0) i.toFloat() / straightSteps else 0f
                layerPositions.add((t * depth))
            }

            // Top bevel
            for (i in 0..bevelSegments) {
                val t = if (bevelSegments > 0) i.toFloat() / bevelSegments else 0f
                val bevelZ =
                    depth - bevelThickness + cos((1f - t) * PI.toFloat() / 2f) * bevelThickness
                layerPositions.add(bevelZ)
            }
        } else {
            // Simple linear layers
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                layerPositions.add((t * depth))
            }
        }

        // Generate layers with beveling
        for ((index, z) in layerPositions.withIndex()) {
            val scaleFactor = calculateBevelScale(
                index,
                layerPositions.size - 1,
                bevelEnabled,
                bevelSize,
                bevelOffset
            )

            val layerPoints = shapePoints.map { point ->
                Vector3(point.x * scaleFactor, point.y * scaleFactor, z)
            }

            val layerHoles = holes.map { hole ->
                hole.map { point ->
                    Vector3(point.x * scaleFactor, point.y * scaleFactor, z)
                }
            }

            layers.add(ExtrusionLayer(layerPoints, layerHoles, scaleFactor))
        }
    }

    private fun generateCurveLayers(
        shapePoints: List<Vector2>,
        holes: List<List<Vector2>>,
        layers: MutableList<ExtrusionLayer>
    ) {
        val extrudePath = options.extrudePath ?: return
        val steps = options.steps

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val point = extrudePath.getPoint(t)
            val tangent = extrudePath.getTangent(t)

            // Create coordinate frame at this point
            val normal = Vector3(0f, 0f, 1f)
            val binormal = Vector3().crossVectors(tangent, normal)
            val binormalLength = binormal.length()
            if (binormalLength > 0.001f) {
                binormal.normalize()
            } else {
                // Fallback to perpendicular vector
                binormal.set(0f, 1f, 0f)
            }

            normal.crossVectors(binormal, tangent)
            val normalLength = normal.length()
            if (normalLength > 0.001f) {
                normal.normalize()
            } else {
                // Fallback to perpendicular vector
                normal.set(1f, 0f, 0f)
            }

            // Transform shape points to curve coordinate frame
            val layerPoints = shapePoints.map { shapePoint ->
                val localX = shapePoint.x * binormal.x + shapePoint.y * normal.x
                val localY = shapePoint.x * binormal.y + shapePoint.y * normal.y
                val localZ = shapePoint.x * binormal.z + shapePoint.y * normal.z

                Vector3(point.x + localX, point.y + localY, point.z + localZ)
            }

            val layerHoles = holes.map { hole ->
                hole.map { holePoint ->
                    val localX = holePoint.x * binormal.x + holePoint.y * normal.x
                    val localY = holePoint.x * binormal.y + holePoint.y * normal.y
                    val localZ = holePoint.x * binormal.z + holePoint.y * normal.z

                    Vector3(point.x + localX, point.y + localY, point.z + localZ)
                }
            }

            layers.add(ExtrusionLayer(layerPoints, layerHoles, 1f))
        }
    }

    private fun calculateBevelScale(
        index: Int,
        maxIndex: Int,
        bevelEnabled: Boolean,
        bevelSize: Float,
        bevelOffset: Float
    ): Float {
        if (!bevelEnabled) return 1f

        val bevelSegments = options.bevelSegments
        val t = index.toFloat() / maxIndex

        return when {
            index <= bevelSegments -> {
                // Bottom bevel
                val bevelT = if (bevelSegments > 0) index.toFloat() / bevelSegments else 0f
                1f - bevelSize * (1f - sin(bevelT * PI.toFloat() / 2f)) + bevelOffset
            }

            index >= maxIndex - bevelSegments -> {
                // Top bevel
                val bevelT =
                    if (bevelSegments > 0) (maxIndex - index).toFloat() / bevelSegments else 0f
                1f - bevelSize * (1f - sin(bevelT * PI.toFloat() / 2f)) + bevelOffset
            }

            else -> {
                // Middle section
                1f + bevelOffset
            }
        }
    }

    private fun generateSideFaces(
        contour: List<Vector2>,
        layers: List<ExtrusionLayer>,
        vertices: MutableList<Vector3>,
        normals: MutableList<Vector3>,
        uvs: MutableList<Vector2>,
        indices: MutableList<Int>,
        vertexOffset: Int
    ) {
        val contourLength = contour.size
        var currentVertexIndex = vertexOffset

        // Generate vertices for all layers
        for (layer in layers) {
            val layerPoints = if (layer.points.size == contourLength) {
                layer.points
            } else {
                // Scale contour points if layer doesn't match
                contour.mapIndexed { index, point ->
                    val scaledPoint =
                        layer.points.getOrElse(index % layer.points.size) { layer.points.last() }
                    Vector3(point.x * layer.scaleFactor, point.y * layer.scaleFactor, scaledPoint.z)
                }
            }

            vertices.addAll(layerPoints)
        }

        // Generate side faces
        for (layerIndex in 0 until layers.size - 1) {
            for (pointIndex in 0 until contourLength) {
                val nextPointIndex = (pointIndex + 1) % contourLength

                val a = currentVertexIndex + layerIndex * contourLength + pointIndex
                val b = currentVertexIndex + layerIndex * contourLength + nextPointIndex
                val c = currentVertexIndex + (layerIndex + 1) * contourLength + nextPointIndex
                val d = currentVertexIndex + (layerIndex + 1) * contourLength + pointIndex

                // Generate quad indices (two triangles)
                indices.addAll(listOf(a, b, d))
                indices.addAll(listOf(b, c, d))

                // Generate normals for side faces
                val v1 = vertices[b - vertexOffset].clone().sub(vertices[a - vertexOffset])
                val v2 = vertices[d - vertexOffset].clone().sub(vertices[a - vertexOffset])
                val normal = Vector3().crossVectors(v1, v2)
                val faceNormalLength = normal.length()
                if (faceNormalLength > 0.001f) {
                    normal.normalize()
                } else {
                    // Fallback to up direction for degenerate faces
                    normal.set(0f, 1f, 0f)
                }

                // Add normals for all four vertices of the quad
                repeat(4) { normals.add(normal.clone()) }

                // Generate UVs for side faces
                val sideUVs = uvGenerator.generateSideUV(
                    this,
                    vertices,
                    a - vertexOffset,
                    b - vertexOffset,
                    c - vertexOffset,
                    d - vertexOffset
                )
                uvs.addAll(sideUVs)
            }
        }
    }

    private fun generateCapFaces(
        triangulatedShape: List<List<Int>>,
        layers: List<ExtrusionLayer>,
        vertices: MutableList<Vector3>,
        normals: MutableList<Vector3>,
        uvs: MutableList<Vector2>,
        indices: MutableList<Int>,
        vertexOffset: Int,
        isTop: Boolean
    ) {
        val layerIndex = if (isTop) layers.size - 1 else 0
        val layer = layers[layerIndex]
        val normal = Vector3(0f, 0f, if (isTop) 1f else -1f)

        var currentVertexIndex = vertexOffset

        // Add vertices for cap
        vertices.addAll(layer.points)

        // Generate triangles for cap
        for (triangle in triangulatedShape) {
            val a = currentVertexIndex + triangle[0]
            val b = currentVertexIndex + triangle[1]
            val c = currentVertexIndex + triangle[2]

            if (isTop) {
                indices.addAll(listOf(a, b, c))
            } else {
                indices.addAll(listOf(a, c, b)) // Reverse winding for bottom
            }

            // Add normals
            repeat(3) { normals.add(normal.clone()) }

            // Generate UVs for cap
            val capUVs = if (isTop) {
                uvGenerator.generateTopUV(
                    this,
                    vertices,
                    a - vertexOffset,
                    b - vertexOffset,
                    c - vertexOffset
                )
            } else {
                uvGenerator.generateBottomUV(
                    this,
                    vertices,
                    a - vertexOffset,
                    b - vertexOffset,
                    c - vertexOffset
                )
            }
            uvs.addAll(capUVs)
        }
    }

    private fun triangulateShape(
        shapePoints: List<Vector2>,
        holes: List<List<Vector2>>
    ): List<List<Int>> {
        // Simple triangulation using ear clipping algorithm
        // This is a simplified implementation - in practice, you'd use a robust triangulation library

        val vertices = shapePoints.toMutableList()
        val triangles = mutableListOf<List<Int>>()

        // Add hole vertices (this is simplified - proper hole handling requires more complex logic)
        var indexOffset = vertices.size
        for (hole in holes) {
            vertices.addAll(hole)
            indexOffset = indexOffset + hole.size
        }

        // Simple triangulation for convex polygons (simplified)
        val indices = (0 until vertices.size).toMutableList()
        val maxIterations = vertices.size * 2 // Safety limit
        var iterations = 0

        while (indices.size > 2 && iterations < maxIterations) {
            iterations++
            var earFound = false

            for (i in indices.indices) {
                val prev = indices[(i - 1 + indices.size) % indices.size]
                val current = indices[i]
                val next = indices[(i + 1) % indices.size]

                if (isEar(vertices, indices, prev, current, next)) {
                    triangles.add(listOf(prev, current, next))
                    indices.removeAt(i)
                    earFound = true
                    break
                }
            }

            if (!earFound) break // Prevent infinite loop
        }

        return triangles
    }

    private fun isEar(
        vertices: List<Vector2>,
        indices: List<Int>,
        prev: Int,
        current: Int,
        next: Int
    ): Boolean {
        val a = vertices[prev]
        val b = vertices[current]
        val c = vertices[next]

        // Check if triangle is oriented counter-clockwise
        val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        if (cross <= 0) return false

        // Check if any other vertex is inside the triangle
        for (i in indices) {
            if (i == prev || i == current || i == next) continue

            val p = vertices[i]
            if (isPointInTriangle(p, a, b, c)) {
                return false
            }
        }

        return true
    }

    private fun isPointInTriangle(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        val denom = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
        if (abs(denom) < 1e-10f) return false

        val alpha = ((b.y - c.y) * (p.x - c.x) + (c.x - b.x) * (p.y - c.y)) / denom
        val beta = ((c.y - a.y) * (p.x - c.x) + (a.x - c.x) * (p.y - c.y)) / denom
        val gamma = 1 - alpha - beta

        return alpha >= 0 && beta >= 0 && gamma >= 0
    }
}

/**
 * Data class representing a layer in the extrusion
 */
data class ExtrusionLayer(
    val points: List<Vector3>,
    val holes: List<List<Vector3>>,
    val scaleFactor: Float
)

// Note: Shape and Path are imported from io.materia.shape and io.materia.curve packages

/**
 * Linear curve for straight extrusions
 */
class LinearCurve(val start: Vector3, val end: Vector3) : Curve {
    override fun getPoint(t: Float): Vector3 {
        return start.clone().lerp(end, t)
    }

    override fun getTangent(t: Float): Vector3 {
        val tangent = end.clone().sub(start)
        val tangentLength = tangent.length()
        return if (tangentLength > 0.001f) {
            tangent.normalize()
        } else {
            // Fallback to forward direction if line is degenerate
            Vector3(0f, 0f, 1f)
        }
    }

    override fun getLength(): Float {
        return start.distanceTo(end)
    }
}

/**
 * Helper functions for creating common shapes
 */
object ShapeHelper {

    /**
     * Create a rectangular shape
     */
    fun createRectangle(width: Float, height: Float): Shape {
        val halfWidth = width / 2f
        val halfHeight = height / 2f

        val points = listOf(
            Vector2(-halfWidth, -halfHeight),
            Vector2(halfWidth, -halfHeight),
            Vector2(halfWidth, halfHeight),
            Vector2(-halfWidth, halfHeight)
        )

        return Shape(points)
    }

    /**
     * Create a circular shape
     */
    fun createCircle(radius: Float, segments: Int = 32): Shape {
        val points = mutableListOf<Vector2>()

        for (i in 0 until segments) {
            val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
            points.add(Vector2(cos(angle) * radius, sin(angle) * radius))
        }

        return Shape(points)
    }

    /**
     * Create a star shape
     */
    fun createStar(outerRadius: Float, innerRadius: Float, points: Int = 5): Shape {
        val vertices = mutableListOf<Vector2>()

        for (i in 0 until (points * 2)) {
            val angle = (i.toFloat() / ((points * 2))) * 2f * PI.toFloat()
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            vertices.add(Vector2(cos(angle) * radius, sin(angle) * radius))
        }

        return Shape(vertices)
    }

    /**
     * Create a heart shape
     */
    fun createHeart(scale: Float = 1f): Shape {
        val points = mutableListOf<Vector2>()

        for (i in 0..100) {
            val t = (i.toFloat() / 100f) * 2f * PI.toFloat()
            val x = 16f * sin(t).pow(3)
            val y = 13f * cos(t) - 5f * cos((2f * t)) - 2f * cos((3f * t)) - cos((4f * t))

            points.add(Vector2(x * scale / 16f, y * scale / 16f))
        }

        return Shape(points)
    }
}