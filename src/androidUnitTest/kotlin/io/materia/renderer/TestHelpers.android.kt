package io.materia.renderer

import io.materia.core.scene.Mesh
import io.materia.geometry.primitives.BoxGeometry
import io.materia.material.MeshBasicMaterial

actual enum class Platform {
    JS, JVM, NATIVE
}

actual fun getPlatform(): Platform = Platform.JVM

actual fun createTestSurface(width: Int, height: Int): RenderSurface {
    return object : RenderSurface {
        override val width: Int = width
        override val height: Int = height
        override fun getHandle(): Any = Unit
    }
}

actual suspend fun captureLog(block: suspend () -> Unit): String {
    val originalOut = System.out
    val outputStream = java.io.ByteArrayOutputStream()
    try {
        System.setOut(java.io.PrintStream(outputStream))
        block()
        return outputStream.toString()
    } finally {
        System.setOut(originalOut)
    }
}

actual fun createTestCube(): Mesh {
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

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatDouble(value: Double, decimals: Int): String =
    String.format("%.${decimals}f", value)
