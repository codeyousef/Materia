package io.kreekt.examples.triangle

import io.kreekt.renderer.BackendType
import io.kreekt.renderer.SurfaceFactory
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

fun main() = runBlocking {
    println("🚀 KreeKt Triangle MVP (JVM)")
    println("================================")

    val errorCallback = GLFWErrorCallback.createPrint(System.err).set()
    if (!glfwInit()) {
        errorCallback.free()
        error("Failed to initialise GLFW")
    }

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    val window = glfwCreateWindow(640, 480, "KreeKt Triangle (Vulkan)", MemoryUtil.NULL, MemoryUtil.NULL)
    if (window == MemoryUtil.NULL) {
        glfwTerminate()
        errorCallback.free()
        error("Failed to create GLFW window")
    }

    glfwShowWindow(window)

    val example = TriangleExample(preferredBackends = listOf(BackendType.VULKAN))

    try {
        val surface = SurfaceFactory.create(window)
        val log = example.boot(renderSurface = surface)

        println(log.pretty())
        println("✅ Renderer initialised. Close the window to exit.")

        var currentWidth = 640
        var currentHeight = 480
        example.resize(currentWidth, currentHeight)

        var lastTime = System.nanoTime()
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()

            MemoryStack.stackPush().use { stack ->
                val pWidth = stack.mallocInt(1)
                val pHeight = stack.mallocInt(1)
                glfwGetFramebufferSize(window, pWidth, pHeight)
                val fbWidth = pWidth[0]
                val fbHeight = pHeight[0]
                if (fbWidth > 0 && fbHeight > 0 && (fbWidth != currentWidth || fbHeight != currentHeight)) {
                    currentWidth = fbWidth
                    currentHeight = fbHeight
                    example.resize(currentWidth, currentHeight)
                }
            }

            val now = System.nanoTime()
            val deltaSeconds = ((now - lastTime) / 1_000_000_000.0).toFloat()
            lastTime = now

            example.renderFrame(deltaSeconds)

            Thread.sleep(16)
        }
    } finally {
        example.shutdown()
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
}
