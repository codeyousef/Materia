/**
 * FogShaderInjector - Generates and injects fog code into shaders
 *
 * Handles both linear and exponential squared fog effects by
 * injecting appropriate WGSL/GLSL code into material shaders.
 */
package io.materia.fog

import io.materia.core.math.Color

/**
 * Generates and injects fog shader code
 */
class FogShaderInjector {

    /**
     * Generate WGSL fog uniforms
     */
    fun generateFogUniforms(fog: FogBase?): String {
        return when (fog) {
            null -> ""
            is Fog -> """
                struct FogUniforms {
                    color: vec3<f32>,
                    near: f32,
                    far: f32,
                }
                @group(2) @binding(3) var<uniform> fog: FogUniforms;
            """.trimIndent()

            is FogExp2 -> """
                struct FogUniforms {
                    color: vec3<f32>,
                    density: f32,
                }
                @group(2) @binding(3) var<uniform> fog: FogUniforms;
            """.trimIndent()
        }
    }

    /**
     * Generate WGSL fog calculation in vertex shader
     */
    fun generateVertexFogCode(fog: FogBase?): String {
        return when (fog) {
            null -> ""
            else -> """
            // Calculate fog distance in view space
            let mvPosition = modelViewMatrix * vec4<f32>(position, 1.0);
            fogDepth = -mvPosition.z;
        """.trimIndent()
        }
    }

    /**
     * Generate WGSL fog calculation in fragment shader
     */
    fun generateFragmentFogCode(fog: FogBase?): String {
        return when (fog) {
            null -> ""
            is Fog -> """
                // Linear fog
                let fogFactor = smoothstep(fog.near, fog.far, fogDepth);
                finalColor = mix(finalColor, vec4<f32>(fog.color, 1.0), fogFactor);
            """.trimIndent()

            is FogExp2 -> """
                // Exponential squared fog
                let fogFactor = 1.0 - exp(-fog.density * fog.density * fogDepth * fogDepth);
                finalColor = mix(finalColor, vec4<f32>(fog.color, 1.0), fogFactor);
            """.trimIndent()
        }
    }

    /**
     * Generate complete fog shader chunk for material
     */
    fun generateFogShaderChunk(fog: FogBase?): FogShaderChunk {
        return when (fog) {
            null -> FogShaderChunk.EMPTY
            else -> {
                val uniforms = generateFogUniforms(fog)
                val vertexCode = generateVertexFogCode(fog)
                val fragmentCode = generateFragmentFogCode(fog)

                val vertexVaryings = """
                    @location(5) fogDepth: f32,
                """.trimIndent()

                val fragmentVaryings = """
                    @location(5) fogDepth: f32,
                """.trimIndent()

                FogShaderChunk(
                    uniforms = uniforms,
                    vertexVaryings = vertexVaryings,
                    fragmentVaryings = fragmentVaryings,
                    vertexCode = vertexCode,
                    fragmentCode = fragmentCode
                )
            }
        }
    }

    /**
     * Inject fog code into existing shader
     */
    fun injectFogIntoShader(
        vertexShader: String,
        fragmentShader: String,
        fog: FogBase?
    ): Pair<String, String> {
        return when (fog) {
            null -> vertexShader to fragmentShader
            else -> {
                val chunk = generateFogShaderChunk(fog)

                // Inject into vertex shader
                val modifiedVertex = buildString {
                    // Add fog uniforms at the beginning
                    appendLine(chunk.uniforms)

                    // Find the output structure and add fogDepth
                    val vertexWithFog = vertexShader.replace(
                        "struct VertexOutput {",
                        """struct VertexOutput {
                    ${chunk.vertexVaryings}"""
                    ).replace(
                        "// END_VERTEX_MAIN",
                        """${chunk.vertexCode}
                // END_VERTEX_MAIN"""
                    )

                    append(vertexWithFog)
                }

                // Inject into fragment shader
                val modifiedFragment = buildString {
                    // Add fog uniforms if not already present
                    if (!fragmentShader.contains("fog:")) {
                        appendLine(chunk.uniforms)
                    }

                    // Find the input structure and add fogDepth
                    val fragmentWithFog = fragmentShader.replace(
                        "struct FragmentInput {",
                        """struct FragmentInput {
                    ${chunk.fragmentVaryings}"""
                    ).replace(
                        "// APPLY_FOG",
                        chunk.fragmentCode
                    ).replace(
                        "return finalColor;",
                        """${chunk.fragmentCode}
                return finalColor;"""
                    )

                    append(fragmentWithFog)
                }

                modifiedVertex to modifiedFragment
            }
        }
    }

