package io.kreekt.engine.geometry

fun Geometry.vertexCount(): Int {
    val positionAttr = layout.attributes[AttributeSemantic.POSITION]
        ?: return vertexBuffer.data.size / (layout.stride / Float.SIZE_BYTES)
    val components = positionAttr.components
    return vertexBuffer.data.size / components
}
