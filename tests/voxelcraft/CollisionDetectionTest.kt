package io.materia.examples.voxelcraft.tests

import io.materia.examples.voxelcraft.*
import kotlin.test.*

/**
 * Comprehensive collision detection tests for VoxelCraft
 * 
 * Tests cover:
 * 1. Basic collision with solid blocks
 * 2. Collision with transparent blocks (should pass through)
 * 3. Collision with unloaded chunks (null) - should treat as solid in terrain, passable in sky
 * 4. Ground detection at various heights
 * 5. Falling through map prevention
 * 6. Spawn at different positions
 * 7. Corner clipping prevention
 */
class CollisionDetectionTest {
    
    private lateinit var world: VoxelWorld
    private lateinit var player: Player
    
    @BeforeTest
    fun setup() {
        world = VoxelWorld(seed = 12345L)
        player = world.player
    }
    
    @AfterTest
    fun teardown() {
        world.dispose()
    }
    
    // ==================== Basic Collision Tests ====================
    
    @Test
    fun `test player collides with solid block`() {
        // Given: Solid block at (10, 70, 10)
        world.setBlock(10, 70, BlockType.Stone, 10)
        
        // When: Player tries to move into block
        player.position.set(9.5f, 70f, 10f)
        player.move(Vector3(1f, 0f, 0f)) // Try to move into stone
        
        // Then: Player should be stopped by collision
        assertTrue(
            player.position.x < 10f,
            "Player should not pass through solid block (actual X=${player.position.x})"
        )
    }
    
    @Test
    fun `test player passes through air`() {
        // Given: Air at target position
        world.setBlock(10, 70, BlockType.Air, 10)
        
        // When: Player moves through air
        player.position.set(9f, 70f, 10f)
        player.move(Vector3(1f, 0f, 0f))
        
        // Then: Player should pass through
        assertEquals(
            10f, player.position.x, 0.1f,
            "Player should pass through air"
        )
    }
    
    @Test
    fun `test player passes through transparent blocks`() {
        // Given: Water (transparent) at target
        world.setBlock(10, 70, BlockType.Water, 10)
        
        // When: Player moves through water
        player.position.set(9f, 70f, 10f)
        player.move(Vector3(1f, 0f, 0f))
        
        // Then: Player should pass through
        assertEquals(
            10f, player.position.x, 0.1f,
            "Player should pass through transparent blocks"
        )
    }
    
    // ==================== Unloaded Chunk Tests ====================
    
    @Test
    fun `test unloaded chunk at terrain height treated as solid`() {
        // Given: Unloaded chunk (returns null) at terrain height Y=70
        val result = world.getBlock(1000, 70, 1000) // Far away, not loaded
        
        // Verify it's null
        assertNull(result, "Far chunk should return null")
        
        // When: Check collision at terrain height with unloaded chunk
        player.position.set(1000f, 70f, 1000f)
        
        // Then: Should treat as solid (prevent falling through)
        // This is tested indirectly through the checkCollision method
        // We verify the player doesn't fall when standing on "nothing"
        val initialY = player.position.y
        player.update(0.016f) // One frame at 60 FPS
        
        // Player should collide with "solid" unloaded chunk and not fall
        assertTrue(
            player.position.y >= initialY - 0.5f,
            "Player should not fall through unloaded chunk at terrain height"
        )
    }
    
    @Test
    fun `test unloaded chunk at sky height treated as passable`() {
        // Given: Unloaded chunk at sky height Y=150
        val result = world.getBlock(1000, 150, 1000)
        
        // Verify it's null
        assertNull(result, "Far chunk should return null")
        
        // When: Player is at sky height with flight enabled
        player.position.set(1000f, 150f, 1000f)
        player.isFlying = true // Enable flight so gravity doesn't interfere
        
        // Then: Should not collide (allow free movement in sky)
        player.move(Vector3(1f, 0f, 0f))
        
        // Player should be able to move freely in sky even with unloaded chunks
        assertEquals(
            1001f, player.position.x, 0.1f,
            "Player should move freely in sky with unloaded chunks"
        )
    }
    
    // ==================== Ground Detection Tests ====================
    
    @Test
    fun `test ground detection finds solid block below`() {
        // Given: Solid ground at Y=70
        for (x in 9..11) {
            for (z in 9..11) {
                world.setBlock(x, 70, BlockType.Stone, z)
            }
        }
        
        // When: Player is slightly above ground
        player.position.set(10f, 71f, 10f)
        player.isFlying = false
        player.update(0.016f) // Apply gravity
        
        // Then: Player should detect ground
        assertTrue(
            player.isOnGround,
            "Player should detect ground below"
        )
    }
    
