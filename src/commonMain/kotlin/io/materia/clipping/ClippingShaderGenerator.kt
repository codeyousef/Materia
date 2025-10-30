package io.materia.clipping

/**
 * Generates shader code for clipping plane integration.
 * Supports both hardware clipping (gl_ClipDistance) and fragment discard.
 */
class ClippingShaderGenerator {

    enum class ClippingMode {
        HARDWARE,     // Use gl_ClipDistance (more efficient)
        FRAGMENT,     // Use discard in fragment shader
        AUTO          // Choose based on platform capabilities
    }

    /**
     * Generate WGSL clipping code for WebGPU.
     */
    fun generateWGSL(
        numClippingPlanes: Int,
        unionClipping: Boolean = false,
        mode: ClippingMode = ClippingMode.FRAGMENT
    ): ClippingShaderCode {
        require(numClippingPlanes in 0..ClippingSupport.MAX_CLIPPING_PLANES) {
            "Invalid number of clipping planes: $numClippingPlanes"
        }

        if (numClippingPlanes == 0) {
            return ClippingShaderCode("", "", "")
        }

        val uniforms = """
            struct ClippingUniforms {
                planes: array<vec4<f32>, ${ClippingSupport.MAX_CLIPPING_PLANES}>,
                numPlanes: u32,
                unionMode: u32,
            }

            @group(1) @binding(0)
            var<uniform> clipping: ClippingUniforms;
        """.trimIndent()

        val vertexCode = when (mode) {
            ClippingMode.HARDWARE -> generateHardwareClippingWGSL(numClippingPlanes)
            else -> generateVertexClippingWGSL()
        }

        val fragmentCode = when (mode) {
            ClippingMode.FRAGMENT -> generateFragmentClippingWGSL(unionClipping)
            else -> ""
        }

        return ClippingShaderCode(uniforms, vertexCode, fragmentCode)
    }

    private fun generateHardwareClippingWGSL(numClippingPlanes: Int): String {
        return """
            // Hardware clipping using clip distances
            struct ClipDistances {
                @builtin(clip_distances) distances: array<f32, $numClippingPlanes>,
            }

            fn calculateClipDistances(worldPos: vec3<f32>) -> ClipDistances {
                var clipDist: ClipDistances;

                for (var i = 0u; i < clipping.numPlanes && i < ${numClippingPlanes}u; i++) {
                    let plane = clipping.planes[i];
                    clipDist.distances[i] = dot(worldPos, plane.xyz) + plane.w;
                }

                return clipDist;
            }
        """.trimIndent()
    }

    private fun generateVertexClippingWGSL(): String {
        return """
            // Pass world position to fragment shader for clipping
            @location(10) worldPositionForClipping: vec3<f32>,
        """.trimIndent()
    }

    private fun generateFragmentClippingWGSL(unionClipping: Boolean): String {
        return """
            // Fragment-based clipping
            fn applyClipping(worldPos: vec3<f32>) {
                if (clipping.numPlanes == 0u) {
                    return;
                }

                ${
            if (unionClipping) {
                """
                    // Union mode: must be behind all planes to be clipped
                    var clipped = true;

                    for (var i = 0u; i < clipping.numPlanes; i++) {
                        let plane = clipping.planes[i];
                        let distance = dot(worldPos, plane.xyz) + plane.w;

                        if (distance > 0.0) {
                            clipped = false;
                            break;
                        }
                    }

                    if (clipped) {
                        discard;
                    }
                    """.trimIndent()
            } else {
                """
                    // Intersection mode: clip if behind any plane
                    for (var i = 0u; i < clipping.numPlanes; i++) {
                        let plane = clipping.planes[i];
                        let distance = dot(worldPos, plane.xyz) + plane.w;

                        if (distance < 0.0) {
                            discard;
                        }
                    }
                    """.trimIndent()
            }
        }
            }

            // Call this in fragment shader main
            // applyClipping(input.worldPositionForClipping);
        """.trimIndent()
    }

