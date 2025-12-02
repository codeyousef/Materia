package io.materia.core.math

import kotlin.math.*

/**
 * Euler angle rotation orders
 */
enum class EulerOrder {
    XYZ, YXZ, ZXY, ZYX, YZX, XZY
}

/**
 * Euler angles representation for rotations.
 * Compatible with Three.js Euler API.
 *
 * Euler angles represent rotations as three sequential rotations
 * around coordinate axes. The order of rotations matters!
 */
class Euler(
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    order: EulerOrder = EulerOrder.XYZ
) {
    private var _x: Float = x
    private var _y: Float = y
    private var _z: Float = z
    private var _order: EulerOrder = order
    
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
    
    var order: EulerOrder
        get() = _order
        set(value) {
            _order = value
            _onChangeCallback?.invoke()
        }
    
    /**
     * Internal onChange callback (set by Object3D to sync with quaternion)
     */
    internal var _onChangeCallback: (() -> Unit)? = null

    companion object {
        /**
         * Creates Euler angles from quaternion
         */
        fun fromQuaternion(q: Quaternion, order: EulerOrder = EulerOrder.XYZ): Euler =
            Euler().setFromQuaternion(q, order)

        /**
         * Creates Euler angles from rotation matrix
         */
        fun fromRotationMatrix(m: Matrix4, order: EulerOrder = EulerOrder.XYZ): Euler =
            Euler().setFromRotationMatrix(m, order)
    }

    /**
     * Sets Euler angle values
     */
    fun set(x: Float, y: Float, z: Float, order: EulerOrder = this._order): Euler {
        this._x = x
        this._y = y
        this._z = z
        this._order = order
        _onChangeCallback?.invoke()
        return this
    }

    /**
     * Creates a copy of this Euler
     */
    fun clone(): Euler {
        return Euler(x, y, z, order)
    }

    /**
     * Copies values from another Euler
     */
    fun copy(euler: Euler): Euler {
        _x = euler._x
        _y = euler._y
        _z = euler._z
        _order = euler._order
        _onChangeCallback?.invoke()
        return this
    }

    /**
     * Sets this Euler from a quaternion
     */
    fun setFromQuaternion(q: Quaternion, order: EulerOrder = this.order): Euler {
        val matrix = Matrix4().makeRotationFromQuaternion(q)
        return setFromRotationMatrix(matrix, order)
    }

    /**
     * Sets this Euler from a rotation matrix
     */
    fun setFromRotationMatrix(m: Matrix4, order: EulerOrder = this.order): Euler {
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

        this.order = order

        when (order) {
            EulerOrder.XYZ -> {
                y = asin(clamp(m13, -1f, 1f))
                if (abs(m13) < 0.9999999f) {
                    x = atan2(-m23, m33)
                    z = atan2(-m12, m11)
                } else {
                    x = atan2(m32, m22)
                    z = 0f
                }
            }

            EulerOrder.YXZ -> {
                x = asin(-clamp(m23, -1f, 1f))
                if (abs(m23) < 0.9999999f) {
                    y = atan2(m13, m33)
                    z = atan2(m21, m22)
                } else {
                    y = atan2(-m31, m11)
                    z = 0f
                }
            }

            EulerOrder.ZXY -> {
                x = asin(clamp(m32, -1f, 1f))
                if (abs(m32) < 0.9999999f) {
                    y = atan2(-m31, m33)
                    z = atan2(-m12, m22)
                } else {
                    y = 0f
                    z = atan2(m21, m11)
                }
            }

            EulerOrder.ZYX -> {
                y = asin(-clamp(m31, -1f, 1f))
                if (abs(m31) < 0.9999999f) {
                    x = atan2(m32, m33)
                    z = atan2(m21, m11)
                } else {
                    x = 0f
                    z = atan2(-m12, m22)
                }
            }

            EulerOrder.YZX -> {
                z = asin(clamp(m21, -1f, 1f))
                if (abs(m21) < 0.9999999f) {
                    x = atan2(-m23, m22)
                    y = atan2(-m31, m11)
                } else {
                    x = 0f
                    y = atan2(m13, m33)
                }
            }

            EulerOrder.XZY -> {
                z = asin(-clamp(m12, -1f, 1f))
                if (abs(m12) < 0.9999999f) {
                    x = atan2(m32, m22)
                    y = atan2(m13, m11)
                } else {
                    x = atan2(-m23, m33)
                    y = 0f
                }
            }
        }

        return this
    }

    /**
     * Converts this Euler to a quaternion
     */
    fun toQuaternion(): Quaternion {
        return Quaternion().setFromEuler(this)
    }

    /**
     * Converts this Euler to a rotation matrix
     */
    fun toMatrix4(): Matrix4 {
        return Matrix4().makeRotationFromEuler(this)
    }

    /**
     * Reorders this Euler to a different rotation order
     */
    fun reorder(newOrder: EulerOrder): Euler {
        val q = toQuaternion()
        return setFromQuaternion(q, newOrder)
    }

    /**
     * Checks if this Euler equals another within tolerance
     */
    fun equals(euler: Euler, tolerance: Float = 1e-6f): Boolean {
        return abs(x - euler.x) < tolerance &&
                abs(y - euler.y) < tolerance &&
                abs(z - euler.z) < tolerance &&
                order == euler.order
    }

    /**
     * Converts to array
     */
    fun toArray(): FloatArray {
        return floatArrayOf(x, y, z)
    }

    /**
     * Sets from array
     */
    fun fromArray(array: FloatArray, offset: Int = 0): Euler {
        x = array[offset]
        y = array[offset + 1]
        z = array[offset + 2]
        return this
    }

    /**
     * Converts angles to degrees
     */
    fun toDegrees(): Euler {
        return Euler(
            x * 180f / PI.toFloat(),
            y * 180f / PI.toFloat(),
            z * 180f / PI.toFloat(),
            order
        )
    }

    /**
     * Converts angles from degrees to radians
     */
    fun fromDegrees(xDeg: Float, yDeg: Float, zDeg: Float, order: EulerOrder = this.order): Euler {
        return set(
            xDeg * PI.toFloat() / 180f,
            yDeg * PI.toFloat() / 180f,
            zDeg * PI.toFloat() / 180f,
            order
        )
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    override fun toString(): String {
        return "Euler(x=$x, y=$y, z=$z, order=$order)"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Euler) return false
        return _x == other._x && _y == other._y && _z == other._z && _order == other._order
    }
    
    override fun hashCode(): Int {
        var result = _x.hashCode()
        result = 31 * result + _y.hashCode()
        result = 31 * result + _z.hashCode()
        result = 31 * result + _order.hashCode()
        return result
    }
}

