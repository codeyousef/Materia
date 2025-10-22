package io.kreekt.examples.triangle

import java.nio.charset.StandardCharsets

actual suspend fun loadShaderResource(path: String): String {
    val resourcePath = if (path.startsWith("/")) path else "/$path"
    val stream = object {}.javaClass.getResourceAsStream(resourcePath)
        ?: error("Shader resource '$path' not found")
    stream.use { input ->
        return input.readBytes().toString(StandardCharsets.UTF_8)
    }
}
