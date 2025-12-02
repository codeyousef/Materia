package io.materia.engine.scene

import io.materia.engine.math.Mat4
import io.materia.engine.math.Transform
import io.materia.engine.math.Vec3
import io.materia.engine.math.mat4
import io.materia.engine.math.vec3

/**
 * Base class for all objects in the scene graph hierarchy.
 *
 * Nodes form a tree structure where each node has a local [transform] and
 * computes a [getWorldMatrix] by concatenating ancestor transforms. Child nodes
 * are added via [add] and removed via [remove].
 *
 * Override [onUpdate] to implement per-frame logic for subclasses.
 *
 * @property name Optional identifier for debugging and scene queries.
 */
open class Node(
    var name: String = "Node"
) {
    /** Local transformation relative to the parent node. */
    val transform: Transform = Transform()
    private val worldMatrix: Mat4 = mat4().setIdentity()
    private val tmpMatrix: Mat4 = mat4()
    private var worldMatrixDirty: Boolean = true

    /**
     * The parent node in the scene hierarchy, or null for root nodes.
     * Set automatically when added to or removed from another node.
     */
    var parent: Node? = null
        private set

    private val _children: MutableList<Node> = mutableListOf()

    /** Read-only list of child nodes. */
    val children: List<Node> get() = _children

    init {
        transform.changeListener = { markWorldMatrixDirty() }
    }

    /**
     * Adds a child node to this node.
     *
     * If the child already has a parent, it is first removed from that parent.
     * The child's world matrix is marked dirty to reflect the new hierarchy.
     *
     * @param child The node to add as a child.
     */
    fun add(child: Node) {
        if (child.parent === this) return
        child.parent?.remove(child)
        child.parent = this
        _children += child
        child.markWorldMatrixDirty()
    }

    /**
     * Removes a child node from this node.
     *
     * @param child The node to remove.
     */
    fun remove(child: Node) {
        if (_children.remove(child)) {
            child.parent = null
        }
    }

    /**
     * Recursively updates world matrices for this node and all descendants.
     *
     * @param force If true, forces recalculation even if not marked dirty.
     */
    open fun updateWorldMatrix(force: Boolean = false) {
        val parentMatrix = parent?.getWorldMatrix()
        val localDirty = transform.isDirty()
        val needsUpdate = force || worldMatrixDirty || localDirty
        val localMatrix = transform.matrix()

        if (needsUpdate) {
            if (parentMatrix != null) {
                tmpMatrix.multiply(parentMatrix, localMatrix)
                worldMatrix.copyFrom(tmpMatrix)
            } else {
                worldMatrix.copyFrom(localMatrix)
            }
            worldMatrixDirty = false
        }

        val childForce = needsUpdate || parentMatrix != null
        children.forEach { it.updateWorldMatrix(childForce) }
    }

    /**
     * Returns the world transformation matrix for this node.
     *
     * Call [updateWorldMatrix] first to ensure the matrix is current.
     *
     * @return The 4x4 world transformation matrix.
     */
    fun getWorldMatrix(): Mat4 = worldMatrix

    /**
     * Extracts the world-space position from the world matrix.
     *
     * @param out Optional pre-allocated vector to write into.
     * @return The world-space position of this node.
     */
    fun positionWorld(out: Vec3 = vec3()): Vec3 {
        val m = worldMatrix.data
        return out.set(m[12], m[13], m[14])
    }

    /**
     * Called each frame during scene updates.
     *
     * Override to implement custom per-frame behavior.
     *
     * @param deltaTime Time elapsed since the last update in seconds.
     */
    open fun onUpdate(deltaTime: Float) {}

    /**
     * Recursively visits this node and all descendants.
     *
     * @param action The function to invoke for each node.
     */
    fun traverse(action: (Node) -> Unit) {
        action(this)
        children.forEach { it.traverse(action) }
    }

    private fun markWorldMatrixDirty() {
        if (!worldMatrixDirty) {
            worldMatrixDirty = true
            children.forEach { it.markWorldMatrixDirty() }
        }
    }
}
