package io.materia.points

import io.materia.core.math.Color
import io.materia.material.Material
import io.materia.texture.Texture

/**
 * PointsMaterial - Material for rendering point clouds
 * T092 - Point size, color, attenuation, texture support
 *
 * Controls the appearance of points in a Points object.
 * Supports per-vertex colors, size attenuation, and texture mapping.
 */
class PointsMaterial(
    /**
     * Base color of points
     */
    var color: Color = Color(0xffffff),

    /**
     * Point size in pixels (or world units if sizeAttenuation is false)
     */
    var size: Float = 1f,

    /**
     * Whether point size is attenuated by camera distance
     */
    var sizeAttenuation: Boolean = true,

    /**
     * Texture to apply to each point (typically a circle/gradient)
     */
    var map: Texture? = null,

    /**
     * Alpha map for transparency
     */
    var alphaMap: Texture? = null,

    /**
     * Whether to use vertex colors if available
     */
    override var vertexColors: Boolean = false,

    /**
     * Opacity of the points
     */
    override var opacity: Float = 1f,

    /**
     * Whether material is transparent
     */
    override var transparent: Boolean = false
) : Material() {

    override val type = "PointsMaterial"

    init {
        // Points are typically rendered with alpha blending
        if (transparent) {
            this.depthWrite = false
        }
    }

    /**
     * Clone this material
     */
    override fun clone(): PointsMaterial {
        return PointsMaterial(
            color = color.clone(),
            size = size,
            sizeAttenuation = sizeAttenuation,
            map = map,
            alphaMap = alphaMap,
            vertexColors = vertexColors,
            opacity = opacity,
            transparent = transparent
        ).also {
            it.copy(this)
        }
    }

    /**
     * Copy from another material
     */
    fun copy(source: PointsMaterial): PointsMaterial {
        super.copy(source)

        this.color.copy(source.color)
        this.size = source.size
        this.sizeAttenuation = source.sizeAttenuation
        this.map = source.map
        this.alphaMap = source.alphaMap
        this.vertexColors = source.vertexColors
        this.opacity = source.opacity
        this.transparent = source.transparent

        return this
    }

    /**
     * Set point size
     */
    fun setSize(size: Float): PointsMaterial {
        this.size = size
        return this
    }

    /**
     * Set point color
     */
    fun setColor(color: Color): PointsMaterial {
        this.color.copy(color)
        return this
    }

    /**
     * Set point color from hex value
     */
    fun setColor(hex: Int): PointsMaterial {
        this.color.setHex(hex)
        return this
    }

    /**
     * Enable/disable vertex colors
     */
    fun setVertexColors(enabled: Boolean): PointsMaterial {
        this.vertexColors = enabled
        return this
    }

    companion object {
        /**
         * Create a basic points material with common settings
         */
        fun createBasic(
            color: Int = 0xffffff,
            size: Float = 1f,
            sizeAttenuation: Boolean = true
        ): PointsMaterial {
            return PointsMaterial(
                color = Color(color),
                size = size,
                sizeAttenuation = sizeAttenuation
            )
        }

        /**
         * Create a textured points material (for particles)
         */
        fun createTextured(
            texture: Texture,
            size: Float = 10f,
            color: Int = 0xffffff,
            transparent: Boolean = true
        ): PointsMaterial {
            return PointsMaterial(
                color = Color(color),
                size = size,
                map = texture,
                transparent = transparent,
                sizeAttenuation = true
            )
        }

        /**
         * Create a material for rendering stars
         */
        fun createStarfield(
            size: Float = 2f,
            vertexColors: Boolean = true
        ): PointsMaterial {
            return PointsMaterial(
                size = size,
                sizeAttenuation = false,  // Stars don't change size with distance
                vertexColors = vertexColors
            )
        }
    }
}