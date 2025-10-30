package io.materia.engine.scene

import io.materia.engine.math.Mat4
import io.materia.engine.math.Transform
import io.materia.engine.math.Vec3
import io.materia.engine.math.mat4
import io.materia.engine.math.vec3

open class Node(
    var name: String = "Node"
) {
    val transform: Transform = Transform()
    private val worldMatrix: Mat4 = mat4().setIdentity()
    private val tmpMatrix: Mat4 = mat4()
    private var worldMatrixDirty: Boolean = true

    var parent: Node? = null
        private set

    private val _children: MutableList<Node> = mutableListOf()
    val children: List<Node> get() = _children

    init {
        transform.changeListener = { markWorldMatrixDirty() }
    }

    fun add(child: Node) {
        if (child.parent === this) return
        child.parent?.remove(child)
        child.parent = this
        _children += child
        child.markWorldMatrixDirty()
    }

    fun remove(child: Node) {
        if (_children.remove(child)) {
            child.parent = null
        }
    }

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

    fun getWorldMatrix(): Mat4 = worldMatrix

    fun positionWorld(out: Vec3 = vec3()): Vec3 {
        val m = worldMatrix.data
        return out.set(m[12], m[13], m[14])
    }

    open fun onUpdate(deltaTime: Float) {}

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
