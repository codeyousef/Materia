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
