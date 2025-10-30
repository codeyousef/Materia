package io.materia.animation.skeleton

import io.materia.core.math.Euler
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3

// Note: compose() is now a member function of Matrix4, no need to import extension

/**
 * Skeleton type definitions including bones, constraints, and poses.
 */

/**
 * Enhanced Bone class with constraints and limits
 */
data class Bone(
    val name: String,
    var position: Vector3 = Vector3(),
    var rotation: Quaternion = Quaternion(),
    var scale: Vector3 = Vector3(1f, 1f, 1f),
    val constraints: BoneConstraints = BoneConstraints(),
    var userData: Map<String, Any> = emptyMap(),
    val bindTransform: Matrix4 = Matrix4(),
    val inverseBindMatrix: Matrix4 = Matrix4(),
    val parentIndex: Int = -1  // -1 indicates root bone
) {
    // Local and world matrices
    val matrix = Matrix4()
    val matrixWorld = Matrix4()

    // Parent-child relationships
    var parent: Bone? = null
    val children = mutableListOf<Bone>()

    fun updateMatrix() {
        matrix.compose(position, rotation, scale)
    }

    fun updateMatrixWorld(force: Boolean = false) {
        updateMatrix()

        parent?.let { p ->
            matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
        } ?: matrixWorld.copy(matrix)

        if (force) {
            children.forEach { it.updateMatrixWorld(true) }
        }
    }

    fun add(child: Bone) {
        child.parent?.remove(child)
        child.parent = this
        children.add(child)
    }

    fun remove(child: Bone) {
        val index = children.indexOf(child)
        if (index != -1) {
            child.parent = null
            children.removeAt(index)
        }
    }

    fun getWorldPosition(target: Vector3 = Vector3()): Vector3 {
        updateMatrixWorld()
        return target.setFromMatrixPosition(matrixWorld)
    }

    fun getWorldQuaternion(target: Quaternion = Quaternion()): Quaternion {
        updateMatrixWorld()
        matrixWorld.decompose(Vector3(), target, Vector3())
        return target
    }
}

/**
 * Bone constraints for limiting rotation and translation
 */
data class BoneConstraints(
    // Rotation limits (in radians)
    val minRotationX: Float = -Float.MAX_VALUE,
    val maxRotationX: Float = Float.MAX_VALUE,
    val minRotationY: Float = -Float.MAX_VALUE,
    val maxRotationY: Float = Float.MAX_VALUE,
    val minRotationZ: Float = -Float.MAX_VALUE,
    val maxRotationZ: Float = Float.MAX_VALUE,

    // Translation limits
    val minTranslationX: Float = -Float.MAX_VALUE,
    val maxTranslationX: Float = Float.MAX_VALUE,
    val minTranslationY: Float = -Float.MAX_VALUE,
    val maxTranslationY: Float = Float.MAX_VALUE,
    val minTranslationZ: Float = -Float.MAX_VALUE,
    val maxTranslationZ: Float = Float.MAX_VALUE,

    // Constraint flags
    val lockRotationX: Boolean = false,
    val lockRotationY: Boolean = false,
    val lockRotationZ: Boolean = false,
    val lockTranslationX: Boolean = false,
    val lockTranslationY: Boolean = false,
    val lockTranslationZ: Boolean = false,

    // IK specific constraints
    val ikEnabled: Boolean = true,
    val twistAxis: Vector3? = null,
    val preferredAngle: Float = 0f
) {
    fun applyRotationConstraints(rotation: Quaternion): Quaternion {
        val euler = rotation.toEuler()

        // Apply constraints
        val constrainedX = if (lockRotationX) 0f else
            euler.x.coerceIn(minRotationX, maxRotationX)
        val constrainedY = if (lockRotationY) 0f else
            euler.y.coerceIn(minRotationY, maxRotationY)
        val constrainedZ = if (lockRotationZ) 0f else
            euler.z.coerceIn(minRotationZ, maxRotationZ)

        return Quaternion().setFromEuler(constrainedX, constrainedY, constrainedZ)
    }

    fun applyTranslationConstraints(position: Vector3): Vector3 {
        return Vector3(
            if (lockTranslationX) 0f else position.x.coerceIn(minTranslationX, maxTranslationX),
            if (lockTranslationY) 0f else position.y.coerceIn(minTranslationY, maxTranslationY),
            if (lockTranslationZ) 0f else position.z.coerceIn(minTranslationZ, maxTranslationZ)
        )
    }
}

/**
 * Bone pose for saving/loading/blending
 */
data class BonePose(
    val position: Vector3,
    val rotation: Quaternion,
    val scale: Vector3
) {
    constructor(bone: Bone) : this(
        bone.position.clone(),
        bone.rotation.clone(),
        bone.scale.clone()
    )

    fun applyTo(bone: Bone) {
        bone.position.copy(position)
        bone.rotation.copy(rotation)
        bone.scale.copy(scale)
    }

    fun clone(): BonePose = BonePose(position.clone(), rotation.clone(), scale.clone())

    fun lerp(other: BonePose, alpha: Float): BonePose {
        return BonePose(
            position.clone().lerp(other.position, alpha),
            rotation.clone().slerp(other.rotation, alpha),
            scale.clone().lerp(other.scale, alpha)
        )
    }
}

// Extension functions for math operations
fun Quaternion.toEuler(): Vector3 {
    val euler = Euler().setFromQuaternion(this)
    return Vector3(euler.x, euler.y, euler.z)
}

fun Quaternion.setFromEuler(x: Float, y: Float, z: Float): Quaternion {
    val euler = Euler(x, y, z)
    return this.setFromEuler(euler)
}

fun Vector3.setFromMatrixPosition(matrix: Matrix4): Vector3 {
    val e = matrix.elements
    this.x = e[12]
    this.y = e[13]
    this.z = e[14]
    return this
}

fun Matrix4.copy(other: Matrix4): Matrix4 {
    other.elements.copyInto(this.elements)
    return this
}
