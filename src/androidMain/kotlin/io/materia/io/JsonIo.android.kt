package io.materia.io

import android.content.res.AssetManager
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmName
import java.io.File

/**
 * Holds the Android [AssetManager] for resource loading.
 * Must be initialised from an Activity or Application context before using [readTextResource].
 */
object AndroidResourceLoader {
    @Volatile
    private var assetManager: AssetManager? = null

    fun initialise(assets: AssetManager) {
        assetManager = assets
    }

    internal fun getAssetManager(): AssetManager? = assetManager
}

actual suspend fun readTextResource(urlOrPath: String): String {
    // First try the file system for absolute paths
    val onDisk = File(urlOrPath)
    if (onDisk.exists()) {
        return onDisk.readText()
    }

    // Then try Android assets
    val assets = AndroidResourceLoader.getAssetManager()
    if (assets != null) {
        try {
            assets.open(urlOrPath).use { stream ->
                return stream.bufferedReader().readText()
            }
        } catch (_: Exception) {
            // Asset not found, fall through
        }
    }

    // For WGSL shader files on Android Vulkan, return a placeholder since the
    // GpuDevice.createShaderModule() implementation loads SPIR-V from assets
    // based on the shader label, ignoring the WGSL code parameter.
    if (urlOrPath.endsWith(".wgsl")) {
        return "// Placeholder: Android Vulkan uses SPIR-V loaded from assets"
    }

    throw IllegalStateException("Resource not found: $urlOrPath")
}

@JvmName("saveJsonWithSerializer")
actual fun <T> saveJson(
    path: String,
    serializer: SerializationStrategy<T>,
    value: T,
    json: Json
) {
    val target = File(path)
    target.parentFile?.mkdirs()
    val payload = json.encodeToString(serializer, value)
    target.writeText(payload)
}
