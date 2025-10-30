package io.materia.animation

import io.materia.core.scene.Object3D

/**
 * Core animation system interface for managing animation lifecycle
 * Provides animation mixer creation and system-wide animation management
 */
interface AnimationSystem {
    /**
     * Creates an animation mixer for the given root object
     * @param root The root object that will be animated
     * @return AnimationMixer instance for managing animations on this object
     */
    fun createAnimationMixer(root: Object3D): AnimationMixer

    /**
     * Creates a skeletal animation system for bone-based animations
     * @param skeleton The skeleton to animate
     * @return SkeletalAnimationSystem instance
     */
    fun createSkeletalAnimationSystem(skeleton: Skeleton): SkeletalAnimationSystem

    /**
     * Updates all active animations in the system
     * @param deltaTime Time elapsed since last update in seconds
     */
    fun update(deltaTime: Float)

    /**
     * Disposes of all animation resources
     */
    fun dispose()
}

/**
 * Default implementation of AnimationSystem
 */
class DefaultAnimationSystem : AnimationSystem {
    private val activeMixers = mutableListOf<AnimationMixer>()
    private val activeSkeletalSystems = mutableListOf<SkeletalAnimationSystem>()

    override fun createAnimationMixer(root: Object3D): AnimationMixer {
        val mixer = DefaultAnimationMixer(root)
        activeMixers.add(mixer)
        return mixer
    }

    override fun createSkeletalAnimationSystem(skeleton: Skeleton): SkeletalAnimationSystem {
        val skeletalSystem = DefaultSkeletalAnimationSystem(skeleton)
        activeSkeletalSystems.add(skeletalSystem)
        return skeletalSystem
    }

    override fun update(deltaTime: Float) {
        // Update all active mixers
        activeMixers.removeAll { mixer ->
            if (mixer.isDisposed) {
                true // Remove disposed mixers
            } else {
                mixer.update(deltaTime)
                false
            }
        }

        // Update all active skeletal systems
        activeSkeletalSystems.removeAll { system ->
            if (system.isDisposed) {
                true // Remove disposed systems
            } else {
                system.update(deltaTime)
                false
            }
        }
    }

    override fun dispose() {
        activeMixers.forEach { it.dispose() }
        activeMixers.clear()

        activeSkeletalSystems.forEach { it.dispose() }
        activeSkeletalSystems.clear()
    }
}