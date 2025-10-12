/**
 * Native stub for RendererFactory.
 * Native platforms are not primary targets for KreeKt.
 */

package io.kreekt.renderer

/**
 * Native actual for RendererFactory object.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual object RendererFactory {
    actual suspend fun create(
        surface: RenderSurface,
        config: RendererConfig
    ): io.kreekt.core.Result<Renderer> {
        return io.kreekt.core.Result.Error(
            message = "Native platforms are not supported",
            exception = RendererInitializationException.NoGraphicsSupportException(
                platform = "Native",
                availableBackends = emptyList(),
                requiredFeatures = listOf("OpenGL", "Vulkan", "WebGPU")
            )
        )
    }

    actual fun detectAvailableBackends(): List<BackendType> {
        return emptyList()
    }
}
