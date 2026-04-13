package io.materia.renderer

import io.materia.core.scene.Mesh
import io.materia.geometry.primitives.BoxGeometry
import io.materia.material.MeshBasicMaterial
import kotlin.math.pow
import kotlin.math.round

actual enum class Platform {
    JS, JVM, NATIVE
}

actual fun getPlatform(): Platform = Platform.NATIVE

actual fun createTestSurface(width: Int, height: Int): RenderSurface {
    return AppleRenderSurface(
        nativeHandle = "native-test-surface",
        width = width,
        height = height,
        label = "NativeTestSurface"
    )
}

actual suspend fun captureLog(block: suspend () -> Unit): String {
    block()
    return ""
}

actual fun createTestCube(): Mesh {
    return Mesh(BoxGeometry(1f, 1f, 1f), MeshBasicMaterial())
}

actual fun currentTimeMillis(): Long = io.materia.datetime.currentTimeMillis()

actual fun formatDouble(value: Double, decimals: Int): String {
    val scale = 10.0.pow(decimals)
    val rounded = round(value * scale) / scale
    return rounded.toString()
}

actual fun createInvalidSurface(): RenderSurface {
    return object : RenderSurface {
        override val width: Int = -1
        override val height: Int = -1
        override fun getHandle(): Any = "invalid-native-surface"
    }
}