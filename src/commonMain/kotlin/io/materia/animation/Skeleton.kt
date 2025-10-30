package io.materia.animation

import io.materia.animation.skeleton.*
import io.materia.core.math.Matrix4

/**
 * Enhanced Skeleton class with IK chain support and bone constraints.
 * Provides comprehensive bone hierarchy management for advanced character animation.
 *
 * T036 - Enhanced Skeleton with IK chain support
 */
class Skeleton(
    val bones: List<Bone>,
    val boneInverses: List<Matrix4>? = null
) {

    // Core managers
    private val hierarchyManager = BoneHierarchyManager(bones)
    private val poseManager = PoseManager(bones)
    private val ikManager = IKChainManager()

    // Update flags
    private var hierarchyNeedsUpdate = true
    private var matricesNeedUpdate = true

    init {
        buildHierarchy()
        computeBoneInverses()
        poseManager.saveBind()
    }

    /**
     * Build bone hierarchy and name lookup
     */
    private fun buildHierarchy() {
        hierarchyManager.buildHierarchy()
        hierarchyNeedsUpdate = false
    }

    /**
     * Compute inverse bind matrices
     */
    private fun computeBoneInverses() {
        bones.forEach { bone ->
            bone.updateMatrixWorld()
        }
        matricesNeedUpdate = false
    }

    /**
     * Update skeleton matrices
     */
    fun update() {
        if (hierarchyNeedsUpdate) {
            buildHierarchy()
        }

        if (matricesNeedUpdate) {
            bones.forEach { bone ->
                bone.updateMatrixWorld()
            }
            matricesNeedUpdate = false
        }
    }

    /**
     * Get bone by name
     */
    fun getBoneByName(name: String): Bone? = hierarchyManager.getBoneByName(name)

    /**
     * Add IK chain
     */
    fun addIKChain(chain: IKChain) {
        ikManager.addIKChain(chain)
    }

    /**
     * Remove IK chain
     */
    fun removeIKChain(name: String) {
        ikManager.removeIKChain(name)
    }

    /**
     * Get IK chain by name
     */
    fun getIKChain(name: String): IKChain? = ikManager.getIKChain(name)

    /**
     * Get all IK chains
     */
    fun getIKChains(): List<IKChain> = ikManager.getIKChains()

    /**
     * Save current pose with name
     */
    fun savePose(name: String) {
        poseManager.savePose(name)
    }

    /**
     * Load saved pose
     */
    fun loadPose(name: String): Boolean {
        val result = poseManager.loadPose(name)
        if (result) matricesNeedUpdate = true
        return result
    }

    /**
     * Blend between current pose and saved pose
     */
    fun blendPose(name: String, alpha: Float): Boolean {
        val result = poseManager.blendPose(name, alpha)
        if (result) matricesNeedUpdate = true
        return result
    }

    /**
     * Reset to bind pose
     */
    fun resetToBind() {
        poseManager.resetToBind()
        matricesNeedUpdate = true
    }

    /**
     * Get bone children
     */
    fun getBoneChildren(bone: Bone): List<Bone> = hierarchyManager.getBoneChildren(bone)

    /**
     * Get bone parent
     */
    fun getBoneParent(bone: Bone): Bone? = hierarchyManager.getBoneParent(bone)

    /**
     * Get root bones (bones with no parent)
     */
    fun getRootBones(): List<Bone> = hierarchyManager.getRootBones()

    /**
     * Get bone path from root to bone
     */
    fun getBonePath(bone: Bone): List<Bone> = hierarchyManager.getBonePath(bone)

    /**
     * Retarget skeleton to another skeleton structure
     */
    fun retargetTo(targetSkeleton: Skeleton, boneMapping: Map<String, String>): Boolean {
        val result = poseManager.retargetTo(targetSkeleton, boneMapping, hierarchyManager)
        if (result) targetSkeleton.matricesNeedUpdate = true
        return result
    }

    /**
     * Calculate bone lengths for the skeleton
     */
    fun calculateBoneLengths(): Map<Bone, Float> = hierarchyManager.calculateBoneLengths()

    /**
     * Validate skeleton hierarchy
     */
    fun validateHierarchy(): List<String> = hierarchyManager.validateHierarchy()

    fun dispose() {
        ikManager.clear()
        poseManager.clear()
    }
}
