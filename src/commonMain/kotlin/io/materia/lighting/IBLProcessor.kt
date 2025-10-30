/**
 * Image-Based Lighting Processor - Facade
 * Delegates to modular implementation
 */
package io.materia.lighting

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.lighting.ibl.*
import io.materia.renderer.CubeTexture
import io.materia.renderer.Texture

// Re-export types from ibl package
typealias IBLResult<T> = io.materia.lighting.ibl.IBLResult<T>
typealias HDREnvironment = io.materia.lighting.ibl.HDREnvironment
typealias IBLConfig = io.materia.lighting.ibl.IBLConfig
typealias IBLEnvironmentMaps = io.materia.lighting.ibl.IBLEnvironmentMaps
typealias SphericalHarmonics = io.materia.lighting.ibl.SphericalHarmonics

/**
 * IBL Processor interface
 */
interface IBLProcessor {
    suspend fun generateEquirectangularMap(cubeMap: CubeTexture, width: Int, height: Int): Texture
    suspend fun generateIrradianceMap(environmentMap: Texture, size: Int): CubeTexture
    suspend fun generatePrefilterMap(
        environmentMap: Texture,
        size: Int,
        roughnessLevels: Int
    ): CubeTexture

    fun generateBRDFLUT(size: Int): Texture
}

/**
 * Create default IBL processor instance
 */
fun createIBLProcessor(): IBLProcessor = IBLProcessorImpl()
