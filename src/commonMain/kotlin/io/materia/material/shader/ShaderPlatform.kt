package io.materia.material.shader

/**
 * Platform-specific shader utilities
 * Handles target language detection and compilation
 */

object ShaderPlatform {

    /**
     * Get target shader language for a specific platform
     */
    fun getTargetLanguageForPlatform(target: String): String {
        return when (target) {
            "WEBGPU" -> "WGSL"
            "VULKAN" -> "SPIRV"
            "OPENGL" -> "GLSL"
            "METAL" -> "MSL"
            "DIRECT3D" -> "HLSL"
            "AUTO" -> detectPlatformShaderLanguage()
            else -> "WGSL"
        }
    }

    /**
     * Detect the platform's native shader language
     */
    fun detectPlatformShaderLanguage(): String {
        // Implementation would detect current platform capabilities
        return "WGSL" // Default to WGSL
    }

    /**
     * Check if a feature is supported on the target platform
     */
    fun isFeatureSupported(feature: String, target: String): Boolean {
        return when (feature) {
            "geometry_shader" -> target !in listOf("WEBGPU", "METAL")
            "tessellation" -> target in listOf("VULKAN", "DIRECT3D", "OPENGL")
            "compute_shader" -> true // Supported on all modern platforms
            "ray_tracing" -> target in listOf("VULKAN", "DIRECT3D")
            else -> true
        }
    }
}
