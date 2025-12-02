/**
 * Common Result type for Materia operations.
 *
 * Provides a unified way to handle success and failure across all modules,
 * avoiding exceptions for expected error cases.
 */
package io.materia.core

/**
 * A discriminated union representing either success with a value or an error.
 *
 * Use this type for operations that can fail in expected ways (file not found,
 * validation errors, etc.) to enable explicit error handling without exceptions.
 *
 * @param T The type of the success value.
 */
sealed class Result<out T> {
    /**
     * Represents a successful result containing a value.
     *
     * @param value The successful result value.
     */
    data class Success<T>(val value: T) : Result<T>()

    /**
     * Represents a failed result with an error message.
     *
     * @param message Human-readable description of the error.
     * @param exception Optional underlying exception if one occurred.
     */
    data class Error(val message: String, val exception: Throwable? = null) : Result<Nothing>()

    /**
     * Extracts the value if successful, or returns null on error.
     *
     * @return The success value, or null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Error -> null
    }

    /**
     * Extracts the value if successful, or throws on error.
     *
     * @return The success value.
     * @throws RuntimeException If this is an error result.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Error -> throw exception ?: RuntimeException(message)
    }

    /**
     * Extracts the value if successful, or returns the default on error.
     *
     * @param defaultValue Value to return if this is an error.
     * @return The success value or the default.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Error -> defaultValue
    }

    /**
     * Transforms the success value using the given function.
     *
     * Error results are passed through unchanged.
     *
     * @param transform Function to apply to the success value.
     * @return A new Result containing the transformed value, or the original error.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(value))
        is Error -> this
    }

    /**
     * Chains Result-returning operations.
     *
     * If this is a success, applies the transform and returns its result.
     * If this is an error, returns the error unchanged.
     *
     * @param transform Function returning a new Result.
     * @return The Result from the transform, or the original error.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(value)
        is Error -> this
    }

    /** Returns true if this is a [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** Returns true if this is an [Error]. */
    val isError: Boolean get() = this is Error
}

/**
 * Executes the given action if this is a success.
 *
 * @param action Function to call with the success value.
 * @return This Result unchanged, for chaining.
 */
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(value)
    }
    return this
}

/**
 * Executes the given action if this is an error.
 *
 * @param action Function to call with the error message.
 * @return This Result unchanged, for chaining.
 */
fun <T> Result<T>.onError(action: (String) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(message)
    }
    return this
}