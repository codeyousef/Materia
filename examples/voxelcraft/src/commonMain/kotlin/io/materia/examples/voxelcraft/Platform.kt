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
 * Whether mesh processing should be done synchronously on the calling thread.
 * 
 * On JVM with Vulkan, mesh updates must happen on the same thread as rendering
 * to avoid race conditions with buffer access. Returns true.
 * 
 * On JS with WebGPU, the browser event loop provides synchronization,
 * so async processing is safe. Returns false.
 */
expect val platformRequiresSyncMeshProcessing: Boolean

/**
 * Platform-specific mesh generation.
 */
expect suspend fun generateMeshForChunk(chunk: Chunk): BufferGeometry
