package io.kreekt.renderer.gpu

import io.kreekt.renderer.RendererCapabilities
import io.kreekt.util.KreektLogger

/**
 * Centralised logging helper that surfaces backend/device details obtained
 * through the GPU abstraction. Ensures diagnostics are consistent across
 * platforms without duplicating console logging logic in each renderer.
 */
object GpuDiagnostics {
    private const val TAG = "GpuDiagnostics"

    /**
     * Logs high-level device information and limits sourced from the
     * abstraction layer. Optionally includes renderer capability details.
     */
    fun logContext(context: GpuContext, capabilities: RendererCapabilities? = null) {
        val device = context.device
        val info = device.info
        val limits = device.limits

        KreektLogger.info(
            TAG,
            buildString {
                append("Backend=${device.backend}")
                append(", Device='${info.name}'")
                info.vendor?.let { append(", Vendor=$it") }
                info.architecture?.let { append(", Architecture=$it") }
                info.driverVersion?.let { append(", Driver=$it") }
            }
        )

        KreektLogger.info(
            TAG,
            "Limits: textures=[1D=${limits.maxTextureDimension1D}," +
                " 2D=${limits.maxTextureDimension2D}, 3D=${limits.maxTextureDimension3D}," +
                " arrayLayers=${limits.maxTextureArrayLayers}], buffers=" +
                "bindGroups=${limits.maxBindGroups}, uniformPerStage=${limits.maxUniformBuffersPerStage}," +
                " storagePerStage=${limits.maxStorageBuffersPerStage}, maxBufferSize=${limits.maxBufferSize}"
        )

        capabilities?.let { caps ->
            KreektLogger.info(
                TAG,
                "Capabilities: backend=${caps.backend}, compute=${caps.supportsCompute}," +
                    " multisampling=${caps.supportsMultisampling}, maxTextureSize=${caps.maxTextureSize}," +
                    " maxUniformBufferSize=${caps.maxUniformBufferSize}, maxUniformBindings=${caps.maxUniformBufferBindings}"
            )
        }
    }
}
