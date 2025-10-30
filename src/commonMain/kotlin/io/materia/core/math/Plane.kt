package io.materia.core.math

import kotlin.math.abs

/**
 * A plane represented by a normal vector and a constant.
 * Compatible with Three.js Plane API.
 *
 * The plane equation is: normal · P + constant = 0
 * where P is any point on the plane.
 */
data class Plane(
    var normal: Vector3 = Vector3(1f, 0f, 0f),
    var constant: Float = 0f
) {

    companion object {
        /**
         * Creates a plane from a normal vector and a point on the plane
         */
        fun fromNormalAndPoint(normal: Vector3, point: Vector3): Plane {
            val constant = -point.dot(normal)
            return Plane(normal.clone().normalize(), constant)
        }

        /**
         * Creates a plane from three coplanar points
         */
        fun fromCoplanarPoints(a: Vector3, b: Vector3, c: Vector3): Plane {
            val normal = Vector3().copy(c).sub(b).cross(Vector3().copy(a).sub(b)).normalize()
            return fromNormalAndPoint(normal, a)
        }
    }

    /**
     * Sets the plane's normal and constant
     */
    fun set(normal: Vector3, constant: Float): Plane {
        this.normal.copy(normal)
        this.constant = constant
        return this
    }

    /**
     * Sets the plane components individually
     */
    fun setComponents(x: Float, y: Float, z: Float, w: Float): Plane {
        normal.set(x, y, z)
        constant = w
        return this
    }

    /**
     * Sets the plane from a normal vector and a point on the plane
     */
    fun setFromNormalAndCoplanarPoint(normal: Vector3, point: Vector3): Plane {
        this.normal.copy(normal).normalize()
        constant = -point.dot(this.normal)
        return this
    }

    /**
     * Sets the plane from three coplanar points
     */
    fun setFromCoplanarPoints(a: Vector3, b: Vector3, c: Vector3): Plane {
        val v1 = Vector3().copy(c).sub(b)
        val v2 = Vector3().copy(a).sub(b)
        normal.copy(v1).cross(v2).normalize()
        constant = -a.dot(normal)
        return this
    }

    /**
     * Creates a copy of this plane
     */
    fun clone(): Plane {
        return Plane(normal.clone(), constant)
    }

    /**
     * Copies values from another plane
     */
    fun copy(plane: Plane): Plane {
        normal.copy(plane.normal)
        constant = plane.constant
        return this
    }

    /**
     * Normalizes the plane's normal vector and adjusts the constant accordingly
     */
    fun normalize(): Plane {
        val normalLength = normal.length()
        normal.divideScalar(normalLength)
        constant /= normalLength
        return this
    }

    /**
     * Negates the plane (flips its orientation)
     */
    fun negate(): Plane {
        constant *= -1f
        normal.negate()
        return this
    }

    /**
     * Calculates the distance from a point to this plane
     */
    fun distanceToPoint(point: Vector3): Float {
        return normal.dot(point) + constant
    }

    /**
     * Calculates the distance from a sphere to this plane
     */
    fun distanceToSphere(sphere: Sphere): Float {
        return distanceToPoint(sphere.center) - sphere.radius
    }

    /**
     * Projects a point onto this plane
     */
    fun projectPoint(point: Vector3): Vector3 {
        val result = Vector3()
        return projectPoint(point, result)
    }

    /**
     * Projects a point onto this plane and stores the result in target
     */
    fun projectPoint(point: Vector3, target: Vector3): Vector3 {
        return target.copy(normal).multiplyScalar(-distanceToPoint(point)).add(point)
    }

    /**
     * Finds the intersection point between this plane and a line segment
     */
    fun intersectLine(line: Line3): Vector3? {
        val result = Vector3()
        return if (intersectLine(line, result)) result else null
    }

    /**
     * Finds the intersection point between this plane and a line segment
     */
    fun intersectLine(line: Line3, target: Vector3): Boolean {
        val direction = Vector3().copy(line.end).sub(line.start)
        val denominator = normal.dot(direction)

        if (abs(denominator) < 0.000001f) {
            // Line is parallel to plane
            return if (abs(distanceToPoint(line.start)) < 0.000001f) {
                target.copy(line.start)
                true
            } else {
                false
            }
        }

        val t = -(normal.dot(line.start) + constant) / denominator

        if (t < 0f || t > 1f) return false

        target.copy(direction).multiplyScalar(t).add(line.start)
        return true
    }

    /**
     * Tests if this plane intersects a sphere
     */
    fun intersectsSphere(sphere: Sphere): Boolean {
        return abs(distanceToSphere(sphere)) <= sphere.radius
    }

    /**
     * Tests if this plane intersects a box
     */
    fun intersectsBox(box: Box3): Boolean {
        return box.intersectsPlane(this)
    }

    /**
     * Tests if a point lies on this plane within tolerance
     */
    fun coplanarPoint(): Vector3 {
        return Vector3().copy(normal).multiplyScalar(-constant)
    }

    /**
     * Gets a point that lies on this plane
     */
    fun coplanarPoint(target: Vector3): Vector3 {
        return target.copy(normal).multiplyScalar(-constant)
    }

    /**
     * Applies a 4x4 transformation matrix to this plane
     */
    fun applyMatrix4(matrix: Matrix4): Plane {
        val normalMatrix = Matrix3().getNormalMatrix(matrix)
        val referencePoint = coplanarPoint().applyMatrix4(matrix)

        normal.applyMatrix3(normalMatrix).normalize()
        constant = -referencePoint.dot(normal)

        return this
    }

    /**
     * Translates this plane by an offset
     */
    fun translate(offset: Vector3): Plane {
        constant = constant - offset.dot(normal)
        return this
    }

    /**
     * Checks if this plane equals another plane within tolerance
     */
    fun equals(plane: Plane, tolerance: Float = 1e-6f): Boolean {
        return normal.equals(plane.normal, tolerance) &&
                abs(constant - plane.constant) < tolerance
    }

    /**
     * Checks if a point is on the plane within tolerance
     */
    fun isPointOnPlane(point: Vector3, tolerance: Float = 1e-6f): Boolean {
        return abs(distanceToPoint(point)) < tolerance
    }

    override fun toString(): String {
        return "Plane(normal=$normal, constant=$constant)"
    }
}

