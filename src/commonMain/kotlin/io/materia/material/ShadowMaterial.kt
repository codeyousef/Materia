package io.materia.material

import io.materia.core.math.Color

/**
 * ShadowMaterial - Renders only shadows
 * Three.js r180 compatible
 *
 * Makes surfaces invisible except where shadows are cast.
 * Useful for ground planes that only show shadows.
 * Commonly used in AR applications to blend shadows with real world.
 */
class ShadowMaterial : Material() {

    override val type = "ShadowMaterial"

    // Shadow color (typically black with alpha)
    var color: Color = Color(0f, 0f, 0f)

    // Rendering properties
    var fog: Boolean = false

    init {
        // Shadows require transparency
        transparent = true
    }

    override fun clone(): Material {
        return ShadowMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is ShadowMaterial) {
            this.color = source.color.clone()
            this.fog = source.fog
        }
        return this
    }
}
