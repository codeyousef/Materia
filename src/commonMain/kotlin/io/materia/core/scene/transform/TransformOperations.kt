package io.materia.core.scene.transform

import io.materia.core.math.*
import io.materia.core.scene.Transform
import io.materia.core.scene.extractRotation

/**
 * Transform operation utilities for position, rotation, and scale manipulation.
 * Provides convenience methods for common transformation operations.
 */

/**
 * Sets position values
 */
fun Transform.setPosition(x: Float, y: Float, z: Float): Transform {
    position.set(x, y, z)
    markMatrixDirty()
    onPositionChanged?.invoke()
    return this
}

/**
 * Sets position from Vector3
 */
fun Transform.setPosition(pos: Vector3): Transform {
    position.copy(pos)
    markMatrixDirty()
    onPositionChanged?.invoke()
    return this
}

/**
 * Sets rotation values in radians
 */
fun Transform.setRotation(
    x: Float,
    y: Float,
    z: Float,
    order: EulerOrder = EulerOrder.XYZ
): Transform {
    rotation.set(x, y, z, order)
    onRotationChanged?.invoke()
    return this
}

/**
 * Sets rotation from Euler
 */
fun Transform.setRotation(rot: Euler): Transform {
    rotation.copy(rot)
    onRotationChanged?.invoke()
    return this
}

/**
 * Sets rotation from quaternion
 */
fun Transform.setRotation(quat: Quaternion): Transform {
    quaternion.copy(quat)
    rotation.setFromQuaternion(quaternion)
    markMatrixDirty()
    return this
}

/**
 * Sets scale values
 */
fun Transform.setScale(x: Float, y: Float, z: Float): Transform {
    scale.set(x, y, z)
    markMatrixDirty()
    onScaleChanged?.invoke()
    return this
}

/**
 * Sets uniform scale
 */
fun Transform.setScale(uniformScale: Float): Transform {
    return setScale(uniformScale, uniformScale, uniformScale)
}

/**
 * Sets scale from Vector3
 */
fun Transform.setScale(scl: Vector3): Transform {
    scale.copy(scl)
    markMatrixDirty()
    onScaleChanged?.invoke()
    return this
}

/**
 * Translates by offset
 */
fun Transform.translate(x: Float, y: Float, z: Float): Transform {
    position.add(Vector3(x, y, z))
    markMatrixDirty()
    onPositionChanged?.invoke()
    return this
}

/**
 * Translates by vector
 */
fun Transform.translate(offset: Vector3): Transform {
    position.add(offset)
    markMatrixDirty()
    onPositionChanged?.invoke()
    return this
}

/**
 * Rotates around axis by angle
 */
fun Transform.rotate(axis: Vector3, angle: Float): Transform {
    val q = Quaternion().setFromAxisAngle(axis, angle)
    quaternion.multiply(q)
    rotation.setFromQuaternion(quaternion)
    markMatrixDirty()
    return this
}

/**
 * Rotates around X axis
 */
fun Transform.rotateX(angle: Float): Transform {
    return rotate(Vector3(1f, 0f, 0f), angle)
}

/**
 * Rotates around Y axis
 */
fun Transform.rotateY(angle: Float): Transform {
    return rotate(Vector3(0f, 1f, 0f), angle)
}

/**
 * Rotates around Z axis
 */
fun Transform.rotateZ(angle: Float): Transform {
    return rotate(Vector3(0f, 0f, 1f), angle)
}

/**
 * Scales by factor
 */
fun Transform.scaleBy(factor: Float): Transform {
    scale.multiplyScalar(factor)
    markMatrixDirty()
    onScaleChanged?.invoke()
    return this
}

/**
 * Scales by vector
 */
fun Transform.scaleBy(factor: Vector3): Transform {
    scale.multiply(factor)
    markMatrixDirty()
    onScaleChanged?.invoke()
    return this
}

/**
 * Interpolates to target transform
 */
fun Transform.lerp(target: Transform, alpha: Float): Transform {
    position.lerp(target.position, alpha)
    quaternion.slerp(target.quaternion, alpha)
    scale.lerp(target.scale, alpha)
    rotation.setFromQuaternion(quaternion)
    markMatrixDirty()
    return this
}

/**
 * Looks at target position
 */
fun Transform.lookAt(target: Vector3, up: Vector3 = Vector3(0f, 1f, 0f)): Transform {
    val worldPosition = getWorldPosition()
    val matrix = Matrix4().lookAt(worldPosition, target, up)

    parent?.let { p ->
        val parentWorldMatrix = p.updateWorldMatrix()
        val parentRotation = Matrix4().extractRotation(parentWorldMatrix)
        val parentQuaternion = Quaternion().setFromRotationMatrix(parentRotation)
        matrix.premultiply(Matrix4().makeRotationFromQuaternion(parentQuaternion.invert()))
    }

    quaternion.setFromRotationMatrix(matrix)
    rotation.setFromQuaternion(quaternion)
    markMatrixDirty()
    return this
}

/**
 * Resets transform to identity
 */
fun Transform.reset(): Transform {
    position.set(0f, 0f, 0f)
    rotation.set(0f, 0f, 0f)
    scale.set(1f, 1f, 1f)
    quaternion.identity()
    markMatrixDirty()
    return this
}

// Helper to mark matrices dirty
private fun Transform.markMatrixDirty() {
    needsMatrixUpdate = true
    needsWorldMatrixUpdate = true
}
