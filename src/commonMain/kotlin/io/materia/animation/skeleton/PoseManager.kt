package io.materia.animation.skeleton

/**
 * Pose management for saving, loading, and blending skeleton poses.
 */
class PoseManager(private val bones: List<Bone>) {
    private val bindPose = mutableMapOf<Bone, BonePose>()
    private val currentPose = mutableMapOf<Bone, BonePose>()
    private val savedPoses = mutableMapOf<String, Map<Bone, BonePose>>()

    fun saveBind() {
        bindPose.clear()
        bones.forEach { bone ->
            bindPose[bone] = BonePose(bone)
        }
    }

    fun savePose(name: String) {
        val pose = mutableMapOf<Bone, BonePose>()
        bones.forEach { bone ->
            pose[bone] = BonePose(bone)
        }
        savedPoses[name] = pose
    }

    fun loadPose(name: String): Boolean {
        val pose = savedPoses[name] ?: return false
        pose.forEach { (bone, bonePose) ->
            bonePose.applyTo(bone)
        }
        return true
    }

    fun blendPose(name: String, alpha: Float): Boolean {
        val targetPose = savedPoses[name] ?: return false

        bones.forEach { bone ->
            val currentBonePose = BonePose(bone)
            val targetBonePose = targetPose[bone] ?: return@forEach
            val blendedPose = currentBonePose.lerp(targetBonePose, alpha)
            blendedPose.applyTo(bone)
        }

        return true
    }

    fun resetToBind() {
        bindPose.forEach { (bone, pose) ->
            pose.applyTo(bone)
        }
    }

    fun retargetTo(
        targetSkeleton: io.materia.animation.Skeleton,
        boneMapping: Map<String, String>,
        hierarchyManager: BoneHierarchyManager
    ): Boolean {
        val retargetedPoses = mutableMapOf<Bone, BonePose>()

        boneMapping.forEach { (sourceName, targetName) ->
            val sourceBone = hierarchyManager.getBoneByName(sourceName)
            val targetBone = targetSkeleton.getBoneByName(targetName)

            if (sourceBone != null && targetBone != null) {
                retargetedPoses[targetBone] = BonePose(sourceBone)
            }
        }

        // Apply retargeted poses
        retargetedPoses.forEach { (bone, pose) ->
            pose.applyTo(bone)
        }

        return retargetedPoses.isNotEmpty()
    }

    fun clear() {
        bindPose.clear()
        currentPose.clear()
        savedPoses.clear()
    }
}
