package io.materia.morph

/**
 * Generates shader code for morph target blending.
 * Supports both WGSL (WebGPU) and GLSL (WebGL fallback) generation.
 */
class MorphShaderGenerator {

    /**
     * Generate WGSL shader code for morph target blending.
     *
     * @param morphTargetCount Number of morph targets
     * @param includeNormals Whether to include normal morphing
     * @param morphTargetsRelative Whether morph targets are relative to base
     * @return WGSL shader code
     */
    fun generateWGSL(
        morphTargetCount: Int,
        includeNormals: Boolean = true,
        morphTargetsRelative: Boolean = true
    ): ShaderCode {
        val vertexStructFields = mutableListOf<String>()
        val vertexInputs = mutableListOf<String>()
        val morphCode = mutableListOf<String>()

        // Base attributes
        vertexStructFields.add("@location(0) position: vec3<f32>")
        if (includeNormals) {
            vertexStructFields.add("@location(1) normal: vec3<f32>")
        }

        // Morph target attributes
        var location = if (includeNormals) 2 else 1
        for (i in 0 until morphTargetCount) {
            vertexStructFields.add("@location($location) morphTarget${i}_position: vec3<f32>")
            location++
            if (includeNormals) {
                vertexStructFields.add("@location($location) morphTarget${i}_normal: vec3<f32>")
                location++
            }
        }

        // Generate morph blending code
        if (morphTargetsRelative) {
            // Relative morphing (add to base)
            morphCode.add("var morphedPosition = input.position;")
            if (includeNormals) {
                morphCode.add("var morphedNormal = input.normal;")
            }

            for (i in 0 until morphTargetCount) {
                morphCode.add("morphedPosition += input.morphTarget${i}_position * uniforms.morphInfluences[$i];")
                if (includeNormals) {
                    morphCode.add("morphedNormal += input.morphTarget${i}_normal * uniforms.morphInfluences[$i];")
                }
            }
        } else {
            // Absolute morphing (interpolate between targets)
            morphCode.add("var morphedPosition = input.position * (1.0 - uniforms.morphInfluenceSum);")
            if (includeNormals) {
                morphCode.add("var morphedNormal = input.normal * (1.0 - uniforms.morphInfluenceSum);")
            }

            for (i in 0 until morphTargetCount) {
                morphCode.add("morphedPosition += input.morphTarget${i}_position * uniforms.morphInfluences[$i];")
                if (includeNormals) {
                    morphCode.add("morphedNormal += input.morphTarget${i}_normal * uniforms.morphInfluences[$i];")
                }
            }
        }

        if (includeNormals) {
            morphCode.add("morphedNormal = normalize(morphedNormal);")
        }

        val vertexShader = """
            struct Uniforms {
                modelMatrix: mat4x4<f32>,
                viewMatrix: mat4x4<f32>,
                projectionMatrix: mat4x4<f32>,
                morphInfluences: array<f32, $morphTargetCount>,
                ${if (!morphTargetsRelative) "morphInfluenceSum: f32," else ""}
            }

            @group(0) @binding(0)
            var<uniform> uniforms: Uniforms;

            struct VertexInput {
                ${vertexStructFields.joinToString(",\n                ")}
            }

            struct VertexOutput {
                @builtin(position) position: vec4<f32>,
                @location(0) worldPosition: vec3<f32>,
                ${if (includeNormals) "@location(1) normal: vec3<f32>," else ""}
            }

            @vertex
            fn vertexMain(input: VertexInput) -> VertexOutput {
                var output: VertexOutput;

                // Morph target blending
                ${morphCode.joinToString("\n                ")}

                // Transform to world space
                let worldPos = uniforms.modelMatrix * vec4<f32>(morphedPosition, 1.0);
                output.worldPosition = worldPos.xyz;

                // Transform to clip space
                output.position = uniforms.projectionMatrix * uniforms.viewMatrix * worldPos;

                ${
            if (includeNormals) {
                """
                // Transform normal
                let normalMatrix = mat3x3<f32>(
                    uniforms.modelMatrix[0].xyz,
                    uniforms.modelMatrix[1].xyz,
                    uniforms.modelMatrix[2].xyz
                );
                output.normal = normalize(normalMatrix * morphedNormal);
                    """.trimIndent()
            } else ""
        }

                return output;
            }
        """.trimIndent()

        val fragmentShader = """
            struct VertexOutput {
                @builtin(position) position: vec4<f32>,
                @location(0) worldPosition: vec3<f32>,
                ${if (includeNormals) "@location(1) normal: vec3<f32>," else ""}
            }

            @fragment
            fn fragmentMain(input: VertexOutput) -> @location(0) vec4<f32> {
                ${
            if (includeNormals) {
                """
                // Simple diffuse shading for visualization
                let lightDir = normalize(vec3<f32>(1.0, 1.0, 1.0));
                let diffuse = max(dot(input.normal, lightDir), 0.0);
                return vec4<f32>(vec3<f32>(diffuse), 1.0);
                    """.trimIndent()
            } else {
                "return vec4<f32>(1.0, 1.0, 1.0, 1.0);"
            }
        }
            }
        """.trimIndent()

        return ShaderCode(
            vertex = vertexShader,
            fragment = fragmentShader,
            uniformsSize = calculateUniformsSize(morphTargetCount, morphTargetsRelative)
        )
    }

