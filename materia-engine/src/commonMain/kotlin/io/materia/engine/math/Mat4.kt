package io.materia.engine.math

import kotlin.jvm.JvmInline
import kotlin.math.PI
import kotlin.math.tan

@PublishedApi
internal fun mat4Array(): FloatArray = FloatArray(16)

/**
 * Mutable 4x4 transformation matrix stored in column-major order.
 *
 * This matrix type supports common 3D transformations including perspective
 * projection, view (look-at), translation, and matrix multiplication.
 * Elements are stored in a 16-element [FloatArray] compatible with GPU uploads.
 *
 * Column-major layout:
 * ```
 * | m[0]  m[4]  m[8]   m[12] |
 * | m[1]  m[5]  m[9]   m[13] |
 * | m[2]  m[6]  m[10]  m[14] |
 * | m[3]  m[7]  m[11]  m[15] |
 * ```
 *
 * @see mat4 Factory function for creating instances.
 */
@JvmInline
value class Mat4 @PublishedApi internal constructor(internal val data: FloatArray) {
    init {
        require(data.size == 16) { "Mat4 requires exactly 16 elements (found ${data.size})" }
    }

    operator fun get(index: Int): Float = data[index]

    operator fun set(index: Int, value: Float) {
        data[index] = value
    }

    fun copy(): Mat4 = mat4().copyFrom(this)

    fun copyFrom(other: Mat4): Mat4 = apply {
        other.data.copyInto(data, 0, 0, 16)
    }

    /**
     * Sets this matrix to the identity matrix (diagonal ones, zeros elsewhere).
     *
     * @return This matrix for chaining.
     */
    fun setIdentity(): Mat4 = apply {
        data.fill(0f)
        data[0] = 1f
        data[5] = 1f
        data[10] = 1f
        data[15] = 1f
    }

    /**
     * Multiplies two matrices and stores the result in this matrix.
     *
     * Computes `this = left × right` using standard matrix multiplication.
     *
     * @param left The left-hand matrix operand.
     * @param right The right-hand matrix operand.
     * @return This matrix containing the product.
     */
    fun multiply(left: Mat4, right: Mat4): Mat4 {
        val a = left.data
        val b = right.data
        val out = data
        for (row in 0 until 4) {
            val r0 = a[row]
            val r1 = a[row + 4]
            val r2 = a[row + 8]
            val r3 = a[row + 12]

            out[row] = r0 * b[0] + r1 * b[1] + r2 * b[2] + r3 * b[3]
            out[row + 4] = r0 * b[4] + r1 * b[5] + r2 * b[6] + r3 * b[7]
            out[row + 8] = r0 * b[8] + r1 * b[9] + r2 * b[10] + r3 * b[11]
            out[row + 12] = r0 * b[12] + r1 * b[13] + r2 * b[14] + r3 * b[15]
        }
        return this
    }

    /**
     * Configures this matrix as a perspective projection.
     *
     * Uses WebGPU/Vulkan depth range [0, 1] rather than OpenGL's [-1, 1].
     * The resulting projection maps the view frustum to normalized device coordinates.
     *
     * @param fovDegrees Vertical field of view in degrees.
     * @param aspect Aspect ratio (width / height).
     * @param near Distance to the near clipping plane (must be positive).
     * @param far Distance to the far clipping plane (must be greater than near).
     * @return This matrix configured as a perspective projection.
     */
    fun setPerspective(fovDegrees: Float, aspect: Float, near: Float, far: Float): Mat4 {
        val halfAngleRadians = (fovDegrees / 180f) * PI.toFloat() * 0.5f
        val f = (1f / tan(halfAngleRadians.toDouble())).toFloat()

        data.fill(0f)
        data[0] = f / aspect
        data[5] = f  // Positive Y - standard right-handed convention
        // WebGPU/Vulkan use depth range [0, 1], not [-1, 1] like OpenGL
        data[10] = far / (near - far)
        data[11] = -1f
        data[14] = (near * far) / (near - far)
        return this
    }

    /**
     * Configures this matrix as a view (camera) transformation.
     *
     * Builds a right-handed look-at matrix that positions the camera at [eye],
     * oriented to face [target], with the given [up] direction.
     *
     * @param eye The camera position in world space.
     * @param target The point the camera is looking at.
     * @param up The world-space up direction, defaults to Y-up.
     * @return This matrix configured as a view transformation.
     */
    fun setLookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.Up): Mat4 {
        // Compute forward direction (from eye to target)
        val forwardX = target.x - eye.x
        val forwardY = target.y - eye.y
        val forwardZ = target.z - eye.z
        val forwardLen = kotlin.math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ)
        val fwdX = forwardX / forwardLen
        val fwdY = forwardY / forwardLen
        val fwdZ = forwardZ / forwardLen

        // Right = up × forward (for right-handed coords)
        var rightX = up.y * fwdZ - up.z * fwdY
        var rightY = up.z * fwdX - up.x * fwdZ
        var rightZ = up.x * fwdY - up.y * fwdX
        val rightLen = kotlin.math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
        rightX /= rightLen
        rightY /= rightLen
        rightZ /= rightLen

        // Recompute up = forward × right
        val upX = fwdY * rightZ - fwdZ * rightY
        val upY = fwdZ * rightX - fwdX * rightZ
        val upZ = fwdX * rightY - fwdY * rightX

        // Build view matrix (camera looks along -Z in view space)
        // Row 0: right
        data[0] = rightX
        data[4] = rightY
        data[8] = rightZ
        data[12] = -(rightX * eye.x + rightY * eye.y + rightZ * eye.z)

        // Row 1: up  
        data[1] = upX
        data[5] = upY
        data[9] = upZ
        data[13] = -(upX * eye.x + upY * eye.y + upZ * eye.z)

        // Row 2: -forward (camera looks along -Z)
        data[2] = -fwdX
        data[6] = -fwdY
        data[10] = -fwdZ
        data[14] = fwdX * eye.x + fwdY * eye.y + fwdZ * eye.z

        data[3] = 0f
        data[7] = 0f
        data[11] = 0f
        data[15] = 1f
        return this
    }

    /**
     * Applies a translation to this matrix by adding to the translation column.
     *
     * @param offset The translation vector to apply.
     * @return This matrix for chaining.
     */
    fun translate(offset: Vec3): Mat4 = apply {
        data[12] += offset.x
        data[13] += offset.y
        data[14] += offset.z
    }

    /**
     * Returns the underlying float array, optionally as a copy.
     *
     * @param copy If true, returns a defensive copy; otherwise returns the backing array.
     * @return The 16-element column-major matrix data.
     */
    fun toFloatArray(copy: Boolean = false): FloatArray = if (copy) data.copyOf() else data

    companion object {
        /**
         * Creates a new identity matrix.
         *
         * @return A fresh [Mat4] set to the identity transformation.
         */
        fun identity(): Mat4 = mat4().setIdentity()
    }
}

/**
 * Creates a new uninitialized [Mat4].
 *
 * The matrix data is zeroed; call [Mat4.setIdentity] or another configuration
 * method before use.
 *
 * @return A new 4x4 matrix.
 */
fun mat4(): Mat4 = Mat4(mat4Array())
