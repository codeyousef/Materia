package io.kreekt.examples.voxelcraft

import io.kreekt.geometry.BufferAttribute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class ChunkMeshGeneratorTest {

    @Test
    fun testSingleBlockRendersAllSixFaces() = runTest {
        // Create a world and chunk
        val world = VoxelWorld(seed = 12345L, parentScope = this)
        val chunkPos = ChunkPosition(0, 0)
        val chunk = Chunk(chunkPos, world)
        
        // Fill with air
        chunk.fill(BlockType.Air)
        
        // Place a single stone block in the middle
        chunk.setBlock(8, 64, 8, BlockType.Stone)
        
        // Generate mesh
        val geometry = ChunkMeshGenerator.generate(chunk)
        
        // Get attributes
        val positionAttr = geometry.getAttribute("position") as BufferAttribute
        val positions = positionAttr.array
        val indexAttr = geometry.getIndex()
        assertNotNull(indexAttr, "Index buffer should not be null")
        val indices = indexAttr.array
        
        // A single block surrounded by air should render all 6 faces
        // 6 faces * 4 vertices * 3 coords = 72 floats
        assertEquals(72, positions.size, "Single block should have 72 position floats (6 faces * 4 vertices * 3 coords)")
        
        // 6 faces * 2 triangles * 3 indices = 36 indices
        assertEquals(36, indices.size, "Single block should have 36 indices (6 faces * 2 triangles * 3 indices)")
    }

    @Test
    fun testChunkEdgeBlockRendersExternalFaces() = runTest {
        val world = VoxelWorld(seed = 12345L, parentScope = this)
        val chunkPos = ChunkPosition(0, 0)
        val chunk = Chunk(chunkPos, world)
        
        chunk.fill(BlockType.Air)
        
        // Place a stone block at the EAST edge (x=15)
        chunk.setBlock(15, 64, 8, BlockType.Stone)
        
        // Generate mesh
        val geometry = ChunkMeshGenerator.generate(chunk)
        
        val positionAttr = geometry.getAttribute("position") as BufferAttribute
        val positions = positionAttr.array
        val indexAttr = geometry.getIndex()
        assertNotNull(indexAttr)
        val indices = indexAttr.array
        
        // Block at edge should render all 6 faces (no neighbor chunk loaded)
        // 6 faces * 4 vertices * 3 coords = 72 floats
        assertEquals(72, positions.size, "Edge block should have 72 position floats (all 6 faces)")
        assertEquals(36, indices.size, "Edge block should have 36 indices (6 faces)")
    }

    @Test
    fun testTwoAdjacentBlocksShareFace() = runTest {
        val world = VoxelWorld(seed = 12345L, parentScope = this)
        val chunkPos = ChunkPosition(0, 0)
        val chunk = Chunk(chunkPos, world)
        
        chunk.fill(BlockType.Air)
        
        // Place two stone blocks next to each other (sharing EAST/WEST face)
        chunk.setBlock(8, 64, 8, BlockType.Stone)
        chunk.setBlock(9, 64, 8, BlockType.Stone)
        
        // Generate mesh
        val geometry = ChunkMeshGenerator.generate(chunk)
        
        val positionAttr = geometry.getAttribute("position") as BufferAttribute
        val positions = positionAttr.array
        val indexAttr = geometry.getIndex()
        assertNotNull(indexAttr)
        val indices = indexAttr.array
        
        // Two blocks share one face, so:
        // 2 blocks * 6 faces - 2 shared faces = 10 faces
        // 10 faces * 4 vertices * 3 coords = 120 floats
        assertEquals(120, positions.size, "Two adjacent blocks should have 120 position floats (10 faces)")
        assertEquals(60, indices.size, "Two adjacent blocks should have 60 indices (10 faces)")
    }
}
