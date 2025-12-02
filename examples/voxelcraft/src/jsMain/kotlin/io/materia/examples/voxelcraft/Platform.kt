package io.materia.examples.voxelcraft

import io.materia.geometry.BufferGeometry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * JS: Use Dispatchers.Main which works with the browser event loop.
 */
actual val platformMainDispatcher: CoroutineDispatcher? = Dispatchers.Main

/**
 * JS mesh generation - runs asynchronously via the event loop.
 */
actual suspend fun generateMeshForChunk(chunk: Chunk): BufferGeometry {
    return ChunkMeshGenerator.generate(chunk)
}
