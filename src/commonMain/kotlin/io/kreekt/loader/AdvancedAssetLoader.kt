package io.kreekt.loader

import io.kreekt.core.Result
import io.kreekt.texture.Texture2D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High level asset loader that routes to the appropriate format-specific loader
 * and applies shared caching policies.
 */
class AdvancedAssetLoader(
    private val gltfLoader: GLTFLoader = GLTFLoader(),
    private val objLoader: OBJLoader = OBJLoader(),
    private val plyLoader: PLYLoader = PLYLoader(),
    private val stlLoader: STLLoader = STLLoader(),
    private val resolver: AssetResolver = AssetResolver.default()
) {

    private val tgaLoader = TGALoader(resolver)

    private val modelCache = mutableMapOf<String, ModelAsset>()
    private val textureCache = mutableMapOf<String, Texture2D>()

    suspend fun loadModel(
        path: String,
        options: LoadOptions = LoadOptions()
    ): Result<ModelAsset> = withContext(Dispatchers.Default) {
        try {
            val cached = if (options.enableCaching) modelCache[path] else null
            if (cached != null) {
                return@withContext Result.Success(cached)
            }

            val format = detectModelFormat(path)
            val model = when (format) {
                ModelFormat.GLTF, ModelFormat.GLB -> loadGLTF(path)
                ModelFormat.OBJ -> objLoader.load(path)
                ModelFormat.PLY -> plyLoader.load(path)
                ModelFormat.STL -> stlLoader.load(path)
            }

            if (options.enableCaching) {
                modelCache[path] = model
            }
            Result.Success(model)
        } catch (e: Exception) {
            Result.Error("Failed to load model: $path", e)
        }
    }

    suspend fun loadTexture(
        path: String,
        options: LoadOptions = LoadOptions()
    ): Result<Texture2D> = withContext(Dispatchers.Default) {
        try {
            val cached = if (options.enableCaching) textureCache[path] else null
            if (cached != null) {
                return@withContext Result.Success(cached)
            }

            val extension = detectTextureExtension(path)
            val texture = when (extension) {
                "tga" -> tgaLoader.load(path).apply {
                    generateMipmaps = options.generateMipmaps
                }
                else -> {
                    val bytes = resolver.load(path, deriveBasePath(path))
                    val decoded = PlatformImageDecoder.decode(bytes)
                    Texture2D.fromImageData(decoded.width, decoded.height, decoded.pixels).apply {
                        setTextureName(path.substringAfterLast('/'))
                        generateMipmaps = options.generateMipmaps
                    }
                }
            }

            if (options.enableCaching) {
                textureCache[path] = texture
            }

            Result.Success(texture)
        } catch (e: Exception) {
            Result.Error("Failed to load texture: $path", e)
        }
    }

    private suspend fun loadGLTF(path: String): ModelAsset {
        val asset = gltfLoader.load(path)
        return ModelAsset(
            scene = asset.scene,
            materials = asset.materials,
            animations = asset.animations
        )
    }

    private fun detectModelFormat(path: String): ModelFormat {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension.isEmpty()) {
            if (path.startsWith("data:", ignoreCase = true)) {
                return ModelFormat.GLTF
            }
        }
        return when (extension) {
            "gltf" -> ModelFormat.GLTF
            "glb" -> ModelFormat.GLB
            "obj" -> ModelFormat.OBJ
            "ply" -> ModelFormat.PLY
            "stl" -> ModelFormat.STL
            else -> throw IllegalArgumentException("Unknown model format: $extension")
        }
    }

    private fun deriveBasePath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else null
    }

    private fun detectTextureExtension(path: String): String? {
        if (path.startsWith("data:", ignoreCase = true)) {
            val mimeEnd = path.indexOf(';', startIndex = 5)
            if (mimeEnd > 5) {
                val mime = path.substring(5, mimeEnd).lowercase()
                return when (mime) {
                    "image/x-tga", "image/tga" -> "tga"
                    "image/png" -> "png"
                    "image/jpeg", "image/jpg" -> "jpg"
                    "image/webp" -> "webp"
                    else -> null
                }
            }
            return null
        }

        val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext.ifEmpty { null }
    }
}

data class LoadOptions(
    val enableCaching: Boolean = true,
    val generateMipmaps: Boolean = true
)

enum class ModelFormat {
    GLTF,
    GLB,
    OBJ,
    PLY,
    STL
}
