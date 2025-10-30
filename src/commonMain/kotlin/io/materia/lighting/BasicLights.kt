package io.materia.lighting

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.core.scene.Group

/**
 * Basic light implementations for common lighting scenarios
 * T109-T115: Core light implementations following Three.js API
 */

private var nextLightId = 1

/**
 * Ambient light implementation
 * T110 - Provides uniform ambient lighting without direction
 */
class AmbientLightImpl(
    override var color: Color = Color.WHITE,
    override var intensity: Float = 1f
) : AmbientLight {

    override val id: Int = nextLightId++
    override val type: LightType = LightType.AMBIENT
    override var position: Vector3 = Vector3.ZERO.clone()
    override var castShadow: Boolean = false // Ambient lights cannot cast shadows
    override var shadowMapSize: Int = 512
    override var shadowBias: Float = 0f
    override var shadowNormalBias: Float = 0f
    override var shadowRadius: Float = 1f

    companion object {
        /**
         * Create a soft ambient light
         */
        fun soft(
            color: Color = Color(0.4f, 0.4f, 0.4f),
            intensity: Float = 0.6f
        ): AmbientLightImpl =
            AmbientLightImpl(color, intensity)

        /**
         * Create a warm ambient light
         */
        fun warm(intensity: Float = 0.3f): AmbientLightImpl =
            AmbientLightImpl(Color(1f, 0.95f, 0.8f), intensity)

        /**
         * Create a cool ambient light
         */
        fun cool(intensity: Float = 0.3f): AmbientLightImpl =
            AmbientLightImpl(Color(0.8f, 0.9f, 1f), intensity)
    }

    fun clone(): AmbientLightImpl = AmbientLightImpl(color.clone(), intensity)
}

/**
 * Directional light implementation
 * T111 - Provides directional lighting like sunlight
 */
class DirectionalLightImpl(
    override var color: Color = Color.WHITE,
    override var intensity: Float = 1f,
    override val direction: Vector3 = Vector3(0f, -1f, 0f),
    override var position: Vector3 = Vector3(0f, 10f, 0f)
) : DirectionalLight {

    override val id: Int = nextLightId++
    override val type: LightType = LightType.DIRECTIONAL
    override var castShadow: Boolean = true
    override var shadowMapSize: Int = 2048
    override var shadowBias: Float = -0.0005f
    override var shadowNormalBias: Float = 0.02f
    override var shadowRadius: Float = 1f

    // Shadow camera settings for orthographic projection
    override var shadowCameraTop: Float = 50f
    override var shadowCameraBottom: Float = -50f
    override var shadowCameraLeft: Float = -50f
    override var shadowCameraRight: Float = 50f
    override var shadowCameraNear: Float = 0.1f
    override var shadowCameraFar: Float = 200f

    // Target for easier direction control
    var target: Object3D = Group().apply {
        this.position.set(0f, 0f, 0f)
    }

    companion object {
        /**
         * Create a sun-like directional light
         */
        fun sun(
            intensity: Float = 1f,
            direction: Vector3 = Vector3(-0.5f, -0.8f, -0.2f).normalize()
        ): DirectionalLightImpl = DirectionalLightImpl(
            color = Color(1f, 0.95f, 0.8f),
            intensity = intensity,
            direction = direction
        ).apply {
            castShadow = true
            shadowMapSize = 4096
        }

        /**
         * Create a moon-like directional light
         */
        fun moon(
            intensity: Float = 0.1f,
            direction: Vector3 = Vector3(0.3f, -0.7f, 0.4f).normalize()
        ): DirectionalLightImpl = DirectionalLightImpl(
            color = Color(0.6f, 0.7f, 1f),
            intensity = intensity,
            direction = direction
        )
    }

    /**
     * Update direction based on target
     */
    fun updateDirection() {
        target?.let { t ->
            direction.copy(t.position).subtract(position).normalize()
        }
    }

    fun clone(): DirectionalLightImpl = DirectionalLightImpl(
        color.clone(),
        intensity,
        direction.clone(),
        position.clone()
    ).apply {
        castShadow = this@DirectionalLightImpl.castShadow
        shadowMapSize = this@DirectionalLightImpl.shadowMapSize
        shadowBias = this@DirectionalLightImpl.shadowBias
        shadowNormalBias = this@DirectionalLightImpl.shadowNormalBias
        shadowRadius = this@DirectionalLightImpl.shadowRadius
        shadowCameraTop = this@DirectionalLightImpl.shadowCameraTop
        shadowCameraBottom = this@DirectionalLightImpl.shadowCameraBottom
        shadowCameraLeft = this@DirectionalLightImpl.shadowCameraLeft
        shadowCameraRight = this@DirectionalLightImpl.shadowCameraRight
        shadowCameraNear = this@DirectionalLightImpl.shadowCameraNear
        shadowCameraFar = this@DirectionalLightImpl.shadowCameraFar
    }
}

