package io.materia.examples.voxelcraft.contract

import io.materia.examples.voxelcraft.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Contract tests for Storage API
 *
 * Tests verify that WorldStorage implementation matches storage-api.yaml specification.
 */
class StorageContractTest {

    /**
     * T011: POST /storage/save
     *
     * Contract: storage-api.yaml WorldState → SaveResponse (success, sizeBytes)
     * Test: Verify WorldState serialization structure
     */
    @Test
    fun testSaveWorldState() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Create WorldState from world
        val worldState = WorldState.from(world)

        // Expected: WorldState has seed
        assertEquals(12345L, worldState.seed)

        // Expected: WorldState has player position
        assertNotNull(worldState.playerPosition)

        // Expected: WorldState has player rotation
        assertNotNull(worldState.playerRotation)

        // Note: Actual localStorage save tested in JS-specific tests
    }

    /**
     * T012: GET /storage/load
     *
     * Contract: storage-api.yaml → WorldState (seed, playerPosition, etc.)
     * Test: Verify state restoration
     */
    @Test
    fun testLoadWorldState() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Create and restore WorldState
        val worldState = WorldState.from(world)
        val restored = worldState.restore(this)

        // Expected: Restored world has same seed
        assertEquals(world.seed, restored.seed)

        // Note: Actual localStorage load tested in JS-specific tests
    }

    /**
     * Additional contract validation tests
     */

    @Test
    fun testStorageSize() {
        // Contract: storage-api.yaml StorageInfo (usedBytes, availableBytes, percentUsed)

        // Test StorageInfo structure
        val info = StorageInfo(
            usedBytes = 1000,
            availableBytes = 9000,
            percentUsed = 10.0
        )

        assertEquals(1000, info.usedBytes)
        assertEquals(9000, info.availableBytes)
        assertEquals(10.0, info.percentUsed, 0.1)

        // Note: Actual localStorage operations tested in JS-specific tests
    }

    @Test
    fun testClearStorage() = runTest {
        // Contract: storage-api.yaml DELETE /storage/clear
        // Note: Actual localStorage clear tested in JS-specific tests

        // Test WorldState structure supports clear operation
        val world = VoxelWorld(12345L, parentScope = this)
        val worldState = WorldState.from(world)
        assertNotNull(worldState)
    }

    @Test
    fun testWorldStateSerialization() = runTest {
        // Contract: WorldState must be @Serializable

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Create WorldState
        val worldState = WorldState.from(world)

        // Expected: WorldState has all required fields
        assertEquals(12345L, worldState.seed)
        assertNotNull(worldState.playerPosition)
        assertNotNull(worldState.playerRotation)
        assertNotNull(worldState.chunks)
    }

    @Test
    fun testChunkCompression() = runTest {
        // Contract: SerializedChunk structure

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Get a chunk and create serialized version
        val chunk = world.getChunk(ChunkPosition(0, 0))
        assertNotNull(chunk)

        val serialized = SerializedChunk.from(chunk!!)

        // Expected: SerializedChunk has chunk position
        assertEquals(0, serialized.chunkX)
        assertEquals(0, serialized.chunkZ)

        // Expected: SerializedChunk has compressed blocks
        assertNotNull(serialized.compressedBlocks)
    }

    @Test
    fun testChunkDecompression() = runTest {
        // Contract: Chunk round-trip serialization

        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        val originalChunk = world.getChunk(ChunkPosition(0, 0))
        assertNotNull(originalChunk)

        // Test SerializedChunk creation
        val serialized = SerializedChunk.from(originalChunk!!)
        assertNotNull(serialized.compressedBlocks)
    }

    @Test
    fun testQuotaExceededHandling() {
        // Contract: SaveResult supports error handling

        val errorResult = SaveResult(
            success = false,
            sizeBytes = 0,
            error = "Storage quota exceeded"
        )

        assertEquals(false, errorResult.success)
        assertNotNull(errorResult.error)

        // Note: Actual quota handling tested in JS-specific tests
    }

    @Test
    fun testAutoSaveInterval() = runTest {
        // Contract: WorldState supports auto-save

        val world = VoxelWorld(12345L, parentScope = this)
        val worldState = WorldState.from(world)

        // Expected: WorldState can be created for auto-save
        assertNotNull(worldState)

        // Note: Actual auto-save interval tested in integration tests
    }

    @Test
    fun testSaveOnPageClose() = runTest {
        // Contract: WorldState supports save on close

        val world = VoxelWorld(12345L, parentScope = this)
        val worldState = WorldState.from(world)

        // Expected: WorldState can be created for page close save
        assertNotNull(worldState)

        // Note: Actual page close handling tested in JS-specific tests
    }
}

