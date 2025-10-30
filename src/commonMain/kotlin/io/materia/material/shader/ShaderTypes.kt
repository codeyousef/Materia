package io.materia.material.shader

import io.materia.core.math.Vector3
import io.materia.renderer.Texture

/**
 * Type definitions for shader materials
 * Extracted from ShaderMaterial.kt for better organization
 */

data class ShaderVariant(
    val name: String,
    val vertexShader: String = "",
    val fragmentShader: String = "",
    val computeShader: String = "",
    val defines: Map<String, String> = emptyMap()
)

data class ShaderUniform(
    val name: String,
    val type: ShaderDataType,
    val value: Any,
    var location: Int = -1,
    var offset: Int = 0,
    var size: Int = type.byteSize
)

data class ShaderAttribute(
    val name: String,
    val location: Int,
    val format: AttributeFormat,
    val offset: Int = 0,
    val stride: Int = format.getByteSize()
)

data class SamplerState(
    val name: String = "default"
) {
    companion object {
        val DEFAULT = SamplerState("default")
        val NEAREST = SamplerState("nearest")
        val CLAMP = SamplerState("clamp")
    }
}

data class TextureBinding(
    val texture: Texture,
    val sampler: SamplerState = SamplerState.DEFAULT,
    var binding: Int = -1
)

data class StorageBuffer(
    val name: String,
    val data: ByteArray,
    val binding: Int = -1,
    val usage: BufferUsage = BufferUsage.STORAGE
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StorageBuffer) return false
        return name == other.name && data.contentEquals(other.data) &&
                binding == other.binding && usage == other.usage
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + binding.hashCode()
        result = 31 * result + usage.hashCode()
        return result
    }
}

data class ShaderOverrides(
    val vertexShader: String? = null,
    val fragmentShader: String? = null,
    val computeShader: String? = null
)

data class ShaderCompilationRequest(
    val vertexSource: String,
    val fragmentSource: String,
    val computeSource: String? = null,
    val sourceLanguage: String,
    val targetLanguage: String,
    val uniforms: List<ShaderUniform>,
    val attributes: List<ShaderAttribute>,
    val textureBindings: List<TextureBinding>,
    val storageBuffers: List<StorageBuffer>,
    val features: List<String>,
    val defines: Map<String, String>,
    val optimizationLevel: String = "NONE"
)

data class ShaderValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)

data class ShaderPerformanceMetrics(
    val compilationTime: Float = 0f,
    val vertexInstructions: Int = 0,
    val fragmentInstructions: Int = 0,
    val uniformBufferSize: Int = 0,
    val textureUnits: Int = 0,
    val estimatedGpuCost: Float = 0f
)

// Type aliases
typealias ShaderDebugInfo = String
typealias ShaderProfiler = Any
typealias HotReloadHandler = () -> Unit
typealias ShaderCache = MutableMap<String, ShaderCompilationResult>
typealias ShaderMixin = String
typealias ShaderLibrary2 = Map<String, String>
typealias PreprocessorContext = Any
typealias PlatformShaderBackend = Any
typealias UniformBuffer = Any
typealias RenderPass = Any
typealias BlendState = String
typealias DepthTestState = String
typealias CullMode = String
typealias PrimitiveTopology = String

// Enumerations
enum class ShaderDataType(val byteSize: Int) {
    FLOAT(4), INT(4), BOOL(4),
    VEC2(8), VEC3(12), VEC4(16),
    MAT3(36), MAT4(64),
    FLOAT_ARRAY(4), INT_ARRAY(4),
    TEXTURE_2D(0), TEXTURE_CUBE(0), SAMPLER(0)
}

enum class BufferUsage {
    VERTEX, INDEX, UNIFORM, STORAGE, COPY_SRC, COPY_DST
}

enum class OptimizationLevel {
    NONE, LOW, MEDIUM, HIGH, AGGRESSIVE
}

enum class ShaderStage {
    VERTEX, FRAGMENT, COMPUTE, GEOMETRY, TESSELLATION_CONTROL, TESSELLATION_EVALUATION
}

enum class AttributeFormat(val components: Int, val componentSize: Int) {
    FLOAT(1, 4), VEC2(2, 4), VEC3(3, 4), VEC4(4, 4),
    INT(1, 4), IVEC2(2, 4), IVEC3(3, 4), IVEC4(4, 4),
    BYTE(1, 1), UBYTE(1, 1),
    SHORT(1, 2), USHORT(1, 2);

    fun getByteSize(): Int = components * componentSize
}

// Type alias to MaterialProcessor's ShaderCompilationResult
// This avoids duplication and uses the more comprehensive definition
typealias ShaderCompilationResult = io.materia.material.ShaderCompilationResult
