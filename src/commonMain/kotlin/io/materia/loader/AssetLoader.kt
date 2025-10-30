package io.materia.loader

/**
 * Base interface for all asset loaders
 */
interface AssetLoader<T> {
    suspend fun load(path: String): T
}
