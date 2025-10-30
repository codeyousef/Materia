package io.materia.core.math

import io.materia.core.math.Box3

import kotlin.math.*
import io.materia.core.platform.platformClone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sphere represented by center point and radius.
 * Compatible with Three.js Sphere API.
 *
 * Used for bounding volumes, collision detection, and intersection tests.
 */
data class Sphere(
    val center: Vector3 = Vector3(),
    var radius: Float = -1f
) {

    companion object {
        /**
         * Creates a sphere from a center point and radius
         */
        fun fromCenterAndRadius(center: Vector3, radius: Float): Sphere {
            return Sphere(center.clone(), radius)
        }

        /**
         * Creates a sphere from an array of points
         */
        fun fromPoints(points: Array<Vector3>, optionalCenter: Vector3? = null): Sphere {
            val sphere = Sphere()
            return sphere.setFromPoints(points, optionalCenter)
        }

        /**
         * Creates a sphere from a list of points
         */
        fun fromPoints(points: List<Vector3>, optionalCenter: Vector3? = null): Sphere {
            val sphere = Sphere()
            return sphere.setFromPoints(points, optionalCenter)
        }

        /**
         * Creates a sphere from a Box3
         */
        fun fromBox3(box: Box3): Sphere {
            val sphere = Sphere()
            return sphere.setFromBox3(box)
        }

        /**
         * Creates a sphere from a BufferAttribute.
         * Compatible with Three.js BufferAttribute pattern.
         */
        fun fromBufferAttribute(attribute: io.materia.geometry.BufferAttribute): Sphere {
            val sphere = Sphere()
            val points = mutableListOf<Vector3>()
            val tempVector = Vector3()
            for (i in 0 until attribute.count) {
                tempVector.fromBufferAttribute(attribute, i)
                points.add(tempVector.clone())
            }
            return sphere.setFromPoints(points)
        }
    }

    /**
     * Sets the sphere's center and radius
     */
    fun set(center: Vector3, radius: Float): Sphere {
        this.center.copy(center)
        this.radius = radius
        return this
    }

    /**
     * Creates a copy of this sphere
     */
    fun clone(): Sphere {
        return Sphere(center.clone(), radius)
    }

    /**
     * Copies values from another sphere
     */
    fun copy(sphere: Sphere): Sphere {
        center.copy(sphere.center)
        radius = sphere.radius
        return this
    }

    /**
     * Makes this sphere empty (negative radius)
     */
    fun makeEmpty(): Sphere {
        center.set(0f, 0f, 0f)
        radius = -1f
        return this
    }

    /**
     * Checks if this sphere is empty
     */
    fun isEmpty(): Boolean {
        return radius < 0f
    }

    /**
     * Sets this sphere to contain all points in the array
     */
    fun setFromPoints(points: Array<Vector3>, optionalCenter: Vector3? = null): Sphere {
        if (points.isEmpty()) {
            return makeEmpty()
        }

        val center = optionalCenter ?: run {
            // Calculate centroid
            val centroid = Vector3()
            points.forEach { centroid.add(it) }
            centroid.divideScalar(points.size.toFloat())
        }

        this.center.copy(center)

        var maxRadiusSq = 0f
        for (point in points) {
            maxRadiusSq = maxOf(maxRadiusSq, this.center.distanceToSquared(point))
        }

        radius = sqrt(maxRadiusSq)
        return this
    }

    /**
     * Sets this sphere to contain all points in the list
     */
    fun setFromPoints(points: List<Vector3>, optionalCenter: Vector3? = null): Sphere {
        return setFromPoints(points.toTypedArray(), optionalCenter)
    }

    /**
     * Sets this sphere to contain a Box3
     */
    fun setFromBox3(box: Box3): Sphere {
        if (box.isEmpty()) {
            return makeEmpty()
        }

        box.getCenter(center)
        radius = box.getSize().length() * 0.5f
        return this
    }

    /**
     * Checks if a point is contained within this sphere
     */
    fun containsPoint(point: Vector3): Boolean {
        return point.distanceToSquared(center) <= ((radius * radius))
    }

    /**
     * Calculates the distance from the surface of this sphere to a point
     */
    fun distanceToPoint(point: Vector3): Float {
        return point.distanceTo(center) - radius
    }

    /**
     * Tests if this sphere intersects another sphere
     */
    fun intersectsSphere(sphere: Sphere): Boolean {
        val radiusSum = radius + sphere.radius
        return sphere.center.distanceToSquared(center) <= ((radiusSum * radiusSum))
    }

    /**
     * Tests if this sphere intersects a box
     */
    fun intersectsBox(box: Box3): Boolean {
        return box.distanceToPoint(center) <= radius
    }

    /**
     * Tests if this sphere intersects a plane
     */
    fun intersectsPlane(plane: Plane): Boolean {
        return abs(plane.distanceToPoint(center)) <= radius
    }

    /**
     * Clamps a point to the surface of this sphere
     */
    fun clampPoint(point: Vector3): Vector3 {
        val result = Vector3()
        return clampPoint(point, result)
    }

    /**
     * Clamps a point to the surface of this sphere and stores it in target
     */
    fun clampPoint(point: Vector3, target: Vector3): Vector3 {
        val deltaLengthSq = center.distanceToSquared(point)

        target.copy(point)

        if (deltaLengthSq > ((radius * radius))) {
            target.sub(center).normalize()
            target.multiplyScalar(radius).add(center)
        }

        return target
    }

    /**
     * Gets a bounding box that contains this sphere
     */
    fun getBoundingBox(): Box3 {
        val result = Box3()
        return getBoundingBox(result)
    }

    /**
     * Gets a bounding box that contains this sphere and stores it in target
     */
    fun getBoundingBox(target: Box3): Box3 {
        if (isEmpty()) {
            target.makeEmpty()
            return target
        }

        target.set(center, center)
        target.expandByScalar(radius)

        return target
    }

    /**
     * Expands this sphere to include a point
     */
    fun expandByPoint(point: Vector3): Sphere {
        if (isEmpty()) {
            center.copy(point)
            radius = 0f
            return this
        }

        val lengthSq = center.distanceToSquared(point)

        if (lengthSq > ((radius * radius))) {
            val length = sqrt(lengthSq)
            val newRadius = (radius + length) * 0.5f
            val alpha = (newRadius - radius) / length

            center.lerp(point, alpha)
            radius = newRadius
        }

        return this
    }

    /**
     * Unions this sphere with another sphere
     */
    fun union(sphere: Sphere): Sphere {
        if (sphere.isEmpty()) {
            return this
        }

        if (isEmpty()) {
            return copy(sphere)
        }

        if (center.equals(sphere.center)) {
            radius = maxOf(radius, sphere.radius)
            return this
        }

        val distance = center.distanceTo(sphere.center)

        if (distance + sphere.radius <= radius) {
            // sphere is inside this sphere
            return this
        }

        if (distance + radius <= sphere.radius) {
            // this sphere is inside sphere
            return copy(sphere)
        }

        // Spheres partially overlap or are separate
        val newRadius = (radius + sphere.radius + distance) * 0.5f
        val alpha = (newRadius - radius) / distance

        center.lerp(sphere.center, alpha)
        radius = newRadius

        return this
    }

    /**
     * Applies a 4x4 transformation matrix to this sphere
     */
    fun applyMatrix4(matrix: Matrix4): Sphere {
        center.applyMatrix4(matrix)
        radius = radius * matrix.getMaxScaleOnAxis()
        return this
    }

    /**
     * Translates this sphere by an offset
     */
    fun translate(offset: Vector3): Sphere {
        center.add(offset)
        return this
    }

    /**
     * Expands this sphere by a scalar
     */
    fun expandByScalar(scalar: Float): Sphere {
        radius = radius + scalar
        return this
    }

    /**
     * Checks if this sphere equals another sphere within tolerance
     */
    fun equals(sphere: Sphere, tolerance: Float = 1e-6f): Boolean {
        return center.equals(sphere.center, tolerance) &&
                abs(radius - sphere.radius) < tolerance
    }

    /**
     * Gets the volume of this sphere
     */
    fun getVolume(): Float {
        return (4f / 3f) * PI.toFloat() * radius * (radius * radius)
    }

    /**
     * Gets the surface area of this sphere
     */
    fun getSurfaceArea(): Float {
        return 4f * PI.toFloat() * (radius * radius)
    }

    /**
     * Gets a point on the surface of the sphere using spherical coordinates
     */
    fun getPointOnSurface(theta: Float, phi: Float): Vector3 {
        val result = Vector3()
        return getPointOnSurface(theta, phi, result)
    }

    /**
     * Gets a point on the surface of the sphere using spherical coordinates
     * @param theta azimuthal angle (0 to 2π)
     * @param phi polar angle (0 to π)
     */
    fun getPointOnSurface(theta: Float, phi: Float, target: Vector3): Vector3 {
        val sinPhi = sin(phi)
        target.set(
            radius * sinPhi * cos(theta),
            radius * cos(phi),
            radius * sinPhi * sin(theta)
        )
        target.add(center)
        return target
    }

    /**
     * Gets a random point on the surface of the sphere
     */
    fun getRandomPointOnSurface(): Vector3 {
        val result = Vector3()
        return getRandomPointOnSurface(result)
    }

    /**
     * Gets a random point on the surface of the sphere and stores it in target
     */
    fun getRandomPointOnSurface(target: Vector3): Vector3 {
        val theta = kotlin.random.Random.nextFloat() * 2f * PI.toFloat()
        val phi = acos(2f * kotlin.random.Random.nextFloat() - 1f)
        return getPointOnSurface(theta, phi, target)
    }

    /**
     * Gets a random point inside the sphere
     */
    fun getRandomPointInside(): Vector3 {
        val result = Vector3()
        return getRandomPointInside(result)
    }

    /**
     * Gets a random point inside the sphere and stores it in target
     */
    fun getRandomPointInside(target: Vector3): Vector3 {
        val r = radius * kotlin.random.Random.nextFloat().pow(1f / 3f)
        val theta = kotlin.random.Random.nextFloat() * 2f * PI.toFloat()
        val phi = acos(2f * kotlin.random.Random.nextFloat() - 1f)

        val sinPhi = sin(phi)
        target.set(
            r * sinPhi * cos(theta),
            r * cos(phi),
            r * sinPhi * sin(theta)
        )
        target.add(center)
        return target
    }

    override fun toString(): String {
        return "Sphere(center=$center, radius=$radius)"
    }
}

/**
 * Extension function for Matrix4 to get maximum scale factor on any axis
 */
fun Matrix4.getMaxScaleOnAxis(): Float {
    val scaleXSq = elements[0] * elements[0] + elements[1] * elements[1] + elements[2] * elements[2]
    val scaleYSq = elements[4] * elements[4] + elements[5] * elements[5] + elements[6] * elements[6]
    val scaleZSq =
        elements[8] * elements[8] + elements[9] * elements[9] + elements[10] * elements[10]

    return sqrt(maxOf(scaleXSq, scaleYSq, scaleZSq))
}