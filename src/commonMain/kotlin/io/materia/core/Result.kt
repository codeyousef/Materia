/**
 * Common Result type for Materia operations
 * Provides a unified way to handle success and failure across all modules
 */
package io.materia.core

/**
 * Generic result type for operations that can succeed or fail
 */
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error(val message: String, val exception: Throwable? = null) : Result<Nothing>()

    /**
     * Returns the value if success, null if error
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Error -> null
    }

    /**
     * Returns the value if success, throws exception if error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Error -> throw exception ?: RuntimeException(message)
    }

    /**
     * Returns the value if success, default value if error
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Error -> defaultValue
    }

    /**
     * Maps the success value
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(value))
        is Error -> this
    }

    /**
     * Flat maps the success value
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(value)
        is Error -> this
    }

    /**
     * Returns true if success
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns true if error
     */
    val isError: Boolean get() = this is Error
}

/**
 * Convenience functions
 */
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(value)
    }
    return this
}

fun <T> Result<T>.onError(action: (String) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(message)
    }
    return this
}