    /**
     * Generate GLSL clipping code for WebGL.
     */
    fun generateGLSL(
        numClippingPlanes: Int,
        unionClipping: Boolean = false,
        mode: ClippingMode = ClippingMode.AUTO,
        glslVersion: Int = 300
    ): ClippingShaderCode {
        require(numClippingPlanes in 0..ClippingSupport.MAX_CLIPPING_PLANES) {
            "Invalid number of clipping planes: $numClippingPlanes"
        }

        if (numClippingPlanes == 0) {
            return ClippingShaderCode("", "", "")
        }

        val actualMode = if (mode == ClippingMode.AUTO) {
            // WebGL2 supports gl_ClipDistance
            if (glslVersion >= 300) ClippingMode.HARDWARE else ClippingMode.FRAGMENT
        } else mode

        val uniforms = """
            uniform vec4 clippingPlanes[${ClippingSupport.MAX_CLIPPING_PLANES}];
            uniform int numClippingPlanes;
            uniform bool unionClipping;
        """.trimIndent()

        val vertexCode = when (actualMode) {
            ClippingMode.HARDWARE -> generateHardwareClippingGLSL(numClippingPlanes, glslVersion)
            else -> generateVertexClippingGLSL(glslVersion)
        }

        val fragmentCode = when (actualMode) {
            ClippingMode.FRAGMENT -> generateFragmentClippingGLSL(unionClipping, glslVersion)
            else -> ""
        }

        return ClippingShaderCode(uniforms, vertexCode, fragmentCode)
    }

    private fun generateHardwareClippingGLSL(numClippingPlanes: Int, glslVersion: Int): String {
        return if (glslVersion >= 300) {
            """
            // Hardware clipping using gl_ClipDistance (WebGL2)
            out float gl_ClipDistance[$numClippingPlanes];

            void calculateClipDistances(vec3 worldPos) {
                for (int i = 0; i < numClippingPlanes && i < $numClippingPlanes; i++) {
                    vec4 plane = clippingPlanes[i];
                    gl_ClipDistance[i] = dot(worldPos, plane.xyz) + plane.w;
                }
            }
            """.trimIndent()
        } else {
            // WebGL1 doesn't support gl_ClipDistance
            generateVertexClippingGLSL(glslVersion)
        }
    }

    private fun generateVertexClippingGLSL(glslVersion: Int): String {
        return if (glslVersion >= 300) {
            """
            // Pass world position to fragment shader for clipping
            out vec3 vWorldPositionForClipping;

            void setupClipping(vec3 worldPos) {
                vWorldPositionForClipping = worldPos;
            }
            """.trimIndent()
        } else {
            """
            // Pass world position to fragment shader for clipping
            varying vec3 vWorldPositionForClipping;

            void setupClipping(vec3 worldPos) {
                vWorldPositionForClipping = worldPos;
            }
            """.trimIndent()
        }
    }

    private fun generateFragmentClippingGLSL(unionClipping: Boolean, glslVersion: Int): String {
        val varyingKeyword = if (glslVersion >= 300) "in" else "varying"

        return """
            $varyingKeyword vec3 vWorldPositionForClipping;

            void applyClipping() {
                if (numClippingPlanes == 0) return;

                ${
            if (unionClipping) {
                """
                    // Union mode: must be behind all planes to be clipped
                    bool clipped = true;

                    for (int i = 0; i < numClippingPlanes; i++) {
                        vec4 plane = clippingPlanes[i];
                        float distance = dot(vWorldPositionForClipping, plane.xyz) + plane.w;

                        if (distance > 0.0) {
                            clipped = false;
                            break;
                        }
                    }

                    if (clipped) discard;
                    """.trimIndent()
            } else {
                """
                    // Intersection mode: clip if behind any plane
                    for (int i = 0; i < numClippingPlanes; i++) {
                        vec4 plane = clippingPlanes[i];
                        float distance = dot(vWorldPositionForClipping, plane.xyz) + plane.w;

                        if (distance < 0.0) discard;
                    }
                    """.trimIndent()
            }
        }
            }
        """.trimIndent()
    }

    /**
     * Generate optimized clipping code for shadows.
     */
    fun generateShadowClipping(
        numClippingPlanes: Int,
        language: ShaderLanguage = ShaderLanguage.WGSL
    ): String {
        if (numClippingPlanes == 0) return ""

        return when (language) {
            ShaderLanguage.WGSL -> """
                fn applyShadowClipping(worldPos: vec3<f32>) {
                    // Simplified clipping for shadow passes
                    for (var i = 0u; i < clipping.numPlanes; i++) {
                        let plane = clipping.planes[i];
                        if (dot(worldPos, plane.xyz) + plane.w < 0.0) {
                            discard;
                        }
                    }
                }
            """.trimIndent()

            ShaderLanguage.GLSL -> """
                void applyShadowClipping(vec3 worldPos) {
                    // Simplified clipping for shadow passes
                    for (int i = 0; i < numClippingPlanes; i++) {
                        vec4 plane = clippingPlanes[i];
                        if (dot(worldPos, plane.xyz) + plane.w < 0.0) {
                            discard;
                        }
                    }
                }
            """.trimIndent()
        }
    }

