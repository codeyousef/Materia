@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package io.materia.tools.editor.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * MaterialDefinition - Data model for shareable material configuration
 *
 * This data class represents a complete material definition that can be shared,
 * saved, and loaded across different projects. It includes shader source code,
 * uniform values, texture assignments, and material settings.
 *
 * Materials can be serialized to JSON format with embedded WGSL shader code.
 */
@Serializable
data class MaterialDefinition @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val type: MaterialType,
    val shaderSource: ShaderCode? = null,
    val uniforms: Map<String, UniformValue>,
    val textures: Map<String, TextureSlot>,
    val settings: MaterialSettings,
    val metadata: MaterialMetadata
) {
    init {
        require(name.isNotBlank()) { "Material name must be non-empty" }

        // Validate shader code if present
        shaderSource?.let { shader ->
            require(shader.vertex.isNotBlank()) { "Vertex shader cannot be empty" }
            require(shader.fragment.isNotBlank()) { "Fragment shader cannot be empty" }
        }

        // Validate uniform types match their values
        uniforms.forEach { (name, uniform) ->
            validateUniformValue(name, uniform)
        }

        // Validate texture slots have valid names
        textures.keys.forEach { slotName ->
            require(slotName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                "Texture slot name '$slotName' must be a valid identifier"
            }
        }
    }

    private fun validateUniformValue(name: String, uniform: UniformValue) {
        when (uniform.type) {
            UniformType.FLOAT -> {
                require(uniform.value is Float || uniform.value is Double || uniform.value is Int) {
                    "Uniform '$name' of type FLOAT must have numeric value"
                }
                uniform.min?.let { min ->
                    val value = (uniform.value as Number).toFloat()
                    require(value >= min) { "Uniform '$name' value $value below minimum $min" }
                }
                uniform.max?.let { max ->
                    val value = (uniform.value as Number).toFloat()
                    require(value <= max) { "Uniform '$name' value $value above maximum $max" }
                }
            }
            UniformType.VEC2 -> require(uniform.value is List<*> && (uniform.value as List<*>).size == 2) {
                "Uniform '$name' of type VEC2 must be a list of 2 numbers"
            }
            UniformType.VEC3 -> require(uniform.value is List<*> && (uniform.value as List<*>).size == 3) {
                "Uniform '$name' of type VEC3 must be a list of 3 numbers"
            }
            UniformType.VEC4 -> require(uniform.value is List<*> && (uniform.value as List<*>).size == 4) {
                "Uniform '$name' of type VEC4 must be a list of 4 numbers"
            }
            UniformType.MATRIX3 -> require(uniform.value is List<*> && (uniform.value as List<*>).size == 9) {
                "Uniform '$name' of type MATRIX3 must be a list of 9 numbers"
            }
            UniformType.MATRIX4 -> require(uniform.value is List<*> && (uniform.value as List<*>).size == 16) {
                "Uniform '$name' of type MATRIX4 must be a list of 16 numbers"
            }
            UniformType.BOOL -> require(uniform.value is Boolean) {
                "Uniform '$name' of type BOOL must be a boolean value"
            }
            UniformType.INT -> require(uniform.value is Int) {
                "Uniform '$name' of type INT must be an integer value"
            }
            UniformType.TEXTURE_2D, UniformType.TEXTURE_CUBE, UniformType.TEXTURE_3D -> {
                require(uniform.value is String) {
                    "Uniform '$name' of texture type must have string value (texture ID)"
                }
            }
        }
    }

    /**
     * Creates a copy of this material with updated metadata
     */
    fun withUpdatedMetadata(): MaterialDefinition {
        return copy(
            metadata = metadata.copy(
                lastModified = kotlinx.datetime.Clock.System.now(),
                version = metadata.version + 1
            )
        )
    }

    /**
     * Creates a copy of this material with a new name (for duplication)
     */
    fun duplicate(newName: String): MaterialDefinition {
        return copy(
            id = Uuid.random().toString(),
            name = newName,
            metadata = metadata.copy(
                created = kotlinx.datetime.Clock.System.now(),
                lastModified = kotlinx.datetime.Clock.System.now(),
                version = 1
            )
        )
    }

    /**
     * Validates that the material is complete and can be used for rendering
     */
    fun validate(): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // Check for required uniforms based on material type
        when (type) {
            MaterialType.STANDARD, MaterialType.PHYSICAL -> {
                if (!uniforms.containsKey("baseColor")) {
                    issues.add(ValidationIssue.WARNING("PBR material should have baseColor uniform"))
                }
                if (!uniforms.containsKey("roughness")) {
                    issues.add(ValidationIssue.WARNING("PBR material should have roughness uniform"))
                }
                if (type == MaterialType.PHYSICAL && !uniforms.containsKey("metallic")) {
                    issues.add(ValidationIssue.WARNING("Physical material should have metallic uniform"))
                }
            }
            MaterialType.CUSTOM_SHADER -> {
                if (shaderSource == null) {
                    issues.add(ValidationIssue.ERROR("Custom shader material must have shader source"))
                }
            }
            else -> { /* Other types have flexible requirements */ }
        }

        // Check texture references
        textures.values.forEach { textureSlot ->
            if (textureSlot.textureId.isNotBlank() && !textureSlot.textureId.matches(Regex("[a-zA-Z0-9_-]+"))) {
                issues.add(ValidationIssue.WARNING("Texture ID '${textureSlot.textureId}' has unusual characters"))
            }
        }

        return ValidationResult(
            isValid = issues.none { it.severity == ValidationSeverity.ERROR },
            issues = issues
        )
    }

    companion object {
        /**
         * Creates a basic PBR material with default values
         */
        fun createPBR(name: String): MaterialDefinition {
            val now = kotlinx.datetime.Clock.System.now()
            return MaterialDefinition(
                name = name,
                type = MaterialType.PHYSICAL,
                uniforms = mapOf(
                    "baseColor" to UniformValue(
                        type = UniformType.VEC3,
                        value = listOf(0.8f, 0.8f, 0.8f),
                        min = 0.0f,
                        max = 1.0f
                    ),
                    "roughness" to UniformValue(
                        type = UniformType.FLOAT,
                        value = 0.5f,
                        min = 0.0f,
                        max = 1.0f
                    ),
                    "metallic" to UniformValue(
                        type = UniformType.FLOAT,
                        value = 0.0f,
                        min = 0.0f,
                        max = 1.0f
                    )
                ),
                textures = emptyMap(),
                settings = MaterialSettings.default(),
                metadata = MaterialMetadata(
                    author = "System",
                    description = "Standard PBR material",
                    tags = listOf("pbr", "standard"),
                    created = now,
                    lastModified = now,
                    version = 1
                )
            )
        }

        /**
         * Creates an unlit material for UI or special effects
         */
        fun createUnlit(name: String, color: List<Float> = listOf(1.0f, 1.0f, 1.0f)): MaterialDefinition {
            val now = kotlinx.datetime.Clock.System.now()
            return MaterialDefinition(
                name = name,
                type = MaterialType.STANDARD, // Using standard type with unlit shader
                shaderSource = ShaderCode(
                    vertex = """
                        @vertex
                        fn vs_main(@location(0) position: vec3<f32>) -> @builtin(position) vec4<f32> {
                            return vec4<f32>(position, 1.0);
                        }
                    """.trimIndent(),
                    fragment = """
                        @group(0) @binding(0) var<uniform> baseColor: vec3<f32>;

                        @fragment
                        fn fs_main() -> @location(0) vec4<f32> {
                            return vec4<f32>(baseColor, 1.0);
                        }
                    """.trimIndent()
                ),
                uniforms = mapOf(
                    "baseColor" to UniformValue(
                        type = UniformType.VEC3,
                        value = color
                    )
                ),
                textures = emptyMap(),
                settings = MaterialSettings(
                    doubleSided = false,
                    transparent = false,
                    alphaTest = null,
                    blendMode = BlendMode.NORMAL,
                    depthWrite = true,
                    depthTest = true
                ),
                metadata = MaterialMetadata(
                    author = "System",
                    description = "Unlit material for UI and effects",
                    tags = listOf("unlit", "simple"),
                    created = now,
                    lastModified = now,
                    version = 1
                )
            )
        }
    }
}

