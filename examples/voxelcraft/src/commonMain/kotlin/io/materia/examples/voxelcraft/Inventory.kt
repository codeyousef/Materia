package io.materia.examples.voxelcraft

/**
 * Inventory class for block storage and selection
 *
 * In creative mode, inventory has unlimited capacity. Players can add any number
 * of blocks and select which block type to place.
 *
 * Contract: player-api.yaml Inventory schema
 * Data model: data-model.md Section 6
 */
class Inventory {
    /**
     * Map of BlockType to count (unlimited capacity in creative mode)
     */
    private val blocks = mutableMapOf<BlockType, Int>()

    /**
     * Currently selected block for placement
     * Defaults to Grass
     */
    var selectedBlock: BlockType = BlockType.Grass

    /**
     * Add blocks to inventory
     *
     * @param type BlockType to add
     * @param count Number of blocks (default 1)
     * @throws IllegalArgumentException if count <= 0
     */
    fun add(type: BlockType, count: Int = 1) {
        require(count > 0) { "Count must be positive, got $count" }
        blocks[type] = (blocks[type] ?: 0) + count
    }

    /**
     * Remove blocks from inventory
     *
     * @param type BlockType to remove
     * @param count Number of blocks to remove (default 1)
     * @return true if removed successfully, false if insufficient blocks
     */
    fun remove(type: BlockType, count: Int = 1): Boolean {
        val current = blocks[type] ?: 0
        if (current < count) return false

        blocks[type] = current - count
        if (blocks[type] == 0) blocks.remove(type)
        return true
    }

    /**
     * Get count of specific block type
     *
     * @param type BlockType to query
     * @return Number of blocks in inventory (0 if none)
     */
    fun getCount(type: BlockType): Int = blocks[type] ?: 0

    /**
     * Check if inventory has at least one block of type
     *
     * @param type BlockType to check
     * @return true if inventory contains this block type
     */
    fun hasBlock(type: BlockType): Boolean = (blocks[type] ?: 0) > 0

    /**
     * Select block for placement
     *
     * @param type BlockType to select (must not be Air)
     */
    fun selectBlock(type: BlockType) {
        require(type != BlockType.Air) { "Cannot select Air block" }
        selectedBlock = type
    }

    /**
     * Clear all blocks from inventory
     */
    fun clear() {
        blocks.clear()
    }

    /**
     * Get all block types currently in inventory
     *
     * @return List of BlockTypes with count > 0
     */
    fun getBlockTypes(): List<BlockType> = blocks.keys.toList()

    override fun toString(): String {
        val totalBlocks = blocks.values.sum()
        return "Inventory(selected=$selectedBlock, total=$totalBlocks, types=${blocks.size})"
    }
}
