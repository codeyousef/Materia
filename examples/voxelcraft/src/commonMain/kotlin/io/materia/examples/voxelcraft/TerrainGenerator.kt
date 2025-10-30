package io.materia.examples.voxelcraft

import kotlinx.coroutines.yield

/**
 * Terrain generator using Simplex noise
 *
 * Generates Minecraft-style terrain with:
 * - 2D noise for height maps (hills, valleys)
 * - 3D noise for caves
 * - Simple tree placement
 *
 * Research: research.md "Terrain Generation Algorithm"
 */
class TerrainGenerator(val seed: Long) {
    private val noise2D = SimplexNoise(seed)
    private val noise3D = SimplexNoise(seed + 1)

    suspend fun generate(chunk: Chunk) {
        val worldX = chunk.position.toWorldX()
        val worldZ = chunk.position.toWorldZ()

        var operations = 0
        for (localX in 0..15) {
            for (localZ in 0..15) {
                val x = worldX + localX
                val z = worldZ + localZ

                // 2D noise for terrain height (range 64-124)
                val heightNoise = noise2D.eval(x * 0.01, z * 0.01)
                val terrainHeight = ((heightNoise + 1.0) * 30.0 + 64.0).toInt()

                for (y in 0..255) {
                    val blockType = when {
                        y > terrainHeight -> BlockType.Air
                        y == terrainHeight && y > 62 -> BlockType.Grass
                        y >= terrainHeight - 3 -> BlockType.Dirt
                        else -> {
                            // 3D noise for caves
                            val caveNoise = noise3D.eval(x * 0.05, y * 0.05, z * 0.05)
                            if (caveNoise > 0.6 && y < terrainHeight - 5) {
                                BlockType.Air
                            } else {
                                BlockType.Stone
                            }
                        }
                    }
                    chunk.setBlock(localX, y, localZ, blockType)
                    operations++
                    if (operations % 8192 == 0) {
                        yield()
                    }
                }
            }
        }
    }
}
