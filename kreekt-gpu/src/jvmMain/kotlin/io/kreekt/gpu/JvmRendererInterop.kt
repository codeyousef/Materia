package io.kreekt.gpu

import io.kreekt.renderer.gpu.GpuBackend as RendererGpuBackend
import io.kreekt.renderer.gpu.GpuBufferDescriptor as RendererGpuBufferDescriptor
import io.kreekt.renderer.gpu.GpuDeviceInfo as RendererGpuDeviceInfo
import io.kreekt.renderer.gpu.GpuPowerPreference as RendererGpuPowerPreference
import io.kreekt.renderer.gpu.GpuRequestConfig as RendererGpuRequestConfig
import io.kreekt.renderer.gpu.GpuSamplerDescriptor as RendererGpuSamplerDescriptor
import io.kreekt.renderer.gpu.GpuSamplerFilter as RendererGpuSamplerFilter
import io.kreekt.renderer.gpu.GpuTextureDescriptor as RendererGpuTextureDescriptor
import io.kreekt.renderer.gpu.GpuTextureDimension as RendererGpuTextureDimension
import io.kreekt.renderer.gpu.GpuTextureViewDescriptor as RendererGpuTextureViewDescriptor
import io.kreekt.renderer.gpu.GpuTextureViewDimension as RendererGpuTextureViewDimension

internal fun RendererGpuDeviceInfo.toAdapterInfo(): GpuAdapterInfo =
    GpuAdapterInfo(
        name = name,
        vendor = vendor,
        architecture = architecture,
        driverVersion = driverVersion
    )

internal fun RendererGpuBackend.toGpuBackend(): GpuBackend = when (this) {
    RendererGpuBackend.WEBGPU -> GpuBackend.WEBGPU
    RendererGpuBackend.VULKAN -> GpuBackend.VULKAN
}

internal fun GpuBackend.toRendererBackend(): RendererGpuBackend = when (this) {
    GpuBackend.WEBGPU -> RendererGpuBackend.WEBGPU
    GpuBackend.VULKAN,
    GpuBackend.MOLTENVK -> RendererGpuBackend.VULKAN
}

internal fun GpuPowerPreference.toRendererPreference(): RendererGpuPowerPreference = when (this) {
    GpuPowerPreference.LOW_POWER -> RendererGpuPowerPreference.LOW_POWER
    GpuPowerPreference.HIGH_PERFORMANCE -> RendererGpuPowerPreference.HIGH_PERFORMANCE
}

internal fun GpuInstanceDescriptor.toRendererConfig(
    options: GpuRequestAdapterOptions
): RendererGpuRequestConfig {
    val preferredBackend = preferredBackends.firstOrNull()?.toRendererBackend()
        ?: RendererGpuBackend.WEBGPU
    return RendererGpuRequestConfig(
        preferredBackend = preferredBackend,
        powerPreference = options.powerPreference.toRendererPreference(),
        forceFallbackAdapter = options.forceFallbackAdapter,
        label = options.label ?: label
    )
}

internal fun GpuBufferDescriptor.toRendererDescriptor(): RendererGpuBufferDescriptor =
    RendererGpuBufferDescriptor(
        size = size,
        usage = usage,
        mappedAtCreation = mappedAtCreation,
        label = label
    )

internal fun GpuTextureDescriptor.toRendererDescriptor(): RendererGpuTextureDescriptor =
    RendererGpuTextureDescriptor(
        width = size.first,
        height = size.second,
        depthOrArrayLayers = size.third,
        mipLevelCount = mipLevelCount,
        sampleCount = sampleCount,
        dimension = dimension.toRendererDimension(),
        format = format.toRendererFormatString(),
        usage = usage,
        label = label
    )

private fun GpuTextureDimension.toRendererDimension(): RendererGpuTextureDimension = when (this) {
    GpuTextureDimension.D1 -> RendererGpuTextureDimension.D1
    GpuTextureDimension.D2 -> RendererGpuTextureDimension.D2
    GpuTextureDimension.D3 -> RendererGpuTextureDimension.D3
}

internal fun GpuTextureViewDescriptor.toRendererDescriptor(): RendererGpuTextureViewDescriptor =
    RendererGpuTextureViewDescriptor(
        label = label,
        format = format?.toRendererFormatString(),
        dimension = dimension?.toRendererViewDimension(),
        aspect = aspect,
        baseMipLevel = baseMipLevel,
        mipLevelCount = mipLevelCount,
        baseArrayLayer = baseArrayLayer,
        arrayLayerCount = arrayLayerCount
    )

private fun GpuTextureViewDimension.toRendererViewDimension(): RendererGpuTextureViewDimension = when (this) {
    GpuTextureViewDimension.D1 -> RendererGpuTextureViewDimension.D1
    GpuTextureViewDimension.D2 -> RendererGpuTextureViewDimension.D2
    GpuTextureViewDimension.D2_ARRAY -> RendererGpuTextureViewDimension.D2_ARRAY
    GpuTextureViewDimension.CUBE -> RendererGpuTextureViewDimension.CUBE
    GpuTextureViewDimension.CUBE_ARRAY -> RendererGpuTextureViewDimension.CUBE_ARRAY
    GpuTextureViewDimension.D3 -> RendererGpuTextureViewDimension.D3
}

internal fun GpuTextureFormat.toRendererFormatString(): String = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> "rgba8unorm"
    GpuTextureFormat.BGRA8_UNORM -> "bgra8unorm"
    GpuTextureFormat.RGBA16_FLOAT -> "rgba16float"
    GpuTextureFormat.DEPTH24_PLUS -> "depth24plus"
}

internal fun GpuSamplerDescriptor.toRendererDescriptor(): RendererGpuSamplerDescriptor =
    RendererGpuSamplerDescriptor(
        magFilter = magFilter.toRendererFilter(),
        minFilter = minFilter.toRendererFilter(),
        mipmapFilter = mipmapFilter.toRendererFilter(),
        lodMinClamp = lodMinClamp,
        lodMaxClamp = lodMaxClamp,
        label = label
    )

private fun GpuFilterMode.toRendererFilter(): RendererGpuSamplerFilter = when (this) {
    GpuFilterMode.NEAREST -> RendererGpuSamplerFilter.NEAREST
    GpuFilterMode.LINEAR -> RendererGpuSamplerFilter.LINEAR
}

private fun GpuMipmapFilterMode.toRendererFilter(): RendererGpuSamplerFilter = when (this) {
    GpuMipmapFilterMode.NEAREST -> RendererGpuSamplerFilter.NEAREST
    GpuMipmapFilterMode.LINEAR -> RendererGpuSamplerFilter.LINEAR
}
