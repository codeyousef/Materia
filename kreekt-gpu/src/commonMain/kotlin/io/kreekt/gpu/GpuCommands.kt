package io.kreekt.gpu

import kotlinx.serialization.Serializable

@Serializable
data class GpuCommandEncoderDescriptor(
    val label: String? = null
)

expect class GpuCommandEncoder internal constructor(
    device: GpuDevice,
    descriptor: GpuCommandEncoderDescriptor?
) {
    val device: GpuDevice
    val descriptor: GpuCommandEncoderDescriptor?

    fun finish(label: String? = descriptor?.label): GpuCommandBuffer
}

expect class GpuCommandBuffer internal constructor(
    device: GpuDevice,
    label: String?
) {
    val device: GpuDevice
    val label: String?
}
