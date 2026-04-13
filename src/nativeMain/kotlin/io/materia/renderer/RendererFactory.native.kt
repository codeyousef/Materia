package io.materia.renderer

actual object RendererFactory {
    actual suspend fun create(
        surface: RenderSurface,
        config: RendererConfig
    ): io.materia.core.Result<Renderer> {
        if (surface.width <= 0 || surface.height <= 0) {
            return io.materia.core.Result.Error(
                "Invalid Apple render surface dimensions: ${surface.width}x${surface.height}",
                RendererInitializationException.SurfaceCreationFailedException(
                    BackendType.VULKAN,
                    surface::class.simpleName ?: "RenderSurface"
                )
            )
        }

        val availableBackends = detectAvailableBackends()
        if (BackendType.VULKAN !in availableBackends) {
            return io.materia.core.Result.Error(
                "MoltenVK is not available on ${currentApplePlatformName()}.",
                RendererInitializationException.NoGraphicsSupportException(
                    platform = currentApplePlatformName(),
                    availableBackends = availableBackends,
                    requiredFeatures = listOf("MoltenVK", "Metal 3")
                )
            )
        }

        return try {
            val renderer = AppleMoltenVkRenderer(surface)
            when (val initResult = renderer.initialize(config)) {
                is io.materia.core.Result.Success -> io.materia.core.Result.Success(renderer)
                is io.materia.core.Result.Error -> io.materia.core.Result.Error(
                    initResult.message,
                    initResult.exception
                )
            }
        } catch (e: Exception) {
            io.materia.core.Result.Error(
                e.message ?: "Unknown error during MoltenVK renderer creation",
                RendererInitializationException.DeviceCreationFailedException(
                    BackendType.VULKAN,
                    currentApplePlatformInfo().deviceId,
                    e.message ?: "Unknown error during MoltenVK renderer creation"
                )
            )
        }
    }

    actual fun detectAvailableBackends(): List<BackendType> {
        return listOf(BackendType.VULKAN)
    }
}
