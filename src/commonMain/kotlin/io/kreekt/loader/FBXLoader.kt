package io.kreekt.loader

/**
 * Placeholder for future FBX support. For now we surface a clear error instead
 * of returning an unusable stub value.
 */
class FBXLoader : AssetLoader<ModelAsset> {
    override suspend fun load(path: String): ModelAsset {
        throw UnsupportedOperationException("FBX loading is not supported. Convert assets to glTF for KreeKt pipelines.")
    }
}
