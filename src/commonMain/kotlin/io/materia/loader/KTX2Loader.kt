package io.materia.loader

class KTX2Loader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // KTX2 loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

