package io.materia.loader

class FBXLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // FBX loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

