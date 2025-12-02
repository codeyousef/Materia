package io.materia.examples.voxelcraft

import io.materia.geometry.BufferGeometry
import kotlinx.coroutines.CoroutineDispatcher

/**
 * JVM: No main dispatcher available for proper synchronization.
 * This signals VoxelWorld to use synchronous mesh updates.
 */
actual val platformMainDispatcher: CoroutineDispatcher? = null

/**
 * JVM mesh generation - runs the generator directly.
 */
actual suspend fun generateMeshForChunk(chunk: Chunk): BufferGeometry {
    return ChunkMeshGenerator.generate(chunk)
}
