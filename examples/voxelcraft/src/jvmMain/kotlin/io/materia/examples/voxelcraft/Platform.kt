package io.materia.examples.voxelcraft

import io.materia.geometry.BufferGeometry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking

/**
 * JVM: No main dispatcher available for proper synchronization.
 * This signals VoxelWorld to use synchronous mesh updates.
 */
actual val platformMainDispatcher: CoroutineDispatcher? = null

/**
 * JVM with Vulkan requires synchronous mesh processing.
 * Mesh updates must happen on the same thread as rendering.
 */
actual val platformRequiresSyncMeshProcessing: Boolean = true

/**
 * JVM mesh generation - runs the generator directly.
 */
actual suspend fun generateMeshForChunk(chunk: Chunk): BufferGeometry {
    return ChunkMeshGenerator.generate(chunk)
}

/**
 * JVM implementation - uses runBlocking.
 */
actual fun <T> runBlockingPlatform(block: suspend () -> T): T {
    return runBlocking { block() }
}
