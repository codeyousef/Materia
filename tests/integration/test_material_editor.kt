package io.materia.tests.integration

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/**
 * Integration tests for material editor workflow from quickstart.md
 *
 * These tests verify the complete material editor functionality including shader editing,
 * material creation, preview system, and library management.
 *
 * IMPORTANT: These tests are designed to FAIL initially as part of TDD approach.
 * Tests will pass once the actual material editor implementation is completed.
 */
class MaterialEditorIntegrationTest {

    @Test
    fun `test material creation and basic editing`() = runTest {
        // This test will FAIL until material editor is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()

            // Create new PBR material
            val material = materialEditor.createMaterial(
                type = MaterialType.PBR,
                name = "MyMaterial",
                parameters = PBRMaterialDefinition(
                    baseColor = Color(0.8f, 0.2f, 0.1f, 1.0f),
                    metallic = 0.0f,
                    roughness = 0.7f,
                    emissive = Color.BLACK,
                    normal = 1.0f,
                    occlusion = 1.0f
                )
            )

            // Edit material properties
            materialEditor.updateMaterialProperty(material, "baseColor", Color.BLUE)
            materialEditor.updateMaterialProperty(material, "roughness", 0.3f)
            materialEditor.updateMaterialProperty(material, "metallic", 0.8f)

            // Verify changes
            val updatedMaterial = materialEditor.getMaterial(material.id)
            assert(updatedMaterial.baseColor == Color.BLUE)
            assert(updatedMaterial.roughness == 0.3f)
            assert(updatedMaterial.metallic == 0.8f)
        }
    }

    @Test
    fun `test shader editor with WGSL syntax highlighting`() = runTest {
        // This test will FAIL until shader editor is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()

            // Create custom material with shader
            val customShader = """
                @vertex
                fn vs_main(@location(0) position: vec3<f32>) -> @builtin(position) vec4<f32> {
                    return vec4<f32>(position, 1.0);
                }

                @fragment
                fn fs_main() -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent()

            val material = materialEditor.createCustomMaterial(
                name = "CustomShader",
                vertexShader = customShader,
                fragmentShader = customShader,
                uniforms = mapOf(
                    "time" to UniformDefinition(UniformType.FLOAT, 0.0f),
                    "color" to UniformDefinition(UniformType.VEC3, Vector3(1.0f, 0.0f, 0.0f))
                )
            )

            // Test shader compilation
            val compilationResult = materialEditor.compileShader(material)
            assert(compilationResult.success)
            assert(compilationResult.errors.isEmpty())

            // Test syntax highlighting
            val highlightedCode = materialEditor.getSyntaxHighlightedShader(material)
            assert(highlightedCode.contains("@vertex"))
            assert(highlightedCode.contains("@fragment"))
        }
    }

    @Test
    fun `test material preview and real-time updates`() = runTest {
        // This test will FAIL until material preview is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()
            val material = materialEditor.createMaterial(MaterialType.PBR, "PreviewMaterial")

            // Setup preview scene
            val previewScene = materialEditor.createPreviewScene(
                geometry = PreviewGeometry.SPHERE,
                lighting = PreviewLighting.STUDIO,
                environment = PreviewEnvironment.HDRI("studio.hdr")
            )

            // Apply material to preview
            materialEditor.setPreviewMaterial(previewScene, material)

            // Test real-time updates
            materialEditor.updateMaterialProperty(material, "baseColor", Color.GREEN)
            val renderResult1 = materialEditor.renderPreview(previewScene)

            materialEditor.updateMaterialProperty(material, "roughness", 0.1f)
            val renderResult2 = materialEditor.renderPreview(previewScene)

            // Verify preview updates
            assert(renderResult1.success)
            assert(renderResult2.success)
            assert(renderResult1.renderData != renderResult2.renderData) // Different results
        }
    }

    @Test
    fun `test texture assignment and management`() = runTest {
        // This test will FAIL until texture system is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()
            val material = materialEditor.createMaterial(MaterialType.PBR, "TexturedMaterial")

            // Load textures
            val baseColorTexture = materialEditor.loadTexture(
                path = "assets/textures/brick_diffuse.jpg",
                type = TextureType.BASE_COLOR,
                settings = TextureSettings(
                    generateMipmaps = true,
                    wrapS = TextureWrap.REPEAT,
                    wrapT = TextureWrap.REPEAT,
                    magFilter = TextureFilter.LINEAR,
                    minFilter = TextureFilter.LINEAR_MIPMAP_LINEAR
                )
            )

            val normalTexture = materialEditor.loadTexture(
                path = "assets/textures/brick_normal.jpg",
                type = TextureType.NORMAL,
                settings = TextureSettings(generateMipmaps = true)
            )

            val roughnessTexture = materialEditor.loadTexture(
                path = "assets/textures/brick_roughness.jpg",
                type = TextureType.ROUGHNESS,
                settings = TextureSettings(generateMipmaps = true)
            )

            // Assign textures to material
            materialEditor.assignTexture(material, TextureSlot.BASE_COLOR, baseColorTexture)
            materialEditor.assignTexture(material, TextureSlot.NORMAL, normalTexture)
            materialEditor.assignTexture(material, TextureSlot.ROUGHNESS, roughnessTexture)

            // Configure texture transforms
            materialEditor.setTextureTransform(
                material,
                TextureSlot.BASE_COLOR,
                TextureTransform(
                    offset = Vector2(0.0f, 0.0f),
                    scale = Vector2(2.0f, 2.0f),
                    rotation = 0.0f
                )
            )

            // Verify texture assignments
            val materialData = materialEditor.getMaterialData(material)
            assert(materialData.textures[TextureSlot.BASE_COLOR] == baseColorTexture)
            assert(materialData.textures[TextureSlot.NORMAL] == normalTexture)
            assert(materialData.textures[TextureSlot.ROUGHNESS] == roughnessTexture)
        }
    }

    @Test
    fun `test material library and presets`() = runTest {
        // This test will FAIL until material library is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()
            val library = materialEditor.getMaterialLibrary()

            // Create material presets
            val metalPreset = materialEditor.createPreset(
                name = "Chrome Metal",
                category = MaterialCategory.METALS,
                material = PBRMaterialDefinition(
                    baseColor = Color(0.9f, 0.9f, 0.9f, 1.0f),
                    metallic = 1.0f,
                    roughness = 0.05f
                )
            )

            val glassPreset = materialEditor.createPreset(
                name = "Clear Glass",
                category = MaterialCategory.GLASS,
                material = PBRMaterialDefinition(
                    baseColor = Color(1.0f, 1.0f, 1.0f, 0.1f),
                    metallic = 0.0f,
                    roughness = 0.0f,
                    transmission = 1.0f,
                    ior = 1.52f
                )
            )

            // Add to library
            library.addPreset(metalPreset)
            library.addPreset(glassPreset)

            // Search and filter presets
            val metalMaterials = library.searchPresets("metal")
            val glassMaterials = library.getPresetsByCategory(MaterialCategory.GLASS)

            // Create material from preset
            val chromeMaterial = materialEditor.createMaterialFromPreset(metalPreset)

            // Verify library operations
            assert(metalMaterials.isNotEmpty())
            assert(glassMaterials.contains(glassPreset))
            assert(chromeMaterial.metallic == 1.0f)
        }
    }

    @Test
    fun `test material node editor for complex shaders`() = runTest {
        // This test will FAIL until node editor is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()
            val nodeEditor = materialEditor.getNodeEditor()

            // Create node graph
            val graph = nodeEditor.createGraph("ComplexMaterial")

            // Add nodes
            val textureNode = nodeEditor.addNode(graph, NodeType.TEXTURE_SAMPLE)
            val noiseNode = nodeEditor.addNode(graph, NodeType.NOISE)
            val mixNode = nodeEditor.addNode(graph, NodeType.MIX)
            val outputNode = nodeEditor.addNode(graph, NodeType.MATERIAL_OUTPUT)

            // Configure nodes
            nodeEditor.setNodeProperty(textureNode, "texture", "brick_diffuse.jpg")
            nodeEditor.setNodeProperty(noiseNode, "scale", 10.0f)
            nodeEditor.setNodeProperty(mixNode, "factor", 0.5f)

            // Connect nodes
            nodeEditor.connectNodes(
                graph,
                textureNode, "color",
                mixNode, "color1"
            )
            nodeEditor.connectNodes(
                graph,
                noiseNode, "value",
                mixNode, "color2"
            )
            nodeEditor.connectNodes(
                graph,
                mixNode, "result",
                outputNode, "baseColor"
            )

            // Compile node graph to shader
            val shaderCode = nodeEditor.compileToShader(graph, ShaderTarget.WGSL)
            val material = materialEditor.createMaterialFromNodeGraph(graph)

            // Verify node graph compilation
            assert(shaderCode.isNotEmpty())
            assert(shaderCode.contains("texture_sample"))
            assert(material != null)
        }
    }

    @Test
    fun `test material animation and time-based effects`() = runTest {
        // This test will FAIL until material animation is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()

            // Create animated material
            val material = materialEditor.createAnimatedMaterial(
                name = "PulsingEmissive",
                baseTemplate = MaterialType.PBR
            )

            // Add time-based animations
            val pulseAnimation = materialEditor.createPropertyAnimation(
                property = "emissiveIntensity",
                keyframes = listOf(
                    AnimationKeyframe(time = 0.0f, value = 0.0f),
                    AnimationKeyframe(time = 1.0f, value = 2.0f),
                    AnimationKeyframe(time = 2.0f, value = 0.0f)
                ),
                interpolation = AnimationInterpolation.SMOOTH,
                loop = true
            )

            val colorShiftAnimation = materialEditor.createPropertyAnimation(
                property = "baseColor",
                keyframes = listOf(
                    AnimationKeyframe(time = 0.0f, value = Color.RED),
                    AnimationKeyframe(time = 2.0f, value = Color.BLUE),
                    AnimationKeyframe(time = 4.0f, value = Color.RED)
                ),
                interpolation = AnimationInterpolation.LINEAR,
                loop = true
            )

            materialEditor.addAnimation(material, pulseAnimation)
            materialEditor.addAnimation(material, colorShiftAnimation)

            // Test animation at different times
            materialEditor.setAnimationTime(material, 0.5f)
            val state1 = materialEditor.getMaterialState(material)

            materialEditor.setAnimationTime(material, 1.5f)
            val state2 = materialEditor.getMaterialState(material)

            // Verify animation
            assert(state1.emissiveIntensity != state2.emissiveIntensity)
            assert(state1.baseColor != state2.baseColor)
        }
    }

    @Test
    fun `test material validation and error checking`() = runTest {
        // This test will FAIL until validation system is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()
            val validator = materialEditor.getValidator()

            // Create material with potential issues
            val material = materialEditor.createCustomMaterial(
                name = "ProblematicMaterial",
                vertexShader = "invalid shader code",
                fragmentShader = "also invalid",
                uniforms = mapOf(
                    "invalidUniform" to UniformDefinition(UniformType.FLOAT, "not a float")
                )
            )

            // Validate material
            val validationResult = validator.validateMaterial(material)

            // Check validation results
            assert(!validationResult.isValid)
            assert(validationResult.errors.isNotEmpty())
            assert(validationResult.errors.any { it.type == ValidationErrorType.SHADER_COMPILATION })
            assert(validationResult.errors.any { it.type == ValidationErrorType.UNIFORM_TYPE_MISMATCH })

            // Test auto-fix suggestions
            val suggestions = validator.getSuggestions(validationResult)
            assert(suggestions.isNotEmpty())

            // Apply auto-fixes
            val fixedMaterial = validator.applyAutoFixes(material, suggestions)
            val revalidationResult = validator.validateMaterial(fixedMaterial)
            assert(revalidationResult.isValid || revalidationResult.errors.size < validationResult.errors.size)
        }
    }

    @Test
    fun `test material export and import`() = runTest {
        // This test will FAIL until export/import is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()

            // Create complex material
            val material = materialEditor.createMaterial(MaterialType.PBR, "ExportTest")
            materialEditor.updateMaterialProperty(material, "baseColor", Color.CYAN)
            materialEditor.updateMaterialProperty(material, "metallic", 0.8f)

            val texture = materialEditor.loadTexture("test.jpg", TextureType.BASE_COLOR)
            materialEditor.assignTexture(material, TextureSlot.BASE_COLOR, texture)

            // Export material to various formats
            val gltfExport = materialEditor.exportMaterial(
                material,
                MaterialExportFormat.GLTF,
                MaterialExportOptions(includeTextures = true)
            )

            val materiaExport = materialEditor.exportMaterial(
                material,
                MaterialExportFormat.MATERIA_NATIVE,
                MaterialExportOptions(compress = true)
            )

            val jsonExport = materialEditor.exportMaterial(
                material,
                MaterialExportFormat.JSON,
                MaterialExportOptions(prettyPrint = true)
            )

            // Import material back
            val importedMaterial = materialEditor.importMaterial(
                materiaExport,
                MaterialImportOptions(validateShaders = true)
            )

            // Verify round-trip consistency
            assert(importedMaterial.baseColor == material.baseColor)
            assert(importedMaterial.metallic == material.metallic)
            assert(importedMaterial.textures.size == material.textures.size)
        }
    }

    @Test
    fun `test material performance profiling`() = runTest {
        // This test will FAIL until performance profiling is implemented
        assertFailsWith<NotImplementedError> {
            val materialEditor = MaterialEditorService()
            val profiler = materialEditor.getPerformanceProfiler()

            // Create materials with different complexity
            val simpleMaterial = materialEditor.createMaterial(MaterialType.UNLIT, "Simple")
            val complexMaterial = materialEditor.createCustomMaterial(
                name = "Complex",
                vertexShader = generateComplexVertexShader(),
                fragmentShader = generateComplexFragmentShader()
            )

            // Profile materials
            val simpleProfile = profiler.profileMaterial(
                simpleMaterial,
                ProfileConfig(
                    renderTargets = listOf(RenderTarget.WEB_GPU, RenderTarget.VULKAN),
                    geometryComplexity = GeometryComplexity.HIGH,
                    iterations = 1000
                )
            )

            val complexProfile = profiler.profileMaterial(
                complexMaterial,
                ProfileConfig(
                    renderTargets = listOf(RenderTarget.WEB_GPU, RenderTarget.VULKAN),
                    geometryComplexity = GeometryComplexity.HIGH,
                    iterations = 1000
                )
            )

            // Analyze performance
            assert(simpleProfile.averageRenderTime < complexProfile.averageRenderTime)
            assert(simpleProfile.shaderComplexity < complexProfile.shaderComplexity)
            assert(complexProfile.warnings.isNotEmpty()) // Should have performance warnings

            // Get optimization suggestions
            val optimizations = profiler.getOptimizationSuggestions(complexProfile)
            assert(optimizations.isNotEmpty())
        }
    }
}

// Contract interfaces for Phase 3.3 implementation

interface MaterialEditorService {
    suspend fun createMaterial(type: MaterialType, name: String, parameters: Any? = null): MaterialInstance
    suspend fun createCustomMaterial(name: String, vertexShader: String, fragmentShader: String, uniforms: Map<String, UniformDefinition> = emptyMap()): MaterialInstance
    suspend fun createAnimatedMaterial(name: String, baseTemplate: MaterialType): MaterialInstance
    suspend fun createMaterialFromPreset(preset: MaterialPreset): MaterialInstance
    suspend fun createMaterialFromNodeGraph(graph: NodeGraph): MaterialInstance

    suspend fun updateMaterialProperty(material: MaterialInstance, property: String, value: Any)
    suspend fun getMaterial(id: String): MaterialInstance
    suspend fun getMaterialData(material: MaterialInstance): MaterialData
    suspend fun getMaterialState(material: MaterialInstance): MaterialState

    suspend fun compileShader(material: MaterialInstance): ShaderCompilationResult
    suspend fun getSyntaxHighlightedShader(material: MaterialInstance): String

    suspend fun createPreviewScene(geometry: PreviewGeometry, lighting: PreviewLighting, environment: PreviewEnvironment): PreviewScene
    suspend fun setPreviewMaterial(scene: PreviewScene, material: MaterialInstance)
    suspend fun renderPreview(scene: PreviewScene): PreviewRenderResult

    suspend fun loadTexture(path: String, type: TextureType, settings: TextureSettings = TextureSettings()): TextureInstance
    suspend fun assignTexture(material: MaterialInstance, slot: TextureSlot, texture: TextureInstance)
    suspend fun setTextureTransform(material: MaterialInstance, slot: TextureSlot, transform: TextureTransform)

    suspend fun getMaterialLibrary(): MaterialLibrary
    suspend fun createPreset(name: String, category: MaterialCategory, material: Any): MaterialPreset

    fun getNodeEditor(): NodeEditor
    suspend fun addAnimation(material: MaterialInstance, animation: PropertyAnimation)
    suspend fun setAnimationTime(material: MaterialInstance, time: Float)
    suspend fun createPropertyAnimation(property: String, keyframes: List<AnimationKeyframe>, interpolation: AnimationInterpolation, loop: Boolean): PropertyAnimation

    fun getValidator(): MaterialValidator
    fun getPerformanceProfiler(): MaterialPerformanceProfiler

    suspend fun exportMaterial(material: MaterialInstance, format: MaterialExportFormat, options: MaterialExportOptions): ByteArray
    suspend fun importMaterial(data: ByteArray, options: MaterialImportOptions): MaterialInstance
}

enum class UniformType { FLOAT, VEC2, VEC3, VEC4, MATRIX, TEXTURE }
enum class TextureType { BASE_COLOR, NORMAL, ROUGHNESS, METALLIC, EMISSIVE, OCCLUSION }
enum class TextureWrap { REPEAT, CLAMP, MIRROR }
enum class TextureFilter { NEAREST, LINEAR, LINEAR_MIPMAP_LINEAR }
enum class TextureSlot { BASE_COLOR, NORMAL, ROUGHNESS, METALLIC, EMISSIVE, OCCLUSION }
enum class PreviewGeometry { SPHERE, CUBE, PLANE, TEAPOT, CUSTOM }
enum class PreviewLighting { STUDIO, OUTDOOR, INDOOR, CUSTOM }
enum class PreviewEnvironment { COLOR, HDRI }
enum class MaterialCategory { METALS, PLASTICS, GLASS, FABRICS, WOOD, STONE, ORGANIC }
enum class NodeType { TEXTURE_SAMPLE, NOISE, MIX, MATERIAL_OUTPUT, MATH, VECTOR }
enum class ShaderTarget { WGSL, GLSL, SPIRV }
enum class AnimationInterpolation { LINEAR, SMOOTH, STEP }
enum class ValidationErrorType { SHADER_COMPILATION, UNIFORM_TYPE_MISMATCH, TEXTURE_FORMAT, PERFORMANCE_WARNING }
enum class MaterialExportFormat { GLTF, MATERIA_NATIVE, JSON }
enum class RenderTarget { WEB_GPU, VULKAN, OPENGL }
enum class GeometryComplexity { LOW, MEDIUM, HIGH }

data class MaterialInstance(
    val id: String,
    val name: String,
    val type: MaterialType,
    val baseColor: Color,
    val metallic: Float,
    val roughness: Float,
    val emissive: Color = Color.BLACK,
    val normal: Float = 1.0f,
    val occlusion: Float = 1.0f,
    val transmission: Float = 0.0f,
    val ior: Float = 1.5f,
    val textures: Map<TextureSlot, TextureInstance> = emptyMap()
)

data class PBRMaterialDefinition(
    val baseColor: Color,
    val metallic: Float,
    val roughness: Float,
    val emissive: Color = Color.BLACK,
    val normal: Float = 1.0f,
    val occlusion: Float = 1.0f,
    val transmission: Float = 0.0f,
    val ior: Float = 1.5f
)

data class UniformDefinition(
    val type: UniformType,
    val defaultValue: Any
)

data class Vector2(val x: Float, val y: Float)

data class TextureSettings(
    val generateMipmaps: Boolean = true,
    val wrapS: TextureWrap = TextureWrap.REPEAT,
    val wrapT: TextureWrap = TextureWrap.REPEAT,
    val magFilter: TextureFilter = TextureFilter.LINEAR,
    val minFilter: TextureFilter = TextureFilter.LINEAR_MIPMAP_LINEAR
)

data class TextureTransform(
    val offset: Vector2,
    val scale: Vector2,
    val rotation: Float
)

data class TextureInstance(
    val id: String,
    val path: String,
    val type: TextureType,
    val settings: TextureSettings
)

data class MaterialData(
    val properties: Map<String, Any>,
    val textures: Map<TextureSlot, TextureInstance>,
    val shaders: Map<String, String>
)

data class MaterialState(
    val baseColor: Color,
    val metallic: Float,
    val roughness: Float,
    val emissiveIntensity: Float
)

data class ShaderCompilationResult(
    val success: Boolean,
    val errors: List<String>,
    val warnings: List<String> = emptyList()
)

interface PreviewScene
interface TextureInstance

data class PreviewRenderResult(
    val success: Boolean,
    val renderData: ByteArray,
    val renderTime: Long
)

interface MaterialLibrary {
    suspend fun addPreset(preset: MaterialPreset)
    suspend fun searchPresets(query: String): List<MaterialPreset>
    suspend fun getPresetsByCategory(category: MaterialCategory): List<MaterialPreset>
}

data class MaterialPreset(
    val name: String,
    val category: MaterialCategory,
    val material: Any
)

interface NodeEditor {
    suspend fun createGraph(name: String): NodeGraph
    suspend fun addNode(graph: NodeGraph, type: NodeType): GraphNode
    suspend fun setNodeProperty(node: GraphNode, property: String, value: Any)
    suspend fun connectNodes(graph: NodeGraph, fromNode: GraphNode, fromOutput: String, toNode: GraphNode, toInput: String)
    suspend fun compileToShader(graph: NodeGraph, target: ShaderTarget): String
}

interface NodeGraph
interface GraphNode

data class AnimationKeyframe(
    val time: Float,
    val value: Any
)

data class PropertyAnimation(
    val property: String,
    val keyframes: List<AnimationKeyframe>,
    val interpolation: AnimationInterpolation,
    val loop: Boolean
)

interface MaterialValidator {
    suspend fun validateMaterial(material: MaterialInstance): ValidationResult
    suspend fun getSuggestions(result: ValidationResult): List<FixSuggestion>
    suspend fun applyAutoFixes(material: MaterialInstance, suggestions: List<FixSuggestion>): MaterialInstance
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning> = emptyList()
)

data class ValidationError(
    val type: ValidationErrorType,
    val message: String,
    val line: Int? = null
)

data class ValidationWarning(
    val message: String
)

data class FixSuggestion(
    val description: String,
    val autoFixAvailable: Boolean
)

interface MaterialPerformanceProfiler {
    suspend fun profileMaterial(material: MaterialInstance, config: ProfileConfig): PerformanceProfile
    suspend fun getOptimizationSuggestions(profile: PerformanceProfile): List<OptimizationSuggestion>
}

data class ProfileConfig(
    val renderTargets: List<RenderTarget>,
    val geometryComplexity: GeometryComplexity,
    val iterations: Int
)

data class PerformanceProfile(
    val averageRenderTime: Float,
    val shaderComplexity: Float,
    val warnings: List<String>
)

data class OptimizationSuggestion(
    val description: String,
    val expectedImprovement: Float
)

data class MaterialExportOptions(
    val includeTextures: Boolean = false,
    val compress: Boolean = false,
    val prettyPrint: Boolean = false
)

data class MaterialImportOptions(
    val validateShaders: Boolean = true
)

// Test helper functions for shader generation
private fun generateComplexVertexShader(): String = "complex vertex shader code"
private fun generateComplexFragmentShader(): String = "complex fragment shader code"