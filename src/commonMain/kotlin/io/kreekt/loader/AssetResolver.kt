package io.kreekt.loader

/**
 * Resolves asset references (buffers, images, etc.) for loaders. Implementations
 * are platform-specific to accommodate filesystem access on desktop and HTTP
 * fetches in browsers.
 */
interface AssetResolver {
    /**
    * Load raw bytes for the given [uri]. Implementations must support relative
    * paths (resolved against [basePath]), absolute file paths, HTTP(S) URLs and
    * data URIs.
    */
    suspend fun load(uri: String, basePath: String?): ByteArray

    companion object {
        /**
         * Create the default resolver for the current platform.
         */
        fun default(): AssetResolver = DefaultAssetResolver()
    }
}

internal expect class DefaultAssetResolver() : AssetResolver
