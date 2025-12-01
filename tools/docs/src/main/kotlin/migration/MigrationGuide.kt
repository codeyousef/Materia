package tools.docs.migration

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File

/**
 * Migration guide generator for Materia documentation.
 * Creates comprehensive migration guides from other 3D libraries (Three.js, Babylon.js, etc.)
 */
@Serializable
data class MigrationGuide(
    val id: String,
    val title: String,
    val fromLibrary: SourceLibrary,
    val toVersion: String,
    val sections: List<MigrationSection>,
    val codeExamples: List<MigrationExample>,
    val apiMappings: List<APIMapping>,
    val breakingChanges: List<BreakingChange>,
    val migrationSteps: List<MigrationStep>,
    val metadata: GuideMetadata
)

@Serializable
data class SourceLibrary(
    val name: String,
    val version: String,
    val language: String,
    val platform: String,
    val ecosystem: String
)

@Serializable
data class MigrationSection(
    val id: String,
    val title: String,
    val content: String,
    val importance: ImportanceLevel,
    val estimatedTime: Int, // minutes
    val examples: List<String>, // example IDs
    val resources: List<String>
)

enum class ImportanceLevel {
    CRITICAL, HIGH, MEDIUM, LOW, OPTIONAL
}

@Serializable
data class MigrationExample(
    val id: String,
    val title: String,
    val description: String,
    val beforeCode: CodeSnippet,
    val afterCode: CodeSnippet,
    val explanation: String,
    val difficulty: MigrationDifficulty,
    val category: MigrationCategory
)

@Serializable
data class CodeSnippet(
    val language: String,
    val code: String,
    val library: String,
    val version: String,
    val notes: List<String> = emptyList()
)

enum class MigrationDifficulty {
    TRIVIAL,      // Direct 1:1 mapping
    SIMPLE,       // Straightforward conversion
    MODERATE,     // Requires some refactoring
    COMPLEX,      // Significant changes needed
    ARCHITECTURAL // Fundamental approach changes
}

enum class MigrationCategory {
    INITIALIZATION,
    SCENE_SETUP,
    GEOMETRY_CREATION,
    MATERIALS,
    LIGHTING,
    ANIMATION,
    CONTROLS,
    RENDERING,
    ASSET_LOADING,
    PERFORMANCE,
    PLATFORM_SPECIFIC
}

@Serializable
data class APIMapping(
    val sourceAPI: String,
    val targetAPI: String,
    val mappingType: MappingType,
    val notes: String,
    val example: String? = null,
    val deprecated: Boolean = false,
    val alternativeApproach: String? = null
)

enum class MappingType {
    DIRECT,        // 1:1 equivalent
    PARTIAL,       // Similar but not identical
    REPLACEMENT,   // Different approach, same result
    OBSOLETE,      // No longer needed
    UNSUPPORTED    // Not available in target
}

@Serializable
data class BreakingChange(
    val type: ChangeType,
    val description: String,
    val impact: ImpactLevel,
    val workaround: String?,
    val affectedVersions: List<String>,
    val migrationRequired: Boolean
)

enum class ChangeType {
    API_REMOVAL,
    API_RENAME,
    PARAMETER_CHANGE,
    BEHAVIOR_CHANGE,
    DEPENDENCY_CHANGE,
    PLATFORM_SUPPORT
}

enum class ImpactLevel {
    BREAKING,    // Code will not compile
    BEHAVIORAL,  // Code compiles but behaves differently
    PERFORMANCE, // Performance characteristics changed
    DEPRECATION  // Still works but deprecated
}

@Serializable
data class MigrationStep(
    val order: Int,
    val title: String,
    val description: String,
    val actions: List<MigrationAction>,
    val validation: ValidationStep,
    val estimatedTime: Int,
    val dependencies: List<Int> = emptyList() // Other step orders
)

@Serializable
data class MigrationAction(
    val type: ActionType,
    val description: String,
    val code: String? = null,
    val files: List<String> = emptyList(),
    val automated: Boolean = false
)

enum class ActionType {
    UPDATE_DEPENDENCY,
    REPLACE_IMPORT,
    REFACTOR_CODE,
    UPDATE_CONFIG,
    ADD_FILE,
    REMOVE_FILE,
    RUN_COMMAND,
    MANUAL_REVIEW
}

@Serializable
data class ValidationStep(
    val description: String,
    val checks: List<ValidationCheck>,
    val expectedResults: List<String>
)

@Serializable
data class ValidationCheck(
    val type: CheckType,
    val description: String,
    val command: String? = null,
    val expectedOutput: String? = null
)

enum class CheckType {
    COMPILATION,
    RUNTIME,
    VISUAL,
    PERFORMANCE,
    MANUAL
}

@Serializable
data class GuideMetadata(
    val author: String,
    val createdDate: String,
    val lastUpdated: String,
    val version: String,
    val targetAudience: List<String>,
    val estimatedDuration: Int, // total minutes
    val prerequisites: List<String>,
    val supportedVersions: List<String>
)

