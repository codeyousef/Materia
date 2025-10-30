package io.materia.material.shader

/**
 * Shader preprocessing utilities
 * Handles defines, includes, and feature flags
 */

object ShaderPreprocessor {

    /**
     * Preprocess shader source with defines and includes
     */
    fun preprocessShaderSource(
        source: String,
        stage: ShaderStage,
        features: Set<String>,
        defines: Map<String, String>,
        includes: List<String>
    ): String {
        var processed = source

        // Add feature defines
        val featureDefines = features.joinToString("\n") { "#define ${it.uppercase()} 1" }
        processed = "$featureDefines\n$processed"

        // Add custom defines
        val customDefines = defines.entries.joinToString("\n") { "#define ${it.key} ${it.value}" }
        processed = "$customDefines\n$processed"

        // Process includes
        includes.forEach { includePath ->
            val includeContent = loadIncludeContent(includePath)
            processed = processed.replace("#include \"$includePath\"", includeContent)
        }

        // Add stage-specific defines
        processed = "#define STAGE_${stage.name} 1\n$processed"

        return processed
    }

    /**
     * Load content from include path
     */
    private fun loadIncludeContent(includePath: String): String {
        // Implementation would load include content from file system or embedded resources
        return "// Include: $includePath\n"
    }

    /**
     * Generate cache key for shader compilation
     */
    fun generateCacheKey(
        vertexShader: String,
        fragmentShader: String,
        computeShader: String,
        features: Set<String>,
        defines: Map<String, String>,
        target: String
    ): String {
        val keyComponents = listOf(
            vertexShader.hashCode(),
            fragmentShader.hashCode(),
            computeShader.hashCode(),
            features.hashCode(),
            defines.hashCode(),
            target.hashCode()
        )
        return keyComponents.joinToString("-")
    }
}
