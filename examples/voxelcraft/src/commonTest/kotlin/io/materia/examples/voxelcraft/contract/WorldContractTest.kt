package io.materia.examples.voxelcraft.contract

import io.materia.examples.voxelcraft.BlockType
import io.materia.examples.voxelcraft.ChunkPosition
import io.materia.examples.voxelcraft.VoxelWorld
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Contract tests for World API
 *
 * Tests verify that VoxelWorld implementation matches world-api.yaml specification.
 */
class WorldContractTest {

    /**
     * T004: POST /world/generate
     *
     * Contract: world-api.yaml WorldGenerationRequest → Response with chunksGenerated=1024
     * Test: Verify world generation with seed produces exactly 1,024 chunks
     */
    @Test
    fun testGenerateWorld() = runTest {
        val seed = 12345L
        val world = VoxelWorld(seed, parentScope = this)

        // Generate terrain
        world.generateTerrain()

        // Expected: world.chunks.size == 1024
        assertEquals(1024, world.chunkCount)
        assertTrue(world.isGenerated)
    }

    /**
     * T005: GET /world/block/{x}/{y}/{z}
     *
     * Contract: world-api.yaml Position3D → Block (type + position)
     * Test: Verify block retrieval returns correct BlockType at valid coordinates
     * Test: Verify out-of-bounds coordinates return null (404)
     */
    @Test
    fun testGetBlock() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Expected: world.getBlock(0, 64, 0) returns BlockType (not null)
        val block = world.getBlock(0, 64, 0)
        assertNotNull(block)

        // Expected: world.getBlock(-257, 0, 0) returns null (out of bounds)
        assertNull(world.getBlock(-257, 0, 0))

        // Expected: world.getBlock(0, 256, 0) returns null (Y too high)
        assertNull(world.getBlock(0, 256, 0))
    }

    /**
     * T006: PUT /world/block/{x}/{y}/{z}
     *
     * Contract: world-api.yaml BlockType → 200 OK or 403 Forbidden
     * Test: Verify block placement updates chunk at valid coordinates
     * Test: Verify out-of-bounds placement returns false (403)
     */
    @Test
    fun testSetBlock() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Expected: world.setBlock(0, 64, 0, BlockType.Stone) returns true
        assertTrue(world.setBlock(0, 64, 0, BlockType.Stone))

        // Verify block was set
        assertEquals(BlockType.Stone, world.getBlock(0, 64, 0))

        // Expected: world.setBlock(-257, 0, 0, BlockType.Stone) returns false (out of bounds)
        assertFalse(world.setBlock(-257, 0, 0, BlockType.Stone))
    }

    /**
     * Additional contract validation tests
     */

    @Test
    fun testWorldBounds() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Contract: world-api.yaml WorldBounds (-256 to 255 X/Z, 0 to 255 Y)

        // Test minimum bounds
        assertNotNull(world.getBlock(-256, 0, -256))

        // Test maximum bounds
        assertNotNull(world.getBlock(255, 255, 255))

        // Test out of bounds
        assertNull(world.getBlock(-257, 0, 0))
        assertNull(world.getBlock(256, 0, 0))
        assertNull(world.getBlock(0, -1, 0))
        assertNull(world.getBlock(0, 256, 0))
    }

    @Test
    fun testChunkPositionValidation() {
        // Contract: ChunkPosition must be in range -16 to 15

        // Expected: ChunkPosition(0, 0) succeeds
        val pos1 = ChunkPosition(0, 0)
        assertEquals(0, pos1.chunkX)
        assertEquals(0, pos1.chunkZ)

        // Expected: ChunkPosition(-16, 15) succeeds (edge case)
        val pos2 = ChunkPosition(-16, 15)
        assertEquals(-16, pos2.chunkX)
        assertEquals(15, pos2.chunkZ)
    }

    @Test
    fun testChunkData() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Get a chunk
        val chunk = world.getChunk(ChunkPosition(0, 0))
        assertNotNull(chunk)

        // Expected: Chunk.getBlock() and setBlock() work with local coordinates (0-15 XZ, 0-255 Y)
        chunk.setBlock(0, 64, 0, BlockType.Stone)
        assertEquals(BlockType.Stone, chunk.getBlock(0, 64, 0))
    }

    @Test
    fun testBlockTypeEnum() {
        // Contract: BlockType enum has Air, Grass, Dirt, Stone, Wood, Leaves, Sand, Water

        // Expected: BlockType sealed class with all types
        assertTrue(BlockType.Air is BlockType)
        assertTrue(BlockType.Grass is BlockType)
        assertTrue(BlockType.Dirt is BlockType)
        assertTrue(BlockType.Stone is BlockType)
        assertTrue(BlockType.Wood is BlockType)
        assertTrue(BlockType.Leaves is BlockType)
        assertTrue(BlockType.Sand is BlockType)
        assertTrue(BlockType.Water is BlockType)
    }
}
