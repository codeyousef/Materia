package io.materia.examples.voxelcraft

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * ChunkPosition data class representing a chunk's location in the world grid
 *
 * VoxelCraft world consists of a 32x32 grid of chunks:
 * - chunkX: -16 to 15 (32 chunks wide)
 * - chunkZ: -16 to 15 (32 chunks deep)
 * - Each chunk is 16x16x256 blocks
 *
 * Total world size: 512x512 blocks horizontal (32 chunks × 16 blocks)
 *
 * Contract: world-api.yaml ChunkPosition schema
 * Data model: data-model.md Section 2
 */
data class ChunkPosition(
    val chunkX: Int,
    val chunkZ: Int
) {
    init {
        require(chunkX in -16..15) {
            "chunkX must be in range -16..15, got $chunkX"
        }
        require(chunkZ in -16..15) {
            "chunkZ must be in range -16..15, got $chunkZ"
        }
    }

    /**
     * Convert chunk X coordinate to world X coordinate
     * World X coordinate is the minimum block X position in this chunk
     *
     * @return World X coordinate (-256 to 240, in steps of 16)
     */
    fun toWorldX(): Int = chunkX * CHUNK_SIZE

    /**
     * Convert chunk Z coordinate to world Z coordinate
     * World Z coordinate is the minimum block Z position in this chunk
     *
     * @return World Z coordinate (-256 to 240, in steps of 16)
     */
    fun toWorldZ(): Int = chunkZ * CHUNK_SIZE

    companion object {
        /**
         * Chunk size in blocks (width and depth)
         * Height is fixed at 256 blocks (0-255)
         */
        const val CHUNK_SIZE = 16

        /**
         * Total chunk height in blocks
         */
        const val CHUNK_HEIGHT = 256

        /**
         * Total chunks in world (32x32 grid)
         */
        const val TOTAL_CHUNKS = 32 * 32 // = 1,024

        /**
         * Convert world block coordinates to chunk position
         *
         * Uses floor division to handle negative coordinates correctly.
         *
         * @param worldX World X coordinate (-256 to 255)
         * @param worldZ World Z coordinate (-256 to 255)
         * @return ChunkPosition containing the block
         */
        fun fromWorldCoordinates(worldX: Int, worldZ: Int): ChunkPosition {
            val chunkX = floor(worldX.toDouble() / CHUNK_SIZE).toInt()
            val chunkZ = floor(worldZ.toDouble() / CHUNK_SIZE).toInt()
            return ChunkPosition(chunkX, chunkZ)
        }

        /**
         * Get all chunk positions in the world (32x32 grid)
         *
         * Used for world generation and iteration.
         *
         * @return List of all 1,024 chunk positions
         */

        fun spiralAround(center: ChunkPosition, radius: Int): List<ChunkPosition> {
            val result = mutableListOf<ChunkPosition>()
            val minBound = -16
            val maxBound = 15
            for (layer in 0..radius) {
                val rangeX = (center.chunkX - layer)..(center.chunkX + layer)
                val rangeZ = (center.chunkZ - layer)..(center.chunkZ + layer)
                for (x in rangeX) {
                    for (z in rangeZ) {
                        if (max(abs(x - center.chunkX), abs(z - center.chunkZ)) != layer) continue
                        if (x !in minBound..maxBound || z !in minBound..maxBound) continue
                        result.add(ChunkPosition(x, z))
                    }
                }
            }
            return result
        }

        fun allChunks(): List<ChunkPosition> = buildList {
            for (chunkX in -16..15) {
                for (chunkZ in -16..15) {
                    add(ChunkPosition(chunkX, chunkZ))
                }
            }
        }
    }

    /**
     * Get local block coordinates within this chunk
     *
     * Converts world coordinates to chunk-relative coordinates (0-15).
     *
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Pair of (localX, localZ) in range 0-15
     */
    fun toLocalCoordinates(worldX: Int, worldZ: Int): Pair<Int, Int> {
        val localX = (worldX % CHUNK_SIZE + CHUNK_SIZE) % CHUNK_SIZE
        val localZ = (worldZ % CHUNK_SIZE + CHUNK_SIZE) % CHUNK_SIZE
        return Pair(localX, localZ)
    }

    override fun toString(): String = "ChunkPosition(x=$chunkX, z=$chunkZ)"
}
