package io.kreekt.examples.embeddinggalaxy

import kotlinx.coroutines.delay

/**
 * Placeholder implementation for the Embedding Galaxy example.
 *
 * The MVP will replace this stub with a real scene built on top of the GPU + engine layers.
 * For now we expose a simple harness so the per-target launchers can compile and run.
 */
object EmbeddingGalaxyApp {
    suspend fun warmUp() {
        // Simulate a small amount of async work so the call sites already use suspend APIs.
        delay(50)
    }

    fun statusMessage(): String =
        "Embedding Galaxy MVP scene is under construction – wiring lands during GPU/engine milestones."
}
