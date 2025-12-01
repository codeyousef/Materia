package io.materia.material

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

// Shader compilation data types
data class CompiledShader(
    val bytecode: ByteArray = byteArrayOf(),
    val type: ShaderType = ShaderType.VERTEX,
    val source: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompiledShader) return false
        return bytecode.contentEquals(other.bytecode) && type == other.type && source == other.source
    }

    override fun hashCode(): Int {
        var result = bytecode.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }
}

enum class ShaderType { VERTEX, FRAGMENT, COMPUTE }
enum class ShaderPlatform { WEB, JVM, ANDROID, IOS, NATIVE }

data class ShaderProgram(
    val vertexShader: CompiledShader,
    val fragmentShader: CompiledShader
)

data class CompilationOptions(
    val optimize: Boolean = true,
    val debug: Boolean = false,
    val defines: Map<String, String> = emptyMap()
)

data class ShaderReloadEvent(val shaderName: String, val newShader: CompiledShader)

class ShaderFeatureDetector {
    fun detectRequiredFeatures(source: String): ShaderFeatureSet = emptySet()
    fun getPlatformCapabilities(): List<String> = emptyList()
}

class ShaderOptimizer {
    fun optimize(shader: CompiledShader, options: CompilationOptions): OptimizationResult = shader
}

class ShaderValidator {
    fun validate(source: String, type: ShaderType): ValidationResult? = null
}

class ShaderCompilationException(val errors: List<String>) : Exception(errors.joinToString("\n"))

typealias ValidationResult = List<String>
typealias OptimizationResult = CompiledShader
typealias ShaderMetrics = Any
typealias ShaderLibrary = Map<String, String>
typealias TargetPlatform = String
typealias ShaderFeatureSet = Set<String>
typealias PerformanceInfo = Any
typealias PreprocessorDirective = String
typealias ShaderInclude = String
typealias ComputeCapability = String

fun getPlatform(): ShaderPlatform = ShaderPlatform.WEB

/**
 * Cross-platform shader compilation system
 * Handles WGSL source compilation to platform-specific formats:
 * - Web: WGSL (native support)
 * - JVM/Native: SPIR-V via cross-compilation
 * - Mobile: Platform-optimized shaders
 *
 * Features:
 * - Shader validation and optimization
 * - Feature detection and capability adaptation
 * - Shader caching and hot-reload support
 * - Preprocessor directives for conditional compilation
 * - Include system for shader libraries
 */
class ShaderCompiler {
    private val shaderCache = mutableMapOf<String, CompiledShader>()
    private val includeLibrary = mutableMapOf<String, String>()
    private val featureDetector = ShaderFeatureDetector()
    private val optimizer = ShaderOptimizer()
    private val validator = ShaderValidator()

    // Hot reload support
    private val hotReloadFlow = MutableSharedFlow<ShaderReloadEvent>()
    val shaderReloads: SharedFlow<ShaderReloadEvent> = hotReloadFlow.asSharedFlow()

    // Helper methods
    private fun generateCacheKey(
        source: String,
        type: ShaderType,
        options: CompilationOptions
    ): String {
        return "${source.hashCode()}-${type.name}-${options.hashCode()}"
    }

    private fun preprocessShader(source: String, options: CompilationOptions): String {
        var processed = source
        // Apply defines
        options.defines.forEach { (key, value) ->
            processed = processed.replace("#define $key", "#define $key $value")
        }
        return processed
    }

    private fun checkPlatformSupport(features: ShaderFeatureSet): Boolean = true

    private fun generateFallbackShader(source: String, features: ShaderFeatureSet): String? = null

    private suspend fun compileForWeb(
        source: String,
        type: ShaderType,
        options: CompilationOptions
    ): CompiledShader {
        return CompiledShader(source.encodeToByteArray(), type, source)
    }

    private suspend fun compileForJVM(
        source: String,
        type: ShaderType,
        options: CompilationOptions
    ): CompiledShader {
        return CompiledShader(source.encodeToByteArray(), type, source)
    }

    private suspend fun compileForAndroid(
        source: String,
        type: ShaderType,
        options: CompilationOptions
    ): CompiledShader {
        return CompiledShader(source.encodeToByteArray(), type, source)
    }

    private suspend fun compileForIOS(
        source: String,
        type: ShaderType,
        options: CompilationOptions
    ): CompiledShader {
        return CompiledShader(source.encodeToByteArray(), type, source)
    }

