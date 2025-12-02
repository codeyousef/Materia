package io.materia.loader

/**
 * Base interface for asset loaders.
 *
 * Implement this interface to create loaders for specific asset types
 * (models, textures, animations, etc.).
 *
 * @param T The type of asset produced by this loader.
 */
interface AssetLoader<T> {
    /**
     * Loads an asset from the given path.
     *
     * @param path URL or file path to the asset.
     * @return The loaded asset.
     * @throws Exception If loading fails.
     */
    suspend fun load(path: String): T
}
