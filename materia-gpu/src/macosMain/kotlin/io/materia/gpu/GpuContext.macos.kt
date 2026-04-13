@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.materia.gpu

import cnames.structs.GLFWwindow
import ffi.NativeAddress
import io.materia.renderer.RenderSurface
import io.ygdrasil.webgpu.DeviceDescriptor
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.Surface
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.WGPUInstanceBackend
import objcnames.protocols.MTLDeviceProtocol
import glfw.glfwGetCocoaWindow
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import platform.AppKit.NSWindow
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.QuartzCore.CAMetalLayer

internal actual suspend fun createAppleWgpuContextBundle(surface: RenderSurface): AppleWgpuContextBundle {
    val handle = surface.getHandle()
    val window = (handle as? CPointer<*>)?.reinterpret<GLFWwindow>()
        ?: error("macOS GPU surface expects a GLFWwindow handle, got ${handle::class.simpleName}")

    val instance = WGPU.createInstance(WGPUInstanceBackend.Metal)
        ?: WGPU.createInstance(WGPUInstanceBackend.Primary)
        ?: WGPU.createInstance()
        ?: error("Failed to create Apple WGPU instance")
    val nativeSurface = instance.createMetalSurface(window)
    val wgpuSurface = Surface(nativeSurface, window)
    val adapter = instance.requestAdapter(nativeSurface)
        ?: error("Failed to acquire Apple GPU adapter")
    val device = adapter.requestDevice(DeviceDescriptor()).getOrThrow()
    nativeSurface.computeSurfaceCapabilities(adapter)
    val preferredFormat = wgpuSurface.supportedFormats.find { it == GPUTextureFormat.BGRA8Unorm }
        ?: wgpuSurface.supportedFormats.first()

    return AppleWgpuContextBundle(
        context = WGPUContext(
            surface = wgpuSurface,
            adapter = adapter,
            device = device,
            renderingContext = SurfaceRenderingContext(wgpuSurface, preferredFormat)
        )
    )
}

private fun WGPU.createMetalSurface(window: CPointer<GLFWwindow>): NativeSurface {
    val nsWindow = interpretObjCPointer<NSWindow>(glfwGetCocoaWindow(window).rawValue)
        ?: error("Failed to resolve Cocoa window from GLFW handle")
    val contentView = nsWindow.contentView()
        ?: error("Cocoa window does not expose a content view")
    val metalDevice = MTLCreateSystemDefaultDevice()
        ?: error("Metal is unavailable on this Mac")

    contentView.setWantsLayer(true)

    val layer = (contentView.layer() as? CAMetalLayer) ?: CAMetalLayer.layer().also {
        contentView.setLayer(it)
    }
    layer.device = metalDevice as MTLDeviceProtocol
    layer.frame = contentView.bounds

    val layerPointer: COpaquePointer = interpretCPointer<COpaque>(layer.objcPtr())!!.reinterpret()
    return getSurfaceFromMetalLayer(NativeAddress(layerPointer))
        ?: error("Failed to create Metal surface from Cocoa layer")
}