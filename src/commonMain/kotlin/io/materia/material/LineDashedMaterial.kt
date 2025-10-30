package io.materia.material

import io.materia.core.math.Color

/**
 * LineDashedMaterial - Dashed/dotted line rendering
 * Three.js r180 compatible
 *
 * Extends LineBasicMaterial with dash pattern support.
 * Requires geometry to have line distances computed.
 * Use BufferGeometry.computeLineDistances() before rendering.
 */
class LineDashedMaterial : Material() {

    override val type = "LineDashedMaterial"

    // Color properties
    var color: Color = Color(1f, 1f, 1f)

    // Line properties
    var linewidth: Float = 1f
    var linecap: String = "round"
    var linejoin: String = "round"

    // Dash pattern
    var scale: Float = 1f      // Scale factor for dash pattern
    var dashSize: Float = 3f   // Length of dash
    var gapSize: Float = 1f    // Length of gap

    // Rendering properties
    var fog: Boolean = true

    override fun clone(): Material {
        return LineDashedMaterial().copy(this)
    }

    override fun copy(source: Material): Material {
        super.copy(source)
        if (source is LineDashedMaterial) {
            this.color = source.color.clone()
            this.linewidth = source.linewidth
            this.linecap = source.linecap
            this.linejoin = source.linejoin
            this.scale = source.scale
            this.dashSize = source.dashSize
            this.gapSize = source.gapSize
            this.fog = source.fog
        }
        return this
    }
}
