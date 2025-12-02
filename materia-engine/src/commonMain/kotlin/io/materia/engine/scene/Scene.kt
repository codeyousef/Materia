package io.materia.engine.scene

/**
 * Root container for a renderable scene graph.
 *
 * A Scene is a specialized [Node] that serves as the root of the hierarchy.
 * It maintains a [backgroundColor] for clearing the render target and provides
 * an [update] method that propagates delta time to all descendants.
 *
 * @param name Optional scene identifier.
 */
class Scene(
    name: String = "Scene"
) : Node(name) {
    /**
     * RGBA clear color for the scene background.
     * Defaults to opaque black.
     */
    var backgroundColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)

    /**
     * Updates all nodes in the scene for the current frame.
     *
     * Invokes [Node.onUpdate] on each descendant and refreshes world matrices.
     *
     * @param deltaTime Time elapsed since the last frame in seconds.
     */
    fun update(deltaTime: Float) {
        traverse { node ->
            if (node !== this) {
                node.onUpdate(deltaTime)
            }
        }
        updateWorldMatrix(force = true)
    }
}
