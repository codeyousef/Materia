package io.materia.animation.skeleton

import io.materia.core.math.Vector3

/**
 * Bone hierarchy management and validation.
 */
class BoneHierarchyManager(private val bones: List<Bone>) {
    private val bonesByName = mutableMapOf<String, Bone>()
    private val parentChildMap = mutableMapOf<Bone, MutableList<Bone>>()
    private val childParentMap = mutableMapOf<Bone, Bone>()

    fun buildHierarchy() {
        bonesByName.clear()
        parentChildMap.clear()
        childParentMap.clear()

        // Build name lookup
        bones.forEach { bone ->
            bonesByName[bone.name] = bone
        }

        // Build parent-child relationships (safe null handling)
        bones.forEach { bone ->
            bone.parent?.let { parent ->
                parentChildMap.getOrPut(parent) { mutableListOf() }.add(bone)
                childParentMap[bone] = parent
            }
        }
    }

    fun getBoneByName(name: String): Bone? = bonesByName[name]

    fun getBoneChildren(bone: Bone): List<Bone> = parentChildMap[bone] ?: emptyList()

    fun getBoneParent(bone: Bone): Bone? = childParentMap[bone]

    fun getRootBones(): List<Bone> = bones.filter { it.parent == null }

    fun getBonePath(bone: Bone): List<Bone> {
        val path = mutableListOf<Bone>()
        var current: Bone? = bone

        while (current != null) {
            path.add(0, current)
            current = current.parent
        }

        return path
    }

    fun calculateBoneLengths(): Map<Bone, Float> {
        val boneLengths = mutableMapOf<Bone, Float>()

        bones.forEach { bone ->
            val children = getBoneChildren(bone)
            if (children.isNotEmpty()) {
                val bonePos = bone.getWorldPosition()
                val avgChildPos = Vector3()
                children.forEach { child ->
                    avgChildPos.add(child.getWorldPosition())
                }
                avgChildPos.divideScalar(children.size.toFloat())
                boneLengths[bone] = bonePos.distanceTo(avgChildPos)
            } else {
                // Leaf bone - use parent distance or default
                val parent = getBoneParent(bone)
                if (parent != null) {
                    boneLengths[bone] =
                        bone.getWorldPosition().distanceTo(parent.getWorldPosition())
                } else {
                    boneLengths[bone] = 1.0f // Default length
                }
            }
        }

        return boneLengths
    }

    fun validateHierarchy(): List<String> {
        val errors = mutableListOf<String>()

        // Check for circular dependencies
        bones.forEach { bone ->
            val visited = mutableSetOf<Bone>()
            var current: Bone? = bone

            while (current != null) {
                if (current in visited) {
                    errors.add("Circular dependency detected involving bone: ${bone.name}")
                    break
                }
                visited.add(current)  // Move here - add AFTER check
                current = current.parent
            }
        }

        // Check for orphaned bones
        val connectedBones = mutableSetOf<Bone>()
        getRootBones().forEach { root ->
            addBoneAndChildren(root, connectedBones)
        }

        val orphanedBones = bones.filter { it !in connectedBones }
        orphanedBones.forEach { bone ->
            errors.add("Orphaned bone detected: ${bone.name}")
        }

        return errors
    }

    private fun addBoneAndChildren(bone: Bone, set: MutableSet<Bone>) {
        set.add(bone)
        getBoneChildren(bone).forEach { child ->
            addBoneAndChildren(child, set)
        }
    }
}