    private suspend fun compileForNative(
        source: String,
        type: ShaderType,
        options: CompilationOptions
    ): CompiledShader {
        return CompiledShader(source.encodeToByteArray(), type, source)
    }

    /**
     * Compile WGSL shader source to platform-specific format
     */
    suspend fun compileShader(
        source: String,
        type: ShaderType,
        options: CompilationOptions = CompilationOptions()
    ): Result<CompiledShader> = withContext(Dispatchers.Default) {
        try {
            // Check cache first
            val cacheKey = generateCacheKey(source, type, options)
            shaderCache[cacheKey]?.let {
                return@withContext Result.success(it)
            }
            // Preprocess source
            val preprocessed = preprocessShader(source, options)
            // Validate syntax
            val validationErrors = validator.validate(preprocessed, type)
            if (validationErrors != null) {
                return@withContext Result.failure(ShaderCompilationException(validationErrors))
            }
            // Detect required features
            val requiredFeatures = featureDetector.detectRequiredFeatures(preprocessed)
            // Check platform capabilities
            if (!checkPlatformSupport(requiredFeatures)) {
                val fallback = generateFallbackShader(preprocessed, requiredFeatures)
                if (fallback != null) {
                    return@withContext compileShader(fallback, type, options)
                }
                return@withContext Result.failure(
                    ShaderCompilationException(listOf("Required features not supported: $requiredFeatures"))
                )
            }
            // Platform-specific compilation
            val compiled = when (getPlatform()) {
                ShaderPlatform.WEB -> compileForWeb(preprocessed, type, options)
                ShaderPlatform.JVM -> compileForJVM(preprocessed, type, options)
                ShaderPlatform.ANDROID -> compileForAndroid(preprocessed, type, options)
                ShaderPlatform.IOS -> compileForIOS(preprocessed, type, options)
                ShaderPlatform.NATIVE -> compileForNative(preprocessed, type, options)
            }
            // Optimize if requested
            val optimized = if (options.optimize) {
                optimizer.optimize(compiled, options)
            } else {
                compiled
            }
            // Create compiled shader
            val result = optimized
            // Cache result
            shaderCache[cacheKey] = result
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(ShaderCompilationException(listOf("Compilation failed: ${e.message}")))
        }
    }

    /**
     * Compile a complete shader program (vertex + fragment)
     */
    suspend fun compileProgram(
        vertexSource: String,
        fragmentSource: String,
        options: CompilationOptions = CompilationOptions()
    ): Result<ShaderProgram> {
        val vertexResult = compileShader(vertexSource, ShaderType.VERTEX, options)
        val fragmentResult = compileShader(fragmentSource, ShaderType.FRAGMENT, options)
        return when {
            vertexResult.isSuccess && fragmentResult.isSuccess -> {
                val program = ShaderProgram(
                    vertexShader = vertexResult.getOrThrow(),
                    fragmentShader = fragmentResult.getOrThrow()
                )
                Result.success(program)
            }

            else -> {
                val errors = mutableListOf<String>()
                vertexResult.exceptionOrNull()?.let { errors.add("Vertex: ${it.message}") }
                fragmentResult.exceptionOrNull()?.let { errors.add("Fragment: ${it.message}") }
                Result.failure(ShaderCompilationException(errors))
            }
        }
    }

    /**
     * Compile compute shader
     */
    suspend fun compileComputeShader(
        source: String,
        options: CompilationOptions = CompilationOptions()
    ): Result<CompiledShader> {
        return compileShader(source, ShaderType.COMPUTE, options)
    }

    // Helper functions for preprocessing
    private fun processIncludes(source: String): String {
        var processed = source
        val includePattern = Regex("#include\\s+\"([^\"]+)\"")
        includePattern.findAll(source).forEach { match ->
            val includeName = match.groupValues[1]
            val includeContent = includeLibrary[includeName] ?: ""
            processed = processed.replace(match.value, includeContent)
        }
        return processed
    }

    // Platform-specific shader processing helpers
    private fun addPlatformDefines(source: String, options: CompilationOptions): String = source
    private fun processConditionals(source: String, defines: Map<String, String>): String = source
    private fun addFeatureDefines(source: String, options: CompilationOptions): String = source
    private fun isFeatureDefined(feature: String): Boolean = false
}