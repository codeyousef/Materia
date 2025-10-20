package io.kreekt.examples.backend

import io.kreekt.core.scene.Scene
import io.kreekt.lighting.DefaultLightingSystem
import io.kreekt.lighting.applyEnvironmentToScene
import io.kreekt.texture.CubeTexture

/**
 * Minimal helper used by integration examples to attach an image-based lighting
 * setup to a scene. It demonstrates the new `applyEnvironmentToScene` extension
 * which generates irradiance/prefilter/BRDF maps and wires them to the scene.
 */
object EnvironmentSceneDemo {
    fun configure(scene: Scene) {
        val skyEnvironment = CubeTexture.gradientSky(size = 128)
        val lightingSystem = DefaultLightingSystem()

        lightingSystem.applyEnvironmentToScene(scene, skyEnvironment)

        println(
            "Environment configured: cubeSize=${skyEnvironment.size}, " +
                "hasBrdfLut=${scene.environmentBrdfLut != null}"
        )
    }
}
