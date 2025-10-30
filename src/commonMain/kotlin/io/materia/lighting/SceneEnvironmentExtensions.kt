package io.materia.lighting

import io.materia.core.scene.Scene
import io.materia.lighting.LightResult
import io.materia.lighting.ibl.IBLConfig
import io.materia.lighting.ibl.IBLEnvironmentMaps
import io.materia.lighting.ibl.IBLResult
import io.materia.renderer.CubeTexture
import io.materia.lighting.IBLProcessorImpl
import io.materia.lighting.HDREnvironment

/**
 * Applies the generated environment maps to a scene so renderers can bind
 * the prefiltered cubemap and BRDF lookup texture.
 */
fun Scene.applyEnvironmentMaps(maps: IBLEnvironmentMaps) {
    environment = maps.prefilter
    environmentBrdfLut = maps.brdfLut
}

/**
 * Generates (or reuses cached) environment convolution results for [environment]
 * and applies them to [scene], wiring both the prefiltered cubemap and BRDF LUT.
 */
fun LightingSystem.applyEnvironmentToScene(
    scene: Scene,
    environment: CubeTexture
): LightResult<Unit> {
    val irradiance = when (val result = generateIrradianceMap(environment)) {
        is LightResult.Success -> result.value
        is LightResult.Error -> return LightResult.Error(result.exception)
    }

    val prefilter = when (val result = generatePrefilterMap(environment)) {
        is LightResult.Success -> result.value
        is LightResult.Error -> return LightResult.Error(result.exception)
    }

    val brdf = when (val result = generateBRDFLUT()) {
        is LightResult.Success -> result.value
        is LightResult.Error -> return LightResult.Error(result.exception)
    }

    val maps = IBLEnvironmentMaps(
        environment = environment,
        irradiance = irradiance,
        prefilter = prefilter,
        brdfLut = brdf
    )
    scene.applyEnvironmentMaps(maps)
    return LightResult.Success(Unit)
}

/**
 * Convenience wrapper that processes an HDR environment, applies the generated
 * maps to [scene], and returns the full [IBLEnvironmentMaps] result.
 */
suspend fun IBLProcessorImpl.processEnvironmentForScene(
    hdr: HDREnvironment,
    config: IBLConfig,
    scene: Scene
): IBLResult<IBLEnvironmentMaps> {
    val result = this.processEnvironment(hdr, config)
    if (result is IBLResult.Success<*>) {
        @Suppress("UNCHECKED_CAST")
        val maps = result.data as IBLEnvironmentMaps
        scene.applyEnvironmentMaps(maps)
    }
    return result
}
