package io.kreekt.engine.scene

import io.kreekt.engine.math.MatrixOps
import io.kreekt.engine.math.Transform
import io.kreekt.engine.math.Vector3f

open class Node(
    var name: String = "Node"
) {
    val transform: Transform = Transform()
    private val worldMatrix: FloatArray = MatrixOps.identity()
    private val tmpMatrix: FloatArray = FloatArray(16)

    var parent: Node? = null
        private set

    private val _children: MutableList<Node> = mutableListOf()
    val children: List<Node> get() = _children

    fun add(child: Node) {
        if (child.parent === this) return
        child.parent?.remove(child)
        child.parent = this
        _children += child
    }

    fun remove(child: Node) {
        if (_children.remove(child)) {
            child.parent = null
        }
    }

    open fun updateWorldMatrix(force: Boolean = false) {
        val parentMatrix = parent?.getWorldMatrix()
        val localMatrix = transform.matrix()
        if (parentMatrix != null) {
            MatrixOps.multiply(tmpMatrix, parentMatrix, localMatrix)
            tmpMatrix.copyInto(worldMatrix)
        } else {
            localMatrix.copyInto(worldMatrix)
        }
        val childForce = force || parentMatrix != null
        children.forEach { it.updateWorldMatrix(childForce) }
    }

    fun getWorldMatrix(): FloatArray = worldMatrix

    fun positionWorld(out: Vector3f = Vector3f()): Vector3f {
        val m = worldMatrix
        return out.set(m[12], m[13], m[14])
    }

    open fun onUpdate(deltaTime: Float) {}

    fun traverse(action: (Node) -> Unit) {
        action(this)
        children.forEach { it.traverse(action) }
    }
}
