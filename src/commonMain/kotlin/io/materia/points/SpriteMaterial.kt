package io.materia.points

import io.materia.core.math.Color
import io.materia.material.Material
import io.materia.material.Side
import io.materia.texture.Texture
import kotlin.math.PI

/**
 * SpriteMaterial - Material for sprites (billboards)
 * T094 - Sprite color, texture, rotation, alpha
 *
 * Controls the appearance of sprites which always face the camera.
 */
class SpriteMaterial(
    /**
     * Base color of the sprite
     */
    var color: Color = Color(0xffffff),

    /**
     * Texture map for the sprite
     */
    var map: Texture? = null,

    /**
     * Alpha map for transparency
     */
    var alphaMap: Texture? = null,

    /**
     * Rotation of the sprite in radians
     */
    var rotation: Float = 0f,

    /**
     * Whether sprite size is in world units (true) or pixels (false)
     */
    var sizeAttenuation: Boolean = true,

    /**
     * Opacity of the sprite
     */
    override var opacity: Float = 1f,

    /**
     * Whether the sprite is transparent
     */
    override var transparent: Boolean = false,

    /**
     * Fog influence on the sprite
     */
    var fog: Boolean = true
) : Material() {

    override val type = "SpriteMaterial"

    init {
        // Sprites typically use alpha blending
        if (transparent) {
            this.depthWrite = false
        }

        // Sprites are usually double-sided
        this.side = Side.DoubleSide
    }

    /**
     * Clone this material
     */
    override fun clone(): SpriteMaterial {
        return SpriteMaterial(
            color = color.clone(),
            map = map,
            alphaMap = alphaMap,
            rotation = rotation,
            sizeAttenuation = sizeAttenuation,
            opacity = opacity,
            transparent = transparent,
            fog = fog
        ).also {
            it.copy(this)
        }
    }

    /**
     * Copy from another sprite material
     */
    fun copy(source: SpriteMaterial): SpriteMaterial {
        super.copy(source)

        this.color.copy(source.color)
        this.map = source.map
        this.alphaMap = source.alphaMap
        this.rotation = source.rotation
        this.sizeAttenuation = source.sizeAttenuation
        this.opacity = source.opacity
        this.transparent = source.transparent
        this.fog = source.fog

        return this
    }

    /**
     * Set sprite color
     */
    fun setColor(color: Color): SpriteMaterial {
        this.color.copy(color)
        return this
    }

    /**
     * Set sprite color from hex
     */
    fun setColor(hex: Int): SpriteMaterial {
        this.color.setHex(hex)
        return this
    }

    /**
     * Set sprite rotation in degrees
     */
    fun setRotationDegrees(degrees: Float): SpriteMaterial {
        this.rotation = degrees * (PI / 180f).toFloat()
        return this
    }

    /**
     * Set sprite texture
     */
    fun setMap(texture: Texture?): SpriteMaterial {
        this.map = texture
        return this
    }

    companion object {
        /**
         * Create a basic colored sprite material
         */
        fun createBasic(
            color: Int = 0xffffff,
            opacity: Float = 1f
        ): SpriteMaterial {
            return SpriteMaterial(
                color = Color(color),
                opacity = opacity,
                transparent = opacity < 1f
            )
        }

        /**
         * Create a textured sprite material
         */
        fun createTextured(
            texture: Texture,
            color: Int = 0xffffff,
            transparent: Boolean = true
        ): SpriteMaterial {
            return SpriteMaterial(
                color = Color(color),
                map = texture,
                transparent = transparent
            )
        }

        /**
         * Create a material for UI labels
         */
        fun createLabel(
            texture: Texture,
            sizeAttenuation: Boolean = false
        ): SpriteMaterial {
            return SpriteMaterial(
                map = texture,
                sizeAttenuation = sizeAttenuation,
                transparent = true,
                fog = false  // UI elements typically ignore fog
            )
        }

        /**
         * Create a material for particle effects
         */
        fun createParticle(
            texture: Texture? = null,
            color: Int = 0xffffff,
            opacity: Float = 1f
        ): SpriteMaterial {
            return SpriteMaterial(
                color = Color(color),
                map = texture,
                opacity = opacity,
                transparent = true,
                sizeAttenuation = true
            )
        }
    }
}