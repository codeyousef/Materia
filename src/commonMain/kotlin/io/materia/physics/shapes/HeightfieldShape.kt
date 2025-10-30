/**
 * Heightfield collision shape implementation (for terrain)
 */
package io.materia.physics.shapes

import io.materia.core.math.*
import io.materia.physics.*

/**
 * Heightfield collision shape implementation (for terrain)
 */
class HeightfieldShapeImpl(
    override val width: Int,
    override val height: Int,
    initialHeightData: FloatArray,
    override val maxHeight: Float,
    override val minHeight: Float,
    override val upAxis: Int = 1
) : CollisionShapeImpl(), HeightfieldShape {

    override val shapeType: ShapeType = ShapeType.HEIGHTFIELD

    private val _heightData = initialHeightData.copyOf()
    override val heightData: FloatArray get() = _heightData.copyOf()

    init {
        require(width > 0 && height > 0) { "Heightfield dimensions must be positive" }
        require(initialHeightData.size == (width * height)) { "Height data size must match width * height" }
        require(maxHeight >= minHeight) { "Max height must be >= min height" }
        require(upAxis in 0..2) { "Up axis must be 0 (X), 1 (Y), or 2 (Z)" }

        calculateBoundingBox()
    }

    override fun getHeightAtPoint(x: Float, z: Float): Float {
        // Clamp coordinates to heightfield bounds
        val clampedX = x.coerceIn(0f, width - 1f)
        val clampedZ = z.coerceIn(0f, height - 1f)

        // Get integer coordinates
        val x0 = clampedX.toInt()
        val z0 = clampedZ.toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val z1 = minOf(z0 + 1, height - 1)

        // Get fractional parts
        val fx = clampedX - x0
        val fz = clampedZ - z0

        // Sample height values
        val h00 = _heightData[z0 * width + x0]
        val h10 = _heightData[z0 * width + x1]
        val h01 = _heightData[z1 * width + x0]
        val h11 = _heightData[z1 * width + x1]

        // Bilinear interpolation
        val h0 = h00 * (1f - fx) + h10 * fx
        val h1 = h01 * (1f - fx) + h11 * fx
        return h0 * (1f - fz) + (h1 * fz)
    }

    override fun setHeightValue(x: Int, z: Int, height: Float) {
        require(x in 0 until width && z in 0 until this.height) { "Coordinates out of bounds" }
        require(height in minHeight..maxHeight) { "Height value out of range" }

        _heightData[z * width + x] = height
        invalidateBoundingBox()
    }

    override fun getVolume(): Float {
        // Volume calculation for heightfield is complex and depends on interpretation
        // Return approximate volume based on average height
        val averageHeight = _heightData.average().toFloat()
        val baseArea = width * height * localScaling.x * localScaling.z
        return baseArea * averageHeight * localScaling.y
    }

    override fun getSurfaceArea(): Float {
        // Approximate surface area calculation
        var totalArea = 0f

        for (z in 0 until height - 1) {
            for (x in 0 until width - 1) {
                // Calculate area of two triangles forming each heightfield cell
                val h00 = _heightData[z * width + x]
                val h10 = _heightData[z * width + x + 1]
                val h01 = _heightData[(z + 1) * width + x]
                val h11 = _heightData[(z + 1) * width + x + 1]

                val v00 = Vector3(x.toFloat(), h00, z.toFloat()) * localScaling
                val v10 = Vector3((x + 1).toFloat(), h10, z.toFloat()) * localScaling
                val v01 = Vector3(x.toFloat(), h01, (z + 1).toFloat()) * localScaling
                val v11 = Vector3((x + 1).toFloat(), h11, (z + 1).toFloat()) * localScaling

                // Triangle 1: v00, v10, v01
                val edge1 = v10 - v00
                val edge2 = v01 - v00
                totalArea = totalArea + edge1.cross(edge2).length() * 0.5f

                // Triangle 2: v10, v11, v01
                val edge3 = v11 - v10
                val edge4 = v01 - v10
                totalArea = totalArea + edge3.cross(edge4).length() * 0.5f
            }
        }

        return totalArea
    }

    override fun isConvex(): Boolean = false
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        // Find the heightfield vertex most in the given direction
        var maxDot = Float.NEGATIVE_INFINITY
        var supportVertex = Vector3.ZERO

        for (z in 0 until height) {
            for (x in 0 until width) {
                val heightValue = _heightData[z * width + x]
                val vertex = Vector3(x.toFloat(), heightValue, z.toFloat()) * localScaling
                val dot = vertex.dot(direction)
                if (dot > maxDot) {
                    maxDot = dot
                    supportVertex = vertex
                }
            }
        }

        return supportVertex
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        return localGetSupportingVertex(direction)
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        // Heightfields are typically static, return zero inertia
        return Vector3.ZERO
    }

    override fun calculateBoundingBox() {
        val minVec = Vector3(0f, minHeight, 0f) * localScaling
        val maxVec = Vector3(width.toFloat(), maxHeight, height.toFloat()) * localScaling
        _boundingBox = Box3(minVec, maxVec)
    }

    override fun clone(): CollisionShape = HeightfieldShapeImpl(
        width, height, _heightData, maxHeight, minHeight, upAxis
    ).apply {
        margin = this@HeightfieldShapeImpl.margin
        localScaling = this@HeightfieldShapeImpl.localScaling
    }
}
