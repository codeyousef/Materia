package io.materia.loader

class PLYLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // PLY loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

