/**
 * T024 - WebGL Shader Compiler Module
 *
 * Encapsulates shader compilation and linking functionality for WebGL rendering.
 * Provides robust error handling, validation, and support for vertex and fragment shaders.
 *
 * This module extracts and improves the shader compilation functionality from
 * BasicSceneExample.js.kt (lines 447-508) into a reusable, production-ready component.
 */

package io.materia.renderer.webgl

/**
 * Shader types supported by the compiler
 */
enum class ShaderType(val glType: Int) {
    VERTEX(35633), // WebGLRenderingContext.VERTEX_SHADER
    FRAGMENT(35632) // WebGLRenderingContext.FRAGMENT_SHADER
}

/**
 * Result type for shader compilation operations
 */
sealed class ShaderResult<out T> {
    data class Success<T>(val value: T) : ShaderResult<T>()
    data class Error(val message: String, val infoLog: String? = null) : ShaderResult<Nothing>()
}

/**
 * Represents a compiled shader program with its attributes and uniforms
 */
data class ShaderProgram(
    val program: dynamic, // WebGLProgram
    val attributes: Map<String, Int>,
    val uniforms: Map<String, dynamic>
) {
    /**
     * Use this shader program for rendering
     */
    fun use(gl: dynamic) {
        gl.useProgram(program)
    }

    /**
     * Clean up the shader program
     */
    fun dispose(gl: dynamic) {
        gl.deleteProgram(program)
    }
}

/**
 * WebGL Shader Compiler with comprehensive error handling and validation
 */
class ShaderCompiler(private val gl: dynamic) {

    /**
     * Compile a single shader from source code
     */
    fun compileShader(type: ShaderType, source: String): ShaderResult<dynamic> {
        val shader = gl.createShader(type.glType) ?: return ShaderResult.Error(
            "Failed to create ${type.name.lowercase()} shader object"
        )

        return try {
            // Set source and compile
            gl.shaderSource(shader, source)
            gl.compileShader(shader)

            // Check compilation status
            val compiled =
                (gl.getShaderParameter(shader, 35713) as? Boolean) ?: false // COMPILE_STATUS
            if (!compiled) {
                val infoLog = gl.getShaderInfoLog(shader) ?: "Unknown compilation error"
                gl.deleteShader(shader)
                ShaderResult.Error(
                    "Failed to compile ${type.name.lowercase()} shader",
                    infoLog
                )
            } else {
                ShaderResult.Success(shader)
            }
        } catch (e: Exception) {
            gl.deleteShader(shader)
            ShaderResult.Error(
                "Exception during ${type.name.lowercase()} shader compilation: ${e.message}"
            )
        }
    }

