package io.materia.loader

class ColladaLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // Collada loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

