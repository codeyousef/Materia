/**
 * Result types for animation operations
 */
package io.materia.animation

/**
 * Animation operation result
 */
sealed class AnimationResult<out T> {
    data class Success<T>(val value: T) : AnimationResult<T>()
    data class Error(val message: String) : AnimationResult<Nothing>()
}

/**
 * Result type for general operations
 */
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