    /**
     * Generate shader code for visualizing clipping planes.
     */
    fun generateClippingVisualization(
        language: ShaderLanguage = ShaderLanguage.WGSL
    ): String {
        return when (language) {
            ShaderLanguage.WGSL -> """
                fn visualizeClipping(worldPos: vec3<f32>) -> vec3<f32> {
                    var color = vec3<f32>(0.0);
                    var minDistance = 1000000.0;

                    for (var i = 0u; i < clipping.numPlanes; i++) {
                        let plane = clipping.planes[i];
                        let distance = abs(dot(worldPos, plane.xyz) + plane.w);

                        if (distance < minDistance) {
                            minDistance = distance;
                            // Color based on plane index
                            let hue = f32(i) / f32(clipping.numPlanes);
                            color = hsv2rgb(vec3<f32>(hue, 1.0, 1.0));
                        }
                    }

                    // Highlight near clipping boundaries
                    let edgeFactor = smoothstep(0.0, 0.1, minDistance);
                    return mix(color, vec3<f32>(1.0), 1.0 - edgeFactor);
                }

                fn hsv2rgb(hsv: vec3<f32>) -> vec3<f32> {
                    let h = hsv.x * 6.0;
                    let s = hsv.y;
                    let v = hsv.z;

                    let c = v * s;
                    let x = c * (1.0 - abs(((h % 2.0) - 1.0)));
                    let m = v - c;

                    var rgb: vec3<f32>;
                    if (h < 1.0) {
                        rgb = vec3<f32>(c, x, 0.0);
                    } else if (h < 2.0) {
                        rgb = vec3<f32>(x, c, 0.0);
                    } else if (h < 3.0) {
                        rgb = vec3<f32>(0.0, c, x);
                    } else if (h < 4.0) {
                        rgb = vec3<f32>(0.0, x, c);
                    } else if (h < 5.0) {
                        rgb = vec3<f32>(x, 0.0, c);
                    } else {
                        rgb = vec3<f32>(c, 0.0, x);
                    }

                    return rgb + m;
                }
            """.trimIndent()

            ShaderLanguage.GLSL -> """
                vec3 visualizeClipping(vec3 worldPos) {
                    vec3 color = vec3(0.0);
                    float minDistance = 1000000.0;

                    for (int i = 0; i < numClippingPlanes; i++) {
                        vec4 plane = clippingPlanes[i];
                        float distance = abs(dot(worldPos, plane.xyz) + plane.w);

                        if (distance < minDistance) {
                            minDistance = distance;
                            // Color based on plane index
                            float hue = float(i) / float(numClippingPlanes);
                            color = hsv2rgb(vec3(hue, 1.0, 1.0));
                        }
                    }

                    // Highlight near clipping boundaries
                    float edgeFactor = smoothstep(0.0, 0.1, minDistance);
                    return mix(color, vec3(1.0), 1.0 - edgeFactor);
                }

                vec3 hsv2rgb(vec3 hsv) {
                    float h = hsv.x * 6.0;
                    float s = hsv.y;
                    float v = hsv.z;

                    float c = v * s;
                    float x = c * (1.0 - abs(mod(h, 2.0) - 1.0));
                    float m = v - c;

                    vec3 rgb;
                    if (h < 1.0) {
                        rgb = vec3(c, x, 0.0);
                    } else if (h < 2.0) {
                        rgb = vec3(x, c, 0.0);
                    } else if (h < 3.0) {
                        rgb = vec3(0.0, c, x);
                    } else if (h < 4.0) {
                        rgb = vec3(0.0, x, c);
                    } else if (h < 5.0) {
                        rgb = vec3(x, 0.0, c);
                    } else {
                        rgb = vec3(c, 0.0, x);
                    }

                    return rgb + m;
                }
            """.trimIndent()
        }
    }

    enum class ShaderLanguage {
        WGSL,
        GLSL
    }
}

/**
 * Container for generated clipping shader code.
 */
data class ClippingShaderCode(
    val uniforms: String,
    val vertexCode: String,
    val fragmentCode: String
)