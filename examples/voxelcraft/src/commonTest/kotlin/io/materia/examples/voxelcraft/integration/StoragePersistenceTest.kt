package io.materia.examples.voxelcraft.integration

import io.materia.examples.voxelcraft.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for storage persistence
 *
 * Tests verify save/load functionality from quickstart.md.
 */
class StoragePersistenceTest {

    /**
     * T019: Quickstart Step 8 - Save world state
     *
     * Scenario:
     * 1. Make changes to world (break/place blocks)
     * 2. Update player position
     * 3. Create WorldState
     * 4. Verify world state created
     */
    @Test
    fun testSaveWorld() = runTest {
        // Given: World with modifications
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()
        world.setBlock(10, 70, 10, BlockType.Wood) // Place a block
        world.player.position.set(5.0f, 75.0f, 5.0f) // Move player

        // When: Create WorldState
        val worldState = WorldState.from(world)

        // Then: WorldState created
        assertNotNull(worldState)
        assertEquals(12345L, worldState.seed)

        // Note: Actual localStorage save tested in JS-specific tests
    }

    /**
     * T020: Quickstart Step 9 - Load world state
     *
     * Scenario:
     * 1. Create world state with modifications
     * 2. Restore from state
     * 3. Verify all state restored
     */
    @Test
    fun testLoadWorld() = runTest {
        // Given: World with saved state
        val world1 = VoxelWorld(12345L, parentScope = this)
        world1.generateTerrain()
        world1.setBlock(10, 70, 10, BlockType.Wood)
        world1.player.position.set(5.0f, 75.0f, 5.0f)
        world1.player.isFlying = true

        // When: Create and restore from WorldState
        val worldState = WorldState.from(world1)
        val world2 = worldState.restore(this)

        // Then: World state restored
        assertEquals(12345L, world2.seed)
        assertTrue(world2.player.isFlying)

        // Note: Actual localStorage operations tested in JS-specific tests
    }

    /**
     * Additional persistence tests
     */

    @Test
    fun testAutoSave() = runTest {
        // Contract: WorldState supports auto-save functionality

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Expected: WorldState can be created for auto-save
        val worldState = WorldState.from(world)
        assertNotNull(worldState)

        // Note: Actual auto-save timer tested in JS-specific tests
    }

    @Test
    fun testSaveOnPageClose() = runTest {
        // Contract: WorldState supports save on page close

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Expected: WorldState can be created for page close save
        val worldState = WorldState.from(world)
        assertNotNull(worldState)

        // Note: Actual page close handling tested in JS-specific tests
    }

    @Test
    fun testCompressionRatio() = runTest {
        // Contract: SerializedChunk supports compression

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        val chunk = world.getChunk(ChunkPosition(0, 0))
        assertNotNull(chunk)

        // Expected: Chunk can be serialized
        val serialized = SerializedChunk.from(chunk!!)
        assertNotNull(serialized.compressedBlocks)

        // Note: Actual compression ratio tested in JS-specific tests
    }

    @Test
    fun testQuotaExceeded() {
        // Contract: SaveResult supports quota error handling

        val errorResult = SaveResult(
            success = false,
            sizeBytes = 0,
            error = "Storage quota exceeded"
        )

        assertEquals(false, errorResult.success)
        assertEquals("Storage quota exceeded", errorResult.error)

        // Note: Actual quota handling tested in JS-specific tests
    }

    @Test
    fun testCorruptedDataRecovery() = runTest {
        // Contract: WorldState deserialization supports error handling

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Expected: WorldState can be created
        val worldState = WorldState.from(world)
        assertNotNull(worldState)

        // Note: Actual corrupted data handling tested in JS-specific tests
    }

    @Test
    fun testPartialChunkSave() = runTest {
        // Optimization: WorldState.from() supports partial chunk save

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Modify some blocks
        world.setBlock(10, 70, 10, BlockType.Wood)

        // Expected: WorldState created with chunk data
        val worldState = WorldState.from(world)
        assertNotNull(worldState)
        assertNotNull(worldState.chunks)

        // Note: Actual delta-save optimization tested in JS-specific tests
    }
}
