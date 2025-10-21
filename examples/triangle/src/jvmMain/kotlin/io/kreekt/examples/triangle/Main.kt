package io.kreekt.examples.triangle

import io.kreekt.gpu.GpuBackend
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("🚀 KreeKt Triangle MVP (JVM)")
    println("================================")

    val example = TriangleExample(
        preferredBackends = listOf(GpuBackend.VULKAN, GpuBackend.WEBGPU)
    )
    val log = example.boot()

    println(log.pretty())
    println("✅ Placeholder command submission executed (no real GPU work yet).")
    println("Next steps: integrate actual Vulkan/WebGPU backends and swapchain management.")
}
