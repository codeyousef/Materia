package io.materia.io

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

actual suspend fun readTextResource(urlOrPath: String): String {
    val response = window.fetch(urlOrPath).await()
    if (!response.ok) {
        throw IllegalStateException("Failed to fetch $urlOrPath: ${response.status} ${response.statusText}")
    }
    return response.text().await()
}

actual fun <T> saveJson(
    path: String,
    serializer: SerializationStrategy<T>,
    value: T,
    json: Json
) {
    error("saveJson is only supported on JVM (path=$path)")
}