/**
 * ShaderCode - Contains shader source code for vertex, fragment, and optional compute shaders
 */
@Serializable
data class ShaderCode(
    val vertex: String,
    val fragment: String,
    val compute: String? = null,
    val includes: List<String> = emptyList(),
    val defines: Map<String, String> = emptyMap()
) {
    init {
        require(vertex.isNotBlank()) { "Vertex shader cannot be empty" }
        require(fragment.isNotBlank()) { "Fragment shader cannot be empty" }
    }

    /**
     * Returns the vertex shader with includes and defines preprocessed
     */
    fun getProcessedVertexShader(): String {
        return preprocessShader(vertex)
    }

    /**
     * Returns the fragment shader with includes and defines preprocessed
     */
    fun getProcessedFragmentShader(): String {
        return preprocessShader(fragment)
    }

    private fun preprocessShader(source: String): String {
        var processed = source

        // Apply defines
        defines.forEach { (name, value) ->
            processed = "#define $name $value\n$processed"
        }

        // Process includes (simplified - would need actual file system access)
        includes.forEach { include ->
            processed = "// #include \"$include\"\n$processed"
        }

        return processed
    }
}

/**
 * UniformValue - Represents a uniform variable with type, value, and optional constraints
 */
@Serializable
data class UniformValue(
    val type: UniformType,
    val value: Any, // Actual value - must match the type
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val displayName: String? = null,
    val description: String? = null,
    val category: String? = null
)

