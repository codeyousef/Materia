package io.kreekt.renderer.lighting

import io.kreekt.core.math.Color
import io.kreekt.core.scene.Fog
import io.kreekt.core.scene.Scene
import io.kreekt.light.AmbientLight
import io.kreekt.light.DirectionalLight

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
    var ambientR = 0f
    var ambientG = 0f
    var ambientB = 0f

    var strongestDirectional: DirectionalLight? = null
    var strongestDirectionalIntensity = -Float.MAX_VALUE

    scene.traverse { obj ->
        when (obj) {
            is AmbientLight -> {
                val intensity = obj.intensity
                val color = obj.color
                ambientR += color.r * intensity
                ambientG += color.g * intensity
                ambientB += color.b * intensity
            }

            is DirectionalLight -> {
                if (obj.intensity > strongestDirectionalIntensity) {
                    strongestDirectionalIntensity = obj.intensity
                    strongestDirectional = obj
                }
            }
        }
    }

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
