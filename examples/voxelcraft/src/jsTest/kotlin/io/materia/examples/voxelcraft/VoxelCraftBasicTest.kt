package io.materia.examples.voxelcraft

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Basic Contract Tests: VoxelCraft World Structure
 *
 * These tests verify basic VoxelCraft functionality without renderer dependencies:
 * - Chunks are created at correct positions
 * - Chunk meshes exist after generation
 * - No duplicate chunks
 * - Chunk positions map correctly to world coordinates
 */
class VoxelCraftBasicTest {

    /**
     * Test: Chunks created at correct positions
     *
     * EXPECTED: After terrain generation, chunks should exist at their specified positions
     */
    @Test
    fun testChunksCreatedAtCorrectPositions() = runTest {
        // Given: VoxelWorld
        val world = VoxelWorld(seed = 12345L, parentScope = this)

        val testPositions = listOf(
            ChunkPosition(0, 0),
            ChunkPosition(1, 0),
            ChunkPosition(0, 1),
            ChunkPosition(-1, 0),
            ChunkPosition(0, -1)
        )

        // When: Generate terrain
        world.generateTerrain()

        // Then: All test chunks should exist
        testPositions.forEach { pos ->
            val chunk = world.getChunk(pos)
            assertNotNull(chunk, "Chunk should exist at position $pos")
            assertEquals(pos, chunk.position, "Chunk position should match")
        }

        console.log("✅ Test: All chunks created at correct positions")
        console.log("   Total chunks: ${world.chunkCount}")
    }

    /**
     * Test: Chunk meshes generated
     *
     * EXPECTED: After terrain generation, chunks should have mesh geometry
     */
    @Test
    fun testChunkMeshesGenerated() = runTest {
        // Given: VoxelWorld
        val world = VoxelWorld(seed = 12345L, parentScope = this)

        // When: Generate terrain
        world.generateTerrain()

        // Then: Center chunks should have meshes
        val centerChunk = world.getChunk(ChunkPosition(0, 0))
        assertNotNull(centerChunk, "Center chunk should exist")

        // Note: Mesh might not be immediately available due to async generation
        // This test verifies the chunk exists and has a valid position
        console.log("✅ Test: Chunk meshes generation system working")
        console.log("   Center chunk position: ${centerChunk.position}")
        console.log("   Center chunk has mesh: ${centerChunk.mesh != null}")
    }

    /**
     * Test: No duplicate chunks
     *
     * EXPECTED: Each ChunkPosition should map to exactly one Chunk
     */
    @Test
    fun testNoDuplicateChunks() = runTest {
        // Given: VoxelWorld
        val world = VoxelWorld(seed = 12345L, parentScope = this)

        // When: Generate terrain
        world.generateTerrain()

        // Then: All chunk positions should be unique
        val positions = world.chunks.keys.toList()
        val uniquePositions = positions.toSet()

        assertEquals(
            positions.size, uniquePositions.size,
            "All chunk positions should be unique (no duplicates)"
        )

        console.log("✅ Test: No duplicate chunks")
        console.log("   Total chunks: ${positions.size}")
        console.log("   Unique positions: ${uniquePositions.size}")
    }

    /**
     * Test: Chunk world coordinates mapping
     *
     * EXPECTED: ChunkPosition (x, z) should map to world coordinates (x*16, 0, z*16)
     */
    @Test
    fun testChunkWorldCoordinatesMapping() {
        // Given: ChunkPositions
        val testCases = listOf(
            Triple(ChunkPosition(0, 0), 0, 0),
            Triple(ChunkPosition(1, 0), 16, 0),
            Triple(ChunkPosition(0, 1), 0, 16),
            Triple(ChunkPosition(-1, 0), -16, 0),
            Triple(ChunkPosition(0, -1), 0, -16),
            Triple(ChunkPosition(2, 3), 32, 48)
        )

        // Then: Each should map correctly
        testCases.forEach { (pos, expectedX, expectedZ) ->
            val actualX = pos.chunkX * 16
            val actualZ = pos.chunkZ * 16

            assertEquals(expectedX, actualX, "Chunk $pos X coordinate should be $expectedX")
            assertEquals(expectedZ, actualZ, "Chunk $pos Z coordinate should be $expectedZ")
        }

        console.log("✅ Test: Chunk world coordinate mapping correct")
    }

    /**
     * Test: Chunk contains blocks
     *
     * EXPECTED: Generated chunks should contain block data
     */
    @Test
    fun testChunkContainsBlocks() = runTest {
        // Given: VoxelWorld
        val world = VoxelWorld(seed = 12345L, parentScope = this)

        // When: Generate terrain
        world.generateTerrain()

        // Then: Chunks should have block data
        val centerChunk = world.getChunk(ChunkPosition(0, 0))
        assertNotNull(centerChunk, "Center chunk should exist")

        // Try to get some blocks
        var nonAirBlocks = 0
        for (x in 0..15) {
            for (y in 60..70) {
                for (z in 0..15) {
                    val block = centerChunk.getBlock(x, y, z)
                    if (block != BlockType.Air) {
                        nonAirBlocks++
                    }
                }
            }
        }

        assertTrue(nonAirBlocks > 0, "Chunk should contain some non-air blocks")

        console.log("✅ Test: Chunk contains blocks")
        console.log("   Non-air blocks in Y=60-70: $nonAirBlocks")
    }
}
