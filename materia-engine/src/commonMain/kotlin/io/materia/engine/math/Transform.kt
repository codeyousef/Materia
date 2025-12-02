package io.materia.engine.math

/**
 * Encapsulates position, rotation, and scale for scene graph transformations.
 *
 * The transform lazily computes its 4x4 matrix via [matrix] and caches the result
 * until any property changes. Attach a [changeListener] to receive notifications
 * when the transform becomes dirty.
 *
 * Rotation is stored as Euler angles (radians) in XYZ order. For complex rotations
 * that require gimbal-lock-free interpolation, consider using [Quat] directly.
 */
class Transform {
    /** World-space position of this transform. */
    val position: Vec3 = vec3()

    /** Rotation as Euler angles (radians) applied in XYZ order. */
    val rotationEuler: Vec3 = vec3()

    /** Non-uniform scale factors along each axis. */
    val scale: Vec3 = vec3(1f, 1f, 1f)

    private val matrixCache = mat4()
    private var dirty = true

    /**
     * Optional callback invoked whenever the transform is marked dirty.
     * Useful for propagating invalidation through a scene hierarchy.
     */
    var changeListener: (() -> Unit)? = null

    /**
     * Marks this transform as dirty, triggering matrix recalculation on next [matrix] call.
     */
    fun markDirty() {
        if (!dirty) {
            dirty = true
            changeListener?.invoke()
        }
    }

    /**
     * Returns the cached 4x4 transformation matrix, recomputing if dirty.
     *
     * The matrix combines scale, rotation (Euler XYZ), and translation.
     *
     * @return The local transformation matrix.
     */
    fun matrix(): Mat4 {
        if (!dirty) return matrixCache
        val matrix = matrixCache
        val sx = scale.x
        val sy = scale.y
        val sz = scale.z
        val rx = rotationEuler.x
        val ry = rotationEuler.y
        val rz = rotationEuler.z

        val cx = kotlin.math.cos(rx)
        val sxSin = kotlin.math.sin(rx)
        val cy = kotlin.math.cos(ry)
        val sySin = kotlin.math.sin(ry)
        val cz = kotlin.math.cos(rz)
        val szSin = kotlin.math.sin(rz)

        val m00 = cy * cz
        val m01 = cy * szSin
        val m02 = -sySin

        val m10 = sxSin * sySin * cz - cx * szSin
        val m11 = sxSin * sySin * szSin + cx * cz
        val m12 = sxSin * cy

        val m20 = cx * sySin * cz + sxSin * szSin
        val m21 = cx * sySin * szSin - sxSin * cz
        val m22 = cx * cy

        matrix[0] = m00 * sx
        matrix[1] = m10 * sx
        matrix[2] = m20 * sx
        matrix[3] = 0f

        matrix[4] = m01 * sy
        matrix[5] = m11 * sy
        matrix[6] = m21 * sy
        matrix[7] = 0f

        matrix[8] = m02 * sz
        matrix[9] = m12 * sz
        matrix[10] = m22 * sz
        matrix[11] = 0f

        matrix[12] = position.x
        matrix[13] = position.y
        matrix[14] = position.z
        matrix[15] = 1f

        dirty = false
        return matrix
    }

    /**
     * Indicates whether the matrix needs recomputation.
     *
     * @return True if position, rotation, or scale has changed since last [matrix] call.
     */
    fun isDirty(): Boolean = dirty

    /**
     * Sets the position and marks the transform dirty.
     *
     * @return This transform for chaining.
     */
    fun setPosition(x: Float, y: Float, z: Float): Transform {
        position.set(x, y, z)
        dirty = true
        changeListener?.invoke()
        return this
    }

    /**
     * Sets the Euler rotation (radians) and marks the transform dirty.
     *
     * @return This transform for chaining.
     */
    fun setRotationEuler(x: Float, y: Float, z: Float): Transform {
        rotationEuler.set(x, y, z)
        dirty = true
        changeListener?.invoke()
        return this
    }

    /**
     * Sets the scale and marks the transform dirty.
     *
     * @return This transform for chaining.
     */
    fun setScale(x: Float, y: Float, z: Float): Transform {
        scale.set(x, y, z)
        dirty = true
        changeListener?.invoke()
        return this
    }
}