    /**
     * Generate GLSL fog code (for compatibility)
     */
    fun generateGLSLFogCode(fog: FogBase?): GLSLFogCode {
        return when (fog) {
            null -> GLSLFogCode.EMPTY
            is Fog -> GLSLFogCode(
                uniforms = """
                    uniform vec3 fogColor;
                    uniform float fogNear;
                    uniform float fogFar;
                """.trimIndent(),
                vertexCode = """
                    vFogDepth = -mvPosition.z;
                """.trimIndent(),
                fragmentCode = """
                    float fogFactor = smoothstep(fogNear, fogFar, vFogDepth);
                    gl_FragColor.rgb = mix(gl_FragColor.rgb, fogColor, fogFactor);
                """.trimIndent()
            )

            is FogExp2 -> GLSLFogCode(
                uniforms = """
                    uniform vec3 fogColor;
                    uniform float fogDensity;
                """.trimIndent(),
                vertexCode = """
                    vFogDepth = -mvPosition.z;
                """.trimIndent(),
                fragmentCode = """
                    float fogFactor = 1.0 - exp(-fogDensity * fogDensity * vFogDepth * vFogDepth);
                    gl_FragColor.rgb = mix(gl_FragColor.rgb, fogColor, fogFactor);
                """.trimIndent()
            )
        }
    }

    /**
     * Check if shader needs fog injection
     */
    fun shaderNeedsFog(shader: String): Boolean {
        return !shader.contains("fog:") && !shader.contains("fogDepth")
    }

    /**
     * Extract fog parameters for uniform buffer
     */
    fun getFogUniforms(fog: FogBase?): FloatArray {
        return when (fog) {
            null -> floatArrayOf()
            is Fog -> floatArrayOf(
                fog.color.r, fog.color.g, fog.color.b,
                fog.near,
                fog.far
            )

            is FogExp2 -> floatArrayOf(
                fog.color.r, fog.color.g, fog.color.b,
                fog.density
            )
        }
    }
}

/**
 * Container for fog shader code chunks
 */
data class FogShaderChunk(
    val uniforms: String,
    val vertexVaryings: String,
    val fragmentVaryings: String,
    val vertexCode: String,
    val fragmentCode: String
) {
    companion object {
        val EMPTY = FogShaderChunk("", "", "", "", "")
    }
}

/**
 * Container for GLSL fog code (compatibility)
 */
data class GLSLFogCode(
    val uniforms: String,
    val vertexCode: String,
    val fragmentCode: String
) {
    companion object {
        val EMPTY = GLSLFogCode("", "", "")
    }
}

/**
 * Fog calculation utilities
 */
object FogUtils {
    /**
     * Calculate linear fog factor
     */
    fun calculateLinearFogFactor(distance: Float, near: Float, far: Float): Float {
        return ((far - distance) / (far - near)).coerceIn(0f, 1f)
    }

    /**
     * Calculate exponential squared fog factor
     */
    fun calculateExp2FogFactor(distance: Float, density: Float): Float {
        val fogExponent = density * density * distance * distance
        return (1f - kotlin.math.exp(-fogExponent)).coerceIn(0f, 1f)
    }

    /**
     * Mix color with fog
     */
    fun applyFog(
        color: Color,
        fogColor: Color,
        fogFactor: Float
    ): Color {
        return Color(
            color.r * (1f - fogFactor) + fogColor.r * fogFactor,
            color.g * (1f - fogFactor) + fogColor.g * fogFactor,
            color.b * (1f - fogFactor) + fogColor.b * fogFactor
        )
    }
}