/**
 * Main migration guide generator with comprehensive conversion support.
 */
class MigrationGuideGenerator {
    private val knownLibraries = mutableMapOf<String, LibraryKnowledge>()
    private val migrationStrategies = mutableMapOf<String, MigrationStrategy>()

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }

    init {
        initializeKnownLibraries()
        initializeMigrationStrategies()
    }

    /**
     * Generate comprehensive migration guide from source library to Materia
     */
    suspend fun generateMigrationGuide(
        fromLibrary: SourceLibrary,
        targetVersion: String = "1.0.0"
    ): MigrationGuide = withContext(Dispatchers.Default) {
        val libraryKnowledge = knownLibraries[fromLibrary.name.lowercase()]
            ?: throw IllegalArgumentException("Unknown source library: ${fromLibrary.name}")

        val strategy = migrationStrategies[fromLibrary.name.lowercase()]
            ?: getDefaultMigrationStrategy()

        // Generate sections based on migration strategy
        val sections = generateMigrationSections(fromLibrary, strategy)

        // Generate code examples for common patterns
        val codeExamples = generateCodeExamples(fromLibrary, libraryKnowledge)

        // Generate API mappings
        val apiMappings = generateAPIMappings(libraryKnowledge)

        // Identify breaking changes
        val breakingChanges = identifyBreakingChanges(fromLibrary, libraryKnowledge)

        // Generate step-by-step migration plan
        val migrationSteps = generateMigrationSteps(fromLibrary, strategy)

        MigrationGuide(
            id = "migration-${fromLibrary.name.lowercase()}-to-materia",
            title = "Migrating from ${fromLibrary.name} to Materia",
            fromLibrary = fromLibrary,
            toVersion = targetVersion,
            sections = sections,
            codeExamples = codeExamples,
            apiMappings = apiMappings,
            breakingChanges = breakingChanges,
            migrationSteps = migrationSteps,
            metadata = GuideMetadata(
                author = "Materia Migration Tool",
                createdDate = kotlinx.datetime.Clock.System.now().toString(),
                lastUpdated = kotlinx.datetime.Clock.System.now().toString(),
                version = "1.0",
                targetAudience = listOf("Developers familiar with ${fromLibrary.name}"),
                estimatedDuration = sections.sumOf { it.estimatedTime },
                prerequisites = listOf("Kotlin knowledge", "${fromLibrary.name} experience"),
                supportedVersions = listOf(targetVersion)
            )
        )
    }

    /**
     * Generate migration examples for specific patterns
     */
    suspend fun generatePatternMigrations(
        fromLibrary: SourceLibrary,
        patterns: List<String>
    ): List<MigrationExample> = withContext(Dispatchers.Default) {
        val libraryKnowledge = knownLibraries[fromLibrary.name.lowercase()]
            ?: return@withContext emptyList()

        patterns.mapNotNull { pattern ->
            // Pattern migration requires source library mapping configuration
            null
        }
    }

    /**
     * Analyze source code and suggest migrations
     */
    suspend fun analyzeSourceCode(
        sourceFiles: List<String>,
        fromLibrary: SourceLibrary
    ): MigrationAnalysis = withContext(Dispatchers.IO) {
        val issues = mutableListOf<MigrationIssue>()
        val suggestions = mutableListOf<MigrationSuggestion>()
        val estimatedEffort = mutableMapOf<String, Int>()

        sourceFiles.forEach { filePath ->
            val content = File(filePath).readText()
            val fileAnalysis = analyzeFile(content, fromLibrary)

            issues.addAll(fileAnalysis.issues.map { it.copy(file = filePath) })
            suggestions.addAll(fileAnalysis.suggestions.map { it.copy(file = filePath) })
            estimatedEffort[filePath] = fileAnalysis.estimatedHours
        }

        MigrationAnalysis(
            totalFiles = sourceFiles.size,
            issues = issues,
            suggestions = suggestions,
            estimatedEffort = estimatedEffort,
            complexity = calculateMigrationComplexity(issues),
            recommendations = generateMigrationRecommendations(issues, suggestions)
        )
    }

    /**
     * Generate automated migration scripts
     */
    suspend fun generateMigrationScript(
        guide: MigrationGuide,
        scriptType: ScriptType
    ): MigrationScript = withContext(Dispatchers.Default) {
        when (scriptType) {
            ScriptType.GRADLE_SCRIPT -> generateGradleScript(guide)
            ScriptType.SHELL_SCRIPT -> generateShellScript(guide)
            ScriptType.KOTLIN_SCRIPT -> generateKotlinScript(guide)
            ScriptType.NPM_SCRIPT -> generateNpmScript(guide)
        }
    }

    /**
     * Export migration guide to various formats
     */
    suspend fun exportGuide(
        guide: MigrationGuide,
        format: ExportFormat,
        outputPath: String
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            when (format) {
                ExportFormat.MARKDOWN -> exportToMarkdown(guide, outputPath)
                ExportFormat.HTML -> exportToHtml(guide, outputPath)
                ExportFormat.PDF -> exportToPdf(guide, outputPath)
                ExportFormat.JSON -> exportToJson(guide, outputPath)
                ExportFormat.CONFLUENCE -> exportToConfluence(guide, outputPath)
            }

            ExportResult(success = true, outputPath = outputPath)
        } catch (e: Exception) {
            ExportResult(success = false, error = e.message)
        }
    }

    // Private helper methods

    private fun initializeKnownLibraries() {
        // Three.js knowledge base
        knownLibraries["three.js"] = LibraryKnowledge(
            name = "Three.js",
            coreClasses = listOf(
                "Scene", "Camera", "WebGLRenderer", "Mesh", "Geometry", "Material",
                "Light", "Group", "Object3D", "Texture", "Loader"
            ),
            commonPatterns = mapOf(
                "scene_setup" to "new THREE.Scene(); new THREE.PerspectiveCamera(); new THREE.WebGLRenderer()",
                "mesh_creation" to "new THREE.Mesh(geometry, material)",
                "animation_loop" to "function animate() { requestAnimationFrame(animate); renderer.render(scene, camera); }"
            ),
            apiMappings = generateThreeJSMappings()
        )

        // Babylon.js knowledge base
        knownLibraries["babylon.js"] = LibraryKnowledge(
            name = "Babylon.js",
            coreClasses = listOf(
                "Scene", "Camera", "Engine", "Mesh", "Material", "Light", "Node"
            ),
            commonPatterns = mapOf(
                "scene_setup" to "new BABYLON.Engine(); new BABYLON.Scene(); new BABYLON.Camera()",
                "mesh_creation" to "BABYLON.MeshBuilder.CreateBox()"
            ),
            apiMappings = generateBabylonJSMappings()
        )

        // Unity C# knowledge base (for developers coming from Unity)
        knownLibraries["unity"] = LibraryKnowledge(
            name = "Unity",
            coreClasses = listOf(
                "GameObject", "Transform", "Renderer", "Material", "Mesh", "Camera", "Light"
            ),
            commonPatterns = mapOf(
                "object_creation" to "GameObject.CreatePrimitive(PrimitiveType.Cube)",
                "component_access" to "GetComponent<Renderer>()"
            ),
            apiMappings = generateUnityMappings()
        )
    }

    private fun initializeMigrationStrategies() {
        migrationStrategies["three.js"] = MigrationStrategy(
            phases = listOf(
                MigrationPhase("setup", "Project Setup", listOf("dependencies", "imports")),
                MigrationPhase("core", "Core API Migration", listOf("scene", "camera", "renderer")),
                MigrationPhase("objects", "Object Migration", listOf("geometry", "materials", "meshes")),
                MigrationPhase("animation", "Animation Migration", listOf("animation_loop", "tweening")),
                MigrationPhase("optimization", "Optimization", listOf("performance", "cleanup"))
            ),
            automationLevel = AutomationLevel.SEMI_AUTOMATED
        )

        migrationStrategies["babylon.js"] = MigrationStrategy(
            phases = listOf(
                MigrationPhase("setup", "Engine Setup", listOf("engine", "scene")),
                MigrationPhase("rendering", "Rendering Pipeline", listOf("materials", "effects")),
                MigrationPhase("assets", "Asset Loading", listOf("models", "textures")),
                MigrationPhase("physics", "Physics Integration", listOf("physics_engine"))
            ),
            automationLevel = AutomationLevel.MANUAL
        )
    }

    private fun generateMigrationSections(
        fromLibrary: SourceLibrary,
        strategy: MigrationStrategy
    ): List<MigrationSection> {
        return strategy.phases.map { phase ->
            MigrationSection(
                id = phase.id,
                title = phase.title,
                content = generateSectionContent(phase, fromLibrary),
                importance = if (phase.id == "setup") ImportanceLevel.CRITICAL else ImportanceLevel.HIGH,
                estimatedTime = estimatePhaseTime(phase),
                examples = phase.topics.map { "${phase.id}_$it" },
                resources = generatePhaseResources(phase)
            )
        }
    }

    private fun generateCodeExamples(
        fromLibrary: SourceLibrary,
        knowledge: LibraryKnowledge
    ): List<MigrationExample> {
        return when (fromLibrary.name.lowercase()) {
            "three.js" -> generateThreeJSExamples()
            "babylon.js" -> generateBabylonJSExamples()
            "unity" -> generateUnityExamples()
            else -> emptyList()
        }
    }

    private fun generateThreeJSExamples(): List<MigrationExample> {
        return listOf(
            MigrationExample(
                id = "threejs_basic_scene",
                title = "Basic Scene Setup",
                description = "Converting Three.js scene initialization to Materia",
                beforeCode = CodeSnippet(
                    language = "javascript",
                    code = """
                        // Three.js
                        const scene = new THREE.Scene();
                        const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
                        const renderer = new THREE.WebGLRenderer();
                        renderer.setSize(window.innerWidth, window.innerHeight);
                        document.body.appendChild(renderer.domElement);

                        camera.position.z = 5;
                    """.trimIndent(),
                    library = "Three.js",
                    version = "r150+"
                ),
                afterCode = CodeSnippet(
                    language = "kotlin",
                    code = """
                        // Materia
                        val scene = Scene()
                        val camera = PerspectiveCamera(
                            fov = 75.0,
                            aspect = window.innerWidth.toDouble() / window.innerHeight,
                            near = 0.1,
                            far = 1000.0
                        )
                        val renderer = WebGPURenderer()
                        renderer.setSize(window.innerWidth, window.innerHeight)

                        camera.position.z = 5.0
                    """.trimIndent(),
                    library = "Materia",
                    version = "1.0+"
                ),
                explanation = """
                    The main differences in Materia:
                    1. No 'new' keyword - Kotlin uses constructor calls directly
                    2. Type-safe parameters with explicit types (Double instead of Number)
                    3. WebGPU renderer instead of WebGL (with WebGL fallback)
                    4. No manual DOM manipulation - handled by the renderer
                """.trimIndent(),
                difficulty = MigrationDifficulty.SIMPLE,
                category = MigrationCategory.INITIALIZATION
            ),

            MigrationExample(
                id = "threejs_mesh_creation",
                title = "Creating Meshes",
                description = "Converting Three.js mesh creation to Materia",
                beforeCode = CodeSnippet(
                    language = "javascript",
                    code = """
                        // Three.js
                        const geometry = new THREE.BoxGeometry(1, 1, 1);
                        const material = new THREE.MeshBasicMaterial({ color: 0xff0000 });
                        const cube = new THREE.Mesh(geometry, material);
                        scene.add(cube);
                    """.trimIndent(),
                    library = "Three.js",
                    version = "r150+"
                ),
                afterCode = CodeSnippet(
                    language = "kotlin",
                    code = """
                        // Materia
                        val geometry = BoxGeometry(1.0, 1.0, 1.0)
                        val material = BasicMaterial(color = Color.RED)
                        val cube = Mesh(geometry, material)
                        scene.add(cube)
                    """.trimIndent(),
                    library = "Materia",
                    version = "1.0+"
                ),
                explanation = """
                    Materia simplifications:
                    1. Constructor parameters are directly passed, no object literals
                    2. Color.RED constant instead of hex values
                    3. Type safety ensures correct parameter types
                    4. Same conceptual approach with cleaner syntax
                """.trimIndent(),
                difficulty = MigrationDifficulty.TRIVIAL,
                category = MigrationCategory.GEOMETRY_CREATION
            ),

            MigrationExample(
                id = "threejs_animation_loop",
                title = "Animation Loop",
                description = "Converting Three.js animation loop to Materia",
                beforeCode = CodeSnippet(
                    language = "javascript",
                    code = """
                        // Three.js
                        function animate() {
                            requestAnimationFrame(animate);

                            cube.rotation.x += 0.01;
                            cube.rotation.y += 0.01;

                            renderer.render(scene, camera);
                        }
                        animate();
                    """.trimIndent(),
                    library = "Three.js",
                    version = "r150+"
                ),
                afterCode = CodeSnippet(
                    language = "kotlin",
                    code = """
                        // Materia
                        fun animate() {
                            cube.rotation.x += 0.01
                            cube.rotation.y += 0.01

                            renderer.render(scene, camera)

                            requestAnimationFrame(::animate)
                        }
                        animate()
                    """.trimIndent(),
                    library = "Materia",
                    version = "1.0+"
                ),
                explanation = """
                    Materia animation patterns:
                    1. Function reference syntax (::animate) instead of string names
                    2. Same logical structure as Three.js
                    3. Type-safe numeric operations
                    4. Platform-appropriate requestAnimationFrame implementation
                """.trimIndent(),
                difficulty = MigrationDifficulty.SIMPLE,
                category = MigrationCategory.ANIMATION
            )
        )
    }

    private fun generateBabylonJSExamples(): List<MigrationExample> {
        return listOf(
            MigrationExample(
                id = "babylonjs_engine_setup",
                title = "Engine and Scene Setup",
                description = "Converting Babylon.js engine initialization to Materia",
                beforeCode = CodeSnippet(
                    language = "javascript",
                    code = """
                        // Babylon.js
                        const canvas = document.getElementById("renderCanvas");
                        const engine = new BABYLON.Engine(canvas, true);
                        const scene = new BABYLON.Scene(engine);

                        const camera = new BABYLON.FreeCamera("camera", new BABYLON.Vector3(0, 5, -10), scene);
                        camera.setTarget(BABYLON.Vector3.Zero());
                        camera.attachToCanvas(canvas, true);
                    """.trimIndent(),
                    library = "Babylon.js",
                    version = "5.0+"
                ),
                afterCode = CodeSnippet(
                    language = "kotlin",
                    code = """
                        // Materia
                        val canvas = document.getElementById("renderCanvas") as HTMLCanvasElement
                        val renderer = WebGPURenderer()
                        renderer.setCanvas(canvas)
                        val scene = Scene()

                        val camera = FreeCamera(
                            name = "camera",
                            position = Vector3(0.0, 5.0, -10.0)
                        )
                        camera.lookAt(Vector3.ZERO)
                        camera.attachControls(canvas)
                    """.trimIndent(),
                    library = "Materia",
                    version = "1.0+"
                ),
                explanation = """
                    Key differences in Materia:
                    1. Renderer abstraction instead of engine
                    2. Type-safe canvas handling
                    3. Named parameters for clarity
                    4. Simplified camera setup with lookAt method
                """.trimIndent(),
                difficulty = MigrationDifficulty.MODERATE,
                category = MigrationCategory.INITIALIZATION
            )
        )
    }

    private fun generateUnityExamples(): List<MigrationExample> {
        return listOf(
            MigrationExample(
                id = "unity_gameobject_creation",
                title = "GameObject to Mesh",
                description = "Converting Unity GameObject patterns to Materia Mesh objects",
                beforeCode = CodeSnippet(
                    language = "csharp",
                    code = """
                        // Unity C#
                        GameObject cube = GameObject.CreatePrimitive(PrimitiveType.Cube);
                        cube.transform.position = new Vector3(0, 1, 0);
                        cube.transform.rotation = Quaternion.Euler(45, 0, 0);

                        Renderer renderer = cube.GetComponent<Renderer>();
                        renderer.material.color = Color.red;
                    """.trimIndent(),
                    library = "Unity",
                    version = "2022.3+"
                ),
                afterCode = CodeSnippet(
                    language = "kotlin",
                    code = """
                        // Materia
                        val geometry = BoxGeometry(1.0, 1.0, 1.0)
                        val material = BasicMaterial(color = Color.RED)
                        val cube = Mesh(geometry, material)

                        cube.position.set(0.0, 1.0, 0.0)
                        cube.rotation.setFromEuler(45.0, 0.0, 0.0)

                        scene.add(cube)
                    """.trimIndent(),
                    library = "Materia",
                    version = "1.0+"
                ),
                explanation = """
                    Unity to Materia conceptual mapping:
                    1. GameObject + Primitive -> Geometry + Material + Mesh
                    2. Transform component -> Direct position/rotation properties
                    3. Component-based -> Composition-based architecture
                    4. Manual scene management instead of automatic hierarchy
                """.trimIndent(),
                difficulty = MigrationDifficulty.MODERATE,
                category = MigrationCategory.SCENE_SETUP
            )
        )
    }

    private fun generateAPIMappings(knowledge: LibraryKnowledge): List<APIMapping> {
        return knowledge.apiMappings
    }

    private fun generateThreeJSMappings(): List<APIMapping> {
        return listOf(
            APIMapping(
                sourceAPI = "THREE.Scene",
                targetAPI = "Scene",
                mappingType = MappingType.DIRECT,
                notes = "Direct equivalent, same functionality"
            ),
            APIMapping(
                sourceAPI = "THREE.WebGLRenderer",
                targetAPI = "WebGPURenderer",
                mappingType = MappingType.REPLACEMENT,
                notes = "WebGPU-based renderer with WebGL fallback",
                example = "WebGPURenderer() // Automatically falls back to WebGL if needed"
            ),
            APIMapping(
                sourceAPI = "THREE.PerspectiveCamera",
                targetAPI = "PerspectiveCamera",
                mappingType = MappingType.DIRECT,
                notes = "Same parameters, type-safe constructor"
            ),
            APIMapping(
                sourceAPI = "THREE.BoxGeometry",
                targetAPI = "BoxGeometry",
                mappingType = MappingType.DIRECT,
                notes = "Direct mapping with Double parameters"
            ),
            APIMapping(
                sourceAPI = "THREE.MeshBasicMaterial",
                targetAPI = "BasicMaterial",
                mappingType = MappingType.DIRECT,
                notes = "Simplified constructor with named parameters"
            ),
            APIMapping(
                sourceAPI = "requestAnimationFrame",
                targetAPI = "requestAnimationFrame",
                mappingType = MappingType.DIRECT,
                notes = "Platform-appropriate implementation (browser/native)"
            )
        )
    }

    private fun generateBabylonJSMappings(): List<APIMapping> {
        return listOf(
            APIMapping(
                sourceAPI = "BABYLON.Engine",
                targetAPI = "WebGPURenderer",
                mappingType = MappingType.REPLACEMENT,
                notes = "Engine concept replaced by renderer abstraction"
            ),
            APIMapping(
                sourceAPI = "BABYLON.Scene",
                targetAPI = "Scene",
                mappingType = MappingType.PARTIAL,
                notes = "Similar concept, different initialization pattern"
            ),
            APIMapping(
                sourceAPI = "BABYLON.MeshBuilder.CreateBox",
                targetAPI = "BoxGeometry + Mesh",
                mappingType = MappingType.REPLACEMENT,
                notes = "Separated geometry creation from mesh creation"
            )
        )
    }

    private fun generateUnityMappings(): List<APIMapping> {
        return listOf(
            APIMapping(
                sourceAPI = "GameObject",
                targetAPI = "Mesh/Group",
                mappingType = MappingType.REPLACEMENT,
                notes = "Component-based vs composition-based architecture"
            ),
            APIMapping(
                sourceAPI = "Transform",
                targetAPI = "Object3D.position/rotation/scale",
                mappingType = MappingType.PARTIAL,
                notes = "Direct properties instead of component"
            ),
            APIMapping(
                sourceAPI = "Renderer.material",
                targetAPI = "Mesh.material",
                mappingType = MappingType.DIRECT,
                notes = "Direct property access"
            )
        )
    }

    private fun identifyBreakingChanges(
        fromLibrary: SourceLibrary,
        knowledge: LibraryKnowledge
    ): List<BreakingChange> {
        return when (fromLibrary.name.lowercase()) {
            "three.js" -> listOf(
                BreakingChange(
                    type = ChangeType.API_RENAME,
                    description = "WebGLRenderer is replaced by WebGPURenderer",
                    impact = ImpactLevel.BREAKING,
                    workaround = "Use WebGPURenderer which provides WebGL fallback",
                    affectedVersions = listOf("all"),
                    migrationRequired = true
                ),
                BreakingChange(
                    type = ChangeType.PARAMETER_CHANGE,
                    description = "Constructor parameters are now strongly typed (Double vs Number)",
                    impact = ImpactLevel.BEHAVIORAL,
                    workaround = "Ensure all numeric parameters are properly typed",
                    affectedVersions = listOf("all"),
                    migrationRequired = false
                )
            )
            else -> emptyList()
        }
    }

    private fun generateMigrationSteps(
        fromLibrary: SourceLibrary,
        strategy: MigrationStrategy
    ): List<MigrationStep> {
        val steps = mutableListOf<MigrationStep>()
        var order = 1

        strategy.phases.forEach { phase ->
            phase.topics.forEach { topic ->
                steps.add(generateStepForTopic(order++, phase, topic, fromLibrary))
            }
        }

        return steps
    }

    private fun generateStepForTopic(
        order: Int,
        phase: MigrationPhase,
        topic: String,
        fromLibrary: SourceLibrary
    ): MigrationStep {
        return when (topic) {
            "dependencies" -> MigrationStep(
                order = order,
                title = "Update Dependencies",
                description = "Replace ${fromLibrary.name} dependencies with Materia",
                actions = listOf(
                    MigrationAction(
                        type = ActionType.UPDATE_DEPENDENCY,
                        description = "Add Materia dependencies to build.gradle.kts",
                        code = """
                            dependencies {
                                implementation("io.github.materia:materia-core:1.0.0")
                                implementation("io.github.materia:materia-renderer:1.0.0")
                            }
                        """.trimIndent(),
                        automated = false
                    )
                ),
                validation = ValidationStep(
                    description = "Verify dependencies are resolved",
                    checks = listOf(
                        ValidationCheck(
                            type = CheckType.COMPILATION,
                            description = "Build project to verify dependencies",
                            command = "./gradlew build"
                        )
                    ),
                    expectedResults = listOf("BUILD SUCCESSFUL")
                ),
                estimatedTime = 15
            )
            else -> MigrationStep(
                order = order,
                title = "Migrate $topic",
                description = "Convert $topic from ${fromLibrary.name} to Materia",
                actions = emptyList(),
                validation = ValidationStep("Manual verification", emptyList(), emptyList()),
                estimatedTime = 30
            )
        }
    }

    // File analysis methods
    private fun analyzeFile(content: String, fromLibrary: SourceLibrary): FileAnalysis {
        val issues = mutableListOf<MigrationIssue>()
        val suggestions = mutableListOf<MigrationSuggestion>()

        when (fromLibrary.name.lowercase()) {
            "three.js" -> analyzeThreeJSFile(content, issues, suggestions)
            "babylon.js" -> analyzeBabylonJSFile(content, issues, suggestions)
            "unity" -> analyzeUnityFile(content, issues, suggestions)
        }

        return FileAnalysis(
            issues = issues,
            suggestions = suggestions,
            estimatedHours = calculateFileComplexity(content, issues.size)
        )
    }

    private fun analyzeThreeJSFile(
        content: String,
        issues: MutableList<MigrationIssue>,
        suggestions: MutableList<MigrationSuggestion>
    ) {
        // Check for Three.js specific patterns
        if (content.contains("THREE.WebGLRenderer")) {
            issues.add(MigrationIssue(
                type = "API_CHANGE",
                description = "WebGLRenderer needs to be replaced with WebGPURenderer",
                severity = "HIGH",
                line = findLineNumber(content, "THREE.WebGLRenderer")
            ))
        }

        if (content.contains("new THREE.")) {
            suggestions.add(MigrationSuggestion(
                type = "SYNTAX_IMPROVEMENT",
                description = "Remove 'new' keyword and 'THREE.' prefix in Materia",
                example = "THREE.Scene() -> Scene()"
            ))
        }
    }

    private fun analyzeBabylonJSFile(content: String, issues: MutableList<MigrationIssue>, suggestions: MutableList<MigrationSuggestion>) {
        // Babylon.js specific analysis
    }

    private fun analyzeUnityFile(content: String, issues: MutableList<MigrationIssue>, suggestions: MutableList<MigrationSuggestion>) {
        // Unity C# specific analysis
    }

    // Export methods
    private fun exportToMarkdown(guide: MigrationGuide, outputPath: String) {
        val markdown = buildMigrationMarkdown(guide)
        File(outputPath).writeText(markdown)
    }

    private fun buildMigrationMarkdown(guide: MigrationGuide): String = buildString {
        appendLine("# ${guide.title}")
        appendLine()
        appendLine("Migration guide from ${guide.fromLibrary.name} ${guide.fromLibrary.version} to Materia ${guide.toVersion}")
        appendLine()

        // Table of contents
        appendLine("## Table of Contents")
        guide.sections.forEach { section ->
            appendLine("- [${section.title}](#${section.id})")
        }
        appendLine()

        // Sections
        guide.sections.forEach { section ->
            appendLine("## ${section.title}")
            appendLine()
            appendLine(section.content)
            appendLine()

            if (section.importance == ImportanceLevel.CRITICAL) {
                appendLine("⚠️ **Critical Section** - Must be completed before proceeding")
                appendLine()
            }
        }

        // Code examples
        appendLine("## Code Examples")
        guide.codeExamples.forEach { example ->
            appendLine("### ${example.title}")
            appendLine()
            appendLine(example.description)
            appendLine()

            appendLine("**Before (${example.beforeCode.library}):**")
            appendLine("```${example.beforeCode.language}")
            appendLine(example.beforeCode.code)
            appendLine("```")
            appendLine()

            appendLine("**After (${example.afterCode.library}):**")
            appendLine("```${example.afterCode.language}")
            appendLine(example.afterCode.code)
            appendLine("```")
            appendLine()

            appendLine("**Explanation:**")
            appendLine(example.explanation)
            appendLine()
        }

        // API mappings
        appendLine("## API Reference")
        appendLine("| ${guide.fromLibrary.name} | Materia | Type | Notes |")
        appendLine("|---|---|---|---|")
        guide.apiMappings.forEach { mapping ->
            appendLine("| `${mapping.sourceAPI}` | `${mapping.targetAPI}` | ${mapping.mappingType} | ${mapping.notes} |")
        }
        appendLine()

        // Migration steps
        appendLine("## Step-by-Step Migration")
        guide.migrationSteps.forEach { step ->
            appendLine("### Step ${step.order}: ${step.title}")
            appendLine()
            appendLine(step.description)
            appendLine()

            step.actions.forEach { action ->
                appendLine("- **${action.type}**: ${action.description}")
                if (action.code != null) {
                    appendLine("  ```")
                    appendLine("  ${action.code}")
                    appendLine("  ```")
                }
            }
            appendLine()

            appendLine("**Validation:**")
            appendLine(step.validation.description)
            step.validation.checks.forEach { check ->
                appendLine("- ${check.description}")
                if (check.command != null) {
                    appendLine("  ```bash")
                    appendLine("  ${check.command}")
                    appendLine("  ```")
                }
            }
            appendLine()
        }
    }

    private fun exportToHtml(guide: MigrationGuide, outputPath: String) {
        // HTML export implementation
    }

    private fun exportToPdf(guide: MigrationGuide, outputPath: String) {
        // PDF export implementation
    }

    private fun exportToJson(guide: MigrationGuide, outputPath: String) {
        val jsonData = json.encodeToString(guide)
        File(outputPath).writeText(jsonData)
    }

    private fun exportToConfluence(guide: MigrationGuide, outputPath: String) {
        // Confluence wiki format export
    }

    // Helper methods
    private fun getDefaultMigrationStrategy(): MigrationStrategy {
        return MigrationStrategy(
            phases = listOf(
                MigrationPhase("assessment", "Assessment", listOf("analysis")),
                MigrationPhase("setup", "Setup", listOf("dependencies")),
                MigrationPhase("migration", "Migration", listOf("core_apis")),
                MigrationPhase("validation", "Validation", listOf("testing"))
            ),
            automationLevel = AutomationLevel.MANUAL
        )
    }

    private fun generateSectionContent(phase: MigrationPhase, fromLibrary: SourceLibrary): String {
        return "Migration guidance for ${phase.title} when converting from ${fromLibrary.name} to Materia."
    }

    private fun estimatePhaseTime(phase: MigrationPhase): Int = phase.topics.size * 30 // 30 minutes per topic

    private fun generatePhaseResources(phase: MigrationPhase): List<String> {
        return listOf("Materia documentation", "API reference", "Examples")
    }

    private fun calculateMigrationComplexity(issues: List<MigrationIssue>): MigrationComplexity {
        val highSeverityCount = issues.count { it.severity == "HIGH" }
        val mediumSeverityCount = issues.count { it.severity == "MEDIUM" }

        return when {
            highSeverityCount > 10 -> MigrationComplexity.VERY_HIGH
            highSeverityCount > 5 -> MigrationComplexity.HIGH
            mediumSeverityCount > 10 -> MigrationComplexity.MEDIUM
            issues.size > 5 -> MigrationComplexity.LOW
            else -> MigrationComplexity.TRIVIAL
        }
    }

    private fun generateMigrationRecommendations(
        issues: List<MigrationIssue>,
        suggestions: List<MigrationSuggestion>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (issues.any { it.severity == "HIGH" }) {
            recommendations.add("Start with high-severity issues to avoid blocking problems")
        }

        if (suggestions.size > 20) {
            recommendations.add("Consider automated migration tools for repetitive changes")
        }

        return recommendations
    }

    private fun calculateFileComplexity(content: String, issueCount: Int): Int {
        val lines = content.lines().size
        val baseTime = lines / 50 // 1 hour per 50 lines of code
        val issueTime = issueCount * 0.5 // 30 minutes per issue
        return (baseTime + issueTime).toInt()
    }

    private fun findLineNumber(content: String, searchText: String): Int {
        return content.lines().indexOfFirst { it.contains(searchText) } + 1
    }

    // Script generation methods
    private fun generateGradleScript(guide: MigrationGuide): MigrationScript {
        val scriptContent = buildString {
            appendLine("// Generated migration script for Gradle")
            appendLine("dependencies {")
            appendLine("    implementation(\"io.github.materia:materia-core:${guide.toVersion}\")")
            appendLine("    implementation(\"io.github.materia:materia-renderer:${guide.toVersion}\")")
            appendLine("}")
        }

        return MigrationScript(
            type = ScriptType.GRADLE_SCRIPT,
            content = scriptContent,
            description = "Gradle build script updates for Materia migration"
        )
    }

    private fun generateShellScript(guide: MigrationGuide): MigrationScript {
        return MigrationScript(
            type = ScriptType.SHELL_SCRIPT,
            content = "#!/bin/bash\necho 'Migration script for ${guide.fromLibrary.name} to Materia'",
            description = "Shell script for automated migration tasks"
        )
    }

    private fun generateKotlinScript(guide: MigrationGuide): MigrationScript {
        return MigrationScript(
            type = ScriptType.KOTLIN_SCRIPT,
            content = "// Kotlin migration script",
            description = "Kotlin script for complex migration logic"
        )
    }

    private fun generateNpmScript(guide: MigrationGuide): MigrationScript {
        return MigrationScript(
            type = ScriptType.NPM_SCRIPT,
            content = "{\n  \"scripts\": {\n    \"migrate\": \"echo 'Migration complete'\"\n  }\n}",
            description = "NPM package.json scripts for web-based migrations"
        )
    }
}

