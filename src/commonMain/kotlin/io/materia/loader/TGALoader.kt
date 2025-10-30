package io.materia.loader

class TGALoader : AssetLoader<Any> {

    override suspend fun load(path: String): Any {
        // TGA loading implementation
        return loadAsset(path)
    }

    private suspend fun loadAsset(path: String): Any {
        // Platform-specific loading
        return Any()
    }
}

