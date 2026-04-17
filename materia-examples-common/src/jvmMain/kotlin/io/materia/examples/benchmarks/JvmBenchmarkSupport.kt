package io.materia.examples.benchmarks

import org.lwjgl.glfw.GLFW.GLFW_CLIENT_API
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_NO_API
import org.lwjgl.glfw.GLFW.GLFW_RESIZABLE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwShowWindow
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil

inline fun <T> withHiddenGlfwWindow(
    width: Int,
    height: Int,
    title: String,
    block: (window: Long) -> T
): T {
    val errorCallback = GLFWErrorCallback.createPrint(System.err).set()
    check(glfwInit()) { "Failed to initialise GLFW" }

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)

    val window = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
    check(window != MemoryUtil.NULL) { "Failed to create hidden GLFW window" }
    runCatching {
        glfwSetWindowPos(window, -32_000, -32_000)
    }
    glfwShowWindow(window)

    return try {
        block(window)
    } finally {
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
}

fun buildJvmBenchmarkEnvironment(
    backend: String,
    deviceName: String,
    driverVersion: String?,
    notes: List<String> = listOf("Vulkan path", "High-performance adapter requested")
): BenchmarkEnvironment {
    val osName = listOfNotNull(
        System.getProperty("os.name"),
        System.getProperty("os.version")
    ).joinToString(" ")
    val runtimeVersion = System.getProperty("java.version") ?: "unknown"
    return BenchmarkEnvironment(
        platform = "JVM",
        backend = backend,
        environmentLabel = "$osName / Java $runtimeVersion / $backend / $deviceName",
        deviceName = deviceName,
        driverVersion = driverVersion,
        osName = osName,
        runtimeVersion = runtimeVersion,
        notes = notes
    )
}
