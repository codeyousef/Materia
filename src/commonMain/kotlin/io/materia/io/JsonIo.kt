package io.materia.io

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName

/**
 * Shared JSON helpers for examples and tooling. Wraps the platform-specific
 * IO (fetch vs file) in a single entry point so the high-level code can stay common.
 */
object JsonIo {
    val defaultJson: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
}

suspend fun <T> loadJson(
    urlOrPath: String,
    deserializer: DeserializationStrategy<T>,
    json: Json = JsonIo.defaultJson
): T {
    val payload = readTextResource(urlOrPath)
    return json.decodeFromString(deserializer, payload)
}

suspend inline fun <reified T> loadJson(
    urlOrPath: String,
    json: Json = JsonIo.defaultJson
): T {
    val payload = readTextResource(urlOrPath)
    return json.decodeFromString(serializer(), payload)
}

suspend fun readJson(urlOrPath: String): String = readTextResource(urlOrPath)

expect suspend fun readTextResource(urlOrPath: String): String

@JvmName("saveJsonWithSerializer")
expect fun <T> saveJson(
    path: String,
    serializer: SerializationStrategy<T>,
    value: T,
    json: Json = JsonIo.defaultJson
)

inline fun <reified T> saveJson(
    path: String,
    value: T,
    json: Json = JsonIo.defaultJson
) {
    saveJson(path, serializer(), value, json)
}
