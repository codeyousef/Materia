package io.kreekt.renderer

import io.kreekt.core.scene.Mesh
import io.kreekt.geometry.primitives.BoxGeometry
import io.kreekt.material.MeshBasicMaterial
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Date

actual enum class Platform {
    JS, JVM, NATIVE
}

actual fun getPlatform(): Platform = Platform.JS

actual fun createTestSurface(width: Int, height: Int): RenderSurface {
    // Create a canvas element for testing
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = width
    canvas.height = height
    
    return object : RenderSurface {
        override val width: Int = width
        override val height: Int = height
        override fun getHandle(): Any = canvas
    }
}

actual suspend fun captureLog(block: suspend () -> Unit): String {
    // For JS, we just capture console output
    val logs = mutableListOf<String>()
    val originalLog = console.asDynamic().log
    
    try {
        console.asDynamic().log = { message: Any? ->
            logs.add(message.toString())
        }
        block()
        return logs.joinToString("\n")
    } finally {
        console.asDynamic().log = originalLog
    }
}

actual fun createTestCube(): Mesh {
    // Create a simple test cube geometry
    val geometry = BoxGeometry(1f, 1f, 1f)
    val material = MeshBasicMaterial()
    return Mesh(geometry, material)
}

actual fun createInvalidSurface(): RenderSurface {
    return object : RenderSurface {
        override val width: Int = -1
        override val height: Int = -1
        override fun getHandle(): Any = Unit
    }
}

actual fun currentTimeMillis(): Long {
    return Date.now().toLong()
}

actual fun formatDouble(value: Double, decimals: Int): String {
    return value.asDynamic().toFixed(decimals) as String
}
