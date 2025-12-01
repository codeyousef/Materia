package io.materia.loader

import io.materia.util.Base64Compat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

internal class DefaultAssetResolver : AssetResolver {

    override suspend fun load(uri: String, basePath: String?): ByteArray =
        withContext(Dispatchers.IO) {
            when {
                uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
                uri.startsWith("http://", ignoreCase = true) ||
                        uri.startsWith("https://", ignoreCase = true) -> readRemote(uri)

                else -> readLocal(uri, basePath)
            }
        }

    private fun decodeDataUri(uri: String): ByteArray {
        val commaIndex = uri.indexOf(',')
        require(commaIndex != -1) { "Invalid data URI: $uri" }
        val metadata = uri.substring(5, commaIndex)
        val dataPart = uri.substring(commaIndex + 1)
        val isBase64 = metadata.endsWith(";base64", ignoreCase = true)
        return if (isBase64) {
            decodeBase64(dataPart)
        } else {
            dataPart.toByteArray(Charsets.UTF_8)
        }
    }

    private fun readRemote(url: String): ByteArray {
        URL(url).openStream().use { stream ->
            return stream.readAllBytesCompat()
        }
    }

    private fun readLocal(uri: String, basePath: String?): ByteArray {
        val resolved = if (basePath != null && !Paths.get(uri).isAbsolute) {
            Paths.get(basePath, uri).normalize()
        } else {
            Paths.get(uri).normalize()
        }

        if (!Files.exists(resolved)) {
            throw IllegalArgumentException("Resource not found: $resolved")
        }
        return Files.readAllBytes(resolved)
    }

    private fun InputStream.readAllBytesCompat(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(data)
            if (read == -1) break
            buffer.write(data, 0, read)
        }
        return buffer.toByteArray()
    }
}

private fun decodeBase64(value: String): ByteArray =
    Base64Compat.decode(value)

internal actual fun createDefaultAssetResolver(): AssetResolver = DefaultAssetResolver()