/**
 * Point light implementation
 * T112 - Provides omnidirectional point lighting
 */
class PointLightImpl(
    override var color: Color = Color.WHITE,
    override var intensity: Float = 1f,
    override var position: Vector3 = Vector3.ZERO.clone(),
    override var decay: Float = 2f,
    override var distance: Float = 0f // 0 = infinite range
) : PointLight {

    override val id: Int = nextLightId++
    override val type: LightType = LightType.POINT
    override var castShadow: Boolean = false // Point light shadows are expensive
    override var shadowMapSize: Int = 1024
    override var shadowBias: Float = -0.0005f
    override var shadowNormalBias: Float = 0.02f
    override var shadowRadius: Float = 1f

    override val shadowCameraNear: Float = 0.1f
    override val shadowCameraFar: Float = 100f

    companion object {
        /**
         * Create a bright point light (like a light bulb)
         */
        fun bulb(
            position: Vector3,
            color: Color = Color(1f, 0.9f, 0.7f),
            intensity: Float = 100f,
            distance: Float = 20f
        ): PointLightImpl = PointLightImpl(
            color = color,
            intensity = intensity,
            position = position.clone(),
            decay = 2f,
            distance = distance
        )

        /**
         * Create a candle-like point light
         */
        fun candle(
            position: Vector3,
            intensity: Float = 10f,
            distance: Float = 5f
        ): PointLightImpl = PointLightImpl(
            color = Color(1f, 0.6f, 0.2f),
            intensity = intensity,
            position = position.clone(),
            decay = 2f,
            distance = distance
        )

        /**
         * Create a firefly-like point light
         */
        fun firefly(
            position: Vector3,
            intensity: Float = 2f
        ): PointLightImpl = PointLightImpl(
            color = Color(0.3f, 1f, 0.3f),
            intensity = intensity,
            position = position.clone(),
            decay = 1f,
            distance = 2f
        )
    }

    /**
     * Calculate attenuation at a distance
     */
    fun getAttenuation(distanceToLight: Float): Float {
        return if (distance == 0f || distanceToLight <= distance) {
            1f / (1f + decay * distanceToLight * distanceToLight)
        } else {
            0f
        }
    }

    fun clone(): PointLightImpl = PointLightImpl(
        color.clone(),
        intensity,
        position.clone(),
        decay,
        distance
    ).apply {
        castShadow = this@PointLightImpl.castShadow
        shadowMapSize = this@PointLightImpl.shadowMapSize
        shadowBias = this@PointLightImpl.shadowBias
        shadowNormalBias = this@PointLightImpl.shadowNormalBias
        shadowRadius = this@PointLightImpl.shadowRadius
    }
}

/**
 * Spot light implementation
 * T113 - Provides focused cone lighting
 */
