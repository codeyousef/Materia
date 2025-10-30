package io.materia.loader

class OBJLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // OBJ loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

