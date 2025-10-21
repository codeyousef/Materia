package io.kreekt.engine.scene

class Scene(
    name: String = "Scene"
) : Node(name) {
    var backgroundColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
}
