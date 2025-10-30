package io.materia.renderer.gpu

/**
 * Lightweight registry that tracks GPU resources allocated during a renderer
 * session and disposes them deterministically when the context is torn down.
 *
 * The registry is intentionally simple – it stores lambdas that are invoked
 * during [disposeAll]. Callers can register destruction callbacks directly or
 * use the typed helpers such as [trackTexture].
 */
class GpuResourceRegistry {
    private val disposers = mutableListOf<() -> Unit>()
    private var disposed = false

    /**
     * Registers a generic disposer.
     */
    fun register(onDispose: () -> Unit) {
        if (disposed) {
            onDispose()
            return
        }
        disposers += onDispose
    }

    /**
     * Tracks a [GpuTexture] so that its `destroy()` method is invoked once the
     * registry is disposed. Callers can combine this with per-frame caches to
     * ensure textures are released even when context loss occurs.
     */
    fun trackTexture(texture: GpuTexture) {
        register { runCatching { texture.destroy() } }
    }

    /**
     * Disposes all registered resources. The registry can be reused afterwards.
     */
    fun disposeAll() {
        if (disposed) return
        disposed = true
        disposers.asReversed().forEach { disposer ->
            runCatching { disposer() }
        }
        disposers.clear()
    }

    /**
     * Resets the registry for reuse after a full dispose.
     */
    fun reset() {
        disposed = false
        disposers.clear()
    }
}
