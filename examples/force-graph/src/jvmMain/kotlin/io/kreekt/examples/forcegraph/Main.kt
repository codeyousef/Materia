package io.kreekt.examples.forcegraph

import io.kreekt.gpu.GpuBackend
import io.kreekt.io.saveJson
import io.kreekt.renderer.SurfaceFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_T
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil
import kotlin.math.max

fun main() = runBlocking {
    val bakeTarget = System.getenv("FORCE_GRAPH_BAKE")
    if (!bakeTarget.isNullOrBlank()) {
        val layout = ForceGraphLayoutGenerator.generate(ForceGraphScene.Config())
        saveJson(
            bakeTarget,
            layout,
            Json {
                prettyPrint = true
                encodeDefaults = true
            }
        )
        println("✅ Baked force-graph layout to $bakeTarget")
        return@runBlocking
    }

    println("🔗 KreeKt Force Graph (JVM)")
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

    val width = 1600
    val height = 900
    val window = glfwCreateWindow(width, height, "Force Graph (Vulkan)", MemoryUtil.NULL, MemoryUtil.NULL)
    if (window == MemoryUtil.NULL) {
        glfwTerminate()
        errorCallback.free()
        error("Failed to create GLFW window")
    }

    glfwShowWindow(window)

    val example = ForceGraphExample(preferredBackends = listOf(GpuBackend.VULKAN))
    val boot = runCatching {
        val surface = SurfaceFactory.create(window)
        example.boot(renderSurface = surface, widthOverride = width, heightOverride = height)
    }.getOrElse { throwable ->
        println("⚠️ Force Graph failed to acquire a GPU surface: ${throwable.message ?: throwable::class.simpleName}")
        println("   Falling back to headless bootstrap to keep the smoke test runnable.")
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()

        val headless = example.boot(renderSurface = null, widthOverride = width, heightOverride = height)
        println(headless.log.pretty())
        println("⚠️ Headless mode active – rendering loop skipped.")
        return@runBlocking
    }
    val runtime = boot.runtime

    println(boot.log.pretty())
    println("Controls: SPACE toggle | T TF-IDF | S Semantic")

    glfwSetFramebufferSizeCallback(window) { _, w, h ->
        val clampedW = max(1, w)
        val clampedH = max(1, h)
        runtime.resize(clampedW, clampedH)
    }

    glfwSetKeyCallback(window) { _, key, _, action, _ ->
        if (action != GLFW_PRESS) return@glfwSetKeyCallback
        when (key) {
            GLFW_KEY_SPACE -> runtime.toggleMode()
            GLFW_KEY_T -> runtime.setMode(ForceGraphScene.Mode.TfIdf)
            GLFW_KEY_S -> runtime.setMode(ForceGraphScene.Mode.Semantic)
        }
    }

    var lastTime = System.nanoTime()
    try {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            val now = System.nanoTime()
            val deltaSeconds = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
            lastTime = now
            runtime.frame(deltaSeconds)
            Thread.sleep(1)
        }
    } finally {
        runtime.dispose()
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
}
