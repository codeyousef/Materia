package io.kreekt.examples.forcegraph

import io.kreekt.examples.common.ExampleRunner
import io.kreekt.examples.common.Hud
import io.kreekt.examples.common.WindowCapture
import io.kreekt.renderer.RendererConfig
import io.kreekt.renderer.RendererFactory
import io.kreekt.renderer.vulkan.VulkanSurface
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil.NULL

fun main(): Unit = runBlocking {
    GLFWErrorCallback.createPrint(System.err).set()
    check(glfwInit()) { "Failed to initialize GLFW" }

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    val window = glfwCreateWindow(1600, 900, "KreeKt – Force Graph Rerank", NULL, NULL)
    require(window != NULL) { "Failed to create GLFW window" }

    val surface = VulkanSurface(window)
    val renderer = RendererFactory.create(surface, RendererConfig()).getOrThrow()
    renderer.initialize(RendererConfig()).getOrThrow()

    val scene = ForceGraphRerankScene()
    val hud = Hud()
    scene.attachHud(hud)

    val runner = ExampleRunner(scene.scene, scene.camera, renderer) { delta ->
        scene.update(delta)
    }
    runner.start()

    var lastWidth = 0
    var lastHeight = 0
    val capturePath = "examples/_captures/force-graph-rerank.png"
    var captureCountdown = 180
    var screenshotCaptured = false

    glfwSetKeyCallback(window) { _, key, _, action, _ ->
        if (action == GLFW_PRESS) {
            when (key) {
                GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true)
                GLFW_KEY_T -> scene.setMode(ForceGraphRerankScene.Mode.TfIdf)
                GLFW_KEY_S -> scene.setMode(ForceGraphRerankScene.Mode.Semantic)
                GLFW_KEY_SPACE -> scene.toggleMode()
            }
        }
    }

    while (!glfwWindowShouldClose(window)) {
        surface.pollEvents()
        val (width, height) = surface.getFramebufferSize()
        if (width != lastWidth || height != lastHeight) {
            if (width > 0 && height > 0) {
                renderer.resize(width, height)
                scene.resize(width.toFloat() / height.toFloat())
            }
            lastWidth = width
            lastHeight = height
        }
        runner.tick()

        if (!screenshotCaptured && lastWidth > 0 && lastHeight > 0) {
            if (captureCountdown > 0) {
                captureCountdown--
            } else {
                if (WindowCapture.capture(window, lastWidth, lastHeight, capturePath, renderer)) {
                    println("[ForceGraphRerank] Screenshot saved to $capturePath")
                }
                screenshotCaptured = true
            }
        }
    }

    renderer.dispose()
    surface.destroySurface()
    glfwDestroyWindow(window)
    glfwTerminate()
}