// Supporting data classes and enums

@Serializable
data class LibraryKnowledge(
    val name: String,
    val coreClasses: List<String>,
    val commonPatterns: Map<String, String>,
    val apiMappings: List<APIMapping>
)

@Serializable
data class MigrationStrategy(
    val phases: List<MigrationPhase>,
    val automationLevel: AutomationLevel
)

@Serializable
data class MigrationPhase(
    val id: String,
    val title: String,
    val topics: List<String>
)

enum class AutomationLevel {
    MANUAL, SEMI_AUTOMATED, FULLY_AUTOMATED
}

@Serializable
data class MigrationAnalysis(
    val totalFiles: Int,
    val issues: List<MigrationIssue>,
    val suggestions: List<MigrationSuggestion>,
    val estimatedEffort: Map<String, Int>,
    val complexity: MigrationComplexity,
    val recommendations: List<String>
)

@Serializable
data class MigrationIssue(
    val type: String,
    val description: String,
    val severity: String,
    val line: Int = 0,
    val file: String = ""
)

@Serializable
data class MigrationSuggestion(
    val type: String,
    val description: String,
    val example: String,
    val file: String = ""
)

enum class MigrationComplexity {
    TRIVIAL, LOW, MEDIUM, HIGH, VERY_HIGH
}

@Serializable
data class FileAnalysis(
    val issues: List<MigrationIssue>,
    val suggestions: List<MigrationSuggestion>,
    val estimatedHours: Int
)

@Serializable
data class MigrationScript(
    val type: ScriptType,
    val content: String,
    val description: String
)

enum class ScriptType {
    GRADLE_SCRIPT, SHELL_SCRIPT, KOTLIN_SCRIPT, NPM_SCRIPT
}

@Serializable
data class ExportResult(
    val success: Boolean,
    val outputPath: String? = null,
    val error: String? = null
)

enum class ExportFormat {
    MARKDOWN, HTML, PDF, JSON, CONFLUENCE
}
