package io.materia.io

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

actual suspend fun readTextResource(urlOrPath: String): String {
    val path = urlOrPath.toPath()
    if (!FileSystem.SYSTEM.exists(path)) {
        throw IllegalStateException("Resource not found: $urlOrPath")
    }
    return FileSystem.SYSTEM.read(path) {
        readUtf8()
    }
}

actual fun <T> saveJson(
    path: String,
    serializer: SerializationStrategy<T>,
    value: T,
    json: Json
) {
    val target = path.toPath()
    target.parent?.let { parent ->
        FileSystem.SYSTEM.createDirectories(parent)
    }
    val payload = json.encodeToString(serializer, value)
    FileSystem.SYSTEM.write(target) {
        writeUtf8(payload)
    }
}