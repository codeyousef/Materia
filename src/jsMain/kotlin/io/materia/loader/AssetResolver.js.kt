package io.materia.loader

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

internal class DefaultAssetResolver : AssetResolver {

    override suspend fun load(uri: String, basePath: String?): ByteArray {
        return when {
            uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
            else -> fetchBytes(resolveUri(uri, basePath))
        }
    }

    private fun resolveUri(uri: String, basePath: String?): String {
        if (uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)
        ) {
            return uri
        }

        val base = basePath ?: return uri
        val separator = if (base.endsWith("/")) "" else "/"
        return base + separator + uri
    }

    private suspend fun fetchBytes(url: String): ByteArray {
        val fetchFn = js("globalThis.fetch") as? (String) -> Promise<dynamic>
            ?: throw IllegalStateException("globalThis.fetch is not available – provide a custom AssetResolver")

        val response = fetchFn(url).await()
        if (!(response.ok as Boolean)) {
            throw IllegalArgumentException("Failed to fetch $url (status=${response.status})")
        }

        val buffer = (response.arrayBuffer() as Promise<ArrayBuffer>).await()
        val view = Uint8Array(buffer)
        return ByteArray(view.length) { index -> (view.asDynamic()[index] as Int).toByte() }
    }

    private fun decodeDataUri(uri: String): ByteArray {
        val commaIndex = uri.indexOf(',')
        require(commaIndex != -1) { "Invalid data URI: $uri" }
        val metadata = uri.substring(5, commaIndex)
        val data = uri.substring(commaIndex + 1)
        val isBase64 = metadata.endsWith(";base64", ignoreCase = true)
        return if (isBase64) {
            val decoded = js("globalThis.atob")(data) as String
            ByteArray(decoded.length) { i -> decoded[i].code.toByte() }
        } else {
            data.encodeToByteArray()
        }
    }
}

internal actual fun createDefaultAssetResolver(): AssetResolver = DefaultAssetResolver()
