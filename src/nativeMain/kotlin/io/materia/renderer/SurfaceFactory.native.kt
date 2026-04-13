package io.materia.renderer

data class AppleSurfaceHandle(
    val handle: Any,
    val width: Int,
    val height: Int,
    val label: String = handle::class.simpleName ?: "AppleSurface"
)

class AppleRenderSurface(
    private val nativeHandle: Any,
    override var width: Int,
    override var height: Int,
    val label: String = nativeHandle::class.simpleName ?: "AppleSurface"
) : RenderSurface {
    override fun getHandle(): Any = nativeHandle

    internal fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun toString(): String = "$label(${width}x$height)"
}

actual object SurfaceFactory {
    actual fun create(handle: Any): RenderSurface {
        return when (handle) {
            is RenderSurface -> handle
            is AppleSurfaceHandle -> AppleRenderSurface(
                nativeHandle = handle.handle,
                width = handle.width,
                height = handle.height,
                label = handle.label
            )

            else -> throw IllegalArgumentException(
                "Native SurfaceFactory.create() expects AppleSurfaceHandle or RenderSurface, got ${handle::class.simpleName}"
            )
        }
    }
}
