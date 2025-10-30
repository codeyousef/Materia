package io.materia.engine.scene

class Scene(
    name: String = "Scene"
) : Node(name) {
    var backgroundColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)

    fun update(deltaTime: Float) {
        traverse { node ->
            if (node !== this) {
                node.onUpdate(deltaTime)
            }
        }
        updateWorldMatrix(force = true)
    }
}
