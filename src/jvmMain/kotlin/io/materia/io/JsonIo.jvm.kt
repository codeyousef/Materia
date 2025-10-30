package io.materia.io

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.io.File

actual suspend fun readTextResource(urlOrPath: String): String {
    val file = File(urlOrPath)
    if (file.exists()) {
        return file.readText()
    }

    // Fall back to classpath resource for bundled assets.
    val resource = Thread.currentThread().contextClassLoader?.getResource(urlOrPath)
        ?: throw IllegalStateException("Resource not found: $urlOrPath")
    return resource.readText()
}

private fun java.net.URL.readText(): String = openStream().bufferedReader().use { it.readText() }

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