/**
 * TextureSlot - Represents a texture binding in a material
 */
@Serializable
data class TextureSlot(
    val slotName: String, // Uniform name in shader
    val textureId: String, // Reference to texture asset
    val transform: TextureTransform = TextureTransform.default(),
    val samplerSettings: SamplerSettings = SamplerSettings.default()
)

/**
 * TextureTransform - 2D transformation applied to texture coordinates
 */
@Serializable
data class TextureTransform(
    val offsetU: Float = 0.0f,
    val offsetV: Float = 0.0f,
    val scaleU: Float = 1.0f,
    val scaleV: Float = 1.0f,
    val rotation: Float = 0.0f // In degrees
) {
    companion object {
        fun default(): TextureTransform = TextureTransform()
    }
}

/**
 * SamplerSettings - GPU sampler configuration for textures
 */
@Serializable
data class SamplerSettings(
    val wrapU: TextureWrap = TextureWrap.REPEAT,
    val wrapV: TextureWrap = TextureWrap.REPEAT,
    val magFilter: TextureFilter = TextureFilter.LINEAR,
    val minFilter: TextureFilter = TextureFilter.LINEAR_MIPMAP_LINEAR,
    val anisotropy: Float = 1.0f
) {
    companion object {
        fun default(): SamplerSettings = SamplerSettings()
    }
}

/**
 * MaterialSettings - Rendering settings for the material
 */
@Serializable
data class MaterialSettings(
    val doubleSided: Boolean = false,
    val transparent: Boolean = false,
    val alphaTest: Float? = null, // Alpha test threshold, null = disabled
    val blendMode: BlendMode = BlendMode.NORMAL,
    val depthWrite: Boolean = true,
    val depthTest: Boolean = true,
    val cullMode: CullMode = CullMode.BACK,
    val priority: Int = 0 // Rendering priority, higher = later
) {
    companion object {
        fun default(): MaterialSettings = MaterialSettings()
    }
}

/**
 * MaterialMetadata - Additional information about the material
 */
@Serializable
data class MaterialMetadata(
    val author: String = "Unknown",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val created: Instant,
    val lastModified: Instant,
    val version: Int = 1,
    val sourceUrl: String? = null,
    val license: String? = null,
    val preview: String? = null // Base64 encoded preview image
)

/**
 * ValidationResult - Result of material validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue>
)

/**
 * ValidationIssue - Individual validation issue
 */
data class ValidationIssue(
    val severity: ValidationSeverity,
    val message: String,
    val location: String? = null
) {
    companion object {
        fun ERROR(message: String, location: String? = null) =
            ValidationIssue(ValidationSeverity.ERROR, message, location)

        fun WARNING(message: String, location: String? = null) =
            ValidationIssue(ValidationSeverity.WARNING, message, location)

        fun INFO(message: String, location: String? = null) =
            ValidationIssue(ValidationSeverity.INFO, message, location)
    }
}

// Enums

@Serializable
enum class UniformType {
    FLOAT, VEC2, VEC3, VEC4,
    MATRIX3, MATRIX4,
    BOOL, INT,
    TEXTURE_2D, TEXTURE_CUBE, TEXTURE_3D
}

@Serializable
enum class BlendMode {
    NORMAL, ADD, MULTIPLY, SCREEN, OVERLAY, ALPHA
}

@Serializable
enum class CullMode {
    NONE, FRONT, BACK
}

enum class ValidationSeverity {
    ERROR, WARNING, INFO
}