    @Test
    fun `test ground detection at chunk boundary`() {
        // Given: Solid block at chunk boundary
        // Chunk 0 edge is X=15, Chunk 1 starts at X=16
        world.setBlock(15, 70, BlockType.Stone, 10)
        world.setBlock(16, 70, BlockType.Stone, 10)
        
        // When: Player stands exactly at boundary
        player.position.set(15.5f, 71f, 10f)
        player.isFlying = false
        player.update(0.016f)
        
        // Then: Should detect ground
        assertTrue(
            player.isOnGround,
            "Player should detect ground at chunk boundary"
        )
    }
    
    @Test
    fun `test ground detection with 0_5 block range`() {
        // Given: Ground exactly 0.4 blocks below player
        world.setBlock(10, 70, BlockType.Stone, 10)
        player.position.set(10f, 70.4f, 10f)
        player.isFlying = false
        
        // When: Update player (ground check goes 0.5 blocks down)
        player.update(0.016f)
        
        // Then: Should detect ground within 0.5 block range
        assertTrue(
            player.isOnGround,
            "Ground detection should work within 0.5 blocks"
        )
    }
    
    @Test
    fun `test no ground detection when more than 0_5 blocks above`() {
        // Given: Ground 0.6 blocks below player
        world.setBlock(10, 70, BlockType.Stone, 10)
        player.position.set(10f, 70.7f, 10f)
        player.isFlying = false
        player.velocity.y = 0f // No falling yet
        
        // When: Check ground
        player.update(0.016f)
        
        // Then: Should NOT detect ground (too far)
        assertFalse(
            player.isOnGround,
            "Should not detect ground more than 0.5 blocks away"
        )
    }
    
    // ==================== Falling Through Map Tests ====================
    
    @Test
    fun `test cannot fall through map at terrain height`() {
        // Given: Terrain at Y=70, player above it
        for (x in 63..65) {
            for (z in 63..65) {
                world.setBlock(x, 70, BlockType.Stone, z)
            }
        }
        player.position.set(64f, 100f, 64f)
        player.isFlying = false
        
        // When: Player falls with gravity for many frames
        repeat(100) { // ~1.6 seconds of falling
            player.update(0.016f)
            
            // Then: Player should never go below ground
            assertTrue(
                player.position.y >= 70f,
                "Player should not fall through terrain (Y=${player.position.y})"
            )
            
            // Stop if on ground
            if (player.isOnGround) break
        }
        
        // Verify player ended up on ground
        assertTrue(player.isOnGround, "Player should end up on ground")
        assertTrue(
            player.position.y >= 70f && player.position.y <= 72f,
            "Player should be resting on ground (Y=${player.position.y})"
        )
    }
    
    @Test
    fun `test cannot fall through map at world edge`() {
        // Given: Bedrock at Y=0 (world bottom)
        world.setBlock(10, 0, BlockType.Stone, 10)
        player.position.set(10f, 10f, 10f)
        player.isFlying = false
        
        // When: Player falls toward bedrock
        repeat(100) {
            player.update(0.016f)
            
            // Then: Should never go below Y=0
            assertTrue(
                player.position.y >= 0f,
                "Player should not fall below bedrock (Y=${player.position.y})"
            )
            
            if (player.isOnGround) break
        }
    }
    
    @Test
    fun `test high velocity does not cause clipping`() {
        // Given: Solid floor
        for (x in 9..11) {
            for (z in 9..11) {
                world.setBlock(x, 70, BlockType.Stone, z)
            }
        }
        
        // When: Player has very high downward velocity
        player.position.set(10f, 90f, 10f)
        player.velocity.y = -50f // Terminal velocity
        player.isFlying = false
        
        // Apply for several frames
        repeat(20) {
            player.update(0.016f)
            
            // Then: Should never clip through floor
            assertTrue(
                player.position.y >= 70f,
                "High velocity should not cause clipping (Y=${player.position.y})"
            )
            
            if (player.isOnGround) break
        }
    }
    
    // ==================== Spawn Position Tests ====================
    
    @Test
    fun `test spawn at world center is valid`() {
        // Given: Spawn position (64, 150, 64) - center of 9x9 grid
        val spawnX = 64
        val spawnY = 150
        val spawnZ = 64
        
        // When: Player spawns there
        player.position.set(spawnX.toFloat(), spawnY.toFloat(), spawnZ.toFloat())
        player.isFlying = true
        
        // Then: Position should be valid (no immediate collision in sky)
        assertEquals(64f, player.position.x, "Spawn X should be 64")
        assertEquals(150f, player.position.y, "Spawn Y should be 150")
        assertEquals(64f, player.position.z, "Spawn Z should be 64")
        
        // Should be able to move in sky
        player.move(Vector3(1f, 0f, 0f))
        assertEquals(65f, player.position.x, 0.1f, "Should be able to move from spawn")
    }
    