/**
 * A line segment represented by start and end points.
 * Used for plane intersection calculations.
 */
data class Line3(
    val start: Vector3 = Vector3(),
    val end: Vector3 = Vector3()
) {

    /**
     * Sets the line's start and end points
     */
    fun set(start: Vector3, end: Vector3): Line3 {
        this.start.copy(start)
        this.end.copy(end)
        return this
    }

    /**
     * Creates a copy of this line
     */
    fun clone(): Line3 {
        return Line3(start.clone(), end.clone())
    }

    /**
     * Copies values from another line
     */
    fun copy(line: Line3): Line3 {
        start.copy(line.start)
        end.copy(line.end)
        return this
    }

    /**
     * Gets the center point of this line
     */
    fun getCenter(): Vector3 {
        return Vector3().copy(start).add(end).multiplyScalar(0.5f)
    }

    /**
     * Gets the delta vector (end - start)
     */
    fun delta(): Vector3 {
        return Vector3().copy(end).sub(start)
    }

    /**
     * Gets the squared length of this line
     */
    fun distanceSq(): Float {
        return start.distanceToSquared(end)
    }

    /**
     * Gets the length of this line
     */
    fun distance(): Float {
        return start.distanceTo(end)
    }

    /**
     * Gets a point along the line at parameter t (0 = start, 1 = end)
     */
    fun at(t: Float): Vector3 {
        return delta().multiplyScalar(t).add(start)
    }

    /**
     * Finds the closest point on this line to a given point
     */
    fun closestPointToPoint(point: Vector3, clampToLine: Boolean = false): Vector3 {
        val result = Vector3()
        return closestPointToPoint(point, clampToLine, result)
    }

    /**
     * Finds the closest point on this line to a given point and stores it in target
     */
    fun closestPointToPoint(point: Vector3, clampToLine: Boolean, target: Vector3): Vector3 {
        val startP = Vector3().copy(point).sub(start)
        val startEnd = Vector3().copy(end).sub(start)

        val startEndLengthSq = startEnd.lengthSq()
        val startPDotStartEnd = startP.dot(startEnd)

        var t = startPDotStartEnd / startEndLengthSq

        if (clampToLine) {
            t = t.coerceIn(0f, 1f)
        }

        return target.copy(startEnd).multiplyScalar(t).add(start)
    }

    /**
     * Applies a 4x4 transformation matrix to this line
     */
    fun applyMatrix4(matrix: Matrix4): Line3 {
        start.applyMatrix4(matrix)
        end.applyMatrix4(matrix)
        return this
    }

    override fun toString(): String {
        return "Line3(start=$start, end=$end)"
    }
}