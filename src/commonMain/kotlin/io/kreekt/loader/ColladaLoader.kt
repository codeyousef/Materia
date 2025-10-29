package io.kreekt.loader

class ColladaLoader : AssetLoader<ModelAsset> {
    override suspend fun load(path: String): ModelAsset {
        throw UnsupportedOperationException("COLLADA loading is not supported. Use glTF or OBJ pipelines instead.")
    }
}