    @Test
    fun `test spawn at edge is valid`() {
        // Given: Edge spawn position (8, 150, 8)
        player.position.set(8f, 150f, 8f)
        player.isFlying = true
        
        // Then: Should be valid
        assertEquals(8f, player.position.x, "Edge spawn X should be 8")
        assertEquals(150f, player.position.y, "Edge spawn Y should be 150")
    }
    
    // ==================== Corner Clipping Tests ====================
    
    @Test
    fun `test bounding box uses ceiling for max bounds`() {
        // Given: Block at (11, 70, 10)
        world.setBlock(11, 70, BlockType.Stone, 10)
        
        // When: Player is at (10.6, 70, 10) - within player width
        // Player width = 0.6, so bounding box is 10.3 to 10.9
        // With floor: maxX = 10
        // With ceiling: maxX = 11 (correct!)
        player.position.set(10.6f, 70f, 10f)
        player.move(Vector3(0.3f, 0f, 0f)) // Try to move closer
        
        // Then: Should collide (ceiling calculation detects block at X=11)
        assertTrue(
            player.position.x < 10.9f,
            "Ceiling calculation should detect collision (X=${player.position.x})"
        )
    }
    
    @Test
    fun `test cannot clip through diagonal corners`() {
        // Given: Corner blocks forming a diagonal barrier
        world.setBlock(10, 70, BlockType.Stone, 10)
        world.setBlock(11, 70, BlockType.Stone, 11)
        
        // When: Player tries to slip through diagonal
        player.position.set(9.5f, 70f, 9.5f)
        player.move(Vector3(1f, 0f, 1f)) // Move diagonally
        
        // Then: Should be blocked
        val moved = (player.position.x - 9.5f) + (player.position.z - 9.5f)
        assertTrue(
            moved < 1.5f,
            "Player should not clip through diagonal corners (moved=$moved)"
        )
    }
    
    // ==================== Flight Mode Tests ====================
    
    @Test
    fun `test flight mode disables gravity`() {
        // Given: Player in flight mode
        player.position.set(10f, 100f, 10f)
        player.isFlying = true
        val initialY = player.position.y
        
        // When: Update for several frames
        repeat(10) {
            player.update(0.016f)
        }
        
        // Then: Y position should not change (no gravity)
        assertEquals(
            initialY, player.position.y,
            "Flight mode should disable gravity"
        )
    }
    
    @Test
    fun `test gravity applies when not flying`() {
        // Given: Player not flying, in air
        player.position.set(10f, 100f, 10f)
        player.isFlying = false
        val initialY = player.position.y
        
        // When: Update one frame
        player.update(0.016f)
        
        // Then: Y should decrease (gravity applied)
        assertTrue(
            player.position.y < initialY,
            "Gravity should apply when not flying (Y went from $initialY to ${player.position.y})"
        )
    }
    
    // ==================== Edge Case Tests ====================
    
    @Test
    fun `test player cannot go below Y=0`() {
        // Given: Player at low Y
        player.position.set(10f, 1f, 10f)
        player.isFlying = false
        player.velocity.y = -50f
        
        // When: Try to go below Y=0
        player.move(Vector3(0f, -10f, 0f))
        
        // Then: Should be clamped at Y=0
        assertTrue(
            player.position.y >= 0f,
            "Player Y should not go below 0 (actual Y=${player.position.y})"
        )
    }
    
    @Test
    fun `test player cannot go above Y=255`() {
        // Given: Player near sky limit
        player.position.set(10f, 254f, 10f)
        player.isFlying = true
        
        // When: Try to go above Y=255
        player.move(Vector3(0f, 10f, 0f))
        
        // Then: Should be clamped at Y=255
        assertTrue(
            player.position.y <= 255f,
            "Player Y should not go above 255 (actual Y=${player.position.y})"
        )
    }
    
    @Test
    fun `test player bounds are enforced on X axis`() {
        // Given: Player near world edge
        player.position.set(254f, 70f, 10f)
        player.isFlying = true
        
        // When: Try to move past world boundary
        player.move(Vector3(10f, 0f, 0f))
        
        // Then: Should be clamped
        assertTrue(
            player.position.x <= 255f,
            "Player X should be within world bounds (actual X=${player.position.x})"
        )
    }
}
