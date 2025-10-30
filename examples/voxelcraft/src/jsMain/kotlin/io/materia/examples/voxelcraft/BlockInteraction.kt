package io.materia.examples.voxelcraft

import kotlin.math.floor

/**
 * BlockInteraction for raycasting and block break/place
 *
 * Implements DDA (Digital Differential Analyzer) raycasting algorithm
 * to find the target block the player is looking at.
 *
 * Max range: 5 blocks
 *
 * Data model: data-model.md (Player interaction)
 * Research: research.md "Block Interaction"
 */
class BlockInteraction(
    private val world: VoxelWorld,
    private val player: Player
) {

    /**
     * Handle left click - break block
     *
     * Raycasts from camera to find target block, removes it,
     * and adds to player inventory.
     */
    fun handleLeftClick() {
        val target = raycast() ?: return

        val block = world.getBlock(target.x, target.y, target.z) ?: return
        if (block == BlockType.Air) return

        // Break block
        world.setBlock(target.x, target.y, target.z, BlockType.Air)

        // Add to inventory
        player.inventory.add(block, 1)
    }

    /**
     * Handle right click - place block
     *
     * Raycasts to find target block, then places selected block
     * adjacent to the target surface.
     */
    fun handleRightClick() {
        val target = raycast() ?: return

        // Get placement position (adjacent to hit face)
        val placePos = getAdjacentPosition(target) ?: return

        // Cannot place inside player
        if (isInsidePlayer(placePos)) {
            return
        }

        // Check if space is empty
        val existing = world.getBlock(placePos.x, placePos.y, placePos.z)
        if (existing != null && existing != BlockType.Air) {
            return
        }

        // Place block
        val selectedBlock = player.inventory.selectedBlock
        world.setBlock(placePos.x, placePos.y, placePos.z, selectedBlock)
    }

    /**
     * Raycast from camera to find target block
     *
     * Uses DDA algorithm to step through blocks along ray direction.
     * Returns first solid block within range, or null if none found.
     *
     * @return BlockPosition of target block, or null
     */
    private fun raycast(): BlockPosition? {
        val origin = player.position
        val direction = getViewDirection()

        // DDA raycasting
        val maxDistance = 5.0 // blocks
        val step = 0.1 // step size

        var distance = 0.0
        while (distance < maxDistance) {
            val x = floor(origin.x.toDouble() + direction.x * distance).toInt()
            val y = floor(origin.y.toDouble() + 1.6 + direction.y * distance).toInt() // Eye level
            val z = floor(origin.z.toDouble() + direction.z * distance).toInt()

            val block = world.getBlock(x, y, z)
            if (block != null && block != BlockType.Air) {
                return BlockPosition(x, y, z)
            }

            distance += step
        }

        return null
    }

    /**
     * Get view direction vector from player rotation
     *
     * Converts pitch/yaw to unit direction vector.
     *
     * @return Direction vector (normalized)
     */
    private fun getViewDirection(): Direction {
        val pitch = player.rotation.x.toDouble()
        val yaw = player.rotation.y.toDouble()

        val cosPitch = kotlin.math.cos(pitch)
        val sinPitch = kotlin.math.sin(pitch)
        val cosYaw = kotlin.math.cos(yaw)
        val sinYaw = kotlin.math.sin(yaw)

        return Direction(
            x = -sinYaw * cosPitch,
            y = sinPitch,
            z = -cosYaw * cosPitch
        )
    }

    /**
     * Get adjacent position for block placement
     *
     * Simplified implementation that places blocks above the target. This provides
     * intuitive block placement for the VoxelCraft demo. Full ray-face detection
     * would require normal calculation from the raycast intersection point.
     *
     * @param target Target block hit by raycast
     * @return Adjacent position for placement
     */
    private fun getAdjacentPosition(target: BlockPosition): BlockPosition? {
        // Simplified: place above target
        return BlockPosition(target.x, target.y + 1, target.z)
    }

    /**
     * Check if position intersects with player bounding box
     *
     * @param pos Block position to check
     * @return true if position is inside player
     */
    private fun isInsidePlayer(pos: BlockPosition): Boolean {
        val playerX = player.position.x.toInt()
        val playerY = player.position.y.toInt()
        val playerZ = player.position.z.toInt()

        return pos.x == playerX &&
                pos.y in playerY..(playerY + 1) &&
                pos.z == playerZ
    }
}

/**
 * Block position in world coordinates
 */
data class BlockPosition(val x: Int, val y: Int, val z: Int)

/**
 * Direction vector (normalized)
 */
data class Direction(val x: Double, val y: Double, val z: Double)
