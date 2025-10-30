package io.materia.renderer

import android.os.Build

actual object RendererFactory {

    actual suspend fun create(
        surface: RenderSurface,
        config: RendererConfig
    ): io.materia.core.Result<Renderer> {
        val androidSurface = when (surface) {
            is AndroidRenderSurface -> surface
            else -> return io.materia.core.Result.Error(
                "Expected AndroidRenderSurface, got ${surface::class.simpleName}",
                RendererInitializationException.SurfaceCreationFailedException(
                    BackendType.VULKAN,
                    surface::class.simpleName ?: "unknown surface"
                )
            )
        }

        val availableBackends = detectAvailableBackends()
        if (BackendType.VULKAN !in availableBackends) {
            return io.materia.core.Result.Error(
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
            is io.materia.core.Result.Success -> io.materia.core.Result.Success(stubRenderer)
            is io.materia.core.Result.Error -> io.materia.core.Result.Error(
                initResult.message,
                initResult.exception
            )
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
