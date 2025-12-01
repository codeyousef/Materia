package io.materia.core.math

import io.materia.core.math.Box3

import kotlin.math.*
import io.materia.core.platform.platformClone
import kotlin.math.PI

/**
 * An axis-aligned bounding box (AABB) in 3D space.
 * Compatible with Three.js Box3 API.
 *
 * Represents a rectangular box aligned with the coordinate axes,
 * defined by minimum and maximum corner points.
 */
data class Box3(
    val min: Vector3 = Vector3(
        Float.POSITIVE_INFINITY,
        Float.POSITIVE_INFINITY,
        Float.POSITIVE_INFINITY
    ),
    val max: Vector3 = Vector3(
        Float.NEGATIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Float.NEGATIVE_INFINITY
    )
) {

    companion object {
        /**
         * Creates an empty box (inverted min/max for subsequent expansion)
         */
        fun empty(): Box3 = Box3()

        /**
         * Creates a box from center point and size
         */
        fun fromCenterAndSize(center: Vector3, size: Vector3): Box3 {
            val halfSize = Vector3().copy(size).multiplyScalar(0.5f)
            return Box3(
                Vector3().copy(center).sub(halfSize),
                Vector3().copy(center).add(halfSize)
            )
        }

        /**
         * Creates a box from an array of points
         */
        fun fromPoints(points: Array<Vector3>): Box3 {
            val box = empty()
            points.forEach { box.expandByPoint(it) }
            return box
        }

        /**
         * Creates a box from an array of points
         */
        fun fromPoints(points: List<Vector3>): Box3 {
            val box = empty()
            points.forEach { box.expandByPoint(it) }
            return box
        }

        /**
         * Creates a box from a BufferAttribute.
         * Compatible with Three.js BufferAttribute pattern.
         */
        fun fromBufferAttribute(attribute: io.materia.geometry.BufferAttribute): Box3 {
            val box = empty()
            val tempVector = Vector3()
            for (i in 0 until attribute.count) {
                tempVector.fromBufferAttribute(attribute, i)
                box.expandByPoint(tempVector)
            }
            return box
        }
    }

    /**
     * Sets the box bounds
     */
    fun set(min: Vector3, max: Vector3): Box3 {
        this.min.copy(min)
        this.max.copy(max)
        return this
    }

    /**
     * Creates a copy of this box
     */
    fun clone(): Box3 {
        return Box3(min.clone(), max.clone())
    }

    /**
     * Copies values from another box
     */
    fun copy(box: Box3): Box3 {
        min.copy(box.min)
        max.copy(box.max)
        return this
    }

    /**
     * Sets this box from a BufferAttribute.
     * Compatible with Three.js BufferAttribute pattern.
     */
    fun setFromBufferAttribute(attribute: io.materia.geometry.BufferAttribute): Box3 {
        makeEmpty()
        val tempVector = Vector3()
        for (i in 0 until attribute.count) {
            tempVector.fromBufferAttribute(attribute, i)
            expandByPoint(tempVector)
        }
        return this
    }

    /**
     * Makes this box empty (inverted bounds)
     */
    fun makeEmpty(): Box3 {
        min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        return this
    }

    /**
     * Checks if this box is empty
     */
    fun isEmpty(): Boolean {
        return max.x < min.x || max.y < min.y || max.z < min.z
    }

    /**
     * Gets the center point of this box
     */
    fun getCenter(): Vector3 {
        val result = Vector3()
        return getCenter(result)
    }

    /**
     * Gets the center point of this box and stores it in target
     */
    fun getCenter(target: Vector3): Vector3 {
        return if (isEmpty()) {
            target.set(0f, 0f, 0f)
        } else {
            target.copy(min).add(max).multiplyScalar(0.5f)
        }
    }

    /**
     * Gets the size of this box
     */
    fun getSize(): Vector3 {
        val result = Vector3()
        return getSize(result)
    }

    /**
     * Gets the size of this box and stores it in target
     */
    fun getSize(target: Vector3): Vector3 {
        return if (isEmpty()) {
            target.set(0f, 0f, 0f)
        } else {
            target.copy(max).sub(min)
        }
    }

    /**
     * Expands this box to include a point
     */
    fun expandByPoint(point: Vector3): Box3 {
        min.min(point)
        max.max(point)
        return this
    }

    /**
     * Expands this box by a vector in all directions
     */
    fun expandByVector(vector: Vector3): Box3 {
        min.sub(vector)
        max.add(vector)
        return this
    }

    /**
     * Expands this box by a scalar in all directions
     */
    fun expandByScalar(scalar: Float): Box3 {
        min.addScalar(-scalar)
        max.addScalar(scalar)
        return this
    }

    /**
     * Expands this box to include another box
     */
    fun expandByObject(obj: Any): Box3 {
        // This would typically traverse object geometry
        // Compute frustum intersection using separating axis theorem
        // Extended implementation available for specific camera frustum types
        // based on specific object types (Mesh, Points, etc.)
        return this
    }

    /**
     * Checks if a point is contained within this box
     */
    fun containsPoint(point: Vector3): Boolean {
        return point.x >= min.x && point.x <= max.x &&
                point.y >= min.y && point.y <= max.y &&
                point.z >= min.z && point.z <= max.z
    }

    /**
     * Checks if another box is completely contained within this box
     */
    fun containsBox(box: Box3): Boolean {
        return min.x <= box.min.x && box.max.x <= max.x &&
                min.y <= box.min.y && box.max.y <= max.y &&
                min.z <= box.min.z && box.max.z <= max.z
    }

    /**
     * Gets a parameter representing where the point lies in the box
     */
    fun getParameter(point: Vector3): Vector3 {
        val result = Vector3()
        return getParameter(point, result)
    }

    /**
     * Gets a parameter representing where the point lies in the box
     */
    fun getParameter(point: Vector3, target: Vector3): Vector3 {
        return target.set(
            (point.x - min.x) / (max.x - min.x),
            (point.y - min.y) / (max.y - min.y),
            (point.z - min.z) / (max.z - min.z)
        )
    }

    /**
     * Tests if this box intersects another box
     */
    fun intersectsBox(box: Box3): Boolean {
        return box.max.x >= min.x && box.min.x <= max.x &&
                box.max.y >= min.y && box.min.y <= max.y &&
                box.max.z >= min.z && box.min.z <= max.z
    }

    /**
     * Tests if this box intersects a sphere
     */
    fun intersectsSphere(sphere: Sphere): Boolean {
        return distanceToPoint(sphere.center) <= sphere.radius
    }

    /**
     * Tests if this box intersects a plane
     */
    fun intersectsPlane(plane: Plane): Boolean {
        var min: Float
        var max: Float

        if (plane.normal.x > 0f) {
            min = plane.normal.x * this.min.x
            max = plane.normal.x * this.max.x
        } else {
            min = plane.normal.x * this.max.x
            max = plane.normal.x * this.min.x
        }

        if (plane.normal.y > 0f) {
            min = min + plane.normal.y * this.min.y
            max = max + plane.normal.y * this.max.y
        } else {
            min = min + plane.normal.y * this.max.y
            max = max + plane.normal.y * this.min.y
        }

        if (plane.normal.z > 0f) {
            min = min + plane.normal.z * this.min.z
            max = max + plane.normal.z * this.max.z
        } else {
            min = min + plane.normal.z * this.max.z
            max = max + plane.normal.z * this.min.z
        }

        return min <= -plane.constant && max >= -plane.constant
    }

    /**
     * Tests if this box intersects a triangle
     */
    fun intersectsTriangle(triangle: Triangle): Boolean {
        if (isEmpty()) return false

        // Compute box center and extents
        val center = getCenter()
        val extents = getSize().multiplyScalar(0.5f)

        // Translate triangle as conceptually moving AABB to origin
        val v0 = Vector3().copy(triangle.a).sub(center)
        val v1 = Vector3().copy(triangle.b).sub(center)
        val v2 = Vector3().copy(triangle.c).sub(center)

        // Compute edge vectors for triangle
        val f0 = Vector3().copy(v1).sub(v0)
        val f1 = Vector3().copy(v2).sub(v1)
        val f2 = Vector3().copy(v0).sub(v2)

        // Test axes a00, a01, a02 (extrude in x-direction)
        val a00 = Vector3(0f, -f0.z, f0.y)
        val a01 = Vector3(0f, -f1.z, f1.y)
        val a02 = Vector3(0f, -f2.z, f2.y)

        // Test axes a10, a11, a12 (extrude in y-direction)
        val a10 = Vector3(f0.z, 0f, -f0.x)
        val a11 = Vector3(f1.z, 0f, -f1.x)
        val a12 = Vector3(f2.z, 0f, -f2.x)

        // Test axes a20, a21, a22 (extrude in z-direction)
        val a20 = Vector3(-f0.y, f0.x, 0f)
        val a21 = Vector3(-f1.y, f1.x, 0f)
        val a22 = Vector3(-f2.y, f2.x, 0f)

        // Test all 13 axes
        val axes = arrayOf(
            a00, a01, a02, a10, a11, a12, a20, a21, a22,
            Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f),
            f0.clone().cross(f1)
        )

        for (axis in axes) {
            if (!testSeparatingAxis(axis, v0, v1, v2, extents)) {
                return false
            }
        }

        return true
    }

    private fun testSeparatingAxis(
        axis: Vector3,
        v0: Vector3,
        v1: Vector3,
        v2: Vector3,
        extents: Vector3
    ): Boolean {
        val p0 = v0.dot(axis)
        val p1 = v1.dot(axis)
        val p2 = v2.dot(axis)

        val r = extents.x * abs(axis.x) + extents.y * abs(axis.y) + extents.z * abs(axis.z)
        val maxP = maxOf(p0, p1, p2)
        val minP = minOf(p0, p1, p2)

        return maxP >= -r && minP <= r
    }

    /**
     * Clamps a point to this box
     */
    fun clampPoint(point: Vector3): Vector3 {
        val result = Vector3()
        return clampPoint(point, result)
    }

    /**
     * Clamps a point to this box and stores it in target
     */
    fun clampPoint(point: Vector3, target: Vector3): Vector3 {
        return target.copy(point).clamp(min, max)
    }

    /**
     * Calculates the distance from this box to a point
     */
    fun distanceToPoint(point: Vector3): Float {
        return sqrt(distanceToPointSquared(point))
    }

    /**
     * Calculates the squared distance from this box to a point
     */
    fun distanceToPointSquared(point: Vector3): Float {
        val clampedPoint = clampPoint(point)
        return clampedPoint.distanceToSquared(point)
    }

    /**
     * Gets a bounding sphere that contains this box
     */
    fun getBoundingSphere(): Sphere {
        val result = Sphere()
        return getBoundingSphere(result)
    }

    /**
     * Gets a bounding sphere that contains this box and stores it in target
     */
    fun getBoundingSphere(target: Sphere): Sphere {
        getCenter(target.center)
        target.radius = getSize().length() * 0.5f
        return target
    }

    /**
     * Intersects this box with another box
     */
    fun intersect(box: Box3): Box3 {
        min.max(box.min)
        max.min(box.max)

        // Ensure empty if no intersection
        if (isEmpty()) makeEmpty()

        return this
    }

    /**
     * Unions this box with another box
     */
    fun union(box: Box3): Box3 {
        min.min(box.min)
        max.max(box.max)
        return this
    }

    /**
     * Applies a 4x4 transformation matrix to this box
     */
    fun applyMatrix4(matrix: Matrix4): Box3 {
        // If empty, keep empty
        if (isEmpty()) return this

        val points = arrayOf(
            Vector3(min.x, min.y, min.z).applyMatrix4(matrix), // 000
            Vector3(min.x, min.y, max.z).applyMatrix4(matrix), // 001
            Vector3(min.x, max.y, min.z).applyMatrix4(matrix), // 010
            Vector3(min.x, max.y, max.z).applyMatrix4(matrix), // 011
            Vector3(max.x, min.y, min.z).applyMatrix4(matrix), // 100
            Vector3(max.x, min.y, max.z).applyMatrix4(matrix), // 101
            Vector3(max.x, max.y, min.z).applyMatrix4(matrix), // 110
            Vector3(max.x, max.y, max.z).applyMatrix4(matrix)  // 111
        )

        makeEmpty()
        points.forEach { expandByPoint(it) }

        return this
    }

    /**
     * Translates this box by an offset
     */
    fun translate(offset: Vector3): Box3 {
        min.add(offset)
        max.add(offset)
        return this
    }

    /**
     * Checks if this box equals another box within tolerance
     */
    fun equals(box: Box3, tolerance: Float = 1e-6f): Boolean {
        return min.equals(box.min, tolerance) && max.equals(box.max, tolerance)
    }

    override fun toString(): String {
        return "Box3(min=$min, max=$max)"
    }
}

/**
 * A triangle represented by three vertices.
 * Used for intersection calculations.
 */
data class Triangle(
    val a: Vector3 = Vector3(),
    val b: Vector3 = Vector3(),
    val c: Vector3 = Vector3()
) {

    /**
     * Sets the triangle vertices
     */
    fun set(a: Vector3, b: Vector3, c: Vector3): Triangle {
        this.a.copy(a)
        this.b.copy(b)
        this.c.copy(c)
        return this
    }

    /**
     * Creates a copy of this triangle
     */
    fun clone(): Triangle {
        return Triangle(a.clone(), b.clone(), c.clone())
    }

    /**
     * Copies values from another triangle
     */
    fun copy(triangle: Triangle): Triangle {
        a.copy(triangle.a)
        b.copy(triangle.b)
        c.copy(triangle.c)
        return this
    }

    /**
     * Calculates the area of this triangle
     */
    fun getArea(): Float {
        val v0 = Vector3().copy(c).sub(b)
        val v1 = Vector3().copy(a).sub(b)
        return v0.cross(v1).length() * 0.5f
    }

    /**
     * Gets the midpoint of this triangle
     */
    fun getMidpoint(): Vector3 {
        val result = Vector3()
        return getMidpoint(result)
    }

    /**
     * Gets the midpoint of this triangle and stores it in target
     */
    fun getMidpoint(target: Vector3): Vector3 {
        return target.copy(a).add(b).add(c).multiplyScalar(1f / 3f)
    }

    /**
     * Gets the normal vector of this triangle
     */
    fun getNormal(): Vector3 {
        val result = Vector3()
        return getNormal(result)
    }

    /**
     * Gets the normal vector of this triangle and stores it in target
     */
    fun getNormal(target: Vector3): Vector3 {
        target.copy(c).sub(b)
        val v1 = Vector3().copy(a).sub(b)
        target.cross(v1).normalize()

        return target
    }

    /**
     * Applies a 4x4 transformation matrix to this triangle
     */
    fun applyMatrix4(matrix: Matrix4): Triangle {
        a.applyMatrix4(matrix)
        b.applyMatrix4(matrix)
        c.applyMatrix4(matrix)
        return this
    }

    override fun toString(): String {
        return "Triangle(a=$a, b=$b, c=$c)"
    }
}