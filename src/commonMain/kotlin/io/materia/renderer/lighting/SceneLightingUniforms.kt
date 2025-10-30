package io.materia.renderer.lighting

import io.materia.core.math.Color
import io.materia.core.scene.Fog
import io.materia.core.scene.Scene
import io.materia.light.AmbientLight
import io.materia.light.DirectionalLight
import io.materia.lighting.Light

data class SceneLightingUniforms(
    val ambientColor: FloatArray,
    val fogColor: FloatArray,
    val fogParams: FloatArray,
    val mainLightDirection: FloatArray,
    val mainLightColor: FloatArray
) {
    companion object {
        val Default = SceneLightingUniforms(
            ambientColor = floatArrayOf(0f, 0f, 0f, 1f),
            fogColor = floatArrayOf(0f, 0f, 0f, 0f),
            fogParams = floatArrayOf(0f, 0f, 0f, 0f),
            mainLightDirection = floatArrayOf(0f, -1f, 0f, 0f),
            mainLightColor = floatArrayOf(0f, 0f, 0f, 0f)
        )
    }
}

fun collectSceneLightingUniforms(scene: Scene): SceneLightingUniforms {
    val lights = collectLights(scene)

    val ambientLights = lights.filterIsInstance<AmbientLight>()
    val ambientR = ambientLights.sumOf { (it.color.r * it.intensity).toDouble() }.toFloat()
    val ambientG = ambientLights.sumOf { (it.color.g * it.intensity).toDouble() }.toFloat()
    val ambientB = ambientLights.sumOf { (it.color.b * it.intensity).toDouble() }.toFloat()

    val strongestDirectional = lights
        .filterIsInstance<DirectionalLight>()
        .maxByOrNull { it.intensity }

    val ambientColor = floatArrayOf(
        ambientR,
        ambientG,
        ambientB,
        1f
    )

    val fog = scene.fog
    val fogColor: Color? = when (fog) {
        is Fog.Linear -> fog.color
        is Fog.Exponential -> fog.color
        else -> null
    }

    val fogColorArray = if (fogColor != null) {
        floatArrayOf(fogColor.r, fogColor.g, fogColor.b, 1f)
    } else {
        floatArrayOf(0f, 0f, 0f, 0f)
    }

    val fogParams = when (fog) {
        is Fog.Linear -> floatArrayOf(fog.near, fog.far, 0f, 1f)
        is Fog.Exponential -> floatArrayOf(fog.density, 0f, 0f, 2f)
        else -> floatArrayOf(0f, 0f, 0f, 0f)
    }

    val mainLight = strongestDirectional
    val mainLightDirection = if (mainLight != null) {
        val dir = mainLight.direction.clone().normalize()
        floatArrayOf(dir.x, dir.y, dir.z, 0f)
    } else {
        floatArrayOf(0f, -1f, 0f, 0f)
    }

    val mainLightColor = if (mainLight != null) {
        val color = mainLight.color
        val intensity = mainLight.intensity
        floatArrayOf(color.r * intensity, color.g * intensity, color.b * intensity, 0f)
    } else {
        floatArrayOf(0f, 0f, 0f, 0f)
    }

    return SceneLightingUniforms(
        ambientColor = ambientColor,
        fogColor = fogColorArray,
        fogParams = fogParams,
        mainLightDirection = mainLightDirection,
        mainLightColor = mainLightColor
    )
}

private fun collectLights(scene: Scene): List<Light> {
    val registry = scene.userData["lights"]
    return when (registry) {
        is Light -> listOf(registry)
        is Collection<*> -> registry.filterIsInstance<Light>()
        is Array<*> -> registry.filterIsInstance<Light>()
        else -> emptyList()
    }
}
