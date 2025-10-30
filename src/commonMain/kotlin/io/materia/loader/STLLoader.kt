package io.materia.loader

class STLLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // STL loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

