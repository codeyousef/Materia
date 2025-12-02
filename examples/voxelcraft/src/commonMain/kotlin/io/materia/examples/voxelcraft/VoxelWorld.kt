package io.materia.examples.voxelcraft

import io.materia.core.scene.Scene
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.ContinuationInterceptor

/**
 * VoxelWorld - Main world entity managing chunks and player.
 *
 * Uses a coroutine-backed meshing and generation pipeline so heavy work yields regularly
 * and keeps the render loop responsive.
 */
class VoxelWorld(
    val seed: Long,
    parentScope: CoroutineScope
) {
    val chunks = mutableMapOf<ChunkPosition, Chunk>()
    val player = Player(this)
    private val generator = TerrainGenerator(seed)
    val scene = Scene()

    private val parentJob: Job? = parentScope.coroutineContext[Job]
    private val worldJob = SupervisorJob(parentJob)
    private val scope = CoroutineScope(parentScope.coroutineContext + worldJob)
    private val mainDispatcher: CoroutineDispatcher =
        (parentScope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)
            ?: Dispatchers.Main

    private val dirtyQueue = ArrayDeque<Chunk>()
    private val dirtySet = mutableSetOf<ChunkPosition>()
    private val pendingMeshes = mutableSetOf<ChunkPosition>()
    private val meshSemaphore = Semaphore(4)
    private val dirtyQueueMutex = Mutex()

    private val generationQueue = ArrayDeque<ChunkPosition>()
    private val activeGeneration = mutableSetOf<ChunkPosition>()
    private val generationLocks = mutableMapOf<ChunkPosition, Mutex>()

    private var lastStreamCenter: ChunkPosition = ChunkPosition(0, 0)
    private var streamingInitialized = false

    val chunkCount: Int get() = chunks.size

    var isGenerated = false
        private set

    var isGeneratingTerrain = false
        private set

    // T021: Track initial mesh generation progress for loading screen
    private var initialMeshTarget = 0  // Expected number of initial meshes (typically 81)
    private var meshesGeneratedCount = 0  // Actual meshes generated so far

    val initialMeshGenerationProgress: Float
        get() = if (initialMeshTarget > 0) {
            meshesGeneratedCount.toFloat() / initialMeshTarget
        } else 1.0f

    val isInitialMeshGenerationComplete: Boolean
        get() = initialMeshTarget > 0 && meshesGeneratedCount >= initialMeshTarget

    fun setInitialMeshTarget(count: Int) {
        initialMeshTarget = count
        meshesGeneratedCount = 0
    }

    // T021: Track mesh regeneration progress to fix missing faces
    private var isRegeneratingMeshes = false
    private var regenerationTargetCount = 0
    private var regenerationCompletedCount = 0

    val isRegenerationComplete: Boolean
        get() = !isRegeneratingMeshes || (regenerationCompletedCount >= regenerationTargetCount)

    val regenerationProgress: Float
        get() = if (regenerationTargetCount == 0) 1f
        else (regenerationCompletedCount.toFloat() / regenerationTargetCount.toFloat())

    suspend fun generateTerrain(onProgress: ((Int, Int) -> Unit)? = null) {
        isGeneratingTerrain = true
        val center = ChunkPosition(0, 0)
        val initialPositions = ChunkPosition.spiralAround(center, INITIAL_GENERATION_RADIUS)
        val total = initialPositions.size
        var processed = 0

        for (pos in initialPositions) {
            ensureChunkGenerated(pos)
            processed++
            onProgress?.invoke(processed, total)
            if (processed % 2 == 0) {
                yield()
            }
        }

        lastStreamCenter = center
        enqueueChunksAround(center, STREAM_RADIUS)
        streamingInitialized = true
        isGenerated = true
        isGeneratingTerrain = false

        logInfo("✅ Terrain generation complete! dirtyQueue=${dirtyQueue.size}, pendingMeshes=${pendingMeshes.size}")
    }

    internal fun onChunkDirty(chunk: Chunk) {
        if (!chunk.isDirty) return
        if (pendingMeshes.contains(chunk.position)) return
        scope.launch {
            dirtyQueueMutex.withLock {
                if (dirtySet.add(chunk.position)) {
                    dirtyQueue.addLast(chunk)
                }
            }
        }
    }

    fun getBlock(x: Int, y: Int, z: Int): BlockType? {
        if (x !in -256..255 || y !in 0..255 || z !in -256..255) return null

        val chunkPos = ChunkPosition.fromWorldCoordinates(x, z)
        val chunk = chunks[chunkPos] ?: return null

        val (localX, localZ) = chunkPos.toLocalCoordinates(x, z)
        return chunk.getBlock(localX, y, localZ)
    }

    fun setBlock(x: Int, y: Int, z: Int, type: BlockType): Boolean {
        if (x !in -256..255 || y !in 0..255 || z !in -256..255) return false

        val chunkPos = ChunkPosition.fromWorldCoordinates(x, z)
        val chunk = chunks[chunkPos] ?: return false

        val (localX, localZ) = chunkPos.toLocalCoordinates(x, z)
        chunk.setBlock(localX, y, localZ, type)
        return true
    }

    fun getChunk(position: ChunkPosition): Chunk? = chunks[position]

    fun update(deltaTime: Float) {
        if (streamingInitialized) {
            updateStreaming()
            pumpGenerationQueue(MAX_GENERATION_PER_FRAME)
        }

        pumpDirtyChunks(MAX_DIRTY_CHUNKS_PER_FRAME)

        // T016b: Only update player physics after initial terrain generation completes
        // This prevents player falling during async mesh generation
        if (isGenerated && !isGeneratingTerrain) {
            player.update(deltaTime)
        }
    }

    private fun updateStreaming() {
        val playerPos = player.position
        val center = ChunkPosition.fromWorldCoordinates(playerPos.x.toInt(), playerPos.z.toInt())
        if (center != lastStreamCenter) {
            lastStreamCenter = center
            enqueueChunksAround(center, STREAM_RADIUS)
        }
    }

    private fun enqueueChunksAround(center: ChunkPosition, radius: Int) {
        ChunkPosition.spiralAround(center, radius).forEach { position ->
            val chunk = chunks[position]
            if (chunk?.terrainGenerated == true) return@forEach
            if (activeGeneration.contains(position) || generationQueue.contains(position)) return@forEach
            generationQueue.addLast(position)
        }
    }

    private fun pumpGenerationQueue(maxPerFrame: Int) {
        var launched = 0
        while (launched < maxPerFrame && generationQueue.isNotEmpty()) {
            val position = generationQueue.removeFirst()
            val chunk = chunks.getOrPut(position) { Chunk(position, this) }
            if (chunk.terrainGenerated || activeGeneration.contains(position)) {
                continue
            }

            activeGeneration.add(position)
            scope.launch {
                try {
                    ensureChunkGenerated(position)
                } finally {
                    activeGeneration.remove(position)
                }
            }
            launched++
        }
    }

    private fun pumpDirtyChunks(maxPerFrame: Int) {
        // Allow mesh generation to proceed even during terrain generation so the user sees progress
        // Try to acquire lock, skip if busy
        if (!dirtyQueueMutex.tryLock()) {
            return
        }

        try {
            var processed = 0
            while (processed < maxPerFrame && dirtyQueue.isNotEmpty()) {
                val chunk = dirtyQueue.removeFirst()
                dirtySet.remove(chunk.position)

                if (!chunk.isDirty || pendingMeshes.contains(chunk.position)) {
                    continue
                }

                // Generate mesh synchronously to avoid race conditions with renderer
                // This ensures mesh updates happen on the same thread as rendering
                val geometry = kotlinx.coroutines.runBlocking { 
                    ChunkMeshGenerator.generate(chunk) 
                }
                val wasNew = chunk.mesh == null
                chunk.updateMesh(geometry)
                chunk.mesh?.let { mesh ->
                    if (wasNew || mesh.parent == null) {
                        scene.add(mesh)
                        meshesGeneratedCount++  // T021: Track mesh generation progress
                    }
                }

                // T021: Track regeneration progress to fix missing faces
                if (isRegeneratingMeshes) {
                    regenerationCompletedCount++
                    if (regenerationCompletedCount >= regenerationTargetCount) {
                        isRegeneratingMeshes = false
                        logInfo("✅ Mesh regeneration complete! $regenerationCompletedCount/$regenerationTargetCount chunks")
                    }
                }
                
                processed++
            }
        } finally {
            dirtyQueueMutex.unlock()
        }
    }

    suspend fun ensureChunkGenerated(position: ChunkPosition): Chunk {
        val chunk = chunks.getOrPut(position) { Chunk(position, this) }
        if (chunk.terrainGenerated) return chunk

        val mutex = generationLocks.getOrPut(position) { Mutex() }
        mutex.withLock {
            if (!chunk.terrainGenerated) {
                chunk.suppressDirtyEvents = true
                try {
                    generator.generate(chunk)
                } finally {
                    chunk.suppressDirtyEvents = false
                }
                chunk.terrainGenerated = true

                // Mark neighbor chunks dirty to regenerate meshes with correct face culling
                // This ensures faces at chunk boundaries are properly culled when both chunks exist
                val neighbors = listOf(
                    ChunkPosition(position.chunkX - 1, position.chunkZ),
                    ChunkPosition(position.chunkX + 1, position.chunkZ),
                    ChunkPosition(position.chunkX, position.chunkZ - 1),
                    ChunkPosition(position.chunkX, position.chunkZ + 1)
                )
                for (neighborPos in neighbors) {
                    chunks[neighborPos]?.let { neighborChunk ->
                        if (neighborChunk.terrainGenerated && neighborChunk.mesh != null) {
                            neighborChunk.markDirty()
                        }
                    }
                }

                if (chunk.isDirty) {
                    onChunkDirty(chunk)
                }
            }
        }
        if (chunk.terrainGenerated) {
            generationLocks.remove(position)
        }
        return chunk
    }

    /**
     * Regenerate all chunk meshes to ensure correct face culling at boundaries.
     * Call this after initial terrain generation completes.
     *
     * T021: Now tracks progress so caller can wait for completion.
     */
    fun regenerateAllMeshes() {
        logInfo("🔄 Regenerating all chunk meshes for correct face culling...")

        isRegeneratingMeshes = true
        regenerationTargetCount = 0
        regenerationCompletedCount = 0

        for (chunk in chunks.values) {
            if (chunk.terrainGenerated && chunk.mesh != null) {
                chunk.markDirty()
                regenerationTargetCount++
            }
        }

        logInfo("✅ Marked $regenerationTargetCount chunks dirty for regeneration")
    }

    fun dispose() {
        worldJob.cancel()
        chunks.values.forEach { chunk ->
            chunk.mesh?.let { scene.remove(it) }
            chunk.dispose()
        }
        chunks.clear()
        dirtyQueue.clear()
        dirtySet.clear()
        pendingMeshes.clear()
        generationQueue.clear()
        activeGeneration.clear()
        generationLocks.clear()
        scene.clear()
    }

    companion object {
        private const val INITIAL_GENERATION_RADIUS = 4
        private const val STREAM_RADIUS = 6
        private const val MAX_GENERATION_PER_FRAME = 8
        private const val MAX_DIRTY_CHUNKS_PER_FRAME =
            32  // Process many chunks per frame for fast initial load
    }
}


