package io.kreekt.lighting

import io.kreekt.core.scene.Scene
import io.kreekt.lighting.LightResult
import io.kreekt.lighting.ibl.IBLConfig
import io.kreekt.lighting.ibl.IBLEnvironmentMaps
import io.kreekt.lighting.ibl.IBLResult
import io.kreekt.renderer.CubeTexture
import io.kreekt.lighting.IBLProcessorImpl
import io.kreekt.lighting.HDREnvironment

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
