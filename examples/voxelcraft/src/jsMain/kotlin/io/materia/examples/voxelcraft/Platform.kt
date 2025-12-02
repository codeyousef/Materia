package io.materia.examples.voxelcraft

import io.materia.geometry.BufferGeometry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * JS: Use Dispatchers.Main which works with the browser event loop.
 */
actual val platformMainDispatcher: CoroutineDispatcher? = Dispatchers.Main

/**
 * JS with WebGPU can use async mesh processing.
 * The browser event loop provides synchronization.
 */
actual val platformRequiresSyncMeshProcessing: Boolean = false

/**
 * JS mesh generation - runs asynchronously via the event loop.
 */
actual suspend fun generateMeshForChunk(chunk: Chunk): BufferGeometry {
    return ChunkMeshGenerator.generate(chunk)
}
