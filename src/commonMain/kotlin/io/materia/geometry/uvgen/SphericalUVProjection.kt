/**
 * Spherical UV Projection
 * Ideal for spherical objects like planets or heads
 */
package io.materia.geometry.uvgen

import io.materia.core.math.Sphere
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.*
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2

/**
 * UV generation options for spherical projection
 */
data class SphericalUVOptions(
    val poleThreshold: Float = 0.05f
)

/**
 * Generate UV coordinates using spherical projection
 * Ideal for spherical objects like planets or heads
 */
fun generateSphericalUV(
    geometry: BufferGeometry,
    options: SphericalUVOptions = SphericalUVOptions()
): UVGenerationResult {
    val positionAttribute = geometry.getAttribute("position")
        ?: return UVGenerationResult(geometry, false, "No position attribute found")

    val vertexCount = positionAttribute.count
    val uvCoordinates = FloatArray((vertexCount * 2))

    val boundingBox = geometry.computeBoundingBox()
    val center = boundingBox.getCenter(Vector3())
    val radius = boundingBox.getBoundingSphere(Sphere()).radius

    for (i in 0 until vertexCount) {
        val position = Vector3(
            positionAttribute.getX(i),
            positionAttribute.getY(i),
            positionAttribute.getZ(i)
        )

        val localPos = position.clone().subtract(center)
        val uv = projectToSphere(localPos, radius, options)

        uvCoordinates[(i * 2)] = uv.x
        uvCoordinates[i * 2 + 1] = uv.y
    }

    // Handle pole singularities
    val poleVertices = findSphericalPoles(uvCoordinates, options.poleThreshold)

    val resultGeometry = geometry.clone()
    resultGeometry.setAttribute("uv", BufferAttribute(uvCoordinates, 2))

    return UVGenerationResult(
        geometry = resultGeometry,
        success = true,
        message = "Spherical UV projection completed",
        seamVertices = poleVertices,
        uvBounds = calculateUVBounds(uvCoordinates)
    )
}

private fun projectToSphere(
    position: Vector3,
    radius: Float,
    options: SphericalUVOptions
): Vector2 {
    val posLength = position.length()
    val normalized = if (posLength > 0.001f) {
        position.clone().normalize()
    } else {
        // Handle center point by defaulting to top pole
        Vector3(0f, 1f, 0f)
    }

    // Calculate spherical coordinates
    val phi = acos(normalized.y.coerceIn(-1f, 1f)) // Latitude (0 to π)
    val theta = atan2(normalized.z, normalized.x) // Longitude (-π to π)

    val u = (theta + PI.toFloat()) / (2 * PI.toFloat())
    val v = phi / PI.toFloat()

    return Vector2(u, v)
}

private fun findSphericalPoles(
    uvCoordinates: FloatArray,
    poleThreshold: Float
): List<Int> {
    // Implementation would find vertices near UV poles
    return emptyList()
}
