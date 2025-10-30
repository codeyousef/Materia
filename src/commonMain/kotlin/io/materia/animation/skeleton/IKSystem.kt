package io.materia.animation.skeleton

import io.materia.core.math.Vector3

/**
 * IK (Inverse Kinematics) system for skeleton animation.
 */

/**
 * IK Chain definition
 */
data class IKChain(
    val name: String,
    val bones: List<Bone>,
    val target: Vector3,
    val poleVector: Vector3? = null,
    val solver: IKSolverType = IKSolverType.FABRIK,
    var weight: Float = 1.0f,
    var iterations: Int = 10,
    var tolerance: Float = 0.001f,
    var isEnabled: Boolean = true
) {
    val effector: Bone get() = bones.last()
    val root: Bone get() = bones.first()

    fun getChainLength(): Float {
        var length = 0f
        for (i in 0 until bones.size - 1) {
            val bone1 = bones[i]
            val bone2 = bones[i + 1]
            length = length + bone1.getWorldPosition().distanceTo(bone2.getWorldPosition())
        }
        return length
    }
}

/**
 * IK Solver types
 */
enum class IKSolverType {
    FABRIK,
    TWO_BONE,
    CCD,
    JACOBIAN
}

/**
 * IK Chain manager
 */
class IKChainManager {
    private val ikChains = mutableListOf<IKChain>()

    fun addIKChain(chain: IKChain) {
        ikChains.add(chain)
    }

    fun removeIKChain(name: String) {
        ikChains.removeAll { it.name == name }
    }

    fun getIKChain(name: String): IKChain? = ikChains.find { it.name == name }

    fun getIKChains(): List<IKChain> = ikChains.toList()

    fun clear() {
        ikChains.clear()
    }
}
