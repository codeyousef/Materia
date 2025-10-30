package io.materia.examples.voxelcraft.integration

import io.materia.examples.voxelcraft.VoxelWorld
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScenePopulationTest {

    @Test
    fun testSceneIsPopulatedAfterTerrainGeneration() = runTest {
        // Given
        val world = VoxelWorld(seed = 12345L, parentScope = this)

        // When
        world.generateTerrain { _, _ -> }

        // Then
        assertTrue(
            world.scene.children.size > 0,
            "Scene should have children after terrain generation"
        )
    }
}
