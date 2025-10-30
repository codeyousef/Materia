/**
 * Base collision shape implementation
 * Provides common functionality for all shape types
 */
package io.materia.physics

import io.materia.core.math.Box3
import io.materia.core.math.*

/**
 * Base collision shape implementation
 * Provides common functionality for all shape types
 */
abstract class CollisionShapeImpl : CollisionShape {
    override var margin: Float = 0.04f
        protected set
    override var localScaling: Vector3 = Vector3.ONE
        protected set

    protected var _boundingBox: Box3 = Box3()

    override val boundingBox: Box3
        get() {
            if (_boundingBox.isEmpty()) {
                calculateBoundingBox()
            }
            return _boundingBox
        }

    /**
     * Calculate the bounding box for this shape
     * Must be implemented by subclasses
     */
    protected abstract fun calculateBoundingBox()

    /**
     * Invalidate cached bounding box when shape changes
     */
    protected fun invalidateBoundingBox() {
        _boundingBox = Box3()
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        if (mass <= 0f) return Matrix3.ZERO

        val localInertia = calculateLocalInertia(mass)
        return Matrix3(
            floatArrayOf(
                localInertia.x, 0f, 0f,
                0f, localInertia.y, 0f,
                0f, 0f, localInertia.z
            )
        )
    }

    override fun serialize(): ByteArray {
        // Basic serialization - can be extended for specific shape types
        return byteArrayOf(
            shapeType.ordinal.toByte(),
            *margin.toBits().let {
                byteArrayOf(
                    (it shr 24).toByte(),
                    (it shr 16).toByte(),
                    (it shr 8).toByte(),
                    it.toByte()
                )
            },
            *localScaling.x.toBits().let {
                byteArrayOf(
                    (it shr 24).toByte(),
                    (it shr 16).toByte(),
                    (it shr 8).toByte(),
                    it.toByte()
                )
            },
            *localScaling.y.toBits().let {
                byteArrayOf(
                    (it shr 24).toByte(),
                    (it shr 16).toByte(),
                    (it shr 8).toByte(),
                    it.toByte()
                )
            },
            *localScaling.z.toBits().let {
                byteArrayOf(
                    (it shr 24).toByte(),
                    (it shr 16).toByte(),
                    (it shr 8).toByte(),
                    it.toByte()
                )
            }
        )
    }
}
