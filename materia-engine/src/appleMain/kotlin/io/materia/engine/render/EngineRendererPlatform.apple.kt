package io.materia.engine.render

import io.materia.renderer.RenderSurface
import io.materia.renderer.RendererConfig

internal actual fun createPlatformEngineRendererOrNull(
    surface: RenderSurface,
    config: RendererConfig,
    options: EngineRendererOptions
): EngineRenderer? = null