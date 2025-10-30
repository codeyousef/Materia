/**
 * Box UV Projection
 * Projects geometry onto 6 faces of a bounding box
 */
package io.materia.geometry.uvgen

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Box face enumeration for box projection
 */
enum class BoxFace {
    POSITIVE_X, NEGATIVE_X, POSITIVE_Y, NEGATIVE_Y, POSITIVE_Z, NEGATIVE_Z
}

/**
 * UV transformation parameters
 */
data class UVTransform(
    val offset: Vector2 = Vector2(),
    val scale: Vector2 = Vector2(1f, 1f),
    val rotation: Float = 0f
)

/**
 * UV generation options for box projection
 */
data class BoxUVOptions(
    val faceMapping: Map<BoxFace, Vector2> = emptyMap(),
    val seamAngle: Float = 0.5f, // ~30 degrees
    val handleSeams: Boolean = true,
    val uvTransform: UVTransform = UVTransform()
)

/**
 * Generate UV coordinates using box projection
 * Projects geometry onto 6 faces of a bounding box
 */
fun generateBoxUV(
    geometry: BufferGeometry,
    options: BoxUVOptions = BoxUVOptions()
): UVGenerationResult {
    val positionAttribute = geometry.getAttribute("position")
        ?: return UVGenerationResult(geometry, false, "No position attribute found")

    val normalAttribute = geometry.getAttribute("normal")
        ?: GeometryProcessor().generateSmoothNormals(geometry).getAttribute("normal")
        ?: return UVGenerationResult(geometry, false, "Could not generate normals")

    val vertexCount = positionAttribute.count
    val uvCoordinates = FloatArray((vertexCount * 2))
    val seamVertices = mutableSetOf<Int>()

    // Calculate bounding box for normalization
    val boundingBox = geometry.computeBoundingBox()
    val size = boundingBox.getSize(Vector3())
    val center = boundingBox.getCenter(Vector3())

    for (i in 0 until vertexCount) {
        val position = Vector3(
            positionAttribute.getX(i),
            positionAttribute.getY(i),
            positionAttribute.getZ(i)
        )
        val normal = Vector3(
            normalAttribute.getX(i),
            normalAttribute.getY(i),
            normalAttribute.getZ(i)
        )

        // Determine dominant axis based on normal
        val face = getDominantFace(normal)
        val localPos = position.clone().subtract(center)

        // Project onto appropriate face
        val uv = projectToBoxFace(localPos, size, face, options.faceMapping)

        uvCoordinates[(i * 2)] = uv.x
        uvCoordinates[i * 2 + 1] = uv.y

        // Mark seam vertices
        if (isSeamVertex(normal, options.seamAngle)) {
            seamVertices.add(i)
        }
    }

    // Apply UV transformations
    applyUVTransformations(uvCoordinates, options.uvTransform)

    // Handle seams if requested
    if (options.handleSeams) {
        handleBoxProjectionSeams(geometry, uvCoordinates, seamVertices)
    }

    val resultGeometry = geometry.clone()
    resultGeometry.setAttribute("uv", BufferAttribute(uvCoordinates, 2))

    return UVGenerationResult(
        geometry = resultGeometry,
        success = true,
        message = "Box UV projection completed",
        seamVertices = seamVertices.toList(),
        uvBounds = calculateUVBounds(uvCoordinates)
    )
}

private fun getDominantFace(normal: Vector3): BoxFace {
    val absNormal = Vector3(abs(normal.x), abs(normal.y), abs(normal.z))

    return when {
        absNormal.x >= absNormal.y && absNormal.x >= absNormal.z -> {
            if (normal.x > 0) BoxFace.POSITIVE_X else BoxFace.NEGATIVE_X
        }

        absNormal.y >= absNormal.z -> {
            if (normal.y > 0) BoxFace.POSITIVE_Y else BoxFace.NEGATIVE_Y
        }

        else -> {
            if (normal.z > 0) BoxFace.POSITIVE_Z else BoxFace.NEGATIVE_Z
        }
    }
}

private fun projectToBoxFace(
    position: Vector3,
    size: Vector3,
    face: BoxFace,
    faceMapping: Map<BoxFace, Vector2>
): Vector2 {
    val normalizedPos = Vector3(
        position.x / size.x,
        position.y / size.y,
        position.z / size.z
    )

    return when (face) {
        BoxFace.POSITIVE_X -> Vector2(normalizedPos.z + 0.5f, normalizedPos.y + 0.5f)
        BoxFace.NEGATIVE_X -> Vector2(-normalizedPos.z + 0.5f, normalizedPos.y + 0.5f)
        BoxFace.POSITIVE_Y -> Vector2(normalizedPos.x + 0.5f, normalizedPos.z + 0.5f)
        BoxFace.NEGATIVE_Y -> Vector2(normalizedPos.x + 0.5f, -normalizedPos.z + 0.5f)
        BoxFace.POSITIVE_Z -> Vector2(-normalizedPos.x + 0.5f, normalizedPos.y + 0.5f)
        BoxFace.NEGATIVE_Z -> Vector2(normalizedPos.x + 0.5f, normalizedPos.y + 0.5f)
    }.apply {
        // Apply face-specific UV offset from mapping
        val offset = faceMapping[face] ?: Vector2()
        add(offset)
    }
}

private fun isSeamVertex(normal: Vector3, seamAngle: Float): Boolean {
    // Detect if this vertex is on a UV seam based on normal angle changes
    val absNormal = Vector3(abs(normal.x), abs(normal.y), abs(normal.z))

    // Check if normal is near an edge between box faces
    val threshold = cos(seamAngle)
    val nearEdge = (absNormal.x < threshold && absNormal.y < threshold) ||
            (absNormal.y < threshold && absNormal.z < threshold) ||
            (absNormal.x < threshold && absNormal.z < threshold)

    return nearEdge
}

private fun applyUVTransformations(uvCoordinates: FloatArray, transform: UVTransform) {
    for (i in uvCoordinates.indices step 2) {
        var u = uvCoordinates[i]
        var v = uvCoordinates[i + 1]

        // Apply offset
        u = u + transform.offset.x
        v = v + transform.offset.y

        // Apply scale
        u = u * transform.scale.x
        v = v * transform.scale.y

        // Apply rotation
        if (transform.rotation != 0f) {
            val cosR = cos(transform.rotation)
            val sinR = sin(transform.rotation)
            val centerU = u - 0.5f
            val centerV = v - 0.5f

            u = centerU * cosR - centerV * sinR + 0.5f
            v = centerU * sinR + centerV * cosR + 0.5f
        }

        uvCoordinates[i] = u
        uvCoordinates[i + 1] = v
    }
}

private fun handleBoxProjectionSeams(
    geometry: BufferGeometry,
    uvCoordinates: FloatArray,
    seamVertices: Set<Int>
) {
    // Implementation would handle UV seams in box projection
}
