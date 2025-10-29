package io.kreekt.loader

class DRACOLoader : AssetLoader<ModelAsset> {
    override suspend fun load(path: String): ModelAsset {
        throw UnsupportedOperationException("DRACO mesh decoding requires native bindings that are not bundled yet.")
    }
}
