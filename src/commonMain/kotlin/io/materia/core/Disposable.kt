package io.materia.core

/**
 * Interface for objects that hold resources which need to be released.
 *
 * Implementations should ensure [dispose] is idempotent - calling it
 * multiple times should have no additional effect after the first call.
 */
interface Disposable {
    /**
     * Whether this resource has been disposed.
     */
    val isDisposed: Boolean

    /**
     * Releases all resources held by this object.
     *
     * After calling dispose, [isDisposed] returns true and the object
     * should not be used further.
     */
    fun dispose()
}
