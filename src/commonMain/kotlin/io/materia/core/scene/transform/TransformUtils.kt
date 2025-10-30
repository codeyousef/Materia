package io.materia.core.scene.transform

import io.materia.core.scene.Transform

/**
 * Transform utility functions and helpers.
 * Provides query and validation methods for transforms.
 */

/**
 * Checks if transform has any rotation
 */
fun Transform.hasRotation(): Boolean {
    return !quaternion.isIdentity()
}

/**
 * Checks if transform has any scale other than 1,1,1
 */
fun Transform.hasScale(): Boolean {
    return scale.x != 1f || scale.y != 1f || scale.z != 1f
}

/**
 * Checks if transform has any translation
 */
fun Transform.hasTranslation(): Boolean {
    return !position.isZero()
}

/**
 * Checks if transform is identity (no transformation)
 */
fun Transform.isIdentity(): Boolean {
    return !hasTranslation() && !hasRotation() && !hasScale()
}

/**
 * Copies from another transform
 */
fun Transform.copy(source: Transform): Transform {
    position.copy(source.position)
    rotation.copy(source.rotation)
    scale.copy(source.scale)
    quaternion.copy(source.quaternion)

    localMatrix.copy(source.localMatrix)
    worldMatrix.copy(source.worldMatrix)

    needsMatrixUpdate = source.needsMatrixUpdate
    needsWorldMatrixUpdate = source.needsWorldMatrixUpdate

    return this
}

/**
 * Creates a copy of this transform
 */
fun Transform.clone(): Transform {
    return Transform().copy(this)
}
