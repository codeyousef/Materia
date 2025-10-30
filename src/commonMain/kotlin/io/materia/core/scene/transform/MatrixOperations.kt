package io.materia.core.scene.transform

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.core.scene.Transform
import io.materia.core.scene.setFromMatrixPosition

/**
 * Matrix operation utilities for transform calculations.
 * Handles matrix updates, world/local conversions, and decomposition.
 */

/**
 * Updates the local transformation matrix
 */
fun Transform.updateLocalMatrix(): Matrix4 {
    if (needsMatrixUpdate) {
        localMatrix.compose(position, quaternion, scale)
        needsMatrixUpdate = false
        needsWorldMatrixUpdate = true
    }
    return localMatrix
}

/**
 * Updates the world transformation matrix
 */
fun Transform.updateWorldMatrix(): Matrix4 {
    updateLocalMatrix()

    if (needsWorldMatrixUpdate) {
        val parentObj = parent
        if (parentObj == null) {
            worldMatrix.copy(localMatrix)
        } else {
            worldMatrix.multiplyMatrices(parentObj.updateWorldMatrix(), localMatrix)
        }
        needsWorldMatrixUpdate = false
    }

    return worldMatrix
}

/**
 * Gets world position
 */
fun Transform.getWorldPosition(target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    return target.setFromMatrixPosition(worldMatrix)
}

/**
 * Gets world rotation as quaternion
 */
fun Transform.getWorldQuaternion(target: Quaternion = Quaternion()): Quaternion {
    updateWorldMatrix()
    worldMatrix.decompose(Vector3(), target, Vector3())
    return target
}

/**
 * Gets world scale
 */
fun Transform.getWorldScale(target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    worldMatrix.decompose(Vector3(), Quaternion(), target)
    return target
}

/**
 * Gets world direction (forward vector)
 */
fun Transform.getWorldDirection(target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    val te = worldMatrix.elements
    return target.set(-te[8], -te[9], -te[10]).normalize()
}

/**
 * Gets world up vector
 */
fun Transform.getWorldUp(target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    val te = worldMatrix.elements
    return target.set(te[4], te[5], te[6]).normalize()
}

/**
 * Gets world right vector
 */
fun Transform.getWorldRight(target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    val te = worldMatrix.elements
    return target.set(te[0], te[1], te[2]).normalize()
}

/**
 * Converts local point to world space
 */
fun Transform.localToWorld(localPoint: Vector3, target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    return target.copy(localPoint).applyMatrix4(worldMatrix)
}

/**
 * Converts world point to local space
 */
fun Transform.worldToLocal(worldPoint: Vector3, target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    val inverseWorldMatrix = Matrix4().copy(worldMatrix).invert()
    return target.copy(worldPoint).applyMatrix4(inverseWorldMatrix)
}

/**
 * Converts local direction to world space
 */
fun Transform.localDirectionToWorld(localDirection: Vector3, target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    return target.copy(localDirection).transformDirection(worldMatrix)
}

/**
 * Converts world direction to local space
 */
fun Transform.worldDirectionToLocal(worldDirection: Vector3, target: Vector3 = Vector3()): Vector3 {
    updateWorldMatrix()
    val inverseWorldMatrix = Matrix4().copy(worldMatrix).invert()
    return target.copy(worldDirection).transformDirection(inverseWorldMatrix)
}

/**
 * Forces update of all matrices
 */
fun Transform.forceUpdate(): Transform {
    needsMatrixUpdate = true
    needsWorldMatrixUpdate = true
    updateWorldMatrix()
    return this
}
