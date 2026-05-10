package io.materia.loader

import io.materia.util.Base64Compat
import okio.FileSystem
import okio.Path.Companion.toPath

internal class DefaultAssetResolver : AssetResolver {
    override val cacheKeyScope: String = "io.materia.loader.default"

    override suspend fun load(uri: String, basePath: String?): ByteArray {
        return when {
            uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
            uri.startsWith("http://", ignoreCase = true) ||
                uri.startsWith("https://", ignoreCase = true) -> {
                throw IllegalArgumentException(
                    "Remote asset loading is not implemented on Apple native targets: $uri"
                )
            }

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
            Base64Compat.decode(dataPart)
        } else {
            dataPart.encodeToByteArray()
        }
    }

    private fun readLocal(uri: String, basePath: String?): ByteArray {
        val resolvedPath = resolveAssetUri(uri, basePath).toPath()
        if (!FileSystem.SYSTEM.exists(resolvedPath)) {
            throw IllegalArgumentException("Resource not found: $resolvedPath")
        }
        return FileSystem.SYSTEM.read(resolvedPath) {
            readByteArray()
        }
    }
}

internal actual fun createDefaultAssetResolver(): AssetResolver = DefaultAssetResolver()

internal actual object PlatformImageDecoder {
    actual suspend fun decode(bytes: ByteArray): DecodedImage {
        throw UnsupportedOperationException(
            "Image decoding is not implemented on Apple native targets yet."
        )
    }
}