class SpotLightImpl(
    override var color: Color = Color.WHITE,
    override var intensity: Float = 1f,
    override var position: Vector3 = Vector3.ZERO.clone(),
    override val direction: Vector3 = Vector3(0f, -1f, 0f),
    override var angle: Float = kotlin.math.PI.toFloat() / 6f, // 30 degrees
    override var penumbra: Float = 0f,
    override var decay: Float = 2f,
    override var distance: Float = 0f
) : SpotLight {

    override val id: Int = nextLightId++
    override val type: LightType = LightType.SPOT
    override var castShadow: Boolean = true
    override var shadowMapSize: Int = 1024
    override var shadowBias: Float = -0.0005f
    override var shadowNormalBias: Float = 0.02f
    override var shadowRadius: Float = 1f

    override val shadowCameraNear: Float = 0.1f
    override val shadowCameraFar: Float = 100f

    // Target for easier direction control
    var target: Object3D = Group().apply {
        this.position.set(0f, 0f, 0f)
    }

    companion object {
        /**
         * Create a spotlight for stage lighting
         */
        fun stage(
            position: Vector3,
            target: Vector3,
            color: Color = Color.WHITE,
            intensity: Float = 1000f,
            angle: Float = kotlin.math.PI.toFloat() / 4f, // 45 degrees
            distance: Float = 50f
        ): SpotLightImpl {
            val direction = target.clone().subtract(position).normalize()
            return SpotLightImpl(
                color = color,
                intensity = intensity,
                position = position.clone(),
                direction = direction,
                angle = angle,
                penumbra = 0.1f,
                distance = distance
            ).apply {
                castShadow = true
                shadowMapSize = 2048
            }
        }

        /**
         * Create a flashlight-style spotlight
         */
        fun flashlight(
            position: Vector3,
            direction: Vector3,
            intensity: Float = 500f,
            distance: Float = 30f
        ): SpotLightImpl = SpotLightImpl(
            color = Color(1f, 0.95f, 0.9f),
            intensity = intensity,
            position = position.clone(),
            direction = direction.clone().normalize(),
            angle = kotlin.math.PI.toFloat() / 8f, // 22.5 degrees
            penumbra = 0.2f,
            distance = distance
        )

        /**
         * Create a car headlight
         */
        fun headlight(
            position: Vector3,
            direction: Vector3,
            intensity: Float = 2000f
        ): SpotLightImpl = SpotLightImpl(
            color = Color(1f, 0.98f, 0.95f),
            intensity = intensity,
            position = position.clone(),
            direction = direction.clone().normalize(),
            angle = kotlin.math.PI.toFloat() / 3f, // 60 degrees
            penumbra = 0.3f,
            distance = 100f
        ).apply {
            castShadow = true
        }
    }

    /**
     * Update direction based on target
     */
    fun updateDirection() {
        target?.let { t ->
            direction.copy(t.position).subtract(position).normalize()
        }
    }

    /**
     * Calculate spot light attenuation including cone falloff
     */
    fun getAttenuation(directionToLight: Vector3, distanceToLight: Float): Float {
        // Distance attenuation
        val distanceAttenuation = if (distance == 0f || distanceToLight <= distance) {
            1f / (1f + decay * distanceToLight * distanceToLight)
        } else {
            0f
        }

        // Cone attenuation
        val cosAngle = direction.dot(directionToLight.clone().negate())
        val cosOuterAngle = kotlin.math.cos(angle)
        val cosInnerAngle = kotlin.math.cos(angle * (1f - penumbra))

        val coneAttenuation = when {
            cosAngle < cosOuterAngle -> 0f
            cosAngle > cosInnerAngle -> 1f
            else -> {
                val t = (cosAngle - cosOuterAngle) / (cosInnerAngle - cosOuterAngle)
                t * t // Smooth falloff
            }
        }

        return distanceAttenuation * coneAttenuation
    }

    fun clone(): SpotLightImpl = SpotLightImpl(
        color.clone(),
        intensity,
        position.clone(),
        direction.clone(),
        angle,
        penumbra,
        decay,
        distance
    ).apply {
        castShadow = this@SpotLightImpl.castShadow
        shadowMapSize = this@SpotLightImpl.shadowMapSize
        shadowBias = this@SpotLightImpl.shadowBias
        shadowNormalBias = this@SpotLightImpl.shadowNormalBias
        shadowRadius = this@SpotLightImpl.shadowRadius
    }
}

/**
 * Hemisphere light implementation
 * T114 - Provides gradient lighting from sky to ground
 */
