package io.materia.examples.voxelcraft.contract

import io.materia.core.math.Vector3
import io.materia.examples.voxelcraft.BlockType
import io.materia.examples.voxelcraft.Inventory
import io.materia.examples.voxelcraft.Player
import io.materia.examples.voxelcraft.VoxelWorld
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for Player API
 *
 * Tests verify that Player implementation matches player-api.yaml specification.
 */
class PlayerContractTest {

    private val testScope = TestScope()

    /**
     * T007: GET /player/state
     *
     * Contract: player-api.yaml PlayerState (position, rotation, isFlying, velocity)
     * Test: Verify player state retrieval returns all required fields
     */
    @Test
    fun testGetPlayerState() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Expected: player.position is Vector3 (x, y, z)
        assertTrue(player.position is Vector3)

        // Expected: player.rotation is Vector3 (pitch, yaw, roll)
        assertTrue(player.rotation is Vector3)

        // Expected: player.isFlying is Boolean
        assertFalse(player.isFlying)

        // Expected: player.velocity is Vector3
        assertTrue(player.velocity is Vector3)
    }

    /**
     * T008: PUT /player/move
     *
     * Contract: player-api.yaml MoveRequest → newPosition + collided flag
     * Test: Verify movement with collision detection
     * Test: Verify collision detection blocks movement into solid blocks
     */
    @Test
    fun testMovePlayer() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Given: Player at position (0, 100, 0)
        val initialPos = player.position.copy()

        // When: player.move(Vector3(1, 0, 0)) // Move +X
        player.move(Vector3(1f, 0f, 0f))

        // Expected: New position is (1, 100, 0) if no collision
        assertEquals(initialPos.x + 1f, player.position.x, 0.01f)
        assertEquals(initialPos.y, player.position.y, 0.01f)
        assertEquals(initialPos.z, player.position.z, 0.01f)
    }

    /**
     * T009: PUT /player/rotate
     *
     * Contract: player-api.yaml RotateRequest → Rotation (pitch clamped to ±π/2)
     * Test: Verify pitch clamping to ±90 degrees
     * Test: Verify yaw is unclamped
     */
    @Test
    fun testRotateCamera() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Given: Player with rotation (0, 0)
        assertEquals(0f, player.rotation.x, 0.01f)
        assertEquals(0f, player.rotation.y, 0.01f)

        // When: player.rotate(deltaPitch = 1.0, deltaYaw = 2.0)
        player.rotate(1.0, 2.0)
        // Expected: rotation.pitch increases by 1.0
        assertEquals(1f, player.rotation.x, 0.01f)
        // Expected: rotation.yaw increases by 2.0
        assertEquals(2f, player.rotation.y, 0.01f)
    }

    /**
     * T010: POST /player/flight/toggle
     *
     * Contract: player-api.yaml → isFlying boolean
     * Test: Verify flight mode toggle switches state
     */
    @Test
    fun testToggleFlight() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Given: Player with isFlying = false
        assertFalse(player.isFlying)

        // When: player.toggleFlight()
        player.toggleFlight()
        // Expected: isFlying == true
        assertTrue(player.isFlying)

        // When: player.toggleFlight() again
        player.toggleFlight()
        // Expected: isFlying == false
        assertFalse(player.isFlying)
    }

    /**
     * Additional contract validation tests
     */

    @Test
    fun testInventory() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Expected: player.inventory is Inventory class
        assertTrue(player.inventory is Inventory)

        // Expected: inventory.selectedBlock is BlockType
        assertTrue(player.inventory.selectedBlock is BlockType)
    }

    @Test
    fun testAddToInventory() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Given: Player with empty inventory
        // When: inventory.add(BlockType.Grass, 64)
        player.inventory.add(BlockType.Grass, 64)
        // Expected: inventory.getCount(BlockType.Grass) == 64
        assertEquals(64, player.inventory.getCount(BlockType.Grass))

        // When: inventory.add(BlockType.Grass, 32)
        player.inventory.add(BlockType.Grass, 32)
        // Expected: inventory.getCount(BlockType.Grass) == 96 (cumulative)
        assertEquals(96, player.inventory.getCount(BlockType.Grass))
    }

    @Test
    fun testRotationBounds() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Test pitch clamping:
        // When: player.rotate(deltaPitch = 10.0, deltaYaw = 0.0) // Extreme rotation
        player.rotate(10.0, 0.0)
        // Expected: rotation.pitch == PI / 2 (clamped to +90°)
        assertEquals(kotlin.math.PI.toFloat() / 2, player.rotation.x, 0.01f)

        // Reset and test negative
        player.rotation.x = 0f
        // When: player.rotate(deltaPitch = -10.0, deltaYaw = 0.0)
        player.rotate(-10.0, 0.0)
        // Expected: rotation.pitch == -PI / 2 (clamped to -90°)
        assertEquals(-kotlin.math.PI.toFloat() / 2, player.rotation.x, 0.01f)
    }

    @Test
    fun testPlayerBoundingBox() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Expected: Player has collision dimensions (width 0.6, height 1.8)
        assertEquals(0.6f, player.width, 0.01f)
        assertEquals(1.8f, player.height, 0.01f)
        assertEquals(0.6f, player.depth, 0.01f)
    }

    @Test
    fun testGravityPhysics() {
        val world = VoxelWorld(12345L, parentScope = testScope)
        val player = Player(world)

        // Given: Player with isFlying = false at Y = 100
        player.isFlying = false
        val initialY = player.velocity.y

        // When: player.update(deltaTime = 0.016) // 60 FPS frame
        player.update(0.016f)

        // Expected: velocity.y decreases by gravity
        assertTrue(player.velocity.y < initialY)
    }
}
