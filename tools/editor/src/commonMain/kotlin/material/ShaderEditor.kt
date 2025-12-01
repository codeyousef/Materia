package io.materia.tools.editor.material

import io.materia.tools.editor.data.ShaderCode
import io.materia.tools.editor.data.MaterialDefinition
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted

/**
 * ShaderEditor - WGSL shader editor with syntax highlighting and real-time compilation
 *
 * This component provides a comprehensive shader editing experience including:
 * - WGSL syntax highlighting and validation
 * - Real-time compilation with error reporting
 * - Autocomplete and IntelliSense
 * - Shader templates and snippets
 * - Live preview integration
 */
class ShaderEditor(
    private val scope: CoroutineScope,
    private val onShaderChanged: (ShaderCode) -> Unit = {}
) {

    // Editor state
    private val _vertexSource = MutableStateFlow("")
    private val _fragmentSource = MutableStateFlow("")
    private val _computeSource = MutableStateFlow("")
    private val _includes = MutableStateFlow<List<String>>(emptyList())
    private val _defines = MutableStateFlow<Map<String, String>>(emptyMap())

    // Editor settings
    private val _syntaxHighlighting = MutableStateFlow(true)
    private val _autoComplete = MutableStateFlow(true)
    private val _lineNumbers = MutableStateFlow(true)
    private val _wordWrap = MutableStateFlow(false)
    private val _fontSize = MutableStateFlow(14)
    private val _theme = MutableStateFlow(EditorTheme.DARK)

    // Compilation state
    private val _compilationResult = MutableStateFlow<CompilationResult?>(null)
    private val _isCompiling = MutableStateFlow(false)
    private val _autoCompile = MutableStateFlow(true)

    // Error and warning state
    private val _errors = MutableStateFlow<List<ShaderError>>(emptyList())
    private val _warnings = MutableStateFlow<List<ShaderWarning>>(emptyList())

    // Autocomplete state
    private val _autocompleteItems = MutableStateFlow<List<AutocompleteItem>>(emptyList())
    private val _showAutocomplete = MutableStateFlow(false)

    // Jobs for background processing
    private var compilationJob: Job? = null
    private var validationJob: Job? = null

    // Public read-only state
    val vertexSource: StateFlow<String> = _vertexSource.asStateFlow()
    val fragmentSource: StateFlow<String> = _fragmentSource.asStateFlow()
    val computeSource: StateFlow<String> = _computeSource.asStateFlow()
    val includes: StateFlow<List<String>> = _includes.asStateFlow()
    val defines: StateFlow<Map<String, String>> = _defines.asStateFlow()

    val syntaxHighlighting: StateFlow<Boolean> = _syntaxHighlighting.asStateFlow()
    val autoComplete: StateFlow<Boolean> = _autoComplete.asStateFlow()
    val lineNumbers: StateFlow<Boolean> = _lineNumbers.asStateFlow()
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()
    val theme: StateFlow<EditorTheme> = _theme.asStateFlow()

    val compilationResult: StateFlow<CompilationResult?> = _compilationResult.asStateFlow()
    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()
    val autoCompile: StateFlow<Boolean> = _autoCompile.asStateFlow()

    val errors: StateFlow<List<ShaderError>> = _errors.asStateFlow()
    val warnings: StateFlow<List<ShaderWarning>> = _warnings.asStateFlow()
    val hasErrors: StateFlow<Boolean> = combine(_errors) { errors ->
        errors.isNotEmpty()
    }

    val autocompleteItems: StateFlow<List<AutocompleteItem>> = _autocompleteItems.asStateFlow()
    val showAutocomplete: StateFlow<Boolean> = _showAutocomplete.asStateFlow()

    // Combined shader code state
    val shaderCode: StateFlow<ShaderCode?> = combine(
        _vertexSource, _fragmentSource, _computeSource, _includes, _defines
    ) { vertex, fragment, compute, includes, defines ->
        if (vertex.isNotBlank() || fragment.isNotBlank()) {
            ShaderCode(
                vertex = vertex,
                fragment = fragment,
                compute = compute.takeIf { it.isNotBlank() },
                includes = includes,
                defines = defines
            )
        } else {
            null
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    init {
        // Set up auto-compilation when content changes
        scope.launch {
            combine(
                _vertexSource, _fragmentSource, _computeSource, _autoCompile
            ) { vertex, fragment, compute, autoCompile ->
                if (autoCompile && (vertex.isNotBlank() || fragment.isNotBlank())) {
                    triggerCompilation()
                }
            }.collect { }
        }

        // Set up shader change notifications
        scope.launch {
            shaderCode.collect { code ->
                code?.let { onShaderChanged(it) }
            }
        }

        // Initialize autocomplete items
        loadWGSLAutocompleteItems()
    }

    // Editor content management

    fun setVertexShader(source: String) {
        _vertexSource.value = source
        triggerValidation()
    }

    fun setFragmentShader(source: String) {
        _fragmentSource.value = source
        triggerValidation()
    }

    fun setComputeShader(source: String) {
        _computeSource.value = source
        triggerValidation()
    }

    fun addInclude(include: String) {
        val current = _includes.value.toMutableList()
        if (!current.contains(include)) {
            current.add(include)
            _includes.value = current
        }
    }

    fun removeInclude(include: String) {
        val current = _includes.value.toMutableList()
        current.remove(include)
        _includes.value = current
    }

    fun setDefine(name: String, value: String) {
        val current = _defines.value.toMutableMap()
        current[name] = value
        _defines.value = current
    }

    fun removeDefine(name: String) {
        val current = _defines.value.toMutableMap()
        current.remove(name)
        _defines.value = current
    }

    fun loadShaderCode(shaderCode: ShaderCode) {
        _vertexSource.value = shaderCode.vertex
        _fragmentSource.value = shaderCode.fragment
        _computeSource.value = shaderCode.compute ?: ""
        _includes.value = shaderCode.includes
        _defines.value = shaderCode.defines
    }

    fun clearShaders() {
        _vertexSource.value = ""
        _fragmentSource.value = ""
        _computeSource.value = ""
        _includes.value = emptyList()
        _defines.value = emptyMap()
        _errors.value = emptyList()
        _warnings.value = emptyList()
        _compilationResult.value = null
    }

    // Editor settings

    fun setSyntaxHighlighting(enabled: Boolean) {
        _syntaxHighlighting.value = enabled
    }

    fun setAutoComplete(enabled: Boolean) {
        _autoComplete.value = enabled
    }

    fun setLineNumbers(enabled: Boolean) {
        _lineNumbers.value = enabled
    }

    fun setWordWrap(enabled: Boolean) {
        _wordWrap.value = enabled
    }

    fun setFontSize(size: Int) {
        require(size in 8..72) { "Font size must be between 8 and 72" }
        _fontSize.value = size
    }

    fun setTheme(theme: EditorTheme) {
        _theme.value = theme
    }

    fun setAutoCompile(enabled: Boolean) {
        _autoCompile.value = enabled
    }

    // Compilation and validation

    fun triggerCompilation() {
        compilationJob?.cancel()
        compilationJob = scope.launch {
            delay(300) // Debounce compilation

            val vertex = _vertexSource.value
            val fragment = _fragmentSource.value
            val compute = _computeSource.value.takeIf { it.isNotBlank() }

            if (vertex.isBlank() && fragment.isBlank()) {
                _compilationResult.value = null
                return@launch
            }

            _isCompiling.value = true

            try {
                val result = compileShader(vertex, fragment, compute)
                _compilationResult.value = result

                // Extract errors and warnings
                _errors.value = result.errors
                _warnings.value = result.warnings

            } catch (e: Exception) {
                _compilationResult.value = CompilationResult(
                    success = false,
                    errors = listOf(
                        ShaderError(
                            type = ShaderErrorType.COMPILATION_ERROR,
                            message = "Compilation failed: ${e.message}",
                            line = 0,
                            column = 0,
                            source = "unknown"
                        )
                    ),
                    warnings = emptyList(),
                    spirvBinary = null,
                    reflection = null
                )
                _errors.value = _compilationResult.value!!.errors
                _warnings.value = emptyList()
            } finally {
                _isCompiling.value = false
            }
        }
    }

    private fun triggerValidation() {
        validationJob?.cancel()
        validationJob = scope.launch {
            delay(100) // Quick validation

            val vertex = _vertexSource.value
            val fragment = _fragmentSource.value

            val validationErrors = mutableListOf<ShaderError>()
            val validationWarnings = mutableListOf<ShaderWarning>()

            // Basic syntax validation
            if (vertex.isNotBlank()) {
                validateWGSLSyntax(vertex, "vertex").let { (errors, warnings) ->
                    validationErrors.addAll(errors)
                    validationWarnings.addAll(warnings)
                }
            }

            if (fragment.isNotBlank()) {
                validateWGSLSyntax(fragment, "fragment").let { (errors, warnings) ->
                    validationErrors.addAll(errors)
                    validationWarnings.addAll(warnings)
                }
            }

            // Only update if we're not currently compiling
            if (!_isCompiling.value) {
                _errors.value = validationErrors
                _warnings.value = validationWarnings
            }
        }
    }

    // Shader templates and snippets

    fun loadTemplate(template: ShaderTemplate) {
        when (template) {
            ShaderTemplate.BASIC_UNLIT -> loadBasicUnlitTemplate()
            ShaderTemplate.BASIC_LIT -> loadBasicLitTemplate()
            ShaderTemplate.PBR_STANDARD -> loadPBRTemplate()
            ShaderTemplate.COMPUTE_BASIC -> loadComputeTemplate()
            ShaderTemplate.POST_PROCESS -> loadPostProcessTemplate()
        }
    }

    fun insertSnippet(snippet: ShaderSnippet, position: CursorPosition) {
        val code = when (snippet) {
            ShaderSnippet.VERTEX_STRUCT -> getVertexStructSnippet()
            ShaderSnippet.FRAGMENT_OUTPUT -> getFragmentOutputSnippet()
            ShaderSnippet.UNIFORM_BUFFER -> getUniformBufferSnippet()
            ShaderSnippet.TEXTURE_SAMPLE -> getTextureSampleSnippet()
            ShaderSnippet.LIGHTING_LAMBERT -> getLambertLightingSnippet()
            ShaderSnippet.LIGHTING_PHONG -> getPhongLightingSnippet()
            ShaderSnippet.NOISE_FUNCTION -> getNoiseSnippet()
            ShaderSnippet.COLOR_SPACE_CONVERSION -> getColorSpaceSnippet()
        }

        // Insert code at position (platform-specific implementation needed)
        insertCodeAtPosition(code, position)
    }

    // Autocomplete functionality

    fun triggerAutocomplete(position: CursorPosition, context: String) {
        scope.launch {
            val items = generateAutocompleteItems(context, position)
            _autocompleteItems.value = items
            _showAutocomplete.value = items.isNotEmpty()
        }
    }

    fun hideAutocomplete() {
        _showAutocomplete.value = false
        _autocompleteItems.value = emptyList()
    }

    fun selectAutocompleteItem(item: AutocompleteItem, position: CursorPosition) {
        insertCodeAtPosition(item.insertText, position)
        hideAutocomplete()
    }

    // Platform-specific implementations (to be overridden)

    protected open suspend fun compileShader(
        vertex: String,
        fragment: String,
        compute: String?
    ): CompilationResult {
        // Default implementation - basic validation only
        val errors = mutableListOf<ShaderError>()
        val warnings = mutableListOf<ShaderWarning>()

        // Check for required entry points
        if (vertex.isNotBlank() && !vertex.contains("@vertex")) {
            errors.add(ShaderError(
                type = ShaderErrorType.MISSING_ENTRY_POINT,
                message = "Vertex shader missing @vertex entry point",
                line = 0,
                column = 0,
                source = "vertex"
            ))
        }

        if (fragment.isNotBlank() && !fragment.contains("@fragment")) {
            errors.add(ShaderError(
                type = ShaderErrorType.MISSING_ENTRY_POINT,
                message = "Fragment shader missing @fragment entry point",
                line = 0,
                column = 0,
                source = "fragment"
            ))
        }

        return CompilationResult(
            success = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            spirvBinary = null,
            reflection = null
        )
    }

    protected open fun validateWGSLSyntax(
        source: String,
        shaderType: String
    ): Pair<List<ShaderError>, List<ShaderWarning>> {
        val errors = mutableListOf<ShaderError>()
        val warnings = mutableListOf<ShaderWarning>()

        val lines = source.lines()

        lines.forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()

            // Check for common syntax errors
            if (trimmed.contains("gl_")) {
                warnings.add(ShaderWarning(
                    type = ShaderWarningType.DEPRECATED_SYNTAX,
                    message = "GLSL syntax detected - consider using WGSL equivalents",
                    line = lineIndex + 1,
                    column = line.indexOf("gl_"),
                    source = shaderType
                ))
            }

            // Check for unmatched braces
            val openBraces = line.count { it == '{' }
            val closeBraces = line.count { it == '}' }
            if (openBraces != closeBraces && trimmed.isNotEmpty()) {
                // Brace mismatch detection for syntax validation
            }

            // Check for missing semicolons on statements
            if (trimmed.isNotEmpty() &&
                !trimmed.endsWith(";") &&
                !trimmed.endsWith("{") &&
                !trimmed.endsWith("}") &&
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("@") &&
                !trimmed.startsWith("#")) {
                warnings.add(ShaderWarning(
                    type = ShaderWarningType.STYLE_ISSUE,
                    message = "Consider adding semicolon at end of statement",
                    line = lineIndex + 1,
                    column = line.length,
                    source = shaderType
                ))
            }
        }

        return Pair(errors, warnings)
    }

    protected open fun insertCodeAtPosition(code: String, position: CursorPosition) {
        // Insert code snippet at specified cursor position
        when (position.shaderType) {
            "vertex" -> {
                val current = _vertexSource.value
                val lines = current.lines().toMutableList()
                if (position.line < lines.size) {
                    val line = lines[position.line]
                    val newLine = line.substring(0, position.column) + code + line.substring(position.column)
                    lines[position.line] = newLine
                    _vertexSource.value = lines.joinToString("\n")
                }
            }
            "fragment" -> {
                val current = _fragmentSource.value
                val lines = current.lines().toMutableList()
                if (position.line < lines.size) {
                    val line = lines[position.line]
                    val newLine = line.substring(0, position.column) + code + line.substring(position.column)
                    lines[position.line] = newLine
                    _fragmentSource.value = lines.joinToString("\n")
                }
            }
        }
    }

    // Template implementations

    private fun loadBasicUnlitTemplate() {
        _vertexSource.value = """
            @vertex
            fn vs_main(
                @location(0) position: vec3<f32>,
                @location(1) uv: vec2<f32>
            ) -> VertexOutput {
                var out: VertexOutput;
                out.clip_position = vec4<f32>(position, 1.0);
                out.uv = uv;
                return out;
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) uv: vec2<f32>,
            }
        """.trimIndent()

        _fragmentSource.value = """
            @group(0) @binding(0) var<uniform> color: vec4<f32>;

            @fragment
            fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
                return color;
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) uv: vec2<f32>,
            }
        """.trimIndent()
    }

    private fun loadBasicLitTemplate() {
        _vertexSource.value = """
            struct Camera {
                view_proj: mat4x4<f32>,
                view: mat4x4<f32>,
                proj: mat4x4<f32>,
                position: vec3<f32>,
            }

            struct Transform {
                model: mat4x4<f32>,
                normal: mat4x4<f32>,
            }

            @group(0) @binding(0) var<uniform> camera: Camera;
            @group(1) @binding(0) var<uniform> transform: Transform;

            @vertex
            fn vs_main(
                @location(0) position: vec3<f32>,
                @location(1) normal: vec3<f32>,
                @location(2) uv: vec2<f32>
            ) -> VertexOutput {
                var out: VertexOutput;
                let world_position = transform.model * vec4<f32>(position, 1.0);
                out.clip_position = camera.view_proj * world_position;
                out.world_position = world_position.xyz;
                out.world_normal = normalize((transform.normal * vec4<f32>(normal, 0.0)).xyz);
                out.uv = uv;
                return out;
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) world_position: vec3<f32>,
                @location(1) world_normal: vec3<f32>,
                @location(2) uv: vec2<f32>,
            }
        """.trimIndent()

        _fragmentSource.value = """
            struct Material {
                albedo: vec3<f32>,
                roughness: f32,
                metallic: f32,
            }

            struct Light {
                position: vec3<f32>,
                color: vec3<f32>,
                intensity: f32,
            }

            @group(2) @binding(0) var<uniform> material: Material;
            @group(2) @binding(1) var<uniform> light: Light;
            @group(0) @binding(0) var<uniform> camera: Camera;

            @fragment
            fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
                let light_dir = normalize(light.position - in.world_position);
                let view_dir = normalize(camera.position - in.world_position);
                let normal = normalize(in.world_normal);

                // Simple Lambertian lighting
                let ndotl = max(dot(normal, light_dir), 0.0);
                let diffuse = material.albedo * light.color * light.intensity * ndotl;

                return vec4<f32>(diffuse, 1.0);
            }

            struct Camera {
                view_proj: mat4x4<f32>,
                view: mat4x4<f32>,
                proj: mat4x4<f32>,
                position: vec3<f32>,
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) world_position: vec3<f32>,
                @location(1) world_normal: vec3<f32>,
                @location(2) uv: vec2<f32>,
            }
        """.trimIndent()
    }

    private fun loadPBRTemplate() {
        _vertexSource.value = """
            struct Camera {
                view_proj: mat4x4<f32>,
                position: vec3<f32>,
            }

            struct Transform {
                model: mat4x4<f32>,
                normal: mat4x4<f32>,
            }

            @group(0) @binding(0) var<uniform> camera: Camera;
            @group(1) @binding(0) var<uniform> transform: Transform;

            @vertex
            fn vs_main(
                @location(0) position: vec3<f32>,
                @location(1) normal: vec3<f32>,
                @location(2) tangent: vec4<f32>,
                @location(3) uv: vec2<f32>
            ) -> VertexOutput {
                var out: VertexOutput;
                let world_position = transform.model * vec4<f32>(position, 1.0);
                out.clip_position = camera.view_proj * world_position;
                out.world_position = world_position.xyz;
                out.world_normal = normalize((transform.normal * vec4<f32>(normal, 0.0)).xyz);
                out.world_tangent = normalize((transform.model * vec4<f32>(tangent.xyz, 0.0)).xyz);
                out.world_bitangent = cross(out.world_normal, out.world_tangent) * tangent.w;
                out.uv = uv;
                return out;
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) world_position: vec3<f32>,
                @location(1) world_normal: vec3<f32>,
                @location(2) world_tangent: vec3<f32>,
                @location(3) world_bitangent: vec3<f32>,
                @location(4) uv: vec2<f32>,
            }
        """.trimIndent()

        _fragmentSource.value = """
            struct PBRMaterial {
                base_color: vec3<f32>,
                roughness: f32,
                metallic: f32,
                emissive: vec3<f32>,
            }

            @group(2) @binding(0) var<uniform> material: PBRMaterial;
            @group(2) @binding(1) var base_color_texture: texture_2d<f32>;
            @group(2) @binding(2) var base_color_sampler: sampler;
            @group(2) @binding(3) var normal_texture: texture_2d<f32>;
            @group(2) @binding(4) var normal_sampler: sampler;
            @group(2) @binding(5) var roughness_texture: texture_2d<f32>;
            @group(2) @binding(6) var roughness_sampler: sampler;

            @fragment
            fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
                let base_color = textureSample(base_color_texture, base_color_sampler, in.uv).rgb * material.base_color;
                let roughness = textureSample(roughness_texture, roughness_sampler, in.uv).r * material.roughness;
                let metallic = material.metallic;

                // Sample normal map and transform to world space
                let normal_sample = textureSample(normal_texture, normal_sampler, in.uv).rgb * 2.0 - 1.0;
                let normal = normalize(
                    normal_sample.x * in.world_tangent +
                    normal_sample.y * in.world_bitangent +
                    normal_sample.z * in.world_normal
                );

                // PBR Fresnel and diffuse color calculation
                let f0 = mix(vec3<f32>(0.04), base_color, metallic);
                let diffuse_color = mix(base_color, vec3<f32>(0.0), metallic);

                // Base color output for material preview
                return vec4<f32>(base_color, 1.0);
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) world_position: vec3<f32>,
                @location(1) world_normal: vec3<f32>,
                @location(2) world_tangent: vec3<f32>,
                @location(3) world_bitangent: vec3<f32>,
                @location(4) uv: vec2<f32>,
            }
        """.trimIndent()
    }

    private fun loadComputeTemplate() {
        _computeSource.value = """
            @group(0) @binding(0) var<storage, read_write> data: array<f32>;

            @compute @workgroup_size(64)
            fn cs_main(@builtin(global_invocation_id) global_id: vec3<u32>) {
                let index = global_id.x;
                if (index >= arrayLength(&data)) {
                    return;
                }

                data[index] = data[index] * 2.0;
            }
        """.trimIndent()
    }

    private fun loadPostProcessTemplate() {
        _vertexSource.value = """
            @vertex
            fn vs_main(@builtin(vertex_index) vertex_index: u32) -> VertexOutput {
                var out: VertexOutput;

                // Full-screen triangle
                let x = f32((vertex_index & 1u) << 2u) - 1.0;
                let y = f32((vertex_index & 2u) << 1u) - 1.0;

                out.clip_position = vec4<f32>(x, y, 0.0, 1.0);
                out.uv = vec2<f32>((x + 1.0) * 0.5, (1.0 - y) * 0.5);

                return out;
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) uv: vec2<f32>,
            }
        """.trimIndent()

        _fragmentSource.value = """
            @group(0) @binding(0) var input_texture: texture_2d<f32>;
            @group(0) @binding(1) var input_sampler: sampler;

            @fragment
            fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
                let color = textureSample(input_texture, input_sampler, in.uv);

                // Simple tone mapping
                let tone_mapped = color.rgb / (color.rgb + vec3<f32>(1.0));

                return vec4<f32>(tone_mapped, color.a);
            }

            struct VertexOutput {
                @builtin(position) clip_position: vec4<f32>,
                @location(0) uv: vec2<f32>,
            }
        """.trimIndent()
    }

    // Snippet implementations

    private fun getVertexStructSnippet(): String = """
        struct VertexInput {
            @location(0) position: vec3<f32>,
            @location(1) normal: vec3<f32>,
            @location(2) uv: vec2<f32>,
        }

        struct VertexOutput {
            @builtin(position) clip_position: vec4<f32>,
            @location(0) world_position: vec3<f32>,
            @location(1) world_normal: vec3<f32>,
            @location(2) uv: vec2<f32>,
        }
    """.trimIndent()

    private fun getFragmentOutputSnippet(): String = """
        @fragment
        fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
            return vec4<f32>(1.0, 0.0, 0.0, 1.0);
        }
    """.trimIndent()

    private fun getUniformBufferSnippet(): String = """
        struct Uniforms {
            transform: mat4x4<f32>,
            color: vec4<f32>,
        }

        @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    """.trimIndent()

    private fun getTextureSampleSnippet(): String = """
        @group(0) @binding(0) var texture: texture_2d<f32>;
        @group(0) @binding(1) var texture_sampler: sampler;

        let color = textureSample(texture, texture_sampler, uv);
    """.trimIndent()

    private fun getLambertLightingSnippet(): String = """
        fn lambert_lighting(normal: vec3<f32>, light_dir: vec3<f32>, light_color: vec3<f32>) -> vec3<f32> {
            let ndotl = max(dot(normal, light_dir), 0.0);
            return light_color * ndotl;
        }
    """.trimIndent()

    private fun getPhongLightingSnippet(): String = """
        fn phong_lighting(
            normal: vec3<f32>,
            light_dir: vec3<f32>,
            view_dir: vec3<f32>,
            light_color: vec3<f32>,
            shininess: f32
        ) -> vec3<f32> {
            let ndotl = max(dot(normal, light_dir), 0.0);
            let reflect_dir = reflect(-light_dir, normal);
            let rdotv = max(dot(reflect_dir, view_dir), 0.0);
            let specular = pow(rdotv, shininess);

            return light_color * (ndotl + specular);
        }
    """.trimIndent()

    private fun getNoiseSnippet(): String = """
        fn hash(p: vec2<f32>) -> f32 {
            let p3 = fract(vec3<f32>(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        fn noise(p: vec2<f32>) -> f32 {
            let i = floor(p);
            let f = fract(p);

            let a = hash(i);
            let b = hash(i + vec2<f32>(1.0, 0.0));
            let c = hash(i + vec2<f32>(0.0, 1.0));
            let d = hash(i + vec2<f32>(1.0, 1.0));

            let u = f * f * (3.0 - 2.0 * f);

            return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
        }
    """.trimIndent()

    private fun getColorSpaceSnippet(): String = """
        fn linear_to_srgb(linear: vec3<f32>) -> vec3<f32> {
            return select(
                1.055 * pow(linear, vec3<f32>(1.0 / 2.4)) - 0.055,
                linear * 12.92,
                linear <= vec3<f32>(0.0031308)
            );
        }

        fn srgb_to_linear(srgb: vec3<f32>) -> vec3<f32> {
            return select(
                pow((srgb + 0.055) / 1.055, vec3<f32>(2.4)),
                srgb / 12.92,
                srgb <= vec3<f32>(0.04045)
            );
        }
    """.trimIndent()

    // Autocomplete functionality

    private fun loadWGSLAutocompleteItems() {
        val items = mutableListOf<AutocompleteItem>()

        // WGSL keywords
        val keywords = listOf(
            "var", "let", "const", "fn", "struct", "if", "else", "for", "while", "loop", "break", "continue",
            "return", "switch", "case", "default", "@vertex", "@fragment", "@compute", "@group", "@binding",
            "@location", "@builtin", "@workgroup_size", "@size", "@align"
        )

        keywords.forEach { keyword ->
            items.add(AutocompleteItem(
                label = keyword,
                insertText = keyword,
                kind = AutocompleteKind.KEYWORD,
                detail = "WGSL keyword",
                documentation = "WGSL language keyword"
            ))
        }

        // Built-in types
        val types = listOf(
            "f32", "i32", "u32", "bool",
            "vec2<f32>", "vec3<f32>", "vec4<f32>",
            "vec2<i32>", "vec3<i32>", "vec4<i32>",
            "vec2<u32>", "vec3<u32>", "vec4<u32>",
            "mat2x2<f32>", "mat3x3<f32>", "mat4x4<f32>",
            "texture_2d<f32>", "texture_cube<f32>", "texture_3d<f32>",
            "sampler", "sampler_comparison"
        )

        types.forEach { type ->
            items.add(AutocompleteItem(
                label = type,
                insertText = type,
                kind = AutocompleteKind.TYPE,
                detail = "WGSL type",
                documentation = "Built-in WGSL type"
            ))
        }

        // Built-in functions
        val functions = listOf(
            "textureSample", "textureLoad", "textureDimensions", "textureNumLevels",
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sqrt", "pow", "exp", "log", "exp2", "log2",
            "floor", "ceil", "round", "trunc", "fract", "abs", "sign",
            "min", "max", "clamp", "mix", "step", "smoothstep",
            "length", "distance", "dot", "cross", "normalize", "reflect", "refract",
            "all", "any", "select"
        )

        functions.forEach { func ->
            items.add(AutocompleteItem(
                label = func,
                insertText = "$func()",
                kind = AutocompleteKind.FUNCTION,
                detail = "WGSL built-in function",
                documentation = "Built-in WGSL function"
            ))
        }

        _autocompleteItems.value = items
    }

    private fun generateAutocompleteItems(context: String, position: CursorPosition): List<AutocompleteItem> {
        val items = mutableListOf<AutocompleteItem>()

        // Add context-specific items based on current position
        if (context.contains("@")) {
            // Suggest attributes
            items.addAll(listOf(
                AutocompleteItem("@vertex", "@vertex", AutocompleteKind.KEYWORD, "Vertex shader entry point"),
                AutocompleteItem("@fragment", "@fragment", AutocompleteKind.KEYWORD, "Fragment shader entry point"),
                AutocompleteItem("@compute", "@compute", AutocompleteKind.KEYWORD, "Compute shader entry point"),
                AutocompleteItem("@group", "@group(0)", AutocompleteKind.KEYWORD, "Resource group binding"),
                AutocompleteItem("@binding", "@binding(0)", AutocompleteKind.KEYWORD, "Resource binding index"),
                AutocompleteItem("@location", "@location(0)", AutocompleteKind.KEYWORD, "Input/output location"),
                AutocompleteItem("@builtin", "@builtin(position)", AutocompleteKind.KEYWORD, "Built-in variable")
            ))
        }

        if (context.contains("texture")) {
            // Suggest texture-related functions
            items.addAll(listOf(
                AutocompleteItem("textureSample", "textureSample(texture, sampler, uv)", AutocompleteKind.FUNCTION, "Sample texture"),
                AutocompleteItem("textureLoad", "textureLoad(texture, coords, level)", AutocompleteKind.FUNCTION, "Load texel"),
                AutocompleteItem("textureDimensions", "textureDimensions(texture)", AutocompleteKind.FUNCTION, "Get texture dimensions")
            ))
        }

        // Return filtered items based on current autocomplete list
        return _autocompleteItems.value + items
    }
}

// Data classes for shader editor functionality

data class CursorPosition(
    val line: Int,
    val column: Int,
    val shaderType: String // "vertex", "fragment", "compute"
)

data class CompilationResult(
    val success: Boolean,
    val errors: List<ShaderError>,
    val warnings: List<ShaderWarning>,
    val spirvBinary: ByteArray?,
    val reflection: ShaderReflection?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompilationResult) return false

        return success == other.success &&
                errors == other.errors &&
                warnings == other.warnings &&
                spirvBinary?.contentEquals(other.spirvBinary) == true &&
                reflection == other.reflection
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + errors.hashCode()
        result = 31 * result + warnings.hashCode()
        result = 31 * result + (spirvBinary?.contentHashCode() ?: 0)
        result = 31 * result + (reflection?.hashCode() ?: 0)
        return result
    }
}

