package io.materia.clipping

import io.materia.core.math.Matrix4
import io.materia.core.math.Sphere
import io.materia.core.math.Vector3
import io.materia.material.Material
import io.materia.core.math.Plane as MathPlane

/**
 * Global and per-material clipping plane support.
 * Manages up to 8 clipping planes for scene-wide and material-specific clipping.
 */
class ClippingSupport {

    companion object {
        const val MAX_CLIPPING_PLANES = 8
        const val MAX_UNION_CLIPPING_PLANES = 4
    }

    /**
     * Global clipping planes applied to all objects in the scene.
     */
    var globalClippingPlanes: List<MathPlane> = emptyList()
        set(value) {
            require(value.size <= MAX_CLIPPING_PLANES) {
                "Maximum $MAX_CLIPPING_PLANES clipping planes supported, got ${value.size}"
            }
            field = value
            needsUpdate = true
        }

    /**
     * Whether clipping is enabled.
     */
    var enabled: Boolean = false

    /**
     * Whether to use union clipping (all planes must clip) or intersection (any plane clips).
     */
    var unionClipping: Boolean = false

    /**
     * Whether clipping planes affect shadows.
     */
    var clipShadows: Boolean = true

    /**
     * Whether clipping needs to be updated in shaders.
     */
    var needsUpdate: Boolean = true
        private set

    /**
     * Uniform data for clipping planes (packed for GPU).
     */
    private val uniformData = FloatArray(MAX_CLIPPING_PLANES * 4)

    /**
     * Get the combined clipping planes for an object.
     * Merges global and material-specific clipping planes.
     */
    fun getCombinedClippingPlanes(material: Material?): List<MathPlane> {
        if (!enabled) return emptyList()

        val materialPlanes = material?.clippingPlanes ?: emptyList()

        return when {
            materialPlanes.isEmpty() -> globalClippingPlanes
            globalClippingPlanes.isEmpty() -> materialPlanes
            else -> {
                // Combine global and material planes
                val combined = mutableListOf<MathPlane>()
                combined.addAll(globalClippingPlanes)
                combined.addAll(materialPlanes)

                // Limit to maximum supported planes
                if (combined.size > MAX_CLIPPING_PLANES) {
                    combined.take(MAX_CLIPPING_PLANES)
                } else {
                    combined
                }
            }
        }
    }

    /**
     * Update uniform data for clipping planes.
     * Transforms planes to view space and packs them for GPU upload.
     */
    fun updateUniforms(viewMatrix: Matrix4, material: Material? = null): FloatArray {
        val planes = getCombinedClippingPlanes(material)

        // Clear uniform data
        uniformData.fill(0f)

        // Pack plane data (normal.xyz, constant)
        planes.forEachIndexed { index, plane ->
            if (index < MAX_CLIPPING_PLANES) {
                // Transform plane to view space
                val viewPlane = plane.clone().applyMatrix4(viewMatrix)

                // Pack into uniform array
                val offset = index * 4
                uniformData[offset] = viewPlane.normal.x
                uniformData[offset + 1] = viewPlane.normal.y
                uniformData[offset + 2] = viewPlane.normal.z
                uniformData[offset + 3] = viewPlane.constant
            }
        }

        needsUpdate = false
        return uniformData
    }

    /**
     * Check if an object is clipped by all planes.
     * Used for CPU-side culling optimization.
     */
    fun isObjectClipped(
        boundingSphere: Sphere,
        modelMatrix: Matrix4,
        material: Material? = null
    ): Boolean {
        if (!enabled) return false

        val planes = getCombinedClippingPlanes(material)
        if (planes.isEmpty()) return false

        // Transform bounding sphere center to world space
        val worldCenter = boundingSphere.center.clone().applyMatrix4(modelMatrix)

        // Get maximum scale from matrix for radius
        val maxScale = modelMatrix.getMaxScaleOnAxis()
        val worldRadius = boundingSphere.radius * maxScale

        // Check against each plane
        for (plane in planes) {
            val distance = plane.distanceToPoint(worldCenter)

            // If sphere is completely behind any plane in intersection mode, it's clipped
            if (!unionClipping && distance < -worldRadius) {
                return true
            }

            // For union mode, track if sphere is in front of all planes
            if (unionClipping && distance >= -worldRadius) {
                return false
            }
        }

        // For union mode, object is clipped only if behind all planes
        return unionClipping
    }

