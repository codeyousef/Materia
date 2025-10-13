package io.kreekt.renderer.webgpu

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise

internal suspend fun <T> Promise<T>.awaitPromise(): T = suspendCancellableCoroutine { cont ->
    then(
        onFulfilled = { value -> cont.resume(value) },
        onRejected = { error ->
            val throwable = error as? Throwable ?: Exception(error.toString())
            cont.resumeWithException(throwable)
        }
    )
}
