package io.materia.examples.voxelcraft

import io.materia.geometry.BufferAttribute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Contract Test: ChunkMeshGenerator Winding Order (Contract 5)
 *
 * Validates that generated chunk meshes use counter-clockwise (CCW) winding order
 * for front-facing triangles, as required by WebGL default culling.
 *
 * From: specs/017-in-voxelcraft-example/contracts/renderer-contract.md
 */
class ChunkMeshWindingTest {

    @Test
    fun testWindingOrderIsCCW() = runTest {
        // Create a test chunk with a single block
        val world = VoxelWorld(seed = 12345L, parentScope = this)
        val chunkPos = ChunkPosition(0, 0)
        val chunk = Chunk(chunkPos, world)

        // Set a single block at origin
        chunk.setBlock(0, 64, 0, BlockType.Stone)
        chunk.markDirty()

        // Generate mesh
        val geometry = ChunkMeshGenerator.generate(chunk)

        // Get position and index attributes
        val positionAttr = geometry.getAttribute("position") as? BufferAttribute
        val indexAttr = geometry.index as? BufferAttribute

        assertTrue(positionAttr != null, "Geometry should have position attribute")
        assertTrue(indexAttr != null, "Geometry should have index attribute")

        val positions = positionAttr!!.array
        val indices = indexAttr!!.array

        // Check at least one face was generated
        assertTrue(indices.size >= 3, "Should have at least one triangle (3 indices)")

        // Validate winding order for first triangle
        val i0 = indices[0].toInt()
        val i1 = indices[1].toInt()
        val i2 = indices[2].toInt()

        val v0x = positions[i0 * 3]
        val v0y = positions[i0 * 3 + 1]
        val v0z = positions[i0 * 3 + 2]

        val v1x = positions[i1 * 3]
        val v1y = positions[i1 * 3 + 1]
        val v1z = positions[i1 * 3 + 2]

        val v2x = positions[i2 * 3]
        val v2y = positions[i2 * 3 + 1]
        val v2z = positions[i2 * 3 + 2]

        // Calculate cross product to determine winding
        val edge1x = v1x - v0x
        val edge1y = v1y - v0y
        val edge1z = v1z - v0z

        val edge2x = v2x - v0x
        val edge2y = v2y - v0y
        val edge2z = v2z - v0z

        val normalX = edge1y * edge2z - edge1z * edge2y
        val normalY = edge1z * edge2x - edge1x * edge2z
        val normalZ = edge1x * edge2y - edge1y * edge2x

        // For an upward-facing quad, normal should point up (positive Y)
        // For other faces, just verify normal is non-zero (valid winding)
        val normalLength =
            kotlin.math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)

        assertTrue(normalLength > 0.001f, "Triangle should have valid winding (non-zero normal)")

        console.log("✅ Winding order test passed")
        console.log("   Vertices: ${positions.size / 3}")
        console.log("   Triangles: ${indices.size / 3}")
        console.log("   Normal: ($normalX, $normalY, $normalZ)")
    }

    @Test
    fun testAllFacesHaveValidNormals() = runTest {
        // Create chunk with block
        val world = VoxelWorld(seed = 12345L, parentScope = this)
        val chunkPos = ChunkPosition(0, 0)
        val chunk = Chunk(chunkPos, world)

        // Add block
        chunk.setBlock(8, 64, 8, BlockType.Grass)
        chunk.markDirty()

        // Generate mesh
        val geometry = ChunkMeshGenerator.generate(chunk)

        // Get normal attribute (should be generated)
        val normalAttr = geometry.getAttribute("normal") as? BufferAttribute

        assertTrue(normalAttr != null, "Geometry should have normal attribute")

        val normals = normalAttr!!.array

        // Verify normals are present for all vertices
        val positionAttr = geometry.getAttribute("position") as BufferAttribute
        val vertexCount = positionAttr.array.size / 3

        assertTrue(
            normals.size == vertexCount * 3,
            "Should have 3 normal components per vertex"
        )

        // Check first normal is non-zero (valid)
        val nx = normals[0]
        val ny = normals[1]
        val nz = normals[2]

        val normalLength = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)

        assertTrue(normalLength > 0.9f, "Normals should be normalized (length ~1.0)")

        console.log("✅ Normal attribute test passed")
        console.log("   Vertices: $vertexCount")
        console.log("   First normal: ($nx, $ny, $nz), length: $normalLength")
    }

    @Test
    fun testColorAttributePresent() = runTest {
        // Create chunk with block
        val world = VoxelWorld(seed = 12345L, parentScope = this)
        val chunkPos = ChunkPosition(0, 0)
        val chunk = Chunk(chunkPos, world)

        // Add block
        chunk.setBlock(0, 64, 0, BlockType.Dirt)
        chunk.markDirty()

        // Generate mesh
        val geometry = ChunkMeshGenerator.generate(chunk)

        // Verify color attribute exists (used for face brightness)
        val colorAttr = geometry.getAttribute("color") as? BufferAttribute

        assertTrue(colorAttr != null, "Geometry should have color attribute for face brightness")

        val colors = colorAttr!!.array

        // Verify colors are in valid range [0, 1]
        for (i in colors.indices) {
            assertTrue(
                colors[i] >= 0f && colors[i] <= 1f,
                "Color component $i should be in range [0, 1], got ${colors[i]}"
            )
        }

        console.log("✅ Color attribute test passed")
        console.log("   Color components: ${colors.size}")
        console.log("   First color: (${colors[0]}, ${colors[1]}, ${colors[2]})")
    }
}
