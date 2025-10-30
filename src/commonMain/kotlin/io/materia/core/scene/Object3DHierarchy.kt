package io.materia.core.scene

import io.materia.core.math.*

/**
 * Object3D hierarchy management including add, remove, attach, and traversal operations
 */

/**
 * Adds children to this object
 */
internal fun Object3D.addChildren(vararg objects: Object3D): Object3D {
    for (obj in objects) {
        if (obj === this) {
            throw IllegalArgumentException("Object cannot be added as a child of itself")
        }

        if (obj.parent !== null) {
            obj.parent?.remove(obj)
        }

        obj.parent = this
        _children.add(obj)
        obj.dispatchEvent(Event.Added(obj))
    }
    return this
}

/**
 * Removes children from this object
 */
internal fun Object3D.removeChildren(vararg objects: Object3D): Object3D {
    for (obj in objects) {
        val index = _children.indexOf(obj)
        if (index >= 0) {
            _children.removeAt(index)
            obj.parent = null
            obj.dispatchEvent(Event.Removed(obj))
        }
    }
    return this
}

/**
 * Removes this object from its parent
 */
internal fun Object3D.detachFromParent(): Object3D {
    parent?.remove(this)
    return this
}

/**
 * Removes all children from this object
 */
internal fun Object3D.clearChildren(): Object3D {
    val childrenCopy = _children.toList()
    _children.clear()
    for (child in childrenCopy) {
        child.parent = null
        child.dispatchEvent(Event.Removed(child))
    }
    return this
}

/**
 * Attaches this object to another object
 */
internal fun Object3D.attachChild(object3d: Object3D): Object3D {
    // Calculate world position and apply inverse transform
    updateMatrixWorld()
    object3d.updateMatrixWorld()

    val targetMatrix = Matrix4().copy(matrixWorld).invert()
    object3d.matrix.premultiply(targetMatrix)
    object3d.matrix.decompose(object3d.position, object3d.quaternion, object3d.scale)

    add(object3d)
    return this
}

/**
 * Gets a child by name
 */
internal fun Object3D.findObjectByName(name: String): Object3D? {
    if (this.name == name) return this

    for (child in children) {
        val result = child.getObjectByName(name)
        if (result != null) return result
    }

    return null
}

/**
 * Gets a child by ID
 */
internal fun Object3D.findObjectById(id: Int): Object3D? {
    if (this.id == id) return this

    for (child in children) {
        val result = child.getObjectById(id)
        if (result != null) return result
    }

    return null
}

/**
 * Gets a child by property
 */
internal fun Object3D.findObjectByProperty(name: String, value: Any): Object3D? {
    when (name) {
        "id" -> if (id == value) return this
        "name" -> if (this.name == value) return this
        "visible" -> if (visible == value) return this
        "castShadow" -> if (castShadow == value) return this
        "receiveShadow" -> if (receiveShadow == value) return this
    }

    for (child in children) {
        val result = child.getObjectByProperty(name, value)
        if (result != null) return result
    }

    return null
}

/**
 * Traverses the object and all its children
 */
internal fun Object3D.traverseAll(callback: (Object3D) -> Unit) {
    callback(this)
    for (child in children) {
        child.traverse(callback)
    }
}

/**
 * Traverses only visible objects
 */
internal fun Object3D.traverseOnlyVisible(callback: (Object3D) -> Unit) {
    if (!visible) return
    callback(this)
    for (child in children) {
        child.traverseVisible(callback)
    }
}

/**
 * Traverses ancestors (parents)
 */
internal fun Object3D.traverseParents(callback: (Object3D) -> Unit) {
    parent?.let { parent ->
        callback(parent)
        parent.traverseAncestors(callback)
    }
}
