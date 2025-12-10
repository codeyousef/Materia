package io.materia.engine.render

import io.materia.engine.core.Disposable

/**
 * Manages a chain of post-processing passes for rendering effects.
 *
 * EffectComposer provides:
 * - Pass chain management (add, remove, reorder)
 * - Size propagation to all passes
 * - Enable/disable filtering
 * - Resource lifecycle management
 *
 * For GPU rendering integration, use [EffectComposerRenderer] which handles
 * the actual WebGPU pipeline execution and ping-pong buffers.
 *
 * Usage:
 * ```kotlin
 * val composer = EffectComposer(width = 1920, height = 1080)
 *
 * composer.addPass(FullScreenEffectPass.create {
 *     fragmentShader = vignetteShader
 * })
 *
 * composer.addPass(FullScreenEffectPass.create {
 *     fragmentShader = colorGradingShader
 * })
 *
 * // Later: render with EffectComposerRenderer
 * renderer.render(composer)
 * ```
 *
 * @property width Current width in pixels
 * @property height Current height in pixels
 */
class EffectComposer(
    width: Int = 0,
    height: Int = 0
) : Disposable {

    private val _passes = mutableListOf<FullScreenEffectPass>()

    /**
     * Read-only list of all passes in the chain.
     */
    val passes: List<FullScreenEffectPass>
        get() = _passes.toList()

    /**
     * Number of passes in the chain.
     */
    val passCount: Int
        get() = _passes.size

    /**
     * Current width in pixels.
     */
    var width: Int = width
        private set

    /**
     * Current height in pixels.
     */
    var height: Int = height
        private set

    /**
     * Whether this composer has been disposed.
     */
    override var isDisposed: Boolean = false
        private set

    /**
     * Adds a pass to the end of the chain.
     *
     * @param pass The pass to add
     * @throws IllegalStateException if the composer has been disposed
     */
    fun addPass(pass: FullScreenEffectPass) {
        checkNotDisposed()
        _passes.add(pass)
        pass.setSize(width, height)
    }

    /**
     * Inserts a pass at the specified index.
     *
     * @param pass The pass to insert
     * @param index The index at which to insert (0-based)
     * @throws IllegalStateException if the composer has been disposed
     * @throws IndexOutOfBoundsException if index is out of range
     */
    fun insertPass(pass: FullScreenEffectPass, index: Int) {
        checkNotDisposed()
        _passes.add(index, pass)
        pass.setSize(width, height)
    }

    /**
     * Removes a pass from the chain.
     *
     * @param pass The pass to remove
     * @return true if the pass was found and removed
     */
    fun removePass(pass: FullScreenEffectPass): Boolean {
        return _passes.remove(pass)
    }

    /**
     * Removes the pass at the specified index.
     *
     * @param index The index of the pass to remove
     * @return The removed pass
     * @throws IndexOutOfBoundsException if index is out of range
     */
    fun removePassAt(index: Int): FullScreenEffectPass {
        return _passes.removeAt(index)
    }

    /**
     * Removes all passes from the chain.
     */
    fun clearPasses() {
        _passes.clear()
    }

    /**
     * Updates the size and propagates to all passes.
     *
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
        for (pass in _passes) {
            pass.setSize(width, height)
        }
    }

    /**
     * Swaps the positions of two passes.
     *
     * @param index1 Index of the first pass
     * @param index2 Index of the second pass
     * @throws IndexOutOfBoundsException if either index is out of range
     */
    fun swapPasses(index1: Int, index2: Int) {
        val temp = _passes[index1]
        _passes[index1] = _passes[index2]
        _passes[index2] = temp
    }

    /**
     * Moves a pass from one index to another.
     *
     * @param fromIndex Current index of the pass
     * @param toIndex Destination index for the pass
     * @throws IndexOutOfBoundsException if either index is out of range
     */
    fun movePass(fromIndex: Int, toIndex: Int) {
        val pass = _passes.removeAt(fromIndex)
        _passes.add(toIndex, pass)
    }

    /**
     * Returns only the enabled passes.
     *
     * @return List of passes where [FullScreenEffectPass.enabled] is true
     */
    fun getEnabledPasses(): List<FullScreenEffectPass> {
        return _passes.filter { it.enabled }
    }

    /**
     * Disposes all passes and releases resources.
     *
     * After calling dispose, the composer cannot be used.
     * This method is idempotent - multiple calls have no effect.
     */
    override fun dispose() {
        if (isDisposed) return
        isDisposed = true

        for (pass in _passes) {
            pass.dispose()
        }
        _passes.clear()
    }

    /**
     * Checks that the composer has not been disposed.
     * @throws IllegalStateException if disposed
     */
    private fun checkNotDisposed() {
        check(!isDisposed) { "EffectComposer has been disposed" }
    }
}