/**
 * Extension function for Matrix4 to create rotation from Euler
 */
fun Matrix4.makeRotationFromEuler(euler: Euler): Matrix4 {
    val x = euler.x;
    val y = euler.y;
    val z = euler.z

    val a = cos(x);
    val b = sin(x)
    val c = cos(y);
    val d = sin(y)
    val e = cos(z);
    val f = sin(z)

    when (euler.order) {
        EulerOrder.XYZ -> {
            val ae = (a * e);
            val af = (a * f);
            val be = (b * e);
            val bf = b * f

            elements[0] = c * e
            elements[4] = -c * f
            elements[8] = d

            elements[1] = af + be * d
            elements[5] = ae - bf * d
            elements[9] = -b * c

            elements[2] = bf - ae * d
            elements[6] = be + af * d
            elements[10] = (a * c)
        }

        EulerOrder.YXZ -> {
            val ce = (c * e);
            val cf = (c * f);
            val de = (d * e);
            val df = d * f

            elements[0] = ce + df * b
            elements[4] = de * b - cf
            elements[8] = a * d

            elements[1] = a * f
            elements[5] = a * e
            elements[9] = -b

            elements[2] = cf * b - de
            elements[6] = df + ce * b
            elements[10] = (a * c)
        }

        EulerOrder.ZXY -> {
            val ce = (c * e);
            val cf = (c * f);
            val de = (d * e);
            val df = d * f

            elements[0] = ce - df * b
            elements[4] = -a * f
            elements[8] = de + cf * b

            elements[1] = cf + de * b
            elements[5] = a * e
            elements[9] = df - ce * b

            elements[2] = -a * d
            elements[6] = b
            elements[10] = (a * c)
        }

        EulerOrder.ZYX -> {
            val ae = (a * e);
            val af = (a * f);
            val be = (b * e);
            val bf = b * f

            elements[0] = c * e
            elements[4] = be * d - af
            elements[8] = ae * d + bf

            elements[1] = c * f
            elements[5] = bf * d + ae
            elements[9] = af * d - be

            elements[2] = -d
            elements[6] = b * c
            elements[10] = (a * c)
        }

        EulerOrder.YZX -> {
            val ac = (a * c);
            val ad = (a * d);
            val bc = (b * c);
            val bd = b * d

            elements[0] = c * e
            elements[4] = bd - ac * f
            elements[8] = bc * f + ad

            elements[1] = f
            elements[5] = a * e
            elements[9] = -b * e

            elements[2] = -d * e
            elements[6] = ad * f + bc
            elements[10] = ac - (bd * f)
        }

        EulerOrder.XZY -> {
            val ac = (a * c);
            val ad = (a * d);
            val bc = (b * c);
            val bd = b * d

            elements[0] = c * e
            elements[4] = -f
            elements[8] = d * e

            elements[1] = ac * f + bd
            elements[5] = a * e
            elements[9] = ad * f - bc

            elements[2] = bc * f - ad
            elements[6] = b * e
            elements[10] = bd * f + ac
        }
    }

    // Bottom row
    elements[3] = 0f
    elements[7] = 0f
    elements[11] = 0f

    // Last column
    elements[12] = 0f
    elements[13] = 0f
    elements[14] = 0f
    elements[15] = 1f

    return this
}