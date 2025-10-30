package io.materia.examples.voxelcraft

/**
 * BlockType sealed class representing all block types in VoxelCraft
 *
 * Each block type has:
 * - id: Unique byte identifier (0-7) for efficient storage
 * - name: Human-readable name
 * - isTransparent: Whether block allows light/visibility through it
 *
 * Transparent blocks (Air, Leaves, Water) don't render adjacent faces when
 * placed next to each other, improving rendering performance.
 *
 * Contract: world-api.yaml BlockType enum
 * Data model: data-model.md Section 1
 */
sealed class BlockType(val id: Byte, val name: String, val isTransparent: Boolean) {

    /**
     * Air - Empty space, fully transparent
     * Used as default block type and to represent broken blocks
     */
    object Air : BlockType(0, "Air", true)

    /**
     * Grass - Surface terrain block with grass texture
     * Most common block at terrain surface (Y ~64-124)
     */
    object Grass : BlockType(1, "Grass", false)

    /**
     * Dirt - Subsurface terrain block
     * Found below grass layer and in underground areas
     */
    object Dirt : BlockType(2, "Dirt", false)

    /**
     * Stone - Primary underground block
     * Most common block below Y=64, used in caves and bedrock
     */
    object Stone : BlockType(3, "Stone", false)

    /**
     * Wood - Tree trunk block
     * Generated as part of trees on grass surface
     */
    object Wood : BlockType(4, "Wood", false)

    /**
     * Leaves - Tree foliage, semi-transparent
     * Generated as tree canopy, allows some visibility through
     */
    object Leaves : BlockType(5, "Leaves", true)

    /**
     * Sand - Beach/desert terrain block
     * Found near water level and in flat biomes
     */
    object Sand : BlockType(6, "Sand", false)

    /**
     * Water - Liquid block, fully transparent
     * Found at sea level (~Y=62), flows and allows visibility
     */
    object Water : BlockType(7, "Water", true)

    companion object {
        /**
         * Convert block ID byte to BlockType instance
         *
         * Used for deserialization from chunk storage (ByteArray).
         * Returns Air for invalid IDs (safe fallback).
         *
         * @param id Block type ID (0-7)
         * @return Corresponding BlockType, or Air if invalid
         */
        fun fromId(id: Byte): BlockType = when (id) {
            0.toByte() -> Air
            1.toByte() -> Grass
            2.toByte() -> Dirt
            3.toByte() -> Stone
            4.toByte() -> Wood
            5.toByte() -> Leaves
            6.toByte() -> Sand
            7.toByte() -> Water
            else -> Air // Safe fallback for corrupted data
        }

        /**
         * Get all placeable block types (excluding Air)
         *
         * Used for inventory UI and block selection
         */
        fun placeableBlocks(): List<BlockType> = listOf(
            Grass, Dirt, Stone, Wood, Leaves, Sand, Water
        )

        /**
         * Total number of block types (including Air)
         */
        const val BLOCK_TYPE_COUNT = 8
    }

    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = other is BlockType && other.id == this.id
    override fun hashCode(): Int = id.toInt()
}
