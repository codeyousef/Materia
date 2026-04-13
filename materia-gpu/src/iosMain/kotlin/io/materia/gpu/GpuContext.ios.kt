@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.materia.gpu

import ffi.NativeAddress
import io.materia.renderer.RenderSurface
import io.ygdrasil.webgpu.DeviceDescriptor
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.Surface
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.WGPUInstanceBackend
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.reinterpret
import platform.CoreGraphics.CGSizeMake
import platform.MetalKit.MTKView
import platform.QuartzCore.CAMetalLayer

internal actual suspend fun createAppleWgpuContextBundle(surface: RenderSurface): AppleWgpuContextBundle {
    val width = surface.width.coerceAtLeast(1)
    val height = surface.height.coerceAtLeast(1)
    val handle = surface.getHandle()
    val layer = when (handle) {
        is MTKView -> {
            handle.drawableSize = CGSizeMake(width.toDouble(), height.toDouble())
            (handle.layer as? CAMetalLayer)
                ?: error("MTKView must be backed by a CAMetalLayer")
        }

        is CAMetalLayer -> handle
        else -> error(
            "iOS GPU surface expects an MTKView or CAMetalLayer handle, got ${handle::class.simpleName}"
        )
    }
    layer.drawableSize = CGSizeMake(width.toDouble(), height.toDouble())

    val instance = WGPU.createInstance(WGPUInstanceBackend.Metal)
        ?: WGPU.createInstance(WGPUInstanceBackend.Primary)
        ?: WGPU.createInstance()
        ?: error("Failed to create Apple WGPU instance")
    val nativeSurface = instance.getSurfaceFromMetalLayer(layer.nativeAddress())
        ?: error("Failed to create Apple Metal surface")
    val adapter = instance.requestAdapter(nativeSurface)
        ?: error("Failed to acquire Apple GPU adapter")
    val device = adapter.requestDevice(DeviceDescriptor()).getOrThrow()
    val wgpuSurface = Surface(nativeSurface, width.toUInt(), height.toUInt())
    nativeSurface.computeSurfaceCapabilities(adapter)
    val preferredFormat = wgpuSurface.supportedFormats.find { it == GPUTextureFormat.BGRA8Unorm }
        ?: wgpuSurface.supportedFormats.first()

    return AppleWgpuContextBundle(
        context = WGPUContext(
            surface = wgpuSurface,
            adapter = adapter,
            device = device,
            renderingContext = SurfaceRenderingContext(wgpuSurface, preferredFormat)
        ),
        resize = { nextWidth, nextHeight ->
            when (handle) {
                is MTKView -> handle.drawableSize = CGSizeMake(nextWidth.toDouble(), nextHeight.toDouble())
                is CAMetalLayer -> handle.drawableSize = CGSizeMake(nextWidth.toDouble(), nextHeight.toDouble())
            }
        }
    )
}

private fun CAMetalLayer.nativeAddress(): NativeAddress {
    val pointer: COpaquePointer = interpretCPointer<COpaque>(objcPtr())!!.reinterpret()
    return NativeAddress(pointer)
}