    /**
     * Create and link a shader program from vertex and fragment shaders
     */
    fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        attributes: List<String> = emptyList(),
        uniforms: List<String> = emptyList()
    ): ShaderResult<ShaderProgram> {
        // Compile vertex shader
        val vertexShader = when (val result = compileShader(ShaderType.VERTEX, vertexSource)) {
            is ShaderResult.Success -> result.value
            is ShaderResult.Error -> return result
        }

        // Compile fragment shader
        val fragmentShader =
            when (val result = compileShader(ShaderType.FRAGMENT, fragmentSource)) {
                is ShaderResult.Success -> result.value
                is ShaderResult.Error -> {
                    gl.deleteShader(vertexShader)
                    return result
                }
            }

        // Create and link program
        return linkProgram(vertexShader, fragmentShader, attributes, uniforms)
    }

    /**
     * Link compiled shaders into a program
     */
    private fun linkProgram(
        vertexShader: dynamic,
        fragmentShader: dynamic,
        attributes: List<String>,
        uniforms: List<String>
    ): ShaderResult<ShaderProgram> {
        val program = gl.createProgram()
        if (program == null) {
            gl.deleteShader(vertexShader)
            gl.deleteShader(fragmentShader)
            return ShaderResult.Error("Failed to create shader program")
        }

        return try {
            // Attach shaders
            gl.attachShader(program, vertexShader)
            gl.attachShader(program, fragmentShader)

            // Link program
            gl.linkProgram(program)

            // Check link status
            val linked =
                (gl.getProgramParameter(program, 35714) as? Boolean) ?: false // LINK_STATUS
            if (!linked) {
                val infoLog = gl.getProgramInfoLog(program) ?: "Unknown linking error"
                cleanupProgram(program, vertexShader, fragmentShader)
                ShaderResult.Error("Failed to link shader program", infoLog)
            } else {
                // Get attribute and uniform locations
                val attributeMap = mutableMapOf<String, Int>()
                val uniformMap = mutableMapOf<String, dynamic>()

                // Get attribute locations
                for (attribute in attributes) {
                    val location = gl.getAttribLocation(program, attribute)
                    if (location >= 0) {
                        attributeMap[attribute] = location
                    }
                }

                // Get uniform locations
                for (uniform in uniforms) {
                    val location = gl.getUniformLocation(program, uniform)
                    if (location != null) {
                        uniformMap[uniform] = location
                    }
                }

                // Clean up individual shaders (they're now part of the program)
                gl.deleteShader(vertexShader)
                gl.deleteShader(fragmentShader)

                ShaderResult.Success(
                    ShaderProgram(
                        program = program,
                        attributes = attributeMap,
                        uniforms = uniformMap
                    )
                )
            }
        } catch (e: Exception) {
            cleanupProgram(program, vertexShader, fragmentShader)
            ShaderResult.Error("Exception during shader program linking: ${e.message}")
        }
    }

    /**
     * Create a basic shader program for simple 3D rendering
     */
    fun createBasic3DProgram(): ShaderResult<ShaderProgram> {
        val vertexSource = """
            attribute vec3 aPosition;
            attribute vec3 aColor;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;

            uniform mat4 uProjectionMatrix;
            uniform mat4 uModelViewMatrix;
            uniform mat4 uNormalMatrix;

            varying vec3 vColor;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            varying vec3 vPosition;

            void main() {
                vec4 worldPosition = uModelViewMatrix * vec4(aPosition, 1.0);
                gl_Position = uProjectionMatrix * worldPosition;

                vColor = aColor;
                vNormal = normalize((uNormalMatrix * vec4(aNormal, 0.0)).xyz);
                vTexCoord = aTexCoord;
                vPosition = worldPosition.xyz;
            }
        """.trimIndent()

        val fragmentSource = """
            precision mediump float;

            varying vec3 vColor;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            varying vec3 vPosition;

            uniform vec3 uLightPosition;
            uniform vec3 uLightColor;
            uniform float uAmbientStrength;
            uniform bool uUseTexture;
            uniform sampler2D uTexture;

            void main() {
                vec3 baseColor = vColor;

                if (uUseTexture) {
                    vec4 texColor = texture2D(uTexture, vTexCoord);
                    baseColor = mix(baseColor, texColor.rgb, texColor.a);
                }

                // Simple Phong lighting
                vec3 ambient = uAmbientStrength * uLightColor;

                vec3 lightDir = normalize(uLightPosition - vPosition);
                float diff = max(dot(vNormal, lightDir), 0.0);
                vec3 diffuse = diff * uLightColor;

                vec3 result = (ambient + diffuse) * baseColor;
                gl_FragColor = vec4(result, 1.0);
            }
        """.trimIndent()

        return createProgram(
            vertexSource = vertexSource,
            fragmentSource = fragmentSource,
            attributes = listOf("aPosition", "aColor", "aNormal", "aTexCoord"),
            uniforms = listOf(
                "uProjectionMatrix", "uModelViewMatrix", "uNormalMatrix",
                "uLightPosition", "uLightColor", "uAmbientStrength",
                "uUseTexture", "uTexture"
            )
        )
    }

    /**
     * Create a simple unlit shader program (original demo functionality)
     */
    fun createSimpleProgram(): ShaderResult<ShaderProgram> {
        val vertexSource = """
            attribute vec3 aPosition;
            attribute vec3 aColor;
            uniform mat4 uProjectionMatrix;
            uniform mat4 uModelViewMatrix;
            varying vec3 vColor;

            void main() {
                gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPosition, 1.0);
                vColor = aColor;
            }
        """.trimIndent()

        val fragmentSource = """
            precision mediump float;
            varying vec3 vColor;

            void main() {
                gl_FragColor = vec4(vColor, 1.0);
            }
        """.trimIndent()

        return createProgram(
            vertexSource = vertexSource,
            fragmentSource = fragmentSource,
            attributes = listOf("aPosition", "aColor"),
            uniforms = listOf("uProjectionMatrix", "uModelViewMatrix")
        )
    }

    /**
     * Validate shader source for common issues
     */
    fun validateShaderSource(source: String, type: ShaderType): List<String> {
        val warnings = mutableListOf<String>()

        // Check for precision qualifiers in fragment shaders
        if (type == ShaderType.FRAGMENT && !source.contains("precision")) {
            warnings.add("Fragment shader missing precision qualifier - may cause issues on some devices")
        }

        // Check for main function
        if (!source.contains("void main()")) {
            warnings.add("Shader missing main() function")
        }

        // Check for required outputs
        when (type) {
            ShaderType.VERTEX -> {
                if (!source.contains("gl_Position")) {
                    warnings.add("Vertex shader must set gl_Position")
                }
            }

            ShaderType.FRAGMENT -> {
                if (!source.contains("gl_FragColor") && !source.contains("gl_FragData")) {
                    warnings.add("Fragment shader must set gl_FragColor or gl_FragData")
                }
            }
        }

        return warnings
    }

    /**
     * Get detailed information about a compiled program
     */
    fun getProgramInfo(program: dynamic): Map<String, Any> {
        val info = mutableMapOf<String, Any>()

        try {
            // Get number of active attributes
            val numAttributes =
                (gl.getProgramParameter(program, 35721) as? Int) ?: 0 // ACTIVE_ATTRIBUTES
            info["activeAttributes"] = numAttributes

            // Get number of active uniforms
            val numUniforms =
                (gl.getProgramParameter(program, 35718) as? Int) ?: 0 // ACTIVE_UNIFORMS
            info["activeUniforms"] = numUniforms

            // Get all active attributes
            val attributes = mutableListOf<String>()
            for (i in 0 until numAttributes) {
                val attribInfo = gl.getActiveAttrib(program, i)
                if (attribInfo != null) {
                    attributes.add(attribInfo.name)
                }
            }
            info["attributeNames"] = attributes

            // Get all active uniforms
            val uniforms = mutableListOf<String>()
            for (i in 0 until numUniforms) {
                val uniformInfo = gl.getActiveUniform(program, i)
                if (uniformInfo != null) {
                    uniforms.add(uniformInfo.name)
                }
            }
            info["uniformNames"] = uniforms

        } catch (e: Exception) {
            info["error"] = "Failed to get program info: ${e.message}"
        }

        return info
    }

    /**
     * Clean up program and associated shaders
     */
    private fun cleanupProgram(
        program: dynamic,
        vertexShader: dynamic,
        fragmentShader: dynamic
    ) {
        gl.deleteProgram(program)
        gl.deleteShader(vertexShader)
        gl.deleteShader(fragmentShader)
    }
}

