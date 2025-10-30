package io.materia.core.scene

import io.materia.core.math.*
import io.materia.core.scene.transform.*

/**
 * Transform system for managing object transformations.
 * Handles position, rotation, scale, and matrix calculations.
 *
 * Core transform class with extension functions for operations in transform package.
 */
class Transform {

    // Local transformation components
    val position: Vector3 = Vector3()
    val rotation: Euler = Euler()
    val scale: Vector3 = Vector3(1f, 1f, 1f)
    val quaternion: Quaternion = Quaternion()

    // Transformation matrices
    val localMatrix: Matrix4 = Matrix4()
    val worldMatrix: Matrix4 = Matrix4()

    // Update flags
    var needsMatrixUpdate: Boolean = true
    var needsWorldMatrixUpdate: Boolean = true

    // Parent transform reference
    var parent: Transform? = null

    // Change callbacks
    var onPositionChanged: (() -> Unit)? = null
    var onRotationChanged: (() -> Unit)? = null
    var onScaleChanged: (() -> Unit)? = null

    init {
        setupChangeCallbacks()
    }

    /**
     * Sets up callbacks to maintain sync between rotation and quaternion
     */
    private fun setupChangeCallbacks() {
        // When rotation changes, update quaternion
        onRotationChanged = {
            quaternion.setFromEuler(rotation)
            needsMatrixUpdate = true
            needsWorldMatrixUpdate = true
        }
    }

    override fun toString(): String {
        return "Transform(pos=$position, rot=$rotation, scale=$scale)"
    }
}

// All operations are now available as extension functions from the transform package:
// - TransformOperations.kt: setPosition, setRotation, setScale, translate, rotate, scale, lerp, lookAt, reset
// - MatrixOperations.kt: updateLocalMatrix, updateWorldMatrix, getWorldPosition, getWorldQuaternion, etc.
// - TransformUtils.kt: hasRotation, hasScale, hasTranslation, isIdentity, copy, clone
// - TransformHierarchy.kt: TransformHierarchy, TransformPropagation, TransformAnimator
