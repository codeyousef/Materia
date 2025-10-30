/**
 * Planar UV Projection
 * Projects geometry onto a custom plane with specified orientation
 */
package io.materia.geometry.uvgen

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.*
import kotlin.math.abs

/**
 * UV generation options for planar projection
 */
data class PlanarUVOptions(
    val normal: Vector3 = Vector3(0f, 0f, 1f),
    val upVector: Vector3 = Vector3(0f, 1f, 0f),
    val origin: Vector3 = Vector3()
)

/**
 * Generate UV coordinates using planar projection
 * Projects onto a custom plane with specified orientation
 */
fun generatePlanarUV(
    geometry: BufferGeometry,
    options: PlanarUVOptions = PlanarUVOptions()
): UVGenerationResult {
    val positionAttribute = geometry.getAttribute("position")
        ?: return UVGenerationResult(geometry, false, "No position attribute found")

    val vertexCount = positionAttribute.count
    val uvCoordinates = FloatArray((vertexCount * 2))

    // Create projection plane
    val normalLength = options.normal.length()
    val normal = if (normalLength > 0.001f) {
        options.normal.clone().normalize()
    } else {
        // Default to Z-axis if normal is degenerate
        Vector3(0f, 0f, 1f)
    }
    val uAxis = calculateUAxis(normal, options.upVector)
    val vAxisRaw = normal.clone().cross(uAxis)
    val vAxisLength = vAxisRaw.length()
    val vAxis = if (vAxisLength > 0.001f) {
        vAxisRaw.normalize()
    } else {
        // Fallback to perpendicular vector
        Vector3(0f, 1f, 0f)
    }

    // Project all vertices onto the plane
    val projectedPoints = mutableListOf<Vector2>()

    for (i in 0 until vertexCount) {
        val position = Vector3(
            positionAttribute.getX(i),
            positionAttribute.getY(i),
            positionAttribute.getZ(i)
        )

        val relativePos = position.clone().subtract(options.origin)
        val u = relativePos.dot(uAxis)
        val v = relativePos.dot(vAxis)

        projectedPoints.add(Vector2(u, v))
    }

    // Normalize to [0,1] range
    val bounds = calculateProjectionBounds(projectedPoints)
    val size = bounds.getSize(Vector2())

    for (i in projectedPoints.indices) {
        val point = projectedPoints[i]
        val normalizedU = if (size.x > 0) (point.x - bounds.min.x) / size.x else 0.5f
        val normalizedV = if (size.y > 0) (point.y - bounds.min.y) / size.y else 0.5f

        uvCoordinates[(i * 2)] = normalizedU
        uvCoordinates[i * 2 + 1] = normalizedV
    }

    val resultGeometry = geometry.clone()
    resultGeometry.setAttribute("uv", BufferAttribute(uvCoordinates, 2))

    return UVGenerationResult(
        geometry = resultGeometry,
        success = true,
        message = "Planar UV projection completed",
        uvBounds = calculateUVBounds(uvCoordinates)
    )
}

internal fun calculateUAxis(normal: Vector3, upVector: Vector3): Vector3 {
    val upLength = upVector.length()
    val up = if (upLength > 0.001f) {
        upVector.clone().normalize()
    } else {
        // Default to Y-up if up vector is degenerate
        Vector3(0f, 1f, 0f)
    }

    val uAxisRaw = up.cross(normal)
    val uAxisLength = uAxisRaw.length()

    // If up vector is parallel to normal, use a different reference
    if (uAxisLength < 0.001f) {
        val fallback = if (abs(normal.y) < 0.9f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
        val fallbackCross = fallback.cross(normal)
        val fallbackLength = fallbackCross.length()
        return if (fallbackLength > 0.001f) {
            fallbackCross.normalize()
        } else {
            // Ultimate fallback to X-axis
            Vector3(1f, 0f, 0f)
        }
    }

    return uAxisRaw.normalize()
}

internal fun calculateProjectionBounds(points: List<Vector2>): Box2 {
    if (points.isEmpty()) return Box2()

    var minX = points[0].x
    var maxX = points[0].x
    var minY = points[0].y
    var maxY = points[0].y

    for (point in points) {
        minX = minOf(minX, point.x)
        maxX = maxOf(maxX, point.x)
        minY = minOf(minY, point.y)
        maxY = maxOf(maxY, point.y)
    }

    return Box2(Vector2(minX, minY), Vector2(maxX, maxY))
}
