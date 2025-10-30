package io.materia.core.math

import kotlin.math.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 3x3 matrix stored in column-major order.
 * Compatible with Three.js Matrix3 API.
 *
 * Matrix layout:
 * | m[0] m[3] m[6] |   | m11 m12 m13 |
 * | m[1] m[4] m[7] | = | m21 m22 m23 |
 * | m[2] m[5] m[8] |   | m31 m32 m33 |
 */
data class Matrix3(
    val elements: FloatArray = floatArrayOf(
        1f, 0f, 0f,  // column 0
        0f, 1f, 0f,  // column 1
        0f, 0f, 1f   // column 2
    )
) {
    // Matrix element accessors (row-column notation)
    val m00: Float get() = elements[0]
    val m10: Float get() = elements[1]
    val m20: Float get() = elements[2]

    val m01: Float get() = elements[3]
    val m11: Float get() = elements[4]
    val m21: Float get() = elements[5]

    val m02: Float get() = elements[6]
    val m12: Float get() = elements[7]
    val m22: Float get() = elements[8]

    companion object {
        val IDENTITY: Matrix3
            get() = Matrix3()

        /**
         * Creates an identity matrix
         */
        fun identity(): Matrix3 = Matrix3()

        /**
         * Creates a scale matrix
         */
        fun scale(x: Float, y: Float): Matrix3 =
            Matrix3().makeScale(x, y)

        /**
         * Creates a diagonal matrix from a vector
         */
        fun diagonal(vector: Vector3): Matrix3 = Matrix3(
            floatArrayOf(
                vector.x, 0f, 0f,
                0f, vector.y, 0f,
                0f, 0f, vector.z
            )
        )

        /**
         * Creates a zero matrix
         */
        fun zero(): Matrix3 = Matrix3(
            floatArrayOf(
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f
            )
        )

        /**
         * Creates a rotation matrix
         */
        fun rotation(theta: Float): Matrix3 =
            Matrix3().makeRotation(theta)

        /**
         * Creates a translation matrix
         */
        fun translation(x: Float, y: Float): Matrix3 =
            Matrix3().makeTranslation(x, y)

        /**
         * Creates a normal matrix from a Matrix4 transformation matrix
         */
        fun normalMatrix(matrix: Matrix4): Matrix3 {
            val result = Matrix3()
            return result.getNormalMatrix(matrix)
        }

        fun fromQuaternion(q: Quaternion): Matrix3 {
            val matrix = Matrix3()
            val x = q.x
            val y = q.y
            val z = q.z
            val w = q.w

            val x2 = x + x
            val y2 = y + y
            val z2 = z + z
            val xx = x * x2
            val xy = x * y2
            val xz = x * z2
            val yy = y * y2
            val yz = y * z2
            val zz = z * z2
            val wx = w * x2
            val wy = w * y2
            val wz = w * z2

            matrix.elements[0] = 1f - (yy + zz)
            matrix.elements[3] = xy - wz
            matrix.elements[6] = xz + wy

            matrix.elements[1] = xy + wz
            matrix.elements[4] = 1f - (xx + zz)
            matrix.elements[7] = yz - wx

            matrix.elements[2] = xz - wy
            matrix.elements[5] = yz + wx
            matrix.elements[8] = 1f - (xx + yy)

            return matrix
        }
    }

    /**
     * Sets this matrix to identity
     */
    fun identity(): Matrix3 {
        elements[0] = 1f; elements[3] = 0f; elements[6] = 0f
        elements[1] = 0f; elements[4] = 1f; elements[7] = 0f
        elements[2] = 0f; elements[5] = 0f; elements[8] = 1f
        return this
    }

    /**
     * Checks if this matrix is the identity matrix
     */
    fun isIdentity(): Boolean {
        return elements[0] == 1f && elements[3] == 0f && elements[6] == 0f &&
                elements[1] == 0f && elements[4] == 1f && elements[7] == 0f &&
                elements[2] == 0f && elements[5] == 0f && elements[8] == 1f
    }

    /**
     * Creates a copy of this matrix
     */
    fun clone(): Matrix3 {
        return Matrix3(elements.copyOf())
    }

    /**
     * Copies values from another matrix
     */
    fun copy(matrix: Matrix3): Matrix3 {
        matrix.elements.copyInto(elements)
        return this
    }

    /**
     * Sets matrix elements from individual values
     */
    fun set(
        m11: Float, m12: Float, m13: Float,
        m21: Float, m22: Float, m23: Float,
        m31: Float, m32: Float, m33: Float
    ): Matrix3 {
        elements[0] = m11; elements[3] = m12; elements[6] = m13
        elements[1] = m21; elements[4] = m22; elements[7] = m23
        elements[2] = m31; elements[5] = m32; elements[8] = m33
        return this
    }

    /**
     * Sets this matrix from a 4x4 matrix (takes upper-left 3x3)
     */
    fun setFromMatrix4(matrix: Matrix4): Matrix3 {
        val me = matrix.elements
        elements[0] = me[0]; elements[3] = me[4]; elements[6] = me[8]
        elements[1] = me[1]; elements[4] = me[5]; elements[7] = me[9]
        elements[2] = me[2]; elements[5] = me[6]; elements[8] = me[10]
        return this
    }

    /**
     * Makes this matrix a 2D scale matrix
     */
    fun makeScale(x: Float, y: Float): Matrix3 {
        identity()
        elements[0] = x
        elements[4] = y
        return this
    }

    /**
     * Makes this matrix a 2D rotation matrix
     */
    fun makeRotation(theta: Float): Matrix3 {
        val c = cos(theta)
        val s = sin(theta)

        elements[0] = c; elements[3] = -s; elements[6] = 0f
        elements[1] = s; elements[4] = c; elements[7] = 0f
        elements[2] = 0f; elements[5] = 0f; elements[8] = 1f

        return this
    }

    /**
     * Makes this matrix a 2D translation matrix
     */
    fun makeTranslation(x: Float, y: Float): Matrix3 {
        identity()
        elements[6] = x
        elements[7] = y
        return this
    }

    /**
     * Multiplies this matrix by another matrix
     */
    fun multiply(matrix: Matrix3): Matrix3 {
        return multiplyMatrices(this, matrix)
    }

    /**
     * Multiplies another matrix by this matrix (order matters!)
     */
    fun premultiply(matrix: Matrix3): Matrix3 {
        return multiplyMatrices(matrix, this)
    }

    /**
     * Multiplies two matrices and stores result in this matrix
     */
    fun multiplyMatrices(a: Matrix3, b: Matrix3): Matrix3 {
        val ae = a.elements
        val be = b.elements
        val te = elements

        val a11 = ae[0];
        val a12 = ae[3];
        val a13 = ae[6]
        val a21 = ae[1];
        val a22 = ae[4];
        val a23 = ae[7]
        val a31 = ae[2];
        val a32 = ae[5];
        val a33 = ae[8]

        val b11 = be[0];
        val b12 = be[3];
        val b13 = be[6]
        val b21 = be[1];
        val b22 = be[4];
        val b23 = be[7]
        val b31 = be[2];
        val b32 = be[5];
        val b33 = be[8]

        te[0] = a11 * b11 + a12 * b21 + a13 * b31
        te[3] = a11 * b12 + a12 * b22 + a13 * b32
        te[6] = a11 * b13 + a12 * b23 + a13 * b33

        te[1] = a21 * b11 + a22 * b21 + a23 * b31
        te[4] = a21 * b12 + a22 * b22 + a23 * b32
        te[7] = a21 * b13 + a22 * b23 + a23 * b33

        te[2] = a31 * b11 + a32 * b21 + a33 * b31
        te[5] = a31 * b12 + a32 * b22 + a33 * b32
        te[8] = a31 * b13 + a32 * b23 + a33 * b33

        return this
    }

    /**
     * Multiplies this matrix by a scalar
     */
    fun multiplyScalar(scalar: Float): Matrix3 {
        for (i in elements.indices) {
            elements[i] *= scalar
        }
        return this
    }


    /**
     * Inverts this matrix
     */
    fun invert(): Matrix3 {
        val n11 = elements[0];
        val n21 = elements[1];
        val n31 = elements[2]
        val n12 = elements[3];
        val n22 = elements[4];
        val n32 = elements[5]
        val n13 = elements[6];
        val n23 = elements[7];
        val n33 = elements[8]

        val t11 = n33 * n22 - n32 * n23
        val t12 = n32 * n13 - n33 * n12
        val t13 = n23 * n12 - n22 * n13

        val det = n11 * t11 + n21 * t12 + n31 * t13

        val epsilon = 0.000001f
        if (abs(det) < epsilon) {
            throw IllegalArgumentException("Cannot invert matrix with determinant near zero: $det")
        }

        val detInv = 1f / det

        elements[0] = t11 * detInv
        elements[1] = (n31 * n23 - (n33 * n21)) * detInv
        elements[2] = (n32 * n21 - (n31 * n22)) * detInv

        elements[3] = t12 * detInv
        elements[4] = (n33 * n11 - (n31 * n13)) * detInv
        elements[5] = (n31 * n12 - (n32 * n11)) * detInv

        elements[6] = t13 * detInv
        elements[7] = (n21 * n13 - (n23 * n11)) * detInv
        elements[8] = (n22 * n11 - (n21 * n12)) * detInv

        return this
    }

    /**
     * Transposes this matrix
     */
    fun transpose(): Matrix3 {
        var tmp = elements[1]; elements[1] = elements[3]; elements[3] = tmp
        tmp = elements[2]; elements[2] = elements[6]; elements[6] = tmp
        tmp = elements[5]; elements[5] = elements[7]; elements[7] = tmp
        return this
    }

    /**
     * Transforms a Vector2 by this matrix (assumes homogeneous coordinates)
     */
    fun transformVector2(v: Vector2): Vector2 {
        val x = v.x;
        val y = v.y
        v.x = elements[0] * x + elements[3] * y + elements[6]
        v.y = elements[1] * x + elements[4] * y + elements[7]
        return v
    }

    /**
     * Transforms a Vector3 by this matrix (2D transform, ignores z)
     */
    fun transformVector3(v: Vector3): Vector3 {
        val x = v.x;
        val y = v.y
        v.x = elements[0] * x + elements[3] * y + elements[6]
        v.y = elements[1] * x + elements[4] * y + elements[7]
        return v
    }

    /**
     * Sets UV transform matrix for texture coordinate transformation
     */
    fun setUvTransform(
        tx: Float,
        ty: Float,
        sx: Float,
        sy: Float,
        rotation: Float,
        cx: Float,
        cy: Float
    ): Matrix3 {
        val c = cos(rotation)
        val s = sin(rotation)

        set(
            (sx * c), (sx * s), -sx * (c * cx + (s * cy)) + cx + tx,
            -(sy * s), (sy * c), -sy * (-s * cx + (c * cy)) + cy + ty,
            0f, 0f, 1f
        )

        return this
    }

    /**
     * Gets the normal matrix from a 4x4 transformation matrix
     * (inverse transpose of upper-left 3x3)
     */
    fun getNormalMatrix(matrix4: Matrix4): Matrix3 {
        return setFromMatrix4(matrix4).invert().transpose()
    }

    /**
     * Converts to array
     */
    fun toArray(): FloatArray {
        return elements.copyOf()
    }

    /**
     * Sets from array
     */
    fun fromArray(array: FloatArray, offset: Int = 0): Matrix3 {
        for (i in 0 until 9) {
            elements[i] = array[offset + i]
        }
        return this
    }

    /**
     * Returns a new inverted matrix (doesn't modify this matrix)
     */
    fun inverse(): Matrix3 = clone().invert()

    /**
     * Calculates the determinant of this matrix
     */
    fun determinant(): Float {
        val m = elements
        return m[0] * (m[4] * m[8] - m[7] * m[5]) -
                m[3] * (m[1] * m[8] - m[7] * m[2]) +
                m[6] * (m[1] * m[5] - m[4] * m[2])
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix3) return false
        return elements.contentEquals(other.elements)
    }

    override fun hashCode(): Int {
        return elements.contentHashCode()
    }

    /**
     * Multiply Matrix3 by Vector3
     */
    operator fun times(vector: Vector3): Vector3 {
        val x = elements[0] * vector.x + elements[3] * vector.y + elements[6] * vector.z
        val y = elements[1] * vector.x + elements[4] * vector.y + elements[7] * vector.z
        val z = elements[2] * vector.x + elements[5] * vector.y + elements[8] * vector.z
        return Vector3(x, y, z)
    }

    /**
     * Multiply Matrix3 by Matrix3
     */
    operator fun times(other: Matrix3): Matrix3 = clone().multiply(other)

    override fun toString(): String {
        return "Matrix3(\n" +
                "  ${elements[0]}, ${elements[3]}, ${elements[6]}\n" +
                "  ${elements[1]}, ${elements[4]}, ${elements[7]}\n" +
                "  ${elements[2]}, ${elements[5]}, ${elements[8]}\n" +
                ")"
    }

}