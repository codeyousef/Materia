/**
 * Disposable Interface for GPU Resource Management
 *
 * Provides a unified lifecycle management contract for GPU resources.
 * Critical for Kotlin/Native where wgpu-native uses manual resource management.
 *
 * Usage:
 * ```kotlin
 * class MyBuffer(device: GpuDevice) : Disposable {
 *     private val buffer = device.createBuffer(...)
 *
 *     override val isDisposed: Boolean get() = _disposed
 *     private var _disposed = false
 *
 *     override fun dispose() {
 *         if (!_disposed) {
 *             buffer.destroy()
 *             _disposed = true
 *         }
 *     }
 * }
 * ```
 */
package io.materia.engine.core

/**
 * Interface for objects that hold GPU resources requiring explicit cleanup.
 *
 * All GPU-allocated resources (buffers, textures, pipelines) must implement
 * this interface to ensure proper VRAM cleanup on Native targets.
 *
 * ## Memory Safety
 *
 * On Kotlin/JVM and JS, garbage collection handles cleanup eventually, but
 * explicit disposal is still recommended for deterministic resource management.
 *
 * On Kotlin/Native with wgpu-native, calling [dispose] is **mandatory** to
 * prevent VRAM leaks, as there is no automatic garbage collection for native
 * GPU resources.
 *
 * ## Thread Safety
 *
 * Implementations should ensure dispose() is thread-safe and idempotent.
 * Multiple calls to dispose() should have no effect after the first call.
 */
interface Disposable {
    /**
     * Whether this resource has been disposed.
     *
     * Once true, the resource should not be used and all GPU handles are invalid.
     */
    val isDisposed: Boolean

    /**
     * Releases all GPU resources held by this object.
     *
     * This method must be:
     * - **Idempotent**: Safe to call multiple times
     * - **Thread-safe**: Can be called from any thread
     * - **Immediate**: Resources should be released synchronously
     *
     * After calling dispose(), [isDisposed] will return true and any
     * attempt to use this resource should throw [IllegalStateException].
     */
    fun dispose()
}

/**
 * Scope guard for automatic resource disposal.
 *
 * Use with Kotlin's [use] pattern for automatic cleanup:
 * ```kotlin
 * buffer.use {
 *     it.writeFloats(data)
 *     renderer.draw(it)
 * } // Automatically disposed here
 * ```
 */
inline fun <T : Disposable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        dispose()
    }
}

/**
 * Extension to safely check and throw if resource is disposed.
 */
fun Disposable.checkNotDisposed(resourceName: String = "Resource") {
    if (isDisposed) {
        throw IllegalStateException("$resourceName has been disposed and cannot be used")
    }
}

/**
 * Container for managing multiple disposable resources.
 *
 * Useful for scene objects that own multiple GPU resources:
 * ```kotlin
 * class Mesh : Disposable {
 *     private val resources = DisposableContainer()
 *
 *     init {
 *         resources += vertexBuffer
 *         resources += indexBuffer
 *         resources += material
 *     }
 *
 *     override fun dispose() = resources.dispose()
 *     override val isDisposed get() = resources.isDisposed
 * }
 * ```
 */
class DisposableContainer : Disposable {
    private val resources = mutableListOf<Disposable>()
    private var _disposed = false

    override val isDisposed: Boolean get() = _disposed

    /**
     * Adds a resource to be managed by this container.
     */
    operator fun plusAssign(resource: Disposable) {
        if (_disposed) {
            throw IllegalStateException("Cannot add resources to disposed container")
        }
        resources.add(resource)
    }

    /**
     * Adds a resource and returns it for chaining.
     */
    fun <T : Disposable> add(resource: T): T {
        this += resource
        return resource
    }

    /**
     * Disposes all managed resources in reverse order of addition.
     */
    override fun dispose() {
        if (_disposed) return
        _disposed = true

        // Dispose in reverse order (LIFO) for proper dependency cleanup
        for (i in resources.indices.reversed()) {
            try {
                resources[i].dispose()
            } catch (e: Exception) {
                // Log but don't throw - ensure all resources get a chance to dispose
                println("Warning: Failed to dispose resource at index $i: ${e.message}")
            }
        }
        resources.clear()
    }

    /**
     * Number of resources in this container.
     */
    val size: Int get() = resources.size
}