    /**
     * Create shader uniforms declaration for clipping.
     */
    fun getShaderUniforms(): String {
        return """
            uniform vec4 clippingPlanes[$MAX_CLIPPING_PLANES];
            uniform int numClippingPlanes;
            uniform bool unionClipping;
        """.trimIndent()
    }

    /**
     * Create shader code for clipping test.
     */
    fun getShaderClippingCode(): String {
        return """
            float clippingPlanesDistance = 0.0;

            if (numClippingPlanes > 0) {
                vec4 plane;

                if (unionClipping) {
                    // Union mode: must be behind all planes to be clipped
                    bool clipped = true;

                    for (int i = 0; i < numClippingPlanes; i++) {
                        plane = clippingPlanes[i];
                        float distance = dot(vWorldPosition, plane.xyz) + plane.w;

                        if (distance > 0.0) {
                            clipped = false;
                            break;
                        }

                        clippingPlanesDistance = max(clippingPlanesDistance, distance);
                    }

                    if (clipped) discard;
                } else {
                    // Intersection mode: clip if behind any plane
                    for (int i = 0; i < numClippingPlanes; i++) {
                        plane = clippingPlanes[i];
                        float distance = dot(vWorldPosition, plane.xyz) + plane.w;

                        if (distance < 0.0) discard;

                        clippingPlanesDistance = min(clippingPlanesDistance, distance);
                    }
                }
            }
        """.trimIndent()
    }

    /**
     * Reset clipping planes.
     */
    fun clear() {
        globalClippingPlanes = emptyList()
        enabled = false
        needsUpdate = true
    }
}

// Note: Material class already has clippingPlanes, clipIntersection, and clipShadows properties
// No need for extension properties as they are shadowed by member properties

/**
 * Helper to create common clipping plane setups.
 */
object ClippingPlanePresets {

    /**
     * Create a box clipping setup (6 planes).
     */
    fun createBoxClipping(
        center: Vector3 = Vector3(),
        size: Vector3 = Vector3(1f, 1f, 1f)
    ): List<MathPlane> {
        val halfSize = size.clone().multiplyScalar(0.5f)

        return listOf(
            // +X face
            MathPlane(Vector3(1f, 0f, 0f), -(center.x + halfSize.x)),
            // -X face
            MathPlane(Vector3(-1f, 0f, 0f), center.x - halfSize.x),
            // +Y face
            MathPlane(Vector3(0f, 1f, 0f), -(center.y + halfSize.y)),
            // -Y face
            MathPlane(Vector3(0f, -1f, 0f), center.y - halfSize.y),
            // +Z face
            MathPlane(Vector3(0f, 0f, 1f), -(center.z + halfSize.z)),
            // -Z face
            MathPlane(Vector3(0f, 0f, -1f), center.z - halfSize.z)
        )
    }

    /**
     * Create a sphere clipping setup (approximated with planes).
     */
    fun createSphereClipping(center: Vector3, radius: Float, segments: Int = 8): List<MathPlane> {
        val planes = mutableListOf<MathPlane>()

        for (i in 0 until segments) {
            val angle = (i * 2 * kotlin.math.PI / segments).toFloat()
            val normal = Vector3(
                kotlin.math.cos(angle),
                0f,
                kotlin.math.sin(angle)
            )

            planes.add(
                MathPlane(normal, -radius).translate(center)
            )
        }

        return planes
    }

    /**
     * Create a single cutting plane.
     */
    fun createCuttingPlane(
        normal: Vector3,
        point: Vector3
    ): List<MathPlane> {
        return listOf(MathPlane().setFromNormalAndCoplanarPoint(normal, point))
    }
}

fun Matrix4.getMaxScaleOnAxis(): Float {
    // Implementation would calculate maximum scale from matrix
    return 1f
}