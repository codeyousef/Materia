/**
 * Cylindrical UV Projection
 * Ideal for objects with cylindrical symmetry
 */
package io.materia.geometry.uvgen

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.*
import kotlin.math.PI
import kotlin.math.atan2

/**
 * UV generation options for cylindrical projection
 */
data class CylindricalUVOptions(
    val axis: Vector3 = Vector3(0f, 1f, 0f),
    val seamThreshold: Float = 0.05f
)

/**
 * Generate UV coordinates using cylindrical projection
 * Ideal for objects with cylindrical symmetry
 */
fun generateCylindricalUV(
    geometry: BufferGeometry,
    options: CylindricalUVOptions = CylindricalUVOptions()
): UVGenerationResult {
    val positionAttribute = geometry.getAttribute("position")
        ?: return UVGenerationResult(geometry, false, "No position attribute found")

    val vertexCount = positionAttribute.count
    val uvCoordinates = FloatArray((vertexCount * 2))

    // Transform to cylinder space
    val axisLength = options.axis.length()
    val axis = if (axisLength > 0.001f) {
        options.axis.clone().normalize()
    } else {
        // Default to Y-axis if axis is degenerate
        Vector3(0f, 1f, 0f)
    }
    val boundingBox = geometry.computeBoundingBox()
    val height = boundingBox.getSize(Vector3()).dot(axis)
    val center = boundingBox.getCenter(Vector3())

    for (i in 0 until vertexCount) {
        val position = Vector3(
            positionAttribute.getX(i),
            positionAttribute.getY(i),
            positionAttribute.getZ(i)
        )

        val localPos = position.clone().subtract(center)
        val uv = projectToCylinder(localPos, axis, height, options)

        uvCoordinates[(i * 2)] = uv.x
        uvCoordinates[i * 2 + 1] = uv.y
    }

    // Handle seam at angle boundary
    val seamVertices = findCylindricalSeam(uvCoordinates, options.seamThreshold)

    val resultGeometry = geometry.clone()
    resultGeometry.setAttribute("uv", BufferAttribute(uvCoordinates, 2))

    return UVGenerationResult(
        geometry = resultGeometry,
        success = true,
        message = "Cylindrical UV projection completed",
        seamVertices = seamVertices,
        uvBounds = calculateUVBounds(uvCoordinates)
    )
}

private fun projectToCylinder(
    position: Vector3,
    axis: Vector3,
    height: Float,
    options: CylindricalUVOptions
): Vector2 {
    // Project position onto cylinder axis
    val axisProjection = axis.clone().multiplyScalar(position.dot(axis))
    val radialVector = position.clone().subtract(axisProjection)

    // Calculate angle around axis
    var angle = atan2(radialVector.z, radialVector.x)
    if (angle < 0) angle = angle + 2 * PI.toFloat()

    // Calculate height coordinate with safe division
    val heightCoord = if (height > 0.001f) {
        (axisProjection.length() + height * 0.5f) / height
    } else {
        0.5f // Default to middle if height is zero
    }

    val u = angle / (2 * PI.toFloat())
    val v = heightCoord.coerceIn(0f, 1f)

    return Vector2(u, v)
}

private fun findCylindricalSeam(
    uvCoordinates: FloatArray,
    seamThreshold: Float
): List<Int> {
    // Implementation would find seam at 0/1 UV boundary
    return emptyList()
}
