package io.kreekt.gpu

import io.kreekt.renderer.RenderSurface

/**
 * Utility that bridges platform [RenderSurface] implementations to KreeKt's
 * multiplatform [GpuSurface]. Attaches the platform surface, configures the
 * swapchain dimensions, and returns a ready-to-use [GpuSurface].
 */
object GpuSurfaceFactory {

    /**
     * Creates and configures a [GpuSurface] that wraps the provided
     * [renderSurface]. A default configuration is derived when one is not
     * supplied, ensuring the swapchain dimensions and format are compatible
     * with the current adapter.
     */
    fun create(
        device: GpuDevice,
        renderSurface: RenderSurface,
        label: String? = null,
        configuration: GpuSurfaceConfiguration? = null
    ): GpuSurface {
        val surfaceLabel = label ?: (renderSurface::class.simpleName ?: "gpu-surface")
        val gpuSurface = GpuSurface(surfaceLabel)
        gpuSurface.attachRenderSurface(renderSurface)

        val adapter = device.adapter
        val preferredFormat = configuration?.format ?: gpuSurface.getPreferredFormat(adapter)

        val resolvedWidth = configuration?.width ?: renderSurface.width.takeIf { it > 0 } ?: 640
        val resolvedHeight = configuration?.height ?: renderSurface.height.takeIf { it > 0 } ?: 480
        val resolvedUsage = configuration?.usage ?: gpuTextureUsage(
            GpuTextureUsage.RENDER_ATTACHMENT,
            GpuTextureUsage.COPY_SRC
        )
        val presentMode = configuration?.presentMode ?: "fifo"

        val resolvedConfiguration = GpuSurfaceConfiguration(
            format = preferredFormat,
            usage = resolvedUsage,
            width = resolvedWidth,
            height = resolvedHeight,
            presentMode = presentMode
        )

        gpuSurface.configure(device, resolvedConfiguration)
        return gpuSurface
    }
}
