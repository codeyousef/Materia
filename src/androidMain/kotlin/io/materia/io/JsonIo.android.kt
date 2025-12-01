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

    // WGSL shader files on Android Vulkan are handled by the shader module loader
    // which reads SPIR-V binaries from assets based on the shader label
    if (urlOrPath.endsWith(".wgsl")) {
        return "// Android Vulkan: SPIR-V loaded from assets by shader module"
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
