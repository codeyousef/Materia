package io.materia.material.shader

/**
 * Shader validation utilities
 * Extracted from ShaderMaterial.kt for better separation of concerns
 */

object ShaderValidator {

    /**
     * Validate shader syntax for a specific stage
     */
    fun validateShaderSyntax(source: String, stage: ShaderStage, language: String): List<String> {
        val issues = mutableListOf<String>()

        // Basic syntax validation
        if (!source.contains("main")) {
            issues.add("Missing main function")
        }

        // Stage-specific validation
        when (stage) {
            ShaderStage.VERTEX -> {
                if (language == "WGSL" && !source.contains("@vertex")) {
                    issues.add("WGSL vertex shader missing @vertex attribute")
                }
            }

            ShaderStage.FRAGMENT -> {
                if (language == "WGSL" && !source.contains("@fragment")) {
                    issues.add("WGSL fragment shader missing @fragment attribute")
                }
            }

            ShaderStage.COMPUTE -> {
                if (language == "WGSL" && !source.contains("@compute")) {
                    issues.add("WGSL compute shader missing @compute attribute")
                }
            }

            ShaderStage.GEOMETRY,
            ShaderStage.TESSELLATION_CONTROL,
            ShaderStage.TESSELLATION_EVALUATION -> {
                // Not commonly used in WGSL, skip validation
            }
        }

        return issues
    }

    /**
     * Validate resource bindings for conflicts
     */
    fun validateBindings(
        textures: Map<String, TextureBinding>,
        storageBuffers: Map<String, StorageBuffer>
    ): List<String> {
        val issues = mutableListOf<String>()
        val usedBindings = mutableSetOf<Int>()

        textures.values.forEach { binding ->
            if (binding.binding >= 0) {
                if (usedBindings.contains(binding.binding)) {
                    issues.add("Binding conflict at binding ${binding.binding}")
                } else {
                    usedBindings.add(binding.binding)
                }
            }
        }

        storageBuffers.values.forEach { buffer ->
            if (buffer.binding >= 0) {
                if (usedBindings.contains(buffer.binding)) {
                    issues.add("Binding conflict at binding ${buffer.binding}")
                } else {
                    usedBindings.add(buffer.binding)
                }
            }
        }

        return issues
    }

    /**
     * Validate feature compatibility
     */
    fun validateFeatureCompatibility(
        features: Set<String>,
        hasComputeShader: Boolean,
        language: String
    ): List<String> {
        val issues = mutableListOf<String>()

        if (features.contains("tessellation") && hasComputeShader) {
            issues.add("Tessellation and compute shaders cannot be used together")
        }

        if (features.contains("geometry_shader") && language == "WGSL") {
            issues.add("Geometry shaders are not supported in WGSL")
        }

        return issues
    }

    /**
     * Calculate total uniform buffer size
     */
    fun calculateUniformBufferSize(uniforms: Map<String, ShaderUniform>): Int {
        return uniforms.values.sumOf { it.type.byteSize }
    }
}
