package io.materia.engine.material

data class RenderState(
    val depthTest: Boolean = true,
    val depthWrite: Boolean = true,
    val cullMode: CullMode = CullMode.BACK,
    val blendMode: BlendMode = BlendMode.Opaque
)

enum class CullMode { NONE, FRONT, BACK }

enum class BlendMode { Opaque, Alpha, Additive }
