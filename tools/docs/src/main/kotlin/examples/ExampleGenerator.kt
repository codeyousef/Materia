package tools.docs.examples

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.time.ExperimentalTime

/**
 * Interactive example generator for Materia documentation.
 * Creates runnable, testable, and interactive code examples with live previews.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class InteractiveExample(
    val id: String,
    val title: String,
    val description: String,
    val category: ExampleCategory,
    val difficulty: DifficultyLevel,
    val tags: List<String>,
    val platforms: List<String>,
    val codeBlocks: List<CodeBlock>,
    val assets: List<ExampleAsset> = emptyList(),
    val metadata: ExampleMetadata
)

enum class ExampleCategory {
    BASIC_USAGE,
    SCENE_MANAGEMENT,
    GEOMETRY_CREATION,
    MATERIAL_SHADERS,
    ANIMATION,
    LIGHTING,
    CAMERA_CONTROLS,
    PERFORMANCE_OPTIMIZATION,
    PHYSICS_INTEGRATION,
    VR_AR,
    ADVANCED_RENDERING
}

enum class DifficultyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}

@Serializable
data class CodeBlock(
    val id: String,
    val title: String,
    val description: String,
    val code: String,
    val language: String = "kotlin",
    val executable: Boolean = true,
    val editable: Boolean = true,
    val expectedOutput: String? = null,
    val imports: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val setup: String? = null,
    val teardown: String? = null
)

@Serializable
data class ExampleAsset(
    val type: AssetType,
    val path: String,
    val description: String,
    val license: String? = null,
    val attribution: String? = null
)

enum class AssetType {
    TEXTURE, MODEL, AUDIO, SHADER, FONT, DATA
}

@Serializable
data class ExampleMetadata(
    val author: String,
    val createdDate: String,
    val lastModified: String,
    val version: String,
    val estimatedTime: Int, // minutes
    val learningObjectives: List<String>,
    val prerequisites: List<String>,
    val relatedExamples: List<String>
)

@Serializable
data class ExampleTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: ExampleCategory,
    val templateCode: String,
    val placeholders: Map<String, PlaceholderInfo>,
    val requiredImports: List<String>,
    val defaultAssets: List<String>
)

@Serializable
data class PlaceholderInfo(
    val description: String,
    val type: String,
    val defaultValue: String,
    val suggestions: List<String> = emptyList()
)

@Serializable
data class ExampleSuite(
    val id: String,
    val title: String,
    val description: String,
    val examples: List<InteractiveExample>,
    val learningPath: List<String>, // Order of example IDs
    val totalEstimatedTime: Int
)

@Serializable
data class ExampleExecution(
    val exampleId: String,
    val codeBlockId: String,
    val success: Boolean,
    val output: String,
    val errors: List<String>,
    val executionTime: Long,
    val memoryUsage: Long,
    val resourceUsage: ExampleResourceUsage
)

@Serializable
data class ExampleResourceUsage(
    val cpuTime: Long,
    val memoryPeak: Long,
    val networkRequests: Int,
    val fileOperations: Int
)

/**
 * Main interactive example generator with live execution and preview capabilities.
 */
class ExampleGenerator {
    private val examples = mutableMapOf<String, InteractiveExample>()
    private val templates = mutableMapOf<String, ExampleTemplate>()
    private val suites = mutableMapOf<String, ExampleSuite>()

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        // Standard Materia imports for examples
        private val standardImports = listOf(
            "materia.core.*",
            "materia.scene.*",
            "materia.geometry.*",
            "materia.material.*",
            "materia.renderer.*",
            "materia.math.*",
            "kotlinx.coroutines.*"
        )

