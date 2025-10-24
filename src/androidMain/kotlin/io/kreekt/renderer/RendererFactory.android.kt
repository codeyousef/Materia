package io.kreekt.renderer

import android.os.Build

actual object RendererFactory {

    actual suspend fun create(
        surface: RenderSurface,
        config: RendererConfig
    ): io.kreekt.core.Result<Renderer> {
        val androidSurface = when (surface) {
            is AndroidRenderSurface -> surface
            else -> return io.kreekt.core.Result.Error(
                "Expected AndroidRenderSurface, got ${surface::class.simpleName}",
                RendererInitializationException.SurfaceCreationFailedException(
                    BackendType.VULKAN,
                    surface::class.simpleName ?: "unknown surface"
                )
            )
        }

        val availableBackends = detectAvailableBackends()
        if (BackendType.VULKAN !in availableBackends) {
            return io.kreekt.core.Result.Error(
                "No supported GPU backend available on this Android device.",
                RendererInitializationException.NoGraphicsSupportException(
                    platform = "Android",
                    availableBackends = availableBackends,
                    requiredFeatures = listOf("Vulkan 1.1+")
                )
            )
        }

        val stubRenderer = AndroidStubRenderer(androidSurface)
        val initResult = stubRenderer.initialize(config)
        return when (initResult) {
            is io.kreekt.core.Result.Success -> io.kreekt.core.Result.Success(stubRenderer)
            is io.kreekt.core.Result.Error -> io.kreekt.core.Result.Error(initResult.message, initResult.exception)
        }
    }

    actual fun detectAvailableBackends(): List<BackendType> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listOf(BackendType.VULKAN)
        } else {
            emptyList()
        }
    }
}