    /**
     * Generate GLSL shader code for morph target blending (WebGL fallback).
     *
     * @param morphTargetCount Number of morph targets
     * @param includeNormals Whether to include normal morphing
     * @param morphTargetsRelative Whether morph targets are relative to base
     * @param glslVersion GLSL version (300 for WebGL2)
     * @return GLSL shader code
     */
    fun generateGLSL(
        morphTargetCount: Int,
        includeNormals: Boolean = true,
        morphTargetsRelative: Boolean = true,
        glslVersion: Int = 300
    ): ShaderCode {
        val vertexAttributes = mutableListOf<String>()
        val morphCode = mutableListOf<String>()

        // Base attributes
        vertexAttributes.add("in vec3 position;")
        if (includeNormals) {
            vertexAttributes.add("in vec3 normal;")
        }

        // Morph target attributes
        for (i in 0 until morphTargetCount) {
            vertexAttributes.add("in vec3 morphTarget${i}_position;")
            if (includeNormals) {
                vertexAttributes.add("in vec3 morphTarget${i}_normal;")
            }
        }

        // Generate morph blending code
        if (morphTargetsRelative) {
            morphCode.add("vec3 morphedPosition = position;")
            if (includeNormals) {
                morphCode.add("vec3 morphedNormal = normal;")
            }

            for (i in 0 until morphTargetCount) {
                morphCode.add("morphedPosition += morphTarget${i}_position * morphInfluences[$i];")
                if (includeNormals) {
                    morphCode.add("morphedNormal += morphTarget${i}_normal * morphInfluences[$i];")
                }
            }
        } else {
            morphCode.add("float influenceSum = 0.0;")
            for (i in 0 until morphTargetCount) {
                morphCode.add("influenceSum += morphInfluences[$i];")
            }

            morphCode.add("vec3 morphedPosition = position * (1.0 - influenceSum);")
            if (includeNormals) {
                morphCode.add("vec3 morphedNormal = normal * (1.0 - influenceSum);")
            }

            for (i in 0 until morphTargetCount) {
                morphCode.add("morphedPosition += morphTarget${i}_position * morphInfluences[$i];")
                if (includeNormals) {
                    morphCode.add("morphedNormal += morphTarget${i}_normal * morphInfluences[$i];")
                }
            }
        }

        if (includeNormals) {
            morphCode.add("morphedNormal = normalize(morphedNormal);")
        }

        val vertexShader = """
            #version $glslVersion es
            precision highp float;

            ${vertexAttributes.joinToString("\n            ")}

            uniform mat4 modelMatrix;
            uniform mat4 viewMatrix;
            uniform mat4 projectionMatrix;
            uniform float morphInfluences[$morphTargetCount];

            out vec3 vWorldPosition;
            ${if (includeNormals) "out vec3 vNormal;" else ""}

            void main() {
                // Morph target blending
                ${morphCode.joinToString("\n                ")}

                // Transform to world space
                vec4 worldPos = modelMatrix * vec4(morphedPosition, 1.0);
                vWorldPosition = worldPos.xyz;

                // Transform to clip space
                gl_Position = projectionMatrix * viewMatrix * worldPos;

                ${
            if (includeNormals) {
                """
                // Transform normal
                mat3 normalMatrix = mat3(modelMatrix);
                vNormal = normalize(normalMatrix * morphedNormal);
                    """.trimIndent()
            } else ""
        }
            }
        """.trimIndent()

        val fragmentShader = """
            #version $glslVersion es
            precision highp float;

            in vec3 vWorldPosition;
            ${if (includeNormals) "in vec3 vNormal;" else ""}

            out vec4 fragColor;

            void main() {
                ${
            if (includeNormals) {
                """
                // Simple diffuse shading for visualization
                vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));
                float diffuse = max(dot(vNormal, lightDir), 0.0);
                fragColor = vec4(vec3(diffuse), 1.0);
                    """.trimIndent()
            } else {
                "fragColor = vec4(1.0, 1.0, 1.0, 1.0);"
            }
        }
            }
        """.trimIndent()

        return ShaderCode(
            vertex = vertexShader,
            fragment = fragmentShader,
            uniformsSize = calculateUniformsSize(morphTargetCount, morphTargetsRelative)
        )
    }

    /**
     * Generate optimized shader code using texture-based morph storage.
     * More efficient for large numbers of morph targets.
     *
     * @param morphTargetCount Number of morph targets
     * @param textureWidth Width of the morph texture
     * @return Optimized WGSL shader code
     */
    fun generateTextureBasedWGSL(
        morphTargetCount: Int,
        textureWidth: Int = 1024
    ): ShaderCode {
        val vertexShader = """
            struct Uniforms {
                modelMatrix: mat4x4<f32>,
                viewMatrix: mat4x4<f32>,
                projectionMatrix: mat4x4<f32>,
                morphInfluences: array<f32, $morphTargetCount>,
                morphTextureSize: vec2<f32>,
            }

            @group(0) @binding(0)
            var<uniform> uniforms: Uniforms;

            @group(0) @binding(1)
            var morphTexture: texture_2d<f32>;

            @group(0) @binding(2)
            var morphSampler: sampler;

            struct VertexInput {
                @builtin(vertex_index) vertexIndex: u32,
                @location(0) position: vec3<f32>,
                @location(1) normal: vec3<f32>,
            }

            struct VertexOutput {
                @builtin(position) position: vec4<f32>,
                @location(0) worldPosition: vec3<f32>,
                @location(1) normal: vec3<f32>,
            }

            fn sampleMorphTarget(vertexIndex: u32, morphIndex: u32) -> vec3<f32> {
                let texelIndex = vertexIndex * ${morphTargetCount}u + morphIndex;
                let u = f32(texelIndex % u32(uniforms.morphTextureSize.x)) / uniforms.morphTextureSize.x;
                let v = f32(texelIndex / u32(uniforms.morphTextureSize.x)) / uniforms.morphTextureSize.y;

                let texCoord = vec2<f32>(u, v);
                let morphData = textureSampleLevel(morphTexture, morphSampler, texCoord, 0.0);

                return morphData.xyz;
            }

            @vertex
            fn vertexMain(input: VertexInput) -> VertexOutput {
                var output: VertexOutput;

                // Texture-based morph target blending
                var morphedPosition = input.position;
                var morphedNormal = input.normal;

                for (var i = 0u; i < ${morphTargetCount}u; i++) {
                    let influence = uniforms.morphInfluences[i];
                    if (influence > 0.001) {
                        let morphPos = sampleMorphTarget(input.vertexIndex, i * 2u);
                        let morphNorm = sampleMorphTarget(input.vertexIndex, i * 2u + 1u);

                        morphedPosition += morphPos * influence;
                        morphedNormal += morphNorm * influence;
                    }
                }

                morphedNormal = normalize(morphedNormal);

                // Transform to world space
                let worldPos = uniforms.modelMatrix * vec4<f32>(morphedPosition, 1.0);
                output.worldPosition = worldPos.xyz;

                // Transform to clip space
                output.position = uniforms.projectionMatrix * uniforms.viewMatrix * worldPos;

                // Transform normal
                let normalMatrix = mat3x3<f32>(
                    uniforms.modelMatrix[0].xyz,
                    uniforms.modelMatrix[1].xyz,
                    uniforms.modelMatrix[2].xyz
                );
                output.normal = normalize(normalMatrix * morphedNormal);

                return output;
            }
        """.trimIndent()

        val fragmentShader = """
            struct VertexOutput {
                @builtin(position) position: vec4<f32>,
                @location(0) worldPosition: vec3<f32>,
                @location(1) normal: vec3<f32>,
            }

            @fragment
            fn fragmentMain(input: VertexOutput) -> @location(0) vec4<f32> {
                // Simple diffuse shading for visualization
                let lightDir = normalize(vec3<f32>(1.0, 1.0, 1.0));
                let diffuse = max(dot(input.normal, lightDir), 0.0);
                return vec4<f32>(vec3<f32>(diffuse), 1.0);
            }
        """.trimIndent()

        return ShaderCode(
            vertex = vertexShader,
            fragment = fragmentShader,
            uniformsSize = calculateUniformsSize(morphTargetCount, false) + 8 // +8 for texture size
        )
    }

    /**
     * Calculate the size of the uniforms buffer in bytes.
     *
     * @param morphTargetCount Number of morph targets
     * @param includeInfluenceSum Whether to include morphInfluenceSum uniform
     * @return Size in bytes
     */
    private fun calculateUniformsSize(
        morphTargetCount: Int,
        includeInfluenceSum: Boolean
    ): Int {
        var size = 0
        size += 64 * 3 // 3 mat4x4 (model, view, projection)
        size += 4 * morphTargetCount // morphInfluences array
        if (includeInfluenceSum) {
            size += 4 // morphInfluenceSum float
        }
        // Align to 16 bytes (WebGPU requirement)
        return (size + 15) and -16
    }
}

/**
 * Container for generated shader code.
 */
data class ShaderCode(
    val vertex: String,
    val fragment: String,
    val uniformsSize: Int
)

/**
 * Configuration for morph target shader generation.
 */
data class MorphShaderConfig(
    val morphTargetCount: Int,
    val includeNormals: Boolean = true,
    val morphTargetsRelative: Boolean = true,
    val useTextureStorage: Boolean = false,
    val maxMorphTargets: Int = 8
) {
    init {
        require(morphTargetCount > 0) { "Must have at least one morph target" }
        require(morphTargetCount <= maxMorphTargets) { "Exceeded maximum morph targets: $maxMorphTargets" }
    }
}