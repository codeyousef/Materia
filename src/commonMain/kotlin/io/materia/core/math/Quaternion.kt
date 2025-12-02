package io.materia.core.math

import kotlin.math.*

/**
 * A quaternion representing rotation in 3D space.
 * Compatible with Three.js Quaternion API.
 *
 * Quaternions provide a compact way to represent rotations and avoid gimbal lock.
 * They are stored as (x, y, z, w) where w is the scalar component.
 */
class Quaternion(
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    w: Float = 1f
) {
    private var _x: Float = x
    private var _y: Float = y
    private var _z: Float = z
    private var _w: Float = w
    
    var x: Float
        get() = _x
        set(value) {
            _x = value
            _onChangeCallback?.invoke()
        }
    
    var y: Float
        get() = _y
        set(value) {
            _y = value
            _onChangeCallback?.invoke()
        }
    
    var z: Float
        get() = _z
        set(value) {
            _z = value
            _onChangeCallback?.invoke()
        }
    
    var w: Float
        get() = _w
        set(value) {
            _w = value
            _onChangeCallback?.invoke()
        }
    
    /**
     * Internal onChange callback (set by Object3D to sync with Euler)
     */
    internal var _onChangeCallback: (() -> Unit)? = null

    companion object {
        val IDENTITY: Quaternion
            get() = Quaternion(0f, 0f, 0f, 1f)

        /**
         * Creates an identity quaternion (no rotation)
         */
        fun identity(): Quaternion = Quaternion()

        /**
         * Creates a quaternion from axis-angle representation
         */
        fun fromAxisAngle(axis: Vector3, angle: Float): Quaternion =
            Quaternion().setFromAxisAngle(axis, angle)

        /**
         * Creates a quaternion from Euler angles
         */
        fun fromEuler(euler: Euler): Quaternion =
            Quaternion().setFromEuler(euler)

        /**
         * Creates a quaternion from rotation matrix
         */
        fun fromRotationMatrix(matrix: Matrix4): Quaternion =
            Quaternion().setFromRotationMatrix(matrix)

        /**
         * Spherical linear interpolation between two quaternions
         */
        fun slerp(qa: Quaternion, qb: Quaternion, t: Float): Quaternion =
            qa.clone().slerp(qb, t)
    }

    /**
     * Sets quaternion values
     */
    fun set(x: Float, y: Float, z: Float, w: Float): Quaternion {
        this._x = x
        this._y = y
        this._z = z
        this._w = w
        _onChangeCallback?.invoke()
        return this
    }

    /**
     * Creates a copy of this quaternion
     */
    fun clone(): Quaternion {
        return Quaternion(_x, _y, _z, _w)
    }

    /**
     * Copies values from another quaternion
     */
    fun copy(quaternion: Quaternion): Quaternion {
        _x = quaternion._x
        _y = quaternion._y
        _z = quaternion._z
        _w = quaternion._w
        _onChangeCallback?.invoke()
        return this
    }

    /**
     * Sets this quaternion to identity (no rotation)
     */
    fun identity(): Quaternion {
        return set(0f, 0f, 0f, 1f)
    }

    /**
     * Checks if this quaternion represents no rotation
     */
    fun isIdentity(epsilon: Float = 0.000001f): Boolean {
        return abs(x) < epsilon && abs(y) < epsilon && abs(z) < epsilon && abs(w - 1f) < epsilon
    }

    /**
     * Sets this quaternion from axis-angle representation
     */
    fun setFromAxisAngle(axis: Vector3, angle: Float): Quaternion {
        val halfAngle = angle / 2f
        val s = sin(halfAngle)

        x = axis.x * s
        y = axis.y * s
        z = axis.z * s
        w = cos(halfAngle)

        return this
    }

    /**
     * Sets this quaternion from Euler angles
     */
    fun setFromEuler(euler: Euler): Quaternion {
        val x = euler.x
        val y = euler.y
        val z = euler.z

        val cos = { angle: Float -> cos(angle / 2f) }
        val sin = { angle: Float -> sin(angle / 2f) }

        val c1 = cos(x)
        val c2 = cos(y)
        val c3 = cos(z)

        val s1 = sin(x)
        val s2 = sin(y)
        val s3 = sin(z)

        when (euler.order) {
            EulerOrder.XYZ -> {
                this.x = s1 * c2 * c3 + c1 * s2 * s3
                this.y = c1 * s2 * c3 - s1 * c2 * s3
                this.z = c1 * c2 * s3 + s1 * s2 * c3
                this.w = c1 * c2 * c3 - s1 * (s2 * s3)
            }

            EulerOrder.YXZ -> {
                this.x = s1 * c2 * c3 + c1 * s2 * s3
                this.y = c1 * s2 * c3 - s1 * c2 * s3
                this.z = c1 * c2 * s3 - s1 * s2 * c3
                this.w = c1 * c2 * c3 + s1 * (s2 * s3)
            }

            EulerOrder.ZXY -> {
                this.x = s1 * c2 * c3 - c1 * s2 * s3
                this.y = c1 * s2 * c3 + s1 * c2 * s3
                this.z = c1 * c2 * s3 + s1 * s2 * c3
                this.w = c1 * c2 * c3 - s1 * (s2 * s3)
            }

            EulerOrder.ZYX -> {
                this.x = s1 * c2 * c3 - c1 * s2 * s3
                this.y = c1 * s2 * c3 + s1 * c2 * s3
                this.z = c1 * c2 * s3 - s1 * s2 * c3
                this.w = c1 * c2 * c3 + s1 * (s2 * s3)
            }

            EulerOrder.YZX -> {
                this.x = s1 * c2 * c3 + c1 * s2 * s3
                this.y = c1 * s2 * c3 + s1 * c2 * s3
                this.z = c1 * c2 * s3 - s1 * s2 * c3
                this.w = c1 * c2 * c3 - s1 * (s2 * s3)
            }

            EulerOrder.XZY -> {
                this.x = s1 * c2 * c3 - c1 * s2 * s3
                this.y = c1 * s2 * c3 - s1 * c2 * s3
                this.z = c1 * c2 * s3 + s1 * s2 * c3
                this.w = c1 * c2 * c3 + s1 * (s2 * s3)
            }
        }

        return this
    }

    /**
     * Sets this quaternion from rotation matrix
     */
    fun setFromRotationMatrix(m: Matrix4): Quaternion {
        val te = m.elements

        val m11 = te[0];
        val m12 = te[4];
        val m13 = te[8]
        val m21 = te[1];
        val m22 = te[5];
        val m23 = te[9]
        val m31 = te[2];
        val m32 = te[6];
        val m33 = te[10]

        val trace = m11 + m22 + m33
        val epsilon = 0.00001f

        if (trace > 0f) {
            val s = 0.5f / sqrt(trace + 1f)
            w = 0.25f / s
            x = (m32 - m23) * s
            y = (m13 - m31) * s
            z = (m21 - m12) * s
        } else if (m11 > m22 && m11 > m33) {
            val discriminant = 1f + m11 - m22 - m33
            if (discriminant < epsilon) {
                // Handle degenerate case
                identity()
                return this
            }
            val s = 2f * sqrt(discriminant)
            w = (m32 - m23) / s
            x = 0.25f * s
            y = (m12 + m21) / s
            z = (m13 + m31) / s
        } else if (m22 > m33) {
            val discriminant = 1f + m22 - m11 - m33
            if (discriminant < epsilon) {
                // Handle degenerate case
                identity()
                return this
            }
            val s = 2f * sqrt(discriminant)
            w = (m13 - m31) / s
            x = (m12 + m21) / s
            y = 0.25f * s
            z = (m23 + m32) / s
        } else {
            val discriminant = 1f + m33 - m11 - m22
            if (discriminant < epsilon) {
                // Handle degenerate case
                identity()
                return this
            }
            val s = 2f * sqrt(discriminant)
            w = (m21 - m12) / s
            x = (m13 + m31) / s
            y = (m23 + m32) / s
            z = 0.25f * s
        }

        return this
    }

    /**
     * Sets this quaternion to look from one direction to another
     */
    fun setFromUnitVectors(vFrom: Vector3, vTo: Vector3): Quaternion {
        var r = vFrom.dot(vTo) + 1f

        if (r < 1e-6f) {
            // Vectors are opposite
            r = 0f
            if (abs(vFrom.x) > abs(vFrom.z)) {
                x = -vFrom.y
                y = vFrom.x
                z = 0f
                w = r
            } else {
                x = 0f
                y = -vFrom.z
                z = vFrom.y
                w = r
            }
        } else {
            // General case
            val cross = vFrom.clone().cross(vTo)
            x = cross.x
            y = cross.y
            z = cross.z
            w = r
        }

        return normalize()
    }

    /**
     * Inverts this quaternion (conjugate for unit quaternions)
     */
    fun invert(): Quaternion {
        return conjugate()
    }

    /**
     * Returns the conjugate of this quaternion
     */
    fun conjugate(): Quaternion {
        x = -x
        y = -y
        z = -z
        return this
    }

    /**
     * Calculates the dot product with another quaternion
     */
    fun dot(q: Quaternion): Float {
        return x * q.x + y * q.y + z * q.z + w * q.w
    }

    /**
     * Calculates the squared length of this quaternion
     */
    fun lengthSq(): Float {
        return x * x + y * y + z * z + (w * w)
    }

    /**
     * Calculates the length of this quaternion
     */
    fun length(): Float {
        return sqrt(lengthSq())
    }

    /**
     * Normalizes this quaternion
     */
    fun normalize(): Quaternion {
        var l = length()
        val epsilon = 0.00001f
        if (kotlin.math.abs(l) < epsilon) {
            x = 0f
            y = 0f
            z = 0f
            w = 1f
        } else {
            l = 1f / l
            x = x * l
            y = y * l
            z = z * l
            w = w * l
        }
        return this
    }

    /**
     * Returns a new normalized quaternion (doesn't modify this quaternion)
     */
    fun normalized(): Quaternion = this.clone().normalize()

    /**
     * Multiplies this quaternion by another quaternion
     */
    fun multiply(q: Quaternion): Quaternion {
        return multiplyQuaternions(this, q)
    }

    /**
     * Multiplies another quaternion by this quaternion (order matters!)
     */
    fun premultiply(q: Quaternion): Quaternion {
        return multiplyQuaternions(q, this)
    }

    /**
     * Multiplies two quaternions and stores result in this quaternion
     */
    fun multiplyQuaternions(a: Quaternion, b: Quaternion): Quaternion {
        val qax = a.x;
        val qay = a.y;
        val qaz = a.z;
        val qaw = a.w
        val qbx = b.x;
        val qby = b.y;
        val qbz = b.z;
        val qbw = b.w

        x = qax * qbw + qaw * qbx + qay * qbz - qaz * qby
        y = qay * qbw + qaw * qby + qaz * qbx - qax * qbz
        z = qaz * qbw + qaw * qbz + qax * qby - qay * qbx
        w = qaw * qbw - qax * qbx - qay * qby - qaz * qbz

        return this
    }

    /**
     * Spherical linear interpolation to another quaternion
     */
    fun slerp(qb: Quaternion, t: Float): Quaternion {
        if (abs(t) < 0.000001f) return this
        if (abs(t - 1f) < 0.000001f) return copy(qb)

        val x = this.x;
        val y = this.y;
        val z = this.z;
        val w = this.w

        // Calculate cosine of angle between quaternions
        var cosHalfTheta = w * qb.w + x * qb.x + y * qb.y + z * qb.z

        // If negative dot, negate one quaternion for shortest path
        val qx: Float
        val qy: Float
        val qz: Float
        val qw: Float

        if (cosHalfTheta < 0f) {
            qw = -qb.w
            qx = -qb.x
            qy = -qb.y
            qz = -qb.z
            cosHalfTheta = -cosHalfTheta
        } else {
            qw = qb.w
            qx = qb.x
            qy = qb.y
            qz = qb.z
        }

        // If quaternions are very close, use linear interpolation
        if (cosHalfTheta >= 1f) {
            this.w = w
            this.x = x
            this.y = y
            this.z = z
            return this
        }

        val sqrSinHalfTheta = 1f - cosHalfTheta * cosHalfTheta

        // Use stricter threshold to avoid numerical instability
        if (sqrSinHalfTheta <= 0.000001f) {
            val s = 1f - t
            this.w = s * w + t * qw
            this.x = s * x + t * qx
            this.y = s * y + t * qy
            this.z = s * z + t * qz

            return normalize()
        }

        val sinHalfTheta = sqrt(sqrSinHalfTheta)
        val halfTheta = atan2(sinHalfTheta, cosHalfTheta)
        val ratioA = sin((1f - t) * halfTheta) / sinHalfTheta
        val ratioB = sin((t * halfTheta)) / sinHalfTheta

        this.w = w * ratioA + qw * ratioB
        this.x = x * ratioA + qx * ratioB
        this.y = y * ratioA + qy * ratioB
        this.z = z * ratioA + qz * ratioB

        return this
    }

    /**
     * Spherical linear interpolation with multiple spins
     */
    fun slerpFlat(
        dst: FloatArray,
        dstOffset: Int,
        src0: FloatArray,
        srcOffset0: Int,
        src1: FloatArray,
        srcOffset1: Int,
        t: Float
    ) {
        // Fused multiply-add is used to improve performance of arithmetic.
        var x0 = src0[srcOffset0 + 0]
        var y0 = src0[srcOffset0 + 1]
        var z0 = src0[srcOffset0 + 2]
        var w0 = src0[srcOffset0 + 3]

        val x1 = src1[srcOffset1 + 0]
        val y1 = src1[srcOffset1 + 1]
        val z1 = src1[srcOffset1 + 2]
        val w1 = src1[srcOffset1 + 3]

        if (abs(t) < 0.000001f) {
            dst[dstOffset + 0] = x0
            dst[dstOffset + 1] = y0
            dst[dstOffset + 2] = z0
            dst[dstOffset + 3] = w0
            return
        }

        if (abs(t - 1f) < 0.000001f) {
            dst[dstOffset + 0] = x1
            dst[dstOffset + 1] = y1
            dst[dstOffset + 2] = z1
            dst[dstOffset + 3] = w1
            return
        }

        var cosHalfTheta = x0 * x1 + y0 * y1 + z0 * z1 + w0 * w1

        // Store destination quaternion, potentially negated for shortest path
        var qx = x1
        var qy = y1
        var qz = z1
        var qw = w1

        if (cosHalfTheta < 0f) {
            qx = -x1
            qy = -y1
            qz = -z1
            qw = -w1
            cosHalfTheta = -cosHalfTheta
        }

        if (cosHalfTheta >= 1f) {
            dst[dstOffset + 0] = x0
            dst[dstOffset + 1] = y0
            dst[dstOffset + 2] = z0
            dst[dstOffset + 3] = w0
            return
        }

        val halfTheta = acos(cosHalfTheta.coerceIn(-1f, 1f))
        val sinHalfTheta = sqrt(1f - (cosHalfTheta * cosHalfTheta))

        if (abs(sinHalfTheta) < 0.001f) {
            dst[dstOffset + 0] = 0.5f * (x0 + qx)
            dst[dstOffset + 1] = 0.5f * (y0 + qy)
            dst[dstOffset + 2] = 0.5f * (z0 + qz)
            dst[dstOffset + 3] = 0.5f * (w0 + qw)
            return
        }

        val ratioA = sin((1f - t) * halfTheta) / sinHalfTheta
        val ratioB = sin((t * halfTheta)) / sinHalfTheta

        dst[dstOffset + 0] = x0 * ratioA + qx * ratioB
        dst[dstOffset + 1] = y0 * ratioA + qy * ratioB
        dst[dstOffset + 2] = z0 * ratioA + qz * ratioB
        dst[dstOffset + 3] = w0 * ratioA + (qw * ratioB)
    }

    /**
     * Checks if this quaternion equals another within a tolerance
     */
    fun equals(quaternion: Quaternion, tolerance: Float = 1e-6f): Boolean {
        return abs(x - quaternion.x) < tolerance &&
                abs(y - quaternion.y) < tolerance &&
                abs(z - quaternion.z) < tolerance &&
                abs(w - quaternion.w) < tolerance
    }

    /**
     * Converts quaternion to array
     */
    fun toArray(): FloatArray {
        return floatArrayOf(x, y, z, w)
    }

    /**
     * Sets quaternion from array
     */
    fun fromArray(array: FloatArray, offset: Int = 0): Quaternion {
        x = array[offset]
        y = array[offset + 1]
        z = array[offset + 2]
        w = array[offset + 3]
        return this
    }

    /**
     * Returns a new inverted quaternion (doesn't modify this quaternion)
     */
    fun inverse(): Quaternion = clone().invert()

    /**
     * Convert quaternion to Euler angles (in radians)
     */
    fun toEulerAngles(): Vector3 {
        val test = x * y + z * w
        if (test > 0.499f) { // singularity at north pole
            return Vector3(
                2f * atan2(x, w),
                PI.toFloat() / 2f,
                0f
            )
        }
        if (test < -0.499f) { // singularity at south pole
            return Vector3(
                -2f * atan2(x, w),
                -PI.toFloat() / 2f,
                0f
            )
        }
        val sqx = x * x
        val sqy = y * y
        val sqz = z * z
        return Vector3(
            atan2(2f * y * w - 2f * x * z, 1f - 2f * sqy - 2f * sqz),
            asin(2f * test),
            atan2(2f * x * w - 2f * y * z, 1f - 2f * sqx - 2f * sqz)
        )
    }

    /**
     * Convert quaternion to axis-angle representation
     * Returns Pair(axis, angle)
     */
    fun toAxisAngle(): Pair<Vector3, Float> {
        val angle = 2f * acos(w.coerceIn(-1f, 1f))
        val s = sqrt(1f - w * w)
        return if (s < 0.001f) {
            // If s is close to zero, direction doesn't matter
            Pair(Vector3.UNIT_X, angle)
        } else {
            Pair(Vector3(x / s, y / s, z / s), angle)
        }
    }

    /**
     * Multiplication operator for quaternions
     */
    operator fun times(other: Quaternion): Quaternion = clone().multiply(other)

    /**
     * Calculates the angle between this quaternion and another quaternion
     * @param quaternion The other quaternion
     * @return The angle in radians
     */
    fun angleTo(quaternion: Quaternion): Float {
        val dot = this.dot(quaternion)
        // Clamp dot product to avoid numerical errors in acos
        val clampedDot = dot.coerceIn(-1f, 1f)
        return 2f * acos(abs(clampedDot))
    }

    override fun toString(): String {
        return "Quaternion(x=$x, y=$y, z=$z, w=$w)"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Quaternion) return false
        return _x == other._x && _y == other._y && _z == other._z && _w == other._w
    }
    
    override fun hashCode(): Int {
        var result = _x.hashCode()
        result = 31 * result + _y.hashCode()
        result = 31 * result + _z.hashCode()
        result = 31 * result + _w.hashCode()
        return result
    }

}