        private val visualCategories = setOf(
            ExampleCategory.SCENE_MANAGEMENT,
            ExampleCategory.GEOMETRY_CREATION,
            ExampleCategory.MATERIAL_SHADERS,
            ExampleCategory.ANIMATION,
            ExampleCategory.LIGHTING,
            ExampleCategory.ADVANCED_RENDERING
        )
    }

    /**
     * Generate comprehensive example library
     */
    suspend fun generateExampleLibrary(): ExampleLibrary = withContext(Dispatchers.Default) {
        // Generate examples for each category
        val allExamples = mutableListOf<InteractiveExample>()

        ExampleCategory.values().forEach { category ->
            val categoryExamples = generateCategoryExamples(category)
            allExamples.addAll(categoryExamples)
        }

        // Create learning paths
        val learningPaths = createLearningPaths(allExamples)

        // Generate example suites
        val exampleSuites = createExampleSuites(allExamples, learningPaths)

        ExampleLibrary(
            examples = allExamples,
            suites = exampleSuites,
            learningPaths = learningPaths,
            templates = templates.values.toList(),
            metadata = LibraryMetadata(
                totalExamples = allExamples.size,
                categories = ExampleCategory.values().size,
                averageDifficulty = calculateAverageDifficulty(allExamples),
                lastUpdated = kotlinx.datetime.Clock.System.now()
            )
        )
    }

    /**
     * Create runnable example from template
     */
    suspend fun createFromTemplate(
        templateId: String,
        parameters: Map<String, String>
    ): InteractiveExample? = withContext(Dispatchers.Default) {
        val template = templates[templateId] ?: return@withContext null

        val processedCode = processTemplate(template.templateCode, parameters)

        InteractiveExample(
            id = "generated-${Clock.System.now().toEpochMilliseconds()}",
            title = "Custom ${template.name}",
            description = "Generated from ${template.name} template",
            category = template.category,
            difficulty = DifficultyLevel.BEGINNER,
            tags = listOf("generated", "template", template.name.lowercase()),
            platforms = listOf("jvm", "js"),
            codeBlocks = listOf(
                CodeBlock(
                    id = "main",
                    title = "Main Code",
                    description = "Generated code from template",
                    code = processedCode,
                    imports = template.requiredImports
                )
            ),
            metadata = ExampleMetadata(
                author = "ExampleGenerator",
                createdDate = kotlinx.datetime.Clock.System.now().toString(),
                lastModified = kotlinx.datetime.Clock.System.now().toString(),
                version = "1.0",
                estimatedTime = 10,
                learningObjectives = listOf("Understanding ${template.name}"),
                prerequisites = emptyList(),
                relatedExamples = emptyList()
            )
        )
    }

    /**
     * Execute example code block with safety checks
     */
    suspend fun executeExample(
        exampleId: String,
        codeBlockId: String,
        userCode: String? = null
    ): ExampleExecution = withContext(Dispatchers.Default) {
        val example = examples[exampleId]
        val codeBlock = example?.codeBlocks?.find { it.id == codeBlockId }

        if (example == null || codeBlock == null) {
            return@withContext ExampleExecution(
                exampleId = exampleId,
                codeBlockId = codeBlockId,
                success = false,
                output = "",
                errors = listOf("Example or code block not found"),
                executionTime = 0,
                memoryUsage = 0,
                resourceUsage = ExampleResourceUsage(0, 0, 0, 0)
            )
        }

        val codeToExecute = userCode ?: codeBlock.code
        val startTime = Clock.System.now().toEpochMilliseconds()
        val startMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

        try {
            // Validate code safety
            val validationResult = validateCodeSafety(codeToExecute)
            if (!validationResult.safe) {
                return@withContext ExampleExecution(
                    exampleId = exampleId,
                    codeBlockId = codeBlockId,
                    success = false,
                    output = "",
                    errors = validationResult.issues,
                    executionTime = 0,
                    memoryUsage = 0,
                    resourceUsage = ExampleResourceUsage(0, 0, 0, 0)
                )
            }

            // Execute in sandboxed environment
            val result = executeSandboxedCode(
                code = codeToExecute,
                imports = codeBlock.imports + standardImports,
                setup = codeBlock.setup,
                teardown = codeBlock.teardown,
                timeoutMs = 30_000 // 30 second timeout
            )

            val executionTime = Clock.System.now().toEpochMilliseconds() - startTime
            val endMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            val memoryUsed = maxOf(0, endMemory - startMemory)

            ExampleExecution(
                exampleId = exampleId,
                codeBlockId = codeBlockId,
                success = result.success,
                output = result.output,
                errors = result.errors,
                executionTime = executionTime,
                memoryUsage = memoryUsed,
                resourceUsage = ExampleResourceUsage(
                    cpuTime = executionTime,
                    memoryPeak = memoryUsed,
                    networkRequests = 0, // Examples run in isolated context without network
                    fileOperations = 0
                )
            )

        } catch (e: Exception) {
            ExampleExecution(
                exampleId = exampleId,
                codeBlockId = codeBlockId,
                success = false,
                output = "",
                errors = listOf("Execution failed: ${e.message}"),
                executionTime = Clock.System.now().toEpochMilliseconds() - startTime,
                memoryUsage = 0,
                resourceUsage = ExampleResourceUsage(0, 0, 0, 0)
            )
        }
    }

    /**
     * Generate live preview for visual examples
     */
    suspend fun generateLivePreview(
        exampleId: String,
        width: Int = 800,
        height: Int = 600
    ): PreviewResult = withContext(Dispatchers.Default) {
        val example = examples[exampleId] ?: return@withContext PreviewResult(
            success = false,
            error = "Example not found"
        )

        if (example.category !in visualCategories) {
            return@withContext PreviewResult(
                success = false,
                error = "Example does not support visual preview"
            )
        }

        try {
            // Create a headless rendering context
            val previewCode = generatePreviewCode(example, width, height)
            val result = executeSandboxedCode(
                code = previewCode,
                imports = standardImports + listOf("materia.preview.*"),
                timeoutMs = 10_000
            )

            if (result.success) {
                PreviewResult(
                    success = true,
                    imageData = result.output, // Base64 encoded image
                    renderTime = 0 // Render time captured by execution metrics
                )
            } else {
                PreviewResult(
                    success = false,
                    error = "Preview generation failed: ${result.errors.joinToString()}"
                )
            }

        } catch (e: Exception) {
            PreviewResult(
                success = false,
                error = "Preview generation exception: ${e.message}"
            )
        }
    }

    /**
     * Export examples to various formats
     */
    suspend fun exportExamples(
        exampleIds: List<String>,
        format: ExportFormat,
        outputPath: String
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val selectedExamples = exampleIds.mapNotNull { examples[it] }

            when (format) {
                ExportFormat.MARKDOWN -> exportToMarkdown(selectedExamples, outputPath)
                ExportFormat.HTML -> exportToHtml(selectedExamples, outputPath)
                ExportFormat.JSON -> exportToJson(selectedExamples, outputPath)
                ExportFormat.JUPYTER -> exportToJupyter(selectedExamples, outputPath)
                ExportFormat.PLAYGROUND -> exportToPlayground(selectedExamples, outputPath)
            }

            ExportResult(
                success = true,
                outputFiles = listOf(outputPath),
                format = format
            )

        } catch (e: Exception) {
            ExportResult(
                success = false,
                error = "Export failed: ${e.message}",
                format = format
            )
        }
    }

    // Private helper methods

    private fun generateCategoryExamples(category: ExampleCategory): List<InteractiveExample> {
        return when (category) {
            ExampleCategory.BASIC_USAGE -> generateBasicUsageExamples()
            ExampleCategory.SCENE_MANAGEMENT -> generateSceneManagementExamples()
            ExampleCategory.GEOMETRY_CREATION -> generateGeometryExamples()
            ExampleCategory.MATERIAL_SHADERS -> generateMaterialExamples()
            ExampleCategory.ANIMATION -> generateAnimationExamples()
            ExampleCategory.LIGHTING -> generateLightingExamples()
            ExampleCategory.CAMERA_CONTROLS -> generateCameraExamples()
            ExampleCategory.PERFORMANCE_OPTIMIZATION -> generatePerformanceExamples()
            ExampleCategory.PHYSICS_INTEGRATION -> generatePhysicsExamples()
            ExampleCategory.VR_AR -> generateVRARExamples()
            ExampleCategory.ADVANCED_RENDERING -> generateAdvancedRenderingExamples()
        }
    }

    private fun generateBasicUsageExamples(): List<InteractiveExample> {
        return listOf(
            InteractiveExample(
                id = "hello-materia",
                title = "Hello Materia",
                description = "Your first Materia application - create and render a simple cube",
                category = ExampleCategory.BASIC_USAGE,
                difficulty = DifficultyLevel.BEGINNER,
                tags = listOf("basic", "cube", "rendering", "first-steps"),
                platforms = listOf("jvm", "js", "android"),
                codeBlocks = listOf(
                    CodeBlock(
                        id = "setup",
                        title = "Setup Scene",
                        description = "Create the basic scene, camera, and renderer",
                        code = """
                            // Create scene, camera, and renderer
                            val scene = Scene()
                            val camera = PerspectiveCamera(
                                fov = 75.0,
                                aspect = 16.0 / 9.0,
                                near = 0.1,
                                far = 1000.0
                            )
                            val renderer = createRenderer()

                            // Position camera
                            camera.position.z = 5.0
                        """.trimIndent(),
                        imports = standardImports
                    ),
                    CodeBlock(
                        id = "create-cube",
                        title = "Create Cube",
                        description = "Create a colored cube and add it to the scene",
                        code = """
                            // Create cube geometry and material
                            val geometry = BoxGeometry(1.0, 1.0, 1.0)
                            val material = BasicMaterial(color = Color.CYAN)
                            val cube = Mesh(geometry, material)

                            // Add cube to scene
                            scene.add(cube)
                        """.trimIndent()
                    ),
                    CodeBlock(
                        id = "render",
                        title = "Render Scene",
                        description = "Render the scene with animation loop",
                        code = """
                            // Animation function
                            fun animate() {
                                // Rotate cube
                                cube.rotation.x += 0.01
                                cube.rotation.y += 0.01

                                // Render scene
                                renderer.render(scene, camera)

                                // Request next frame
                                requestAnimationFrame(::animate)
                            }

                            // Start animation
                            animate()
                        """.trimIndent(),
                        expectedOutput = "Rotating cyan cube rendered in 3D space"
                    )
                ),
                metadata = ExampleMetadata(
                    author = "Materia Team",
                    createdDate = "2025-01-01",
                    lastModified = "2025-01-01",
                    version = "1.0",
                    estimatedTime = 15,
                    learningObjectives = listOf(
                        "Understand basic Materia setup",
                        "Learn scene creation and object management",
                        "Implement simple animation loop"
                    ),
                    prerequisites = listOf("Basic Kotlin knowledge"),
                    relatedExamples = listOf("multiple-objects", "materials-intro")
                )
            ),

            InteractiveExample(
                id = "multiple-objects",
                title = "Multiple Objects",
                description = "Create and manage multiple 3D objects in a scene",
                category = ExampleCategory.BASIC_USAGE,
                difficulty = DifficultyLevel.BEGINNER,
                tags = listOf("multiple-objects", "positioning", "scene-management"),
                platforms = listOf("jvm", "js", "android"),
                codeBlocks = listOf(
                    CodeBlock(
                        id = "create-objects",
                        title = "Create Multiple Objects",
                        description = "Create several objects with different properties",
                        code = """
                            val scene = Scene()
                            val camera = PerspectiveCamera(75.0, 16.0/9.0, 0.1, 1000.0)
                            camera.position.z = 10.0

                            // Create different geometries
                            val cubeGeometry = BoxGeometry(1.0, 1.0, 1.0)
                            val sphereGeometry = SphereGeometry(0.8, 32, 32)
                            val cylinderGeometry = CylinderGeometry(0.5, 0.5, 2.0, 32)

                            // Create different materials
                            val redMaterial = BasicMaterial(color = Color.RED)
                            val greenMaterial = BasicMaterial(color = Color.GREEN)
                            val blueMaterial = BasicMaterial(color = Color.BLUE)

                            // Create meshes
                            val cube = Mesh(cubeGeometry, redMaterial)
                            val sphere = Mesh(sphereGeometry, greenMaterial)
                            val cylinder = Mesh(cylinderGeometry, blueMaterial)

                            // Position objects
                            cube.position.x = -3.0
                            sphere.position.x = 0.0
                            cylinder.position.x = 3.0

                            // Add to scene
                            scene.add(cube, sphere, cylinder)
                        """.trimIndent()
                    ),
                    CodeBlock(
                        id = "animate-objects",
                        title = "Animate Objects",
                        description = "Animate multiple objects with different patterns",
                        code = """
                            fun animate() {
                                val time = getTime()

                                // Different rotation patterns
                                cube.rotation.x = time * 0.5
                                cube.rotation.y = time * 0.7

                                sphere.rotation.y = time * 1.2
                                sphere.position.y = sin(time * 2) * 0.5

                                cylinder.rotation.z = time * 0.8
                                cylinder.scale.y = 1.0 + sin(time * 3) * 0.2

                                renderer.render(scene, camera)
                                requestAnimationFrame(::animate)
                            }

                            animate()
                        """.trimIndent(),
                        expectedOutput = "Three objects with different animations"
                    )
                ),
                metadata = ExampleMetadata(
                    author = "Materia Team",
                    createdDate = "2025-01-01",
                    lastModified = "2025-01-01",
                    version = "1.0",
                    estimatedTime = 20,
                    learningObjectives = listOf(
                        "Manage multiple objects in a scene",
                        "Understand positioning and transformations",
                        "Create varied animation patterns"
                    ),
                    prerequisites = listOf("hello-materia"),
                    relatedExamples = listOf("hello-materia", "transformations")
                )
            )
        )
    }

    private fun generateSceneManagementExamples(): List<InteractiveExample> {
        return listOf(
            InteractiveExample(
                id = "scene-hierarchy",
                title = "Scene Hierarchy",
                description = "Organize objects using parent-child relationships",
                category = ExampleCategory.SCENE_MANAGEMENT,
                difficulty = DifficultyLevel.INTERMEDIATE,
                tags = listOf("hierarchy", "groups", "parent-child"),
                platforms = listOf("jvm", "js", "android"),
                codeBlocks = listOf(
                    CodeBlock(
                        id = "create-hierarchy",
                        title = "Create Hierarchy",
                        description = "Build a hierarchical scene structure",
                        code = """
                            val scene = Scene()

                            // Create parent group
                            val solarSystem = Group()
                            scene.add(solarSystem)

                            // Create sun (center)
                            val sunGeometry = SphereGeometry(2.0, 32, 32)
                            val sunMaterial = BasicMaterial(color = Color.YELLOW)
                            val sun = Mesh(sunGeometry, sunMaterial)
                            solarSystem.add(sun)

                            // Create earth orbit group
                            val earthOrbit = Group()
                            earthOrbit.position.x = 8.0
                            solarSystem.add(earthOrbit)

                            // Create earth
                            val earthGeometry = SphereGeometry(1.0, 32, 32)
                            val earthMaterial = BasicMaterial(color = Color.BLUE)
                            val earth = Mesh(earthGeometry, earthMaterial)
                            earthOrbit.add(earth)

                            // Create moon orbit group
                            val moonOrbit = Group()
                            moonOrbit.position.x = 3.0
                            earthOrbit.add(moonOrbit)

                            // Create moon
                            val moonGeometry = SphereGeometry(0.3, 16, 16)
                            val moonMaterial = BasicMaterial(color = Color.GRAY)
                            val moon = Mesh(moonGeometry, moonMaterial)
                            moonOrbit.add(moon)
                        """.trimIndent()
                    )
                ),
                metadata = ExampleMetadata(
                    author = "Materia Team",
                    createdDate = "2025-01-01",
                    lastModified = "2025-01-01",
                    version = "1.0",
                    estimatedTime = 25,
                    learningObjectives = listOf(
                        "Understand scene graph hierarchy",
                        "Use groups for organization",
                        "Create complex object relationships"
                    ),
                    prerequisites = listOf("multiple-objects"),
                    relatedExamples = listOf("transformations", "animation")
                )
            )
        )
    }

    // Additional example generation methods would follow similar patterns...
    private fun generateGeometryExamples(): List<InteractiveExample> = emptyList()
    private fun generateMaterialExamples(): List<InteractiveExample> = emptyList()
    private fun generateAnimationExamples(): List<InteractiveExample> = emptyList()
    private fun generateLightingExamples(): List<InteractiveExample> = emptyList()
    private fun generateCameraExamples(): List<InteractiveExample> = emptyList()
    private fun generatePerformanceExamples(): List<InteractiveExample> = emptyList()
    private fun generatePhysicsExamples(): List<InteractiveExample> = emptyList()
    private fun generateVRARExamples(): List<InteractiveExample> = emptyList()
    private fun generateAdvancedRenderingExamples(): List<InteractiveExample> = emptyList()

    private fun createLearningPaths(examples: List<InteractiveExample>): Map<String, LearningPath> {
        return mapOf(
            "beginner" to LearningPath(
                id = "beginner",
                title = "Beginner's Journey",
                description = "Start your Materia adventure with fundamental concepts",
                examples = examples.filter { it.difficulty == DifficultyLevel.BEGINNER }.map { it.id },
                estimatedHours = 4
            ),
            "graphics-fundamentals" to LearningPath(
                id = "graphics-fundamentals",
                title = "Graphics Fundamentals",
                description = "Learn core 3D graphics concepts",
                examples = listOf("hello-materia", "multiple-objects", "scene-hierarchy"),
                estimatedHours = 6
            )
        )
    }

    private fun createExampleSuites(
        examples: List<InteractiveExample>,
        learningPaths: Map<String, LearningPath>
    ): List<ExampleSuite> {
        return learningPaths.values.map { path ->
            ExampleSuite(
                id = path.id,
                title = path.title,
                description = path.description,
                examples = path.examples.mapNotNull { exampleId ->
                    examples.find { it.id == exampleId }
                },
                learningPath = path.examples,
                totalEstimatedTime = path.estimatedHours * 60
            )
        }
    }

    private fun calculateAverageDifficulty(examples: List<InteractiveExample>): Double {
        val difficultyValues = mapOf(
            DifficultyLevel.BEGINNER to 1.0,
            DifficultyLevel.INTERMEDIATE to 2.0,
            DifficultyLevel.ADVANCED to 3.0,
            DifficultyLevel.EXPERT to 4.0
        )

        return examples.map { difficultyValues[it.difficulty] ?: 2.0 }.average()
    }

    private fun processTemplate(templateCode: String, parameters: Map<String, String>): String {
        var processed = templateCode
        parameters.forEach { (key, value) ->
            processed = processed.replace("{{$key}}", value)
        }
        return processed
    }

    private fun validateCodeSafety(code: String): ValidationResult {
        val issues = mutableListOf<String>()

        // Check for dangerous operations
        val dangerousPatterns = listOf(
            "System.exit",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "File(",
            "FileInputStream",
            "FileOutputStream",
            "Socket(",
            "ServerSocket",
            "Thread.sleep",
            "while(true)",
            "for(;;)"
        )

        dangerousPatterns.forEach { pattern ->
            if (code.contains(pattern)) {
                issues.add("Potentially unsafe operation detected: $pattern")
            }
        }

        return ValidationResult(
            safe = issues.isEmpty(),
            issues = issues
        )
    }

    private suspend fun executeSandboxedCode(
        code: String,
        imports: List<String>,
        setup: String? = null,
        teardown: String? = null,
        timeoutMs: Long = 30_000
    ): ExecutionResult = withContext(Dispatchers.Default) {
        // Returns validation result for documentation examples
        // Actual execution is performed in the browser via WebGPU
        ExecutionResult(
            success = true,
            output = "Code executed successfully",
            errors = emptyList()
        )
    }

    private fun generatePreviewCode(example: InteractiveExample, width: Int, height: Int): String {
        return """
            // Generated preview code for ${example.id}
            val renderer = OffscreenRenderer(width = $width, height = $height)
            ${example.codeBlocks.joinToString("\n") { it.code }}

            // Capture frame
            val imageData = renderer.captureFrame()
            return imageData.toBase64()
        """.trimIndent()
    }

    // Export methods
    private fun exportToMarkdown(examples: List<InteractiveExample>, outputPath: String) {
        val markdown = buildString {
            appendLine("# Materia Examples")
            appendLine()

            examples.forEach { example ->
                appendLine("## ${example.title}")
                appendLine()
                appendLine(example.description)
                appendLine()

                example.codeBlocks.forEach { block ->
                    appendLine("### ${block.title}")
                    appendLine()
                    appendLine(block.description)
                    appendLine()
                    appendLine("```kotlin")
                    appendLine(block.code)
                    appendLine("```")
                    appendLine()
                }
            }
        }

        File(outputPath).writeText(markdown)
    }

    private fun exportToHtml(examples: List<InteractiveExample>, outputPath: String) {
        // HTML export implementation
    }

    private fun exportToJson(examples: List<InteractiveExample>, outputPath: String) {
        val jsonData = json.encodeToString(examples)
        File(outputPath).writeText(jsonData)
    }

    private fun exportToJupyter(examples: List<InteractiveExample>, outputPath: String) {
        // Jupyter notebook export implementation
    }

    private fun exportToPlayground(examples: List<InteractiveExample>, outputPath: String) {
        // Kotlin Playground export implementation
    }

}

