package io.materia.lighting.shadow

import io.materia.camera.Camera
import io.materia.camera.OrthographicCamera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.core.scene.Scene
import io.materia.lighting.*
import io.materia.renderer.Texture
import kotlin.math.PI

/**
 * Shadow map generation for various light types
 */
internal class ShadowGenerator(
    private val frustumCalculator: FrustumCalculator
) {

    companion object {
        private var idCounter = 0
        private fun nextId(): Int = ++idCounter
    }

    private var shadowBias: Float = 0.0005f

    fun setShadowBias(bias: Float) {
        this.shadowBias = bias
    }

    /**
     * Generate shadow map for directional light
     */
    suspend fun generateDirectionalShadowMap(
        light: DirectionalLight,
        scene: Scene
    ): ShadowResult<io.materia.lighting.ShadowMap> {
        val shadowMapSize = calculateShadowMapSize(light, scene)
        val frustum = frustumCalculator.calculateDirectionalLightFrustum(light, scene)

        val lightCamera = OrthographicCamera(
            left = frustum.left,
            right = frustum.right,
            top = frustum.top,
            bottom = frustum.bottom,
            near = frustum.near,
            far = frustum.far
        )

        val lightPos = light.position
        lightCamera.position.copy(lightPos)
        lightCamera.lookAt(lightPos + light.direction)
        lightCamera.updateMatrixWorld()

        val texture = createShadowTexture(shadowMapSize, shadowMapSize)
        renderDepthToTexture(scene, lightCamera, texture)

        val shadowMap = ShadowMapImpl(
            texture = texture,
            lightSpaceMatrix = lightCamera.projectionMatrix.multiply(lightCamera.matrixWorldInverse),
            near = frustum.near,
            far = frustum.far,
            bias = shadowBias
        )

        return ShadowResult.Success(shadowMap)
    }

    /**
     * Generate shadow map for spot light
     */
    suspend fun generateSpotShadowMap(
        light: SpotLight,
        scene: Scene
    ): ShadowResult<io.materia.lighting.ShadowMap> {
        val shadowMapSize = calculateShadowMapSize(light, scene)

        val lightCamera = PerspectiveCamera(
            fov = light.angle * 2f * 180f / PI.toFloat(),
            aspect = 1f,
            near = 0.1f,
            far = light.distance
        )

        val lightPos = light.position
        lightCamera.position.copy(lightPos)
        lightCamera.lookAt(lightPos + light.direction)
        lightCamera.updateMatrixWorld()

        val texture = createShadowTexture(shadowMapSize, shadowMapSize)
        renderDepthToTexture(scene, lightCamera, texture)

        val shadowMap = ShadowMapImpl(
            texture = texture,
            lightSpaceMatrix = lightCamera.projectionMatrix.multiply(lightCamera.matrixWorldInverse),
            near = 0.1f,
            far = light.distance,
            bias = shadowBias
        )

        return ShadowResult.Success(shadowMap)
    }

    /**
     * Generate cascaded shadow map
     */
    suspend fun generateCascadedShadowMap(
        light: DirectionalLight,
        scene: Scene,
        camera: Camera,
        cascadeCount: Int
    ): ShadowResult<io.materia.lighting.CascadedShadowMap> {
        return try {
            val cascades = frustumCalculator.calculateCascadeSplits(camera, cascadeCount)
            val shadowMaps = mutableListOf<io.materia.lighting.ShadowCascade>()

            for (i in 0 until cascadeCount - 1) {
                val cascade = generateCascade(light, scene, camera, cascades[i], cascades[i + 1])
                shadowMaps.add(cascade)
            }
            // Handle last cascade
            if (cascadeCount > 0 && cascades.size > cascadeCount - 1) {
                val lastCascade =
                    generateCascade(light, scene, camera, cascades[cascadeCount - 1], camera.far)
                shadowMaps.add(lastCascade)
            }

            val cascadedMap = CascadedShadowMapImpl(
                cascades = shadowMaps,
                splitDistances = cascades,
                texture = createCascadedTexture(shadowMaps),
                lightSpaceMatrix = Matrix4.identity(),
                near = camera.near,
                far = camera.far,
                bias = shadowBias
            )

            ShadowResult.Success(cascadedMap)
        } catch (e: Exception) {
            ShadowResult.Error(ShadowMapGenerationFailed("Failed to generate cascaded shadow map: ${e.message}"))
        }
    }

    /**
     * Generate omnidirectional shadow map
     */
    suspend fun generateOmnidirectionalShadowMap(
        light: PointLight,
        scene: Scene
    ): ShadowResult<io.materia.lighting.CubeShadowMap> {
        return try {
            val shadowMapSize = 1024
            val near = 0.1f
            val far = light.distance

            val faceTextures = Array(6) { createShadowTexture(shadowMapSize, shadowMapSize) }

            val cubeDirections = arrayOf(
                Vector3(1f, 0f, 0f), Vector3(-1f, 0f, 0f),
                Vector3(0f, 1f, 0f), Vector3(0f, -1f, 0f),
                Vector3(0f, 0f, 1f), Vector3(0f, 0f, -1f)
            )

            val cubeUps = arrayOf(
                Vector3(0f, -1f, 0f), Vector3(0f, -1f, 0f),
                Vector3(0f, 0f, 1f), Vector3(0f, 0f, -1f),
                Vector3(0f, -1f, 0f), Vector3(0f, -1f, 0f)
            )

            val lightPos = light.position

            for (face in 0 until 6) {
                val camera = PerspectiveCamera(
                    fov = 90f,
                    aspect = 1f,
                    near = near,
                    far = far
                )

                camera.position.copy(lightPos)
                camera.lookAt(lightPos + cubeDirections[face])
                camera.updateMatrixWorld()

                renderDepthToTexture(scene, camera, faceTextures[face])
            }

            val cubeMap = CubeShadowMapImpl(
                textures = faceTextures,
                lightPosition = lightPos,
                near = near,
                far = far
            )

            ShadowResult.Success(cubeMap)
        } catch (e: Exception) {
            ShadowResult.Error(ShadowMapGenerationFailed("Failed to generate omnidirectional shadow map: ${e.message}"))
        }
    }

    /**
     * Generate a single cascade for CSM
     */
    private suspend fun generateCascade(
        light: DirectionalLight,
        scene: Scene,
        camera: Camera,
        nearPlane: Float,
        farPlane: Float
    ): io.materia.lighting.ShadowCascade {
        val frustum = frustumCalculator.calculateCascadeFrustum(camera, nearPlane, farPlane)

        val lightCamera = OrthographicCamera(
            left = frustum.left,
            right = frustum.right,
            top = frustum.top,
            bottom = frustum.bottom,
            near = frustum.near,
            far = frustum.far
        )

        val lightPos = light.position
        lightCamera.position.copy(lightPos)
        lightCamera.lookAt(lightPos + light.direction)
        lightCamera.updateMatrixWorld()

        val shadowMapSize = calculateCascadeShadowMapSize(nearPlane, farPlane)
        val texture = createShadowTexture(shadowMapSize, shadowMapSize)
        renderDepthToTexture(scene, lightCamera, texture)

        return ShadowCascadeImpl(
            texture = texture,
            projectionViewMatrix = lightCamera.projectionMatrix.multiply(lightCamera.matrixWorldInverse),
            splitDistance = farPlane
        )
    }

    private fun calculateShadowMapSize(light: Light, scene: Scene): Int {
        val baseSize = 1024
        val qualityMultiplier = when (light.shadowQuality) {
            ShadowQuality.LOW -> 0.5f
            ShadowQuality.MEDIUM -> 1.0f
            ShadowQuality.HIGH -> 2.0f
            ShadowQuality.ULTRA -> 4.0f
        }

        return (baseSize * qualityMultiplier).toInt().coerceIn(256, 4096)
    }

    private fun calculateCascadeShadowMapSize(near: Float, far: Float): Int {
        val cascadeSize = far - near
        return when {
            cascadeSize < 10f -> 2048
            cascadeSize < 50f -> 1024
            cascadeSize < 200f -> 512
            else -> 256
        }
    }

    private fun createShadowTexture(width: Int, height: Int): Texture {
        return object : Texture {
            override val id: Int = nextId()
            override var needsUpdate: Boolean = true
            override val width: Int = width
            override val height: Int = height

            override fun dispose() {
                // Shadow texture disposal
            }
        }
    }

    private fun createCascadedTexture(cascades: List<ShadowCascade>): Texture {
        return object : Texture {
            override val id: Int = nextId()
            override var needsUpdate: Boolean = true
            override val width: Int = 1024
            override val height: Int = 1024

            override fun dispose() {
                // Cascaded shadow texture disposal
            }
        }
    }

    private suspend fun renderDepthToTexture(scene: Scene, camera: Camera, texture: Texture) {
        // Platform-specific depth rendering implementation
    }
}
