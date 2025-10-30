package io.materia.examples.voxelcraft.integration

import io.materia.examples.voxelcraft.BlockType
import io.materia.examples.voxelcraft.VoxelWorld
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for game loop and core gameplay
 *
 * Tests verify end-to-end scenarios from quickstart.md.
 */
class GameLoopTest {

    /**
     * T013: Quickstart Steps 1-2 - Generate world and spawn player
     *
     * Scenario:
     * 1. Initialize VoxelWorld with seed
     * 2. Verify world generated with 1,024 chunks
     * 3. Verify player spawns at valid Y position
     */
    @Test
    fun testWorldGenerationAndSpawn() = runTest {
        // Step 1: Generate world
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Step 2: Verify world generated
        assertEquals(1024, world.chunkCount)
        assertTrue(world.player.position.y >= 64.0f) // Player above terrain
    }

    /**
     * T014: Quickstart Step 3 - Move player
     *
     * Scenario:
     * 1. Get initial player position
     * 2. Move player programmatically
     * 3. Verify player moved
     */
    @Test
    fun testPlayerMovement() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Given: Player at starting position
        val initialPos = world.player.position.copy()

        // When: Move player
        world.player.move(io.materia.core.math.Vector3(1f, 0f, 0f))

        // Then: Player moved
        assertTrue(world.player.position.x > initialPos.x)
    }

    /**
     * T015: Quickstart Step 4 - Rotate camera
     *
     * Scenario:
     * 1. Get initial camera rotation
     * 2. Rotate camera
     * 3. Verify camera rotated
     */
    @Test
    fun testCameraRotation() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Given: Initial rotation
        val initialRotation = world.player.rotation.copy()

        // When: Rotate camera
        world.player.rotate(0.1, 0.2)

        // Then: Camera rotated
        assertTrue(world.player.rotation.x > initialRotation.x)
        assertTrue(world.player.rotation.y > initialRotation.y)
    }

    /**
     * T016: Quickstart Step 5 - Break block
     *
     * Scenario:
     * 1. Place a known block
     * 2. Break it (set to Air)
     * 3. Verify block broken
     */
    @Test
    fun testBreakBlock() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Given: Block at position
        world.setBlock(0, 65, 0, BlockType.Stone)
        assertEquals(BlockType.Stone, world.getBlock(0, 65, 0))

        // When: Break block
        world.setBlock(0, 65, 0, BlockType.Air)

        // Then: Block broken
        assertEquals(BlockType.Air, world.getBlock(0, 65, 0))
    }

    /**
     * T017: Quickstart Step 6 - Place block
     *
     * Scenario:
     * 1. Verify block doesn't exist
     * 2. Place block
     * 3. Verify block placed
     */
    @Test
    fun testPlaceBlock() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Given: Air at position
        world.setBlock(0, 100, 0, BlockType.Air)

        // When: Place Dirt block
        world.setBlock(0, 100, 0, BlockType.Dirt)

        // Then: Dirt block placed
        assertEquals(BlockType.Dirt, world.getBlock(0, 100, 0))
    }

    /**
     * T018: Quickstart Step 7 - Toggle flight mode
     *
     * Scenario:
     * 1. Verify player starts in non-flying mode
     * 2. Toggle flight
     * 3. Verify flight enabled
     */
    @Test
    fun testFlightMode() = runTest {
        val world = VoxelWorld(12345L, parentScope = this)
        world.generateTerrain()

        // Given: Non-flying mode
        assertEquals(false, world.player.isFlying)

        // When: Toggle flight
        world.player.toggleFlight()

        // Then: Flying enabled
        assertEquals(true, world.player.isFlying)

        // When: Toggle again
        world.player.toggleFlight()

        // Then: Flying disabled
        assertEquals(false, world.player.isFlying)
    }
}
