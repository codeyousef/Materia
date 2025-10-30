package io.materia.loader

class DRACOLoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // DRACO loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

