package io.kreekt.examples.forcegraph

/**
 * Lightweight placeholder used while the full force-graph scene is under construction.
 *
 * The aim is to keep the example module compiling so launchers and wiring can be exercised
 * without dragging the partially ported engine surface into the build yet.
 */
class ForceGraphRerankScene(
    private val config: Config = Config()
) {
    enum class Mode { TfIdf, Semantic }

    data class Config(
        val nodeCount: Int = 2000,
        val seed: Long = 42L
    )

    var mode: Mode = Mode.TfIdf
        private set

    private var isTransitioning = false
    private var transitionElapsed = 0f

    fun toggleMode() {
        mode = if (mode == Mode.TfIdf) Mode.Semantic else Mode.TfIdf
        isTransitioning = true
        transitionElapsed = 0f
    }

    fun update(deltaTimeSeconds: Float) {
        if (!isTransitioning) return
        transitionElapsed += deltaTimeSeconds
        if (transitionElapsed >= TRANSITION_DURATION) {
            isTransitioning = false
            transitionElapsed = 0f
        }
    }

    fun status(): String = buildString {
        append("Force Graph placeholder\n")
        append("Nodes: ${config.nodeCount}\n")
        append("Mode: $mode\n")
        append(
            if (isTransitioning) "Transitioning…" else "Standing by for real renderer integration."
        )
    }

    companion object {
        private const val TRANSITION_DURATION = 0.5f
    }
}