class HemisphereLightImpl(
    override var color: Color = Color(0.8f, 0.9f, 1f), // Sky color
    override var groundColor: Color = Color(0.4f, 0.3f, 0.2f), // Ground color
    override var intensity: Float = 1f,
    override var position: Vector3 = Vector3(0f, 50f, 0f)
) : HemisphereLight {

    override val id: Int = nextLightId++
    override val type: LightType = LightType.HEMISPHERE
    override var castShadow: Boolean = false // Hemisphere lights don't cast shadows
    override var shadowMapSize: Int = 512
    override var shadowBias: Float = 0f
    override var shadowNormalBias: Float = 0f
    override var shadowRadius: Float = 1f

    companion object {
        /**
         * Create outdoor hemisphere lighting
         */
        fun outdoor(
            skyColor: Color = Color(0.5f, 0.7f, 1f),
            groundColor: Color = Color(0.3f, 0.25f, 0.2f),
            intensity: Float = 0.6f
        ): HemisphereLightImpl = HemisphereLightImpl(
            color = skyColor,
            groundColor = groundColor,
            intensity = intensity
        )

        /**
         * Create indoor hemisphere lighting
         */
        fun indoor(
            ceilingColor: Color = Color(0.9f, 0.9f, 0.85f),
            floorColor: Color = Color(0.2f, 0.2f, 0.25f),
            intensity: Float = 0.4f
        ): HemisphereLightImpl = HemisphereLightImpl(
            color = ceilingColor,
            groundColor = floorColor,
            intensity = intensity
        )

        /**
         * Create sunset hemisphere lighting
         */
        fun sunset(
            intensity: Float = 0.8f
        ): HemisphereLightImpl = HemisphereLightImpl(
            color = Color(1f, 0.6f, 0.3f),
            groundColor = Color(0.2f, 0.1f, 0.05f),
            intensity = intensity
        )
    }

    /**
     * Get the interpolated color based on surface normal
     */
    fun getColorForNormal(normal: Vector3): Color {
        val factor = (normal.y + 1f) * 0.5f // Convert from [-1,1] to [0,1]
        return groundColor.clone().lerp(color, factor).multiplyScalar(intensity)
    }

    fun clone(): HemisphereLightImpl = HemisphereLightImpl(
        color.clone(),
        groundColor.clone(),
        intensity,
        position.clone()
    )
}

/**
 * Rect area light implementation
 * T115 - Provides rectangular area lighting
 */
class RectAreaLightImpl(
    override var color: Color = Color.WHITE,
    override var intensity: Float = 1f,
    override var position: Vector3 = Vector3.ZERO.clone(),
    override var width: Float = 10f,
    override var height: Float = 10f
) : RectAreaLight {

    override val id: Int = nextLightId++
    override val type: LightType = LightType.RECTAREA
    override var castShadow: Boolean =
        false // Rect area lights typically don't cast shadows in real-time
    override var shadowMapSize: Int = 512
    override var shadowBias: Float = 0f
    override var shadowNormalBias: Float = 0f
    override var shadowRadius: Float = 1f

    // Orientation (normal direction)
    var normal: Vector3 = Vector3(0f, 0f, -1f)

    companion object {
        /**
         * Create a panel light (like LED panel)
         */
        fun panel(
            position: Vector3,
            width: Float = 2f,
            height: Float = 1f,
            color: Color = Color(1f, 0.95f, 0.9f),
            intensity: Float = 50f
        ): RectAreaLightImpl = RectAreaLightImpl(
            color = color,
            intensity = intensity,
            position = position.clone(),
            width = width,
            height = height
        )

        /**
         * Create a window light
         */
        fun window(
            position: Vector3,
            width: Float = 3f,
            height: Float = 2f,
            intensity: Float = 20f
        ): RectAreaLightImpl = RectAreaLightImpl(
            color = Color(0.9f, 0.95f, 1f),
            intensity = intensity,
            position = position.clone(),
            width = width,
            height = height
        )

        /**
         * Create a softbox light (photography)
         */
        fun softbox(
            position: Vector3,
            size: Float = 1f,
            intensity: Float = 100f
        ): RectAreaLightImpl = RectAreaLightImpl(
            color = Color.WHITE,
            intensity = intensity,
            position = position.clone(),
            width = size,
            height = size
        )
    }

    /**
     * Get the area of the light (for intensity calculations)
     */
    fun getArea(): Float = width * height

    /**
     * Get intensity per unit area
     */
    fun getIntensityPerArea(): Float = intensity / getArea()

    fun clone(): RectAreaLightImpl = RectAreaLightImpl(
        color.clone(),
        intensity,
        position.clone(),
        width,
        height
    ).apply {
        normal = this@RectAreaLightImpl.normal.clone()
    }
}