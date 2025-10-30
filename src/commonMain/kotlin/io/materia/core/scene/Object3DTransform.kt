package io.materia.core.scene

import io.materia.core.math.*

/**
 * Object3D transformation operations including rotation, translation, and matrix operations
 */

/**
 * Gets the world position of this object
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.extractWorldPosition(target: Vector3 = Vector3()): Vector3 {
    updateMatrixWorld()
    return target.setFromMatrixPosition(matrixWorld)
}

/**
 * Gets the world quaternion of this object
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.extractWorldQuaternion(target: Quaternion = Quaternion()): Quaternion {
    updateMatrixWorld()
    return MathObjectPools.withVector3 { tempPos ->
        MathObjectPools.withVector3 { tempScale ->
            matrixWorld.decompose(tempPos, target, tempScale)
            target
        }
    }
}

/**
 * Gets the world scale of this object
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.extractWorldScale(target: Vector3 = Vector3()): Vector3 {
    updateMatrixWorld()
    return MathObjectPools.withVector3 { tempPos ->
        MathObjectPools.withQuaternion { tempQuat ->
            matrixWorld.decompose(tempPos, tempQuat, target)
            target
        }
    }
}

/**
 * Gets the world direction vector for this object
 */
internal fun Object3D.extractWorldDirection(target: Vector3 = Vector3()): Vector3 {
    updateMatrixWorld()
    val te = matrixWorld.elements
    return target.set(-te[8], -te[9], -te[10]).normalize()
}

/**
 * Rotates this object to face a target position
 */
internal fun Object3D.setLookAt(target: Vector3) {
    setLookAt(target.x, target.y, target.z)
}

/**
 * Rotates this object to face a target position
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.setLookAt(x: Float, y: Float, z: Float) {
    MathObjectPools.withVector3 { target ->
        target.set(x, y, z)
        MathObjectPools.withVector3 { position ->
            getWorldPosition(position)
            MathObjectPools.withMatrix4 { m1 ->
                m1.lookAt(position, target, Vector3.UP)
                quaternion.setFromRotationMatrix(m1)

                parent?.let { p ->
                    m1.extractRotation(p.matrixWorld)
                    MathObjectPools.withQuaternion { q1 ->
                        q1.setFromRotationMatrix(m1)
                        quaternion.premultiply(q1.invert())
                    }
                }
            }
        }
    }
}

/**
 * Rotates around an axis by an angle
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.applyRotationOnAxis(axis: Vector3, angle: Float): Object3D {
    MathObjectPools.withQuaternion { q1 ->
        q1.setFromAxisAngle(axis, angle)
        quaternion.multiply(q1)
    }
    return this
}

/**
 * Rotates around world axis
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.applyRotationOnWorldAxis(axis: Vector3, angle: Float): Object3D {
    MathObjectPools.withQuaternion { q1 ->
        q1.setFromAxisAngle(axis, angle)
        quaternion.premultiply(q1)
    }
    return this
}

/**
 * Rotates around X axis
 */
internal fun Object3D.applyRotationX(angle: Float): Object3D {
    return rotateOnAxis(Vector3(1f, 0f, 0f), angle)
}

/**
 * Rotates around Y axis
 */
internal fun Object3D.applyRotationY(angle: Float): Object3D {
    return rotateOnAxis(Vector3(0f, 1f, 0f), angle)
}

/**
 * Rotates around Z axis
 */
internal fun Object3D.applyRotationZ(angle: Float): Object3D {
    return rotateOnAxis(Vector3(0f, 0f, 1f), angle)
}

/**
 * Translates along an axis
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.applyTranslationOnAxis(axis: Vector3, distance: Float): Object3D {
    MathObjectPools.withVector3 { v1 ->
        v1.copy(axis).applyQuaternion(quaternion)
        position.add(v1.multiplyScalar(distance))
    }
    return this
}

/**
 * Translates along X axis
 */
internal fun Object3D.applyTranslationX(distance: Float): Object3D {
    return translateOnAxis(Vector3(1f, 0f, 0f), distance)
}

/**
 * Translates along Y axis
 */
internal fun Object3D.applyTranslationY(distance: Float): Object3D {
    return translateOnAxis(Vector3(0f, 1f, 0f), distance)
}

/**
 * Translates along Z axis
 */
internal fun Object3D.applyTranslationZ(distance: Float): Object3D {
    return translateOnAxis(Vector3(0f, 0f, 1f), distance)
}

/**
 * Converts local coordinates to world coordinates
 */
internal fun Object3D.convertLocalToWorld(vector: Vector3): Vector3 {
    return vector.applyMatrix4(matrixWorld)
}

/**
 * Converts world coordinates to local coordinates
 * OPTIMIZED: Uses object pooling to avoid allocations
 */
internal fun Object3D.convertWorldToLocal(vector: Vector3): Vector3 {
    return MathObjectPools.withMatrix4 { inverseMatrix ->
        inverseMatrix.copy(matrixWorld).invert()
        vector.applyMatrix4(inverseMatrix)
    }
}

/**
 * Applies a matrix transformation to this object
 */
internal fun Object3D.applyMatrixTransform(matrix: Matrix4): Object3D {
    if (matrixAutoUpdate) updateMatrix()
    this.matrix.premultiply(matrix)
    this.matrix.decompose(position, quaternion, scale)
    return this
}

/**
 * Updates the local transformation matrix
 * OPTIMIZED: Marks world matrix as dirty
 */
internal fun Object3D.updateLocalMatrix() {
    matrix.compose(position, quaternion, scale)
    matrixWorldNeedsUpdate = true

    // Propagate dirty flag to children
    for (child in children) {
        child.matrixWorldNeedsUpdate = true
    }
}

/**
 * Updates the world matrix and all children
 */
internal fun Object3D.updateWorldMatrixWithOptions(
    updateParents: Boolean = false,
    updateChildren: Boolean = false
) {
    var parent = this.parent

    if (updateParents && parent != null) {
        parent.updateWorldMatrix(true, false)
    }

    if (matrixAutoUpdate) updateMatrix()

    this.parent?.let { p ->
        matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
    } ?: matrixWorld.copy(matrix)

    if (updateChildren) {
        for (child in children) {
            child.updateWorldMatrix(false, true)
        }
    }
}
