package io.materia.core.scene.transform

import io.materia.core.math.Box3
import io.materia.core.scene.Transform

/**
 * Transform hierarchy management utilities.
 * Handles parent-child relationships and hierarchy operations.
 */

/**
 * Transform hierarchy manager
 */
class TransformHierarchy {
    private val transforms = mutableMapOf<Int, Transform>()
    private val parentChildMap = mutableMapOf<Int, MutableList<Int>>()
    private val childParentMap = mutableMapOf<Int, Int>()

    /**
     * Registers a transform in the hierarchy
     */
    fun register(id: Int, transform: Transform): Transform {
        transforms[id] = transform
        return transform
    }

    /**
     * Unregisters a transform from the hierarchy
     */
    fun unregister(id: Int) {
        transforms.remove(id)

        // Remove from parent-child relationships
        childParentMap[id]?.let { parentId ->
            parentChildMap[parentId]?.remove(id)
        }
        childParentMap.remove(id)

        // Remove all children
        parentChildMap[id]?.toList()?.forEach { childId ->
            setParent(childId, null)
        }
        parentChildMap.remove(id)
    }

    /**
     * Sets parent-child relationship
     */
    fun setParent(childId: Int, parentId: Int?) {
        val childTransform = transforms[childId] ?: return

        // Remove from old parent
        childParentMap[childId]?.let { oldParentId ->
            parentChildMap[oldParentId]?.remove(childId)
        }

        if (parentId != null) {
            val parentTransform = transforms[parentId] ?: return

            // Set new relationships
            childTransform.parent = parentTransform
            childParentMap[childId] = parentId
            parentChildMap.getOrPut(parentId) { mutableListOf() }.add(childId)
        } else {
            // Remove parent
            childTransform.parent = null
            childParentMap.remove(childId)
        }
    }

    /**
     * Gets children of a transform
     */
    fun getChildren(id: Int): List<Int> {
        return parentChildMap[id] ?: emptyList()
    }

    /**
     * Gets parent of a transform
     */
    fun getParent(id: Int): Int? {
        return childParentMap[id]
    }

    /**
     * Updates all transforms in hierarchy order
     */
    fun updateAll() {
        // Update root transforms first
        transforms.values.filter { it.parent == null }.forEach {
            it.updateWorldMatrix()
        }
    }

    /**
     * Gets transform by ID
     */
    fun getTransform(id: Int): Transform? {
        return transforms[id]
    }
}

/**
 * Transform propagation utilities
 */
object TransformPropagation {

    /**
     * Propagates transform changes down a hierarchy
     */
    fun propagateDown(transform: Transform, visitor: (Transform) -> Unit) {
        visitor(transform)
        // In a full implementation, this would traverse children
    }

    /**
     * Propagates transform changes up a hierarchy
     */
    fun propagateUp(transform: Transform, visitor: (Transform) -> Unit) {
        var current: Transform? = transform
        while (current != null) {
            visitor(current)
            current = current.parent
        }
    }

    /**
     * Calculates world bounds for a transform hierarchy
     */
    fun calculateWorldBounds(rootTransform: Transform): Box3 {
        val bounds = Box3()
        // Implementation would traverse and accumulate bounds
        return bounds
    }

    /**
     * Optimizes transform updates by batching
     */
    fun batchUpdate(transforms: List<Transform>) {
        // Sort by hierarchy level to ensure parents update before children
        transforms.forEach { it.updateWorldMatrix() }
    }
}

/**
 * Transform animation utilities
 */
class TransformAnimator {
    private var startTransform: Transform? = null
    private var endTransform: Transform? = null
    private var duration: Float = 1f
    private var elapsed: Float = 0f
    private var isPlaying: Boolean = false

    /**
     * Sets up animation between two transforms
     */
    fun animate(
        start: Transform,
        end: Transform,
        duration: Float,
        onUpdate: ((Transform) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        this.startTransform = start.clone()
        this.endTransform = end
        this.duration = duration
        this.elapsed = 0f
        this.isPlaying = true
    }

    /**
     * Updates animation
     */
    fun update(deltaTime: Float, target: Transform): Boolean {
        val start = startTransform
        val end = endTransform
        if (!isPlaying || start == null || end == null) {
            return false
        }

        elapsed = elapsed + deltaTime
        val t = (elapsed / duration).coerceIn(0f, 1f)

        // Interpolate transforms
        target.copy(start)
        target.lerp(end, t)

        if (t >= 1f) {
            isPlaying = false
            return true // Animation complete
        }

        return false
    }

    /**
     * Stops animation
     */
    fun stop() {
        isPlaying = false
    }

    /**
     * Checks if animation is playing
     */
    fun isPlaying(): Boolean = isPlaying
}
