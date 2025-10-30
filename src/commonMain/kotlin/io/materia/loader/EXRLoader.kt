package io.materia.loader

class EXRLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // EXR loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

