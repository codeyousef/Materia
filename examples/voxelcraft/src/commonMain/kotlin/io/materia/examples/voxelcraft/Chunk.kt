package io.materia.examples.voxelcraft

import io.materia.core.math.Box3
import io.materia.core.math.Vector3
import io.materia.core.scene.Mesh
import io.materia.examples.voxelcraft.util.decodeRLE
import io.materia.examples.voxelcraft.util.encodeRLE
import io.materia.geometry.BufferGeometry
import io.materia.material.MeshBasicMaterial

/**
 * Chunk entity representing a 16x16x256 block region
 *
 * Each chunk stores 65,536 blocks (16 × 16 × 256) as a flat ByteArray where each byte
 * is a BlockType ID (0-7). Chunks are the fundamental unit of world storage and rendering.
 *
 * Memory layout:
 * - blocks[x + z * 16 + y * 16 * 16] = block at (x, y, z)
 * - x: 0-15 (east-west)
 * - y: 0-255 (up-down)
 * - z: 0-15 (north-south)
 *
 * Dirty flag optimization:
 * - isDirty = true when blocks are modified
 * - Mesh regenerated only when dirty
 * - Reduces redundant mesh generation

 *
 * Contract: world-api.yaml ChunkData schema
 * Data model: data-model.md Section 3
 */