/**
 * Extension functions for easier shader management
 */

/**
 * Set a uniform matrix4fv value safely
 */
fun ShaderProgram.setMatrix4(gl: dynamic, name: String, matrix: FloatArray) {
    uniforms[name]?.let { location ->
        gl.uniformMatrix4fv(location, false, matrix.toTypedArray())
    }
}

/**
 * Set a uniform vector3 value safely
 */
fun ShaderProgram.setVector3(gl: dynamic, name: String, x: Float, y: Float, z: Float) {
    uniforms[name]?.let { location ->
        gl.uniform3f(location, x, y, z)
    }
}

/**
 * Set a uniform float value safely
 */
fun ShaderProgram.setFloat(gl: dynamic, name: String, value: Float) {
    uniforms[name]?.let { location ->
        gl.uniform1f(location, value)
    }
}

/**
 * Set a uniform boolean value safely
 */
fun ShaderProgram.setBoolean(gl: dynamic, name: String, value: Boolean) {
    uniforms[name]?.let { location ->
        gl.uniform1i(location, if (value) 1 else 0)
    }
}

/**
 * Enable a vertex attribute safely
 */
fun ShaderProgram.enableAttribute(gl: dynamic, name: String) {
    attributes[name]?.let { location ->
        gl.enableVertexAttribArray(location)
    }
}

/**
 * Disable a vertex attribute safely
 */
fun ShaderProgram.disableAttribute(gl: dynamic, name: String) {
    attributes[name]?.let { location ->
        gl.disableVertexAttribArray(location)
    }
}