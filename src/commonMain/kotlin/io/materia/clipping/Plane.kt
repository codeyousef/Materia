package io.materia.clipping

import io.materia.core.math.Vector3
import io.materia.core.math.Matrix4
import io.materia.core.math.Matrix3
import io.materia.core.math.Sphere
import io.materia.core.math.Line3
import io.materia.core.math.Box3
import io.materia.geometry.BufferAttribute
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Represents a plane in 3D space defined by a normal vector and a constant.
 * The plane equation is: normal.x * x + normal.y * y + normal.z * z + constant = 0
 */
class Plane(
    val normal: Vector3 = Vector3(1f, 0f, 0f),
    var constant: Float = 0f
) {

    constructor(x: Float, y: Float, z: Float, w: Float) : this(
        Vector3(x, y, z),
        w
    )

    /**
     * Set the plane from a normal and a constant.
     */
    fun set(normal: Vector3, constant: Float): Plane {
        this.normal.copy(normal)
        this.constant = constant
        return this
    }

    /**
     * Set the plane from components.
     */
    fun setComponents(x: Float, y: Float, z: Float, w: Float): Plane {
        normal.set(x, y, z)
        constant = w
        return this
    }

    /**
     * Set the plane from a normal and a point on the plane.
     */
    fun setFromNormalAndCoplanarPoint(normal: Vector3, point: Vector3): Plane {
        this.normal.copy(normal)
        constant = -point.dot(this.normal)
        return this
    }

    /**
     * Set the plane from three coplanar points.
     * Points should be in counter-clockwise order.
     */
    fun setFromCoplanarPoints(a: Vector3, b: Vector3, c: Vector3): Plane {
        val v1 = Vector3()
        val v2 = Vector3()

        v1.subVectors(c, b)
        v2.subVectors(a, b)

        normal.crossVectors(v1, v2).normalize()

        setFromNormalAndCoplanarPoint(normal, a)

        return this
    }

    /**
     * Copy values from another plane.
     */
    fun copy(plane: Plane): Plane {
        normal.copy(plane.normal)
        constant = plane.constant
        return this
    }

    /**
     * Normalize the plane (unit normal and adjusted constant).
     */
    fun normalize(): Plane {
        val inverseNormalLength = 1.0f / normal.length()
        normal.multiplyScalar(inverseNormalLength)
        constant *= inverseNormalLength
        return this
    }

    /**
     * Negate the plane (flip normal direction).
     */
    fun negate(): Plane {
        constant *= -1f
        normal.negate()
        return this
    }

    /**
     * Calculate the signed distance from a point to the plane.
     * Positive = in front of plane, negative = behind plane
     */
    fun distanceToPoint(point: Vector3): Float {
        return normal.dot(point) + constant
    }

    /**
     * Calculate the distance from a sphere to the plane.
     */
    fun distanceToSphere(sphere: Sphere): Float {
        return distanceToPoint(sphere.center) - sphere.radius
    }

    /**
     * Project a point onto the plane.
     */
    fun projectPoint(point: Vector3, target: Vector3 = Vector3()): Vector3 {
        target.copy(normal).multiplyScalar(-distanceToPoint(point)).add(point)
        return target
    }

    /**
     * Find the intersection point between the plane and a line.
     * Returns null if the line is parallel to the plane.
     */
    fun intersectLine(line: Line3, target: Vector3 = Vector3()): Vector3? {
        val direction = Vector3().subVectors(line.end, line.start)
        val denominator = normal.dot(direction)

        if (abs(denominator) < 1e-6f) {
            // Line is parallel to plane
            return if (distanceToPoint(line.start) == 0f) {
                // Line is coplanar, return any point on the line
                target.copy(line.start)
            } else {
                null
            }
        }

        val t = -(normal.dot(line.start) + constant) / denominator

        if (t < 0f || t > 1f) {
            return null // Intersection point is outside line segment
        }

        return target.copy(direction).multiplyScalar(t).add(line.start)
    }

    /**
     * Test if the plane intersects with a box.
     */
    fun intersectsBox(box: Box3): Boolean {
        val min = Vector3()
        val max = Vector3()

        // Find the min and max projections of the box corners
        if (normal.x > 0) {
            max.x = box.max.x
            min.x = box.min.x
        } else {
            max.x = box.min.x
            min.x = box.max.x
        }

        if (normal.y > 0) {
            max.y = box.max.y
            min.y = box.min.y
        } else {
            max.y = box.min.y
            min.y = box.max.y
        }

        if (normal.z > 0) {
            max.z = box.max.z
            min.z = box.min.z
        } else {
            max.z = box.min.z
            min.z = box.max.z
        }

        val minDistance = normal.dot(min) + constant
        val maxDistance = normal.dot(max) + constant

        return minDistance <= 0 && maxDistance >= 0
    }

    /**
     * Test if the plane intersects with a sphere.
     */
    fun intersectsSphere(sphere: Sphere): Boolean {
        return abs(distanceToPoint(sphere.center)) <= sphere.radius
    }

    /**
     * Test if a point is in front of the plane.
     */
    fun isPointInFront(point: Vector3): Boolean {
        return distanceToPoint(point) > 0
    }

    /**
     * Apply a matrix transformation to the plane.
     */
    fun applyMatrix4(matrix: Matrix4, normalMatrix: Matrix3? = null): Plane {
        val referencePoint = Vector3()
        val transformedNormal = Vector3()

        val actualNormalMatrix = normalMatrix ?: Matrix3().getNormalMatrix(matrix)

        transformedNormal.copy(normal).applyMatrix3(actualNormalMatrix)

        // Transform a point on the plane
        coplanarPoint(referencePoint)
        referencePoint.applyMatrix4(matrix)

        setFromNormalAndCoplanarPoint(transformedNormal, referencePoint)

        return this
    }

    /**
     * Get a coplanar point on the plane.
     */
    fun coplanarPoint(target: Vector3 = Vector3()): Vector3 {
        return target.copy(normal).multiplyScalar(-constant)
    }

    /**
     * Translate the plane by a vector.
     */
    fun translate(offset: Vector3): Plane {
        constant -= offset.dot(normal)
        return this
    }

    /**
     * Check if two planes are equal.
     */
    fun equals(plane: Plane): Boolean {
        return plane.normal.equals(normal) && plane.constant == constant
    }

    /**
     * Clone the plane.
     */
    fun clone(): Plane {
        return Plane(normal.clone(), constant)
    }

    companion object {
        /**
         * Create a plane from a buffer attribute.
         */
        fun fromBufferAttribute(attribute: BufferAttribute, index: Int): Plane {
            val offset = index * 4 // 4 floats per plane (nx, ny, nz, constant)
            val normal = Vector3(
                attribute.getX(offset),
                attribute.getY(offset),
                attribute.getZ(offset)
            )
            val constant = attribute.getW(offset)
            return Plane(normal, constant)
        }

        /**
         * Standard clipping planes for cube clipping.
         */
        fun createCubeClippingPlanes(): List<Plane> {
            return listOf(
                Plane(Vector3(1f, 0f, 0f), 1f),   // +X
                Plane(Vector3(-1f, 0f, 0f), 1f),  // -X
                Plane(Vector3(0f, 1f, 0f), 1f),   // +Y
                Plane(Vector3(0f, -1f, 0f), 1f),  // -Y
                Plane(Vector3(0f, 0f, 1f), 1f),   // +Z
                Plane(Vector3(0f, 0f, -1f), 1f)   // -Z
            )
        }
    }
}