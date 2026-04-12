package io.materia.loader

import io.materia.animation.AnimationClip
import io.materia.core.scene.Material
import io.materia.core.scene.Scene

/**
 * Resolves asset URIs into raw byte content. Platform implementations handle
 * local files, remote URLs, and embedded data URIs.
 */
interface AssetResolver {
    /**
     * Load an asset located at [uri], optionally resolving against [basePath].
     */
    suspend fun load(uri: String, basePath: String? = null): ByteArray

    companion object {
        fun default(): AssetResolver = createDefaultAssetResolver()
    }
}

internal expect fun createDefaultAssetResolver(): AssetResolver

private val absoluteAssetUriPattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

internal fun resolveAssetUri(uri: String, basePath: String? = null): String {
    if (uri.isEmpty() || isAbsoluteAssetUri(uri)) {
        return uri
    }

    val base = basePath?.trimEnd('/') ?: return uri
    if (base.isEmpty()) {
        return uri
    }

    if (uri == base || uri.startsWith("$base/")) {
        return uri
    }

    return "$base/$uri"
}

internal fun isAbsoluteAssetUri(uri: String): Boolean {
    return uri.startsWith("/") || uri.startsWith("//") || absoluteAssetUriPattern.containsMatchIn(uri)
}

/**
 * Decoded bitmap data returned by [PlatformImageDecoder].
 */
data class DecodedImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray
)

/**
 * Platform-specific image decoding helper.
 */
internal expect object PlatformImageDecoder {
    suspend fun decode(bytes: ByteArray): DecodedImage
}

/**
 * Aggregated result from legacy model loaders (OBJ, FBX, STL, etc.).
 */
data class ModelAsset(
    val scene: Scene,
    val materials: List<Material> = emptyList(),
    val animations: List<AnimationClip> = emptyList()
)
