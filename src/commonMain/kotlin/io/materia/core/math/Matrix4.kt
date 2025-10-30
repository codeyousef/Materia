package io.materia.core.math

import kotlin.math.*

/**
 * A 4x4 matrix stored in column-major order (OpenGL/WebGL convention).
 * Compatible with Three.js Matrix4 API.
 *
 * Matrix layout:
 * | m[0] m[4] m[8]  m[12] |   | m11 m12 m13 m14 |
 * | m[1] m[5] m[9]  m[13] |   | m21 m22 m23 m24 |
 * | m[2] m[6] m[10] m[14] | = | m31 m32 m33 m34 |
 * | m[3] m[7] m[11] m[15] |   | m41 m42 m43 m44 |
 */
data class Matrix4(
    val elements: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,  // column 0
        0f, 1f, 0f, 0f,  // column 1
        0f, 0f, 1f, 0f,  // column 2
        0f, 0f, 0f, 1f   // column 3
    )
) {
    // Matrix element accessors (row-column notation)
    val m00: Float get() = elements[0]
    val m10: Float get() = elements[1]
    val m20: Float get() = elements[2]
    val m30: Float get() = elements[3]

    val m01: Float get() = elements[4]
    val m11: Float get() = elements[5]
    val m21: Float get() = elements[6]
    val m31: Float get() = elements[7]

    val m02: Float get() = elements[8]
    val m12: Float get() = elements[9]
    val m22: Float get() = elements[10]
    val m32: Float get() = elements[11]

    val m03: Float get() = elements[12]
    val m13: Float get() = elements[13]
    val m23: Float get() = elements[14]
    val m33: Float get() = elements[15]

    companion object {
        val IDENTITY: Matrix4 get() = Matrix4()

        fun identity(): Matrix4 = Matrix4()
        fun translation(x: Float, y: Float, z: Float): Matrix4 = Matrix4().makeTranslation(x, y, z)
        fun scale(x: Float, y: Float, z: Float): Matrix4 = Matrix4().makeScale(x, y, z)
        fun rotationX(theta: Float): Matrix4 = Matrix4().makeRotationX(theta)
        fun rotationY(theta: Float): Matrix4 = Matrix4().makeRotationY(theta)
        fun rotationZ(theta: Float): Matrix4 = Matrix4().makeRotationZ(theta)
    }

    // Core operations (delegated to Matrix4Core.kt)
    fun identity(): Matrix4 = setIdentity()
    fun isIdentity(): Boolean = checkIsIdentity()
    fun clone(): Matrix4 = createClone()
    fun copy(matrix: Matrix4): Matrix4 = copyFrom(matrix)
    fun set(
        m11: Float, m12: Float, m13: Float, m14: Float,
        m21: Float, m22: Float, m23: Float, m24: Float,
        m31: Float, m32: Float, m33: Float, m34: Float,
        m41: Float, m42: Float, m43: Float, m44: Float
    ): Matrix4 =
        setElements(m11, m12, m13, m14, m21, m22, m23, m24, m31, m32, m33, m34, m41, m42, m43, m44)

    fun getPosition(): Vector3 = extractPosition()
    fun setPosition(x: Float, y: Float, z: Float): Matrix4 = updatePosition(x, y, z)
    fun setPosition(v: Vector3): Matrix4 = updatePositionFromVector(v)
    fun getScale(): Vector3 = extractScale()
    fun multiplyPoint3(point: Vector3): Vector3 = transformPoint3(point)
    fun extractTranslation(target: Vector3 = Vector3()): Vector3 = extractTranslationTo(target)
    fun toArray(): FloatArray = asArray()
    fun toArray(array: FloatArray, offset: Int = 0) = copyToArray(array, offset)
    fun fromArray(array: FloatArray, offset: Int = 0): Matrix4 = setFromArray(array, offset)

    // Transformation operations (delegated to Matrix4Transformations.kt)
    fun makeTranslation(x: Float, y: Float, z: Float): Matrix4 = makeTranslationMatrix(x, y, z)
    fun makeScale(x: Float, y: Float, z: Float): Matrix4 = makeScaleMatrix(x, y, z)
    fun makeRotationX(theta: Float): Matrix4 = makeRotationXMatrix(theta)
    fun makeRotationY(theta: Float): Matrix4 = makeRotationYMatrix(theta)
    fun makeRotationZ(theta: Float): Matrix4 = makeRotationZMatrix(theta)
    fun getTranslation(): Vector3 = getTranslationVector()
    fun getRotation(): Quaternion = getRotationQuaternion()
    fun transformPoint(point: Vector3): Vector3 = transformPointWithTranslation(point)
    fun transformDirection(direction: Vector3): Vector3 = transformDirectionOnly(direction)
    fun translate(offset: Vector3): Matrix4 = applyTranslation(offset)
    fun rotate(rotation: Quaternion): Matrix4 = applyRotation(rotation)
    fun makeRotationFromQuaternion(q: Quaternion): Matrix4 = makeRotationFromQuaternionInternal(q)
    fun scale(scale: Vector3): Matrix4 = applyScale(scale)
    fun lookAt(eye: Vector3, target: Vector3, up: Vector3): Matrix4 = setLookAt(eye, target, up)

    // Matrix operations (delegated to Matrix4Operations.kt)
    fun multiply(matrix: Matrix4): Matrix4 = multiplyMatrix(matrix)
    fun premultiply(matrix: Matrix4): Matrix4 = premultiplyMatrix(matrix)
    fun multiplyMatrices(a: Matrix4, b: Matrix4): Matrix4 = multiplyMatricesInternal(a, b)
    fun determinant(): Float = calculateDeterminant()
    fun invert(): Matrix4 = invertMatrix()
    fun transpose(): Matrix4 = transposeMatrix()
    operator fun times(other: Matrix4): Matrix4 = clone().multiply(other)

    // Projection operations (delegated to Matrix4Projections.kt)
    fun makePerspective(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        near: Float,
        far: Float
    ): Matrix4 =
        makePerspectiveProjection(left, right, top, bottom, near, far)

    fun makePerspectiveWebGPU(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        near: Float,
        far: Float
    ): Matrix4 =
        makePerspectiveProjectionWebGPU(left, right, top, bottom, near, far)

    fun makeOrthographic(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        near: Float,
        far: Float
    ): Matrix4 =
        makeOrthographicProjection(left, right, top, bottom, near, far)

    // Decomposition operations (delegated to Matrix4Decomposition.kt)
    fun compose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4 =
        composeFromPQS(position, quaternion, scale)

    fun decompose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4 =
        decomposeToPQS(position, quaternion, scale)

    // Utility methods
    val viewMatrix: Matrix4 get() = clone().invert()
    fun inverse(): Matrix4 = clone().invert()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix4) return false
        return elements.contentEquals(other.elements)
    }

    override fun hashCode(): Int = elements.contentHashCode()

    override fun toString(): String {
        return "Matrix4(\n" +
                "  ${elements[0]}, ${elements[4]}, ${elements[8]}, ${elements[12]}\n" +
                "  ${elements[1]}, ${elements[5]}, ${elements[9]}, ${elements[13]}\n" +
                "  ${elements[2]}, ${elements[6]}, ${elements[10]}, ${elements[14]}\n" +
                "  ${elements[3]}, ${elements[7]}, ${elements[11]}, ${elements[15]}\n" +
                ")"
    }
}
