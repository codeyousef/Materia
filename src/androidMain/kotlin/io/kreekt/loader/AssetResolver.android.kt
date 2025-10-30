package io.kreekt.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.Base64
import kotlin.io.DEFAULT_BUFFER_SIZE

internal actual class DefaultAssetResolver : AssetResolver {

    override suspend fun load(uri: String, basePath: String?): ByteArray = withContext(Dispatchers.IO) {
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
            Base64.getDecoder().decode(dataPart)
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
        val file = if (basePath != null) {
            File(basePath, uri).canonicalFile
        } else {
            File(uri).canonicalFile
        }

        if (!file.exists()) {
            throw IllegalArgumentException("Resource not found: ${file.path}")
        }
        return file.inputStream().use { it.readAllBytesCompat() }
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
