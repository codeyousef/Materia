package io.materia.examples.voxelcraft

import io.materia.geometry.BufferGeometry
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-specific main dispatcher.
 * 
 * On JS, returns Dispatchers.Main which works with the browser event loop.
 * On JVM, returns null to indicate synchronous processing is needed.
 */
expect val platformMainDispatcher: CoroutineDispatcher?

/**
 * Platform-specific mesh generation.
 */
expect suspend fun generateMeshForChunk(chunk: Chunk): BufferGeometry
