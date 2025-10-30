package io.materia.io

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmName
import java.io.File

actual suspend fun readTextResource(urlOrPath: String): String {
    val onDisk = File(urlOrPath)
    if (onDisk.exists()) {
        return onDisk.readText()
    }

    throw IllegalStateException("Resource not found: $urlOrPath")
}

@JvmName("saveJsonWithSerializer")
actual fun <T> saveJson(
    path: String,
    serializer: SerializationStrategy<T>,
    value: T,
    json: Json
) {
    val target = File(path)
    target.parentFile?.mkdirs()
    val payload = json.encodeToString(serializer, value)
    target.writeText(payload)
}
