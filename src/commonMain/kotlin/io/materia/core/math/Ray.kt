package io.materia.core.math

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A ray represented by an origin point and a direction vector.
 * Compatible with Three.js Ray API.
 *
 * Used for raycasting, collision detection, and intersection tests.
 */
data class Ray(
    val origin: Vector3 = Vector3(),
    val direction: Vector3 = Vector3(0f, 0f, -1f)
) {

    companion object {
        /**
         * Creates a ray from two points
         */
        fun fromPoints(from: Vector3, to: Vector3): Ray {
            return Ray(from.clone(), to.clone().sub(from).normalize())
        }

        /**
         * Creates a ray from origin and direction
         */
        fun from(origin: Vector3, direction: Vector3): Ray {
            return Ray(origin.clone(), direction.clone().normalize())
        }
    }

    /**
     * Sets the ray's origin and direction
     */
    fun set(origin: Vector3, direction: Vector3): Ray {
        this.origin.copy(origin)
        this.direction.copy(direction).normalize()
        return this
    }

    /**
     * Creates a copy of this ray
     */
    fun clone(): Ray {
        return Ray(origin.clone(), direction.clone())
    }

    /**
     * Copies values from another ray
     */
    fun copy(ray: Ray): Ray {
        origin.copy(ray.origin)
        direction.copy(ray.direction)
        return this
    }

    /**
     * Gets a point along the ray at distance t
     */
    fun at(t: Float): Vector3 {
        return Vector3().copy(direction).multiplyScalar(t).add(origin)
    }

    /**
     * Gets a point along the ray at distance t and stores it in target
     */
    fun at(t: Float, target: Vector3): Vector3 {
        return target.copy(direction).multiplyScalar(t).add(origin)
    }

    /**
     * Looks at a target point
     */
    fun lookAt(target: Vector3): Ray {
        direction.copy(target).sub(origin).normalize()
        return this
    }

    /**
     * Resets the ray to point down the negative Z axis
     */
    fun recast(t: Float): Ray {
        origin.copy(at(t))
        return this
    }

    /**
     * Finds the closest point on this ray to a given point
     */
    fun closestPointToPoint(point: Vector3): Vector3 {
        val result = Vector3()
        return closestPointToPoint(point, result)
    }

    /**
     * Finds the closest point on this ray to a given point and stores it in target
     */
    fun closestPointToPoint(point: Vector3, target: Vector3): Vector3 {
        target.copy(point).sub(origin)
        val directionDistance = target.dot(direction)

        return if (directionDistance < 0f) {
            target.copy(origin)
        } else {
            target.copy(direction).multiplyScalar(directionDistance).add(origin)
        }
    }

    /**
     * Calculates the distance from this ray to a point
     */
    fun distanceToPoint(point: Vector3): Float {
        return sqrt(distanceSqToPoint(point))
    }

    /**
     * Calculates the squared distance from this ray to a point
     */
    fun distanceSqToPoint(point: Vector3): Float {
        val v1 = Vector3().copy(point).sub(origin)
        val directionDistance = v1.dot(direction)

        // Point behind the ray
        if (directionDistance < 0f) {
            return origin.distanceToSquared(point)
        }

        v1.copy(direction).multiplyScalar(directionDistance).add(origin)
        return v1.distanceToSquared(point)
    }

    /**
     * Calculates the distance from this ray to a line segment
     */
    fun distanceSqToSegment(v0: Vector3, v1: Vector3): DistanceResult {
        val segCenter = Vector3().copy(v0).add(v1).multiplyScalar(0.5f)
        val segDir = Vector3().copy(v1).sub(v0).normalize()
        val segExtent = v0.distanceTo(v1) * 0.5f

        val diff = Vector3().copy(origin).sub(segCenter)

        val a01 = -direction.dot(segDir)
        val b0 = diff.dot(direction)
        val b1 = -diff.dot(segDir)
        val c = diff.lengthSq()
        val det = abs(1f - (a01 * a01))

        var s0: Float
        var s1: Float
        var sqrDist: Float

        if (det > 0f) {
            // The ray and segment are not parallel
            s0 = a01 * b1 - b0
            s1 = a01 * b0 - b1

            val extDet = segExtent * det

            if (s0 >= 0f) {
                if (s1 >= -extDet) {
                    if (s1 <= extDet) {
                        // Region 0
                        val invDet = 1f / det
                        s0 = s0 * invDet
                        s1 = s1 * invDet
                        sqrDist =
                            s0 * (s0 + a01 * s1 + (2f * b0)) + s1 * (a01 * s0 + s1 + (2f * b1)) + c
                    } else {
                        // Region 1
                        s1 = segExtent
                        s0 = maxOf(0f, -(a01 * s1 + b0))
                        sqrDist = -s0 * s0 + s1 * (s1 + (2f * b1)) + c
                    }
                } else {
                    // Region 5
                    s1 = -segExtent
                    s0 = maxOf(0f, -(a01 * s1 + b0))
                    sqrDist = -s0 * s0 + s1 * (s1 + (2f * b1)) + c
                }
            } else {
                if (s1 <= -extDet) {
                    // Region 4
                    s0 = maxOf(0f, -(-a01 * segExtent + b0))
                    s1 = if (s0 > 0f) -segExtent else minOf(maxOf(-segExtent, -b1), segExtent)
                    sqrDist = -s0 * s0 + s1 * (s1 + (2f * b1)) + c
                } else if (s1 <= extDet) {
                    // Region 3
                    s0 = 0f
                    s1 = minOf(maxOf(-segExtent, -b1), segExtent)
                    sqrDist = s1 * (s1 + (2f * b1)) + c
                } else {
                    // Region 2
                    s0 = maxOf(0f, -(a01 * segExtent + b0))
                    s1 = if (s0 > 0f) segExtent else minOf(maxOf(-segExtent, -b1), segExtent)
                    sqrDist = -s0 * s0 + s1 * (s1 + (2f * b1)) + c
                }
            }
        } else {
            // Ray and segment are parallel
            s1 = if (a01 > 0f) -segExtent else segExtent
            s0 = maxOf(0f, -(a01 * s1 + b0))
            sqrDist = -s0 * s0 + s1 * (s1 + (2f * b1)) + c
        }

        val closestPointOnRay = Vector3().copy(direction).multiplyScalar(s0).add(origin)
        val closestPointOnSegment = Vector3().copy(segDir).multiplyScalar(s1).add(segCenter)

        return DistanceResult(sqrDist, s0, s1, closestPointOnRay, closestPointOnSegment)
    }

    /**
     * Tests intersection with a sphere and returns intersection distances
     * @return Vector2 with x = near distance, y = far distance, or null if no intersection
     */
    fun intersectSphere(sphere: Sphere): Vector2? {
        val v1 = Vector3().copy(sphere.center).sub(origin)
        val tca = v1.dot(direction)
        val d2 = v1.dot(v1) - tca * tca
        val radius2 = sphere.radius * sphere.radius

        if (d2 > radius2) return null

        val thc = sqrt(radius2 - d2)
        val t0 = tca - thc
        val t1 = tca + thc

        // Both t0 and t1 are negative, sphere is behind ray
        if (t0 < 0f && t1 < 0f) return null

        return Vector2(t0, t1)
    }

    /**
     * Tests intersection with a sphere and returns intersection point
     */
    fun intersectSpherePoint(sphere: Sphere): Vector3? {
        val result = Vector3()
        return if (intersectSpherePoint(sphere, result)) result else null
    }

    /**
     * Tests intersection with a sphere and stores result in target
     */
    fun intersectSpherePoint(sphere: Sphere, target: Vector3): Boolean {
        val v1 = Vector3().copy(sphere.center).sub(origin)
        val tca = v1.dot(direction)
        val d2 = v1.dot(v1) - tca * tca
        val radius2 = sphere.radius * sphere.radius

        if (d2 > radius2) return false

        val thc = sqrt(radius2 - d2)
        val t0 = tca - thc
        val t1 = tca + thc

        // Both t0 and t1 are negative, sphere is behind ray
        if (t0 < 0f && t1 < 0f) return false

        // Use the nearest positive intersection
        val t = if (t0 < 0f) t1 else t0

        at(t, target)
        return true
    }

    /**
     * Tests if this ray intersects a sphere
     */
    fun intersectsSphere(sphere: Sphere): Boolean {
        return distanceSqToPoint(sphere.center) <= (sphere.radius * sphere.radius)
    }

    /**
     * Tests intersection with a plane
     */
    fun intersectPlane(plane: Plane): Vector3? {
        val result = Vector3()
        return if (intersectPlane(plane, result)) result else null
    }

    /**
     * Tests intersection with a plane and stores result in target
     */
    fun intersectPlane(plane: Plane, target: Vector3): Boolean {
        val denominator = plane.normal.dot(direction)

        if (abs(denominator) < 0.000001f) {
            // Ray is parallel to plane
            return if (abs(plane.distanceToPoint(origin)) < 0.000001f) {
                // Ray lies in plane
                target.copy(origin)
                true
            } else {
                false
            }
        }

        val t = -(origin.dot(plane.normal) + plane.constant) / denominator

        if (t < 0f) return false

        at(t, target)
        return true
    }

    /**
     * Tests if this ray intersects a plane
     */
    fun intersectsPlane(plane: Plane): Boolean {
        val denominator = plane.normal.dot(direction)
        if (abs(denominator) < 0.000001f) {
            return abs(plane.distanceToPoint(origin)) < 0.000001f
        }
        val t = -(origin.dot(plane.normal) + plane.constant) / denominator
        return t >= 0f
    }

    /**
     * Tests intersection with a box
     */
    fun intersectBox(box: Box3): Vector3? {
        val result = Vector3()
        return if (intersectBox(box, result)) result else null
    }

    /**
     * Tests intersection with a box and stores result in target
     */
    fun intersectBox(box: Box3, target: Vector3): Boolean {
        var tmin: Float
        var tmax: Float

        val invdirx = 1f / direction.x
        val invdiry = 1f / direction.y
        val invdirz = 1f / direction.z

        if (invdirx >= 0f) {
            tmin = (box.min.x - origin.x) * invdirx
            tmax = (box.max.x - origin.x) * invdirx
        } else {
            tmin = (box.max.x - origin.x) * invdirx
            tmax = (box.min.x - origin.x) * invdirx
        }

        val tymin: Float
        val tymax: Float

        if (invdiry >= 0f) {
            tymin = (box.min.y - origin.y) * invdiry
            tymax = (box.max.y - origin.y) * invdiry
        } else {
            tymin = (box.max.y - origin.y) * invdiry
            tymax = (box.min.y - origin.y) * invdiry
        }

        if (tmin > tymax || tymin > tmax) return false

        if (tymin > tmin || tmin.isNaN()) tmin = tymin
        if (tymax < tmax || tmax.isNaN()) tmax = tymax

        val tzmin: Float
        val tzmax: Float

        if (invdirz >= 0f) {
            tzmin = (box.min.z - origin.z) * invdirz
            tzmax = (box.max.z - origin.z) * invdirz
        } else {
            tzmin = (box.max.z - origin.z) * invdirz
            tzmax = (box.min.z - origin.z) * invdirz
        }

        if (tmin > tzmax || tzmin > tmax) return false

        if (tzmin > tmin || tmin.isNaN()) tmin = tzmin
        if (tzmax < tmax || tmax.isNaN()) tmax = tzmax

        // Return intersection point closest to ray origin
        if (tmax < 0f) return false

        at(if (tmin >= 0f) tmin else tmax, target)
        return true
    }

    /**
     * Tests if this ray intersects a box
     */
    fun intersectsBox(box: Box3): Boolean {
        return intersectBox(box) != null
    }

    /**
     * Applies a 4x4 transformation matrix to this ray
     */
    fun applyMatrix4(matrix: Matrix4): Ray {
        origin.applyMatrix4(matrix)
        direction.transformDirection(matrix)
        return this
    }

    /**
     * Checks if this ray equals another ray within tolerance
     */
    fun equals(ray: Ray, tolerance: Float = 1e-6f): Boolean {
        return origin.equals(ray.origin, tolerance) &&
                direction.equals(ray.direction, tolerance)
    }

    override fun toString(): String {
        return "Ray(origin=$origin, direction=$direction)"
    }
}

/**
 * Result of distance calculation to segment
 */
data class DistanceResult(
    val distanceSq: Float,
    val rayParameter: Float,
    val segmentParameter: Float,
    val closestPointOnRay: Vector3,
    val closestPointOnSegment: Vector3
) {
    val distance: Float get() = sqrt(distanceSq)
}