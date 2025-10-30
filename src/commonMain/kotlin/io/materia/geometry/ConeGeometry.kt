/**
 * Cone geometry implementation following Three.js r180 API
 * A cone is a cylinder with radiusTop = 0
 */
package io.materia.geometry

import io.materia.geometry.primitives.CylinderGeometry
import kotlin.math.PI

/**
 * Cone geometry with configurable radius, height, and subdivision
 * Extends CylinderGeometry with radiusTop fixed at 0
 *
 * @param radius Radius of the cone base (default: 1)
 * @param height Height of the cone (default: 1)
 * @param radialSegments Number of segmented faces around the circumference (default: 32)
 * @param heightSegments Number of rows of faces along the height (default: 1)
 * @param openEnded Whether the base is open or capped (default: false)
 * @param thetaStart Start angle for first segment (default: 0)
 * @param thetaLength Central angle of the circular sector (default: 2π)
 */
class ConeGeometry(
    radius: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat() * 2f
) : CylinderGeometry(
    radiusTop = 0f,  // Cone has zero radius at top
    radiusBottom = radius,
    height = height,
    radialSegments = radialSegments,
    heightSegments = heightSegments,
    openEnded = openEnded,
    thetaStart = thetaStart,
    thetaLength = thetaLength
) {

    /**
     * Cone-specific parameters (convenience wrapper)
     */
    class ConeParameters(
        var radius: Float,
        var height: Float,
        var radialSegments: Int,
        var heightSegments: Int,
        var openEnded: Boolean,
        var thetaStart: Float,
        var thetaLength: Float
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            height: Float = this.height,
            radialSegments: Int = this.radialSegments,
            heightSegments: Int = this.heightSegments,
            openEnded: Boolean = this.openEnded,
            thetaStart: Float = this.thetaStart,
            thetaLength: Float = this.thetaLength
        ) {
            if (this.radius != radius || this.height != height ||
                this.radialSegments != radialSegments || this.heightSegments != heightSegments ||
                this.openEnded != openEnded || this.thetaStart != thetaStart ||
                this.thetaLength != thetaLength
            ) {

                this.radius = radius
                this.height = height
                this.radialSegments = radialSegments
                this.heightSegments = heightSegments
                this.openEnded = openEnded
                this.thetaStart = thetaStart
                this.thetaLength = thetaLength
                markDirty()
            }
        }
    }

    /**
     * Expose cone-specific parameters for convenience
     * (internally wraps the cylinder parameters)
     */
    val coneParameters = ConeParameters(
        radius, height, radialSegments, heightSegments,
        openEnded, thetaStart, thetaLength
    )

    /**
     * Update cone parameters
     * This is a convenience method that updates the underlying cylinder parameters
     */
    fun setConeParameters(
        radius: Float = coneParameters.radius,
        height: Float = coneParameters.height,
        radialSegments: Int = coneParameters.radialSegments,
        heightSegments: Int = coneParameters.heightSegments,
        openEnded: Boolean = coneParameters.openEnded,
        thetaStart: Float = coneParameters.thetaStart,
        thetaLength: Float = coneParameters.thetaLength
    ) {
        coneParameters.set(
            radius,
            height,
            radialSegments,
            heightSegments,
            openEnded,
            thetaStart,
            thetaLength
        )

        // Update the underlying cylinder parameters
        setParameters(
            radiusTop = 0f,  // Always 0 for cone
            radiusBottom = radius,
            height = height,
            radialSegments = radialSegments,
            heightSegments = heightSegments,
            openEnded = openEnded,
            thetaStart = thetaStart,
            thetaLength = thetaLength
        )
    }
}