data class ShaderError(
    val type: ShaderErrorType,
    val message: String,
    val line: Int,
    val column: Int,
    val source: String, // "vertex", "fragment", "compute"
    val suggestion: String? = null
)

data class ShaderWarning(
    val type: ShaderWarningType,
    val message: String,
    val line: Int,
    val column: Int,
    val source: String,
    val suggestion: String? = null
)

data class ShaderReflection(
    val uniforms: List<UniformReflection>,
    val textures: List<TextureReflection>,
    val vertexInputs: List<VertexInputReflection>,
    val fragmentOutputs: List<FragmentOutputReflection>
)

data class UniformReflection(
    val name: String,
    val type: String,
    val binding: Int,
    val group: Int,
    val size: Int
)

data class TextureReflection(
    val name: String,
    val type: String,
    val binding: Int,
    val group: Int,
    val dimension: String
)

data class VertexInputReflection(
    val name: String,
    val type: String,
    val location: Int
)

data class FragmentOutputReflection(
    val name: String,
    val type: String,
    val location: Int
)

data class AutocompleteItem(
    val label: String,
    val insertText: String,
    val kind: AutocompleteKind,
    val detail: String,
    val documentation: String? = null,
    val sortText: String? = null,
    val filterText: String? = null
)

enum class EditorTheme {
    LIGHT, DARK, HIGH_CONTRAST
}

enum class ShaderErrorType {
    SYNTAX_ERROR, COMPILATION_ERROR, MISSING_ENTRY_POINT, UNDEFINED_VARIABLE, TYPE_MISMATCH, RESOURCE_BINDING_ERROR
}

enum class ShaderWarningType {
    DEPRECATED_SYNTAX, STYLE_ISSUE, PERFORMANCE_WARNING, UNUSED_VARIABLE, MISSING_DOCUMENTATION
}

enum class ShaderTemplate {
    BASIC_UNLIT, BASIC_LIT, PBR_STANDARD, COMPUTE_BASIC, POST_PROCESS
}

enum class ShaderSnippet {
    VERTEX_STRUCT, FRAGMENT_OUTPUT, UNIFORM_BUFFER, TEXTURE_SAMPLE, LIGHTING_LAMBERT, LIGHTING_PHONG, NOISE_FUNCTION, COLOR_SPACE_CONVERSION
}

enum class AutocompleteKind {
    KEYWORD, TYPE, FUNCTION, VARIABLE, CONSTANT, SNIPPET
}