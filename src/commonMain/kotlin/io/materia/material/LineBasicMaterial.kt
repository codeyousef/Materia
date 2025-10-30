package io.materia.material

import io.materia.core.math.Color
import io.materia.core.scene.Material
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * Basic material for line rendering
 */
class LineBasicMaterial(
    var color: Color = Color(0xffffff),
    var linewidth: Float = 1f,
    var linecap: String = "round",
    var linejoin: String = "round",
    var vertexColors: Boolean = false,
    var fog: Boolean = true,
    var toneMapped: Boolean = true,
    materialName: String = "LineBasicMaterial"
) : Material {
    override val id: Int = generateId()
    override val name: String = materialName
    override var needsUpdate: Boolean = true
    override var visible: Boolean = true

    private companion object {
        private val nextId: AtomicInt = atomic(0)
        private fun generateId(): Int = nextId.incrementAndGet()
    }

    fun clone(): LineBasicMaterial = LineBasicMaterial(
        color.clone(),
        linewidth,
        linecap,
        linejoin,
        vertexColors,
        fog,
        toneMapped,
        name
    )
}