class Chunk(
    val position: ChunkPosition,
    internal val world: VoxelWorld
) {

    /**
     * Block storage as flat ByteArray (65,536 elements)
     * Each byte is a BlockType.id (0-7)
     *
     * Initialized to all Air blocks (id = 0)
     */
    private val blocks = ByteArray(BLOCKS_PER_CHUNK) { BlockType.Air.id }

    /**
     * Mesh object for rendering (generated from blocks)
     * Null until first regenerateMesh() call
     */
    var mesh: Mesh? = null
        private set

    /**
     * Bounding box for frustum culling (T009)
     * Calculated from chunk world position
     */
    val boundingBox: Box3 = calculateBoundingBox(position)

    internal var suppressDirtyEvents: Boolean = false

    var terrainGenerated: Boolean = false
        internal set

    /**
     * Dirty flag indicating mesh needs regeneration
     * Set to true when blocks are modified via setBlock()
     */
    var isDirty: Boolean = false
        private set

    internal fun markDirty() {
        if (suppressDirtyEvents) {
            isDirty = true
            return
        }
        if (!isDirty) {
            isDirty = true
            world.onChunkDirty(this)
        }
    }

    /**
     * Flag indicating if this chunk has been modified by the player (for delta saves)
     */
    var isModifiedByPlayer: Boolean = false

    /**
     * Get block type at local chunk coordinates
     *
     * @param x Local X coordinate (0-15)
     * @param y Local Y coordinate (0-255)
     * @param z Local Z coordinate (0-15)
     * @return BlockType at the position
     * @throws IllegalArgumentException if coordinates out of range
     */
    fun getBlock(x: Int, y: Int, z: Int): BlockType {
        require(x in 0..15) { "X must be 0-15, got $x" }
        require(y in 0..255) { "Y must be 0-255, got $y" }
        require(z in 0..15) { "Z must be 0-15, got $z" }

        val index = coordinateToIndex(x, y, z)
        return BlockType.fromId(blocks[index])
    }

    /**
     * Set block type at local chunk coordinates
     *
     * Marks chunk as dirty for mesh regeneration.
     *
     * @param x Local X coordinate (0-15)
     * @param y Local Y coordinate (0-255)
     * @param z Local Z coordinate (0-15)
     * @param type BlockType to set
     * @throws IllegalArgumentException if coordinates out of range
     */
    fun setBlock(x: Int, y: Int, z: Int, type: BlockType) {
        require(x in 0..15) { "X must be 0-15, got $x" }
        require(y in 0..255) { "Y must be 0-255, got $y" }
        require(z in 0..15) { "Z must be 0-15, got $z" }

        val index = coordinateToIndex(x, y, z)
        val oldType = blocks[index]

        if (oldType != type.id) {
            blocks[index] = type.id
            markDirty()
        }
    }

    /**
     * Regenerate mesh from block data
     *
     * Called when isDirty = true. Generates optimized mesh using greedy meshing
     * and face culling (implemented in ChunkMeshGenerator).
     */
    fun updateMesh(geometry: BufferGeometry) {
        if (mesh == null) {
            val material = MeshBasicMaterial().apply {
                vertexColors = true
            }
            mesh = Mesh(geometry, material)
            mesh?.position?.set(
                position.toWorldX().toFloat(),
                0f,
                position.toWorldZ().toFloat()
            )
            // T009: Link chunk to mesh for frustum culling
            mesh?.userData?.set("chunk", this)
        } else {
            mesh?.geometry = geometry
        }
        isDirty = false
    }

    /**
     * Serialize chunk data to compressed ByteArray
     *
     * Uses Run-Length Encoding to compress blocks array from 65,536 bytes
     * down to ~2,000-6,000 bytes (90%+ compression).
     *
     * Empty chunks (all Air) compress to just 2 bytes!
     *
     * @return RLE-compressed ByteArray
     */
    fun serialize(): ByteArray {
        return blocks.encodeRLE()
    }

    /**
     * Deserialize chunk data from compressed ByteArray
     *
     * Decodes RLE-compressed data back to 65,536 byte blocks array.
     * Marks chunk as dirty to trigger mesh regeneration.
     *
     * @param data RLE-compressed ByteArray
     * @throws IllegalArgumentException if decompressed size != 65,536 bytes
     */
    fun deserialize(data: ByteArray) {
        val decoded = data.decodeRLE()
        require(decoded.size == BLOCKS_PER_CHUNK) {
            "Invalid chunk data size: expected $BLOCKS_PER_CHUNK, got ${decoded.size}"
        }
        decoded.copyInto(blocks)
        terrainGenerated = true
        markDirty()
    }

    /**
     * Check if block at position is solid (not Air or transparent)
     *
     * Used for collision detection and face culling.
     *
     * @param x Local X coordinate (0-15)
     * @param y Local Y coordinate (0-255)
     * @param z Local Z coordinate (0-15)
     * @return true if block is solid (opaque), false if Air or transparent
     */
    fun isSolid(x: Int, y: Int, z: Int): Boolean {
        if (x !in 0..15 || y !in 0..255 || z !in 0..15) return false
        val block = getBlock(x, y, z)
        return !block.isTransparent
    }

    /**
     * Fill entire chunk with a single block type
     *
     * Optimization for world generation (e.g., fill all Air initially).
     *
     * @param type BlockType to fill with
     */
    fun fill(type: BlockType) {
        blocks.fill(type.id)
        terrainGenerated = true
        markDirty()
    }

    /**
     * Check if chunk is empty (all Air blocks)
     *
     * Used to skip rendering and saving empty chunks.
     *
     * @return true if all blocks are Air
     */
    fun isEmpty(): Boolean {
        return blocks.all { it == BlockType.Air.id }
    }

    /**
     * Get block count by type
     *
     * Useful for analytics and debugging.
     *
     * @param type BlockType to count
     * @return Number of blocks of this type in chunk
     */
    fun countBlocks(type: BlockType): Int {
        return blocks.count { it == type.id }
    }

    private fun coordinateToIndex(x: Int, y: Int, z: Int): Int {
        // Index = x + z * 16 + y * 16 * 16
        return x + z * 16 + y * 256
    }

    /**
     * Clean up chunk resources
     * Note: Mesh geometry and materials are managed by Scene
     */
    fun dispose() {
        mesh = null
        isDirty = false
        terrainGenerated = false
    }

    companion object {
        /**
         * Total blocks per chunk (16 × 16 × 256)
         */
        const val BLOCKS_PER_CHUNK = 65536

        /**
         * Chunk width/depth in blocks
         */
        const val SIZE = 16

        /**
         * Chunk height in blocks
         */
        const val HEIGHT = 256

        /**
         * Calculates bounding box for frustum culling (T009)
         * @param pos Chunk position
         * @return Axis-aligned bounding box in world coordinates
         */
        fun calculateBoundingBox(pos: ChunkPosition): Box3 {
            val worldX = pos.toWorldX().toFloat()
            val worldZ = pos.toWorldZ().toFloat()
            return Box3(
                min = Vector3(worldX, 0f, worldZ),
                max = Vector3(worldX + SIZE, HEIGHT.toFloat(), worldZ + SIZE)
            )
        }
    }

    override fun toString(): String {
        val solidBlocks = blocks.count { BlockType.fromId(it) != BlockType.Air }
        return "Chunk($position, solid=$solidBlocks/$BLOCKS_PER_CHUNK, dirty=$isDirty)"
    }
}