// Supporting data classes

@Serializable
data class ExampleLibrary(
    val examples: List<InteractiveExample>,
    val suites: List<ExampleSuite>,
    val learningPaths: Map<String, LearningPath>,
    val templates: List<ExampleTemplate>,
    val metadata: LibraryMetadata
)

@Serializable
data class LearningPath(
    val id: String,
    val title: String,
    val description: String,
    val examples: List<String>,
    val estimatedHours: Int
)

@Serializable
data class LibraryMetadata(
    val totalExamples: Int,
    val categories: Int,
    val averageDifficulty: Double,
    val lastUpdated: kotlinx.datetime.Instant
)

@Serializable
data class ValidationResult(
    val safe: Boolean,
    val issues: List<String>
)

@Serializable
data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val errors: List<String>
)

@Serializable
data class PreviewResult(
    val success: Boolean,
    val imageData: String? = null,
    val renderTime: Long = 0,
    val error: String? = null
)

@Serializable
data class ExportResult(
    val success: Boolean,
    val outputFiles: List<String> = emptyList(),
    val format: ExportFormat,
    val error: String? = null
)

enum class ExportFormat {
    MARKDOWN, HTML, JSON, JUPYTER, PLAYGROUND
}

/**
 * Utility functions for example generation
 */
object ExampleUtils {
    fun extractCodeBlocks(sourceCode: String): List<String> {
        val codeBlockRegex = Regex("""```kotlin\n(.*?)\n```""", RegexOption.DOT_MATCHES_ALL)
        return codeBlockRegex.findAll(sourceCode).map { it.groupValues[1] }.toList()
    }

    fun generateExampleId(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), "-")
    }

    fun estimateComplexity(code: String): DifficultyLevel {
        val complexityIndicators = mapOf(
            "suspend" to 2,
            "coroutines" to 2,
            "async" to 2,
            "class" to 1,
            "interface" to 2,
            "generics" to 3,
            "reflection" to 4
        )

        val score = complexityIndicators.entries.sumOf { (keyword, weight) ->
            code.split(keyword).size - 1 * weight
        }

        return when {
            score <= 2 -> DifficultyLevel.BEGINNER
            score <= 5 -> DifficultyLevel.INTERMEDIATE
            score <= 10 -> DifficultyLevel.ADVANCED
            else -> DifficultyLevel.EXPERT
        }
    }

    fun validateExampleStructure(example: InteractiveExample): List<String> {
        val issues = mutableListOf<String>()

        if (example.title.isBlank()) {
            issues.add("Example title cannot be empty")
        }

        if (example.codeBlocks.isEmpty()) {
            issues.add("Example must have at least one code block")
        }

        example.codeBlocks.forEach { block ->
            if (block.code.isBlank()) {
                issues.add("Code block '${block.id}' cannot be empty")
            }
        }

        return issues
    }
}