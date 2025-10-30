package io.materia.examples.voxelcraft

import io.materia.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

/**
 * WorldState serialization model for localStorage persistence
 *
 * Provides serialization/deserialization of the complete game state:
 * - World seed (for regenerating unmodified chunks)
 * - Player state (position, rotation, flight)
 * - Modified chunks only (optimization)
 *
 * Contract: storage-api.yaml WorldState schema
 * Data model: data-model.md Section 7
 */
@Serializable
data class WorldState(
    val seed: Long,
    val playerPosition: SerializableVector3,
    val playerRotation: SerializableRotation,
    val isFlying: Boolean,
    val chunks: List<SerializedChunk>
) {
    companion object {
        /**
         * Create WorldState from VoxelWorld
         *
         * Only serializes non-empty chunks to reduce storage size.
         *
         * @param world VoxelWorld to serialize
         * @return WorldState with compressed chunk data
         */
        fun from(world: VoxelWorld): WorldState {
            // Only save chunks modified by player (delta saves)
            // Unmodified chunks can be regenerated from seed
            val serializedChunks = world.chunks.values
                .filter { it.isModifiedByPlayer }
                .map { SerializedChunk.from(it) }

            return WorldState(
                seed = world.seed,
                playerPosition = SerializableVector3(world.player.position),
                playerRotation = SerializableRotation(world.player.rotation),
                isFlying = world.player.isFlying,
                chunks = serializedChunks
            )
        }
    }

    /**
     * Restore VoxelWorld from saved state
     *
     * Regenerates world from seed, then applies saved chunk modifications.
     * NOTE: This is synchronous - terrain must be generated async before calling this
     *
     * @return Restored VoxelWorld instance (terrain NOT generated - call generateTerrain())
     */
    fun restore(scope: CoroutineScope): VoxelWorld {
        val world = VoxelWorld(seed, scope)

        // Restore player state
        world.player.position =
            Vector3(
                playerPosition.x.toFloat(),
                playerPosition.y.toFloat(),
                playerPosition.z.toFloat()
            )
        world.player.rotation =
            Vector3(playerRotation.pitch.toFloat(), playerRotation.yaw.toFloat(), 0.0f)
        world.player.isFlying = isFlying

        // NOTE: Player-modified chunks will be applied after terrain generation
        // Store them temporarily for later application

        return world
    }

    /**
     * Apply saved chunk modifications after terrain generation
     * This is called after generateTerrain() completes
     *
     * @param world VoxelWorld to apply modifications to
     */
    suspend fun applyModifications(world: VoxelWorld) {
        chunks.forEach { serializedChunk ->
            val chunkPos = ChunkPosition(serializedChunk.chunkX, serializedChunk.chunkZ)
            val chunk = world.ensureChunkGenerated(chunkPos)
            chunk.deserialize(serializedChunk.compressedBlocks)
            chunk.isModifiedByPlayer = true  // Mark as player-modified
        }
    }
}

/**
 * Serializable Vector3 for position data
 */
@Serializable
data class SerializableVector3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    constructor(vec: Vector3) : this(vec.x.toDouble(), vec.y.toDouble(), vec.z.toDouble())
}

/**
 * Serializable Rotation for camera orientation
 */
@Serializable
data class SerializableRotation(
    val pitch: Double,
    val yaw: Double
) {
    constructor(vec: Vector3) : this(vec.x.toDouble(), vec.y.toDouble())
}

/**
 * Serialized chunk with RLE-compressed block data
 */
@Serializable
data class SerializedChunk(
    val chunkX: Int,
    val chunkZ: Int,
    val compressedBlocks: ByteArray // RLE-encoded block data
) {
    companion object {
        /**
         * Create SerializedChunk from Chunk
         *
         * @param chunk Chunk to serialize
         * @return SerializedChunk with compressed data
         */
        fun from(chunk: Chunk): SerializedChunk {
            return SerializedChunk(
                chunkX = chunk.position.chunkX,
                chunkZ = chunk.position.chunkZ,
                compressedBlocks = chunk.serialize()
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SerializedChunk

        if (chunkX != other.chunkX) return false
        if (chunkZ != other.chunkZ) return false
        if (!compressedBlocks.contentEquals(other.compressedBlocks)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkX
        result = 31 * result + chunkZ
        result = 31 * result + compressedBlocks.contentHashCode()
        return result
    }
}
