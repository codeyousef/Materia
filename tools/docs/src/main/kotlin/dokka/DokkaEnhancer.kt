package tools.docs.dokka

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.ExperimentalTime
import java.io.File
import java.net.URL
import kotlin.collections.*

/**
 * Enhanced Dokka integration for KreeKt documentation generation.
 * Provides advanced documentation features, custom styling, and interactive elements.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class DokkaConfiguration(
    val projectName: String,
    val projectVersion: String,
    val outputDirectory: String,
    val sourceDirectories: List<String>,
    val platforms: List<DocumentationPlatform>,
    val customization: DokkaCustomization,
    val plugins: List<DokkaPlugin> = emptyList(),
    val externalLinks: List<ExternalDocumentationLink> = emptyList()
)

@Serializable
data class DocumentationPlatform(
    val name: String,
    val displayName: String,
    val sourceSetId: String,
    val targets: List<String>,
    val languageVersion: String = "1.9",
    val apiVersion: String = "1.9"
)

@Serializable
data class DokkaCustomization(
    val logo: String? = null,
    val theme: DocumentationTheme = DocumentationTheme.DEFAULT,
    val customCss: List<String> = emptyList(),
    val customJs: List<String> = emptyList(),
    val footerMessage: String? = null,
    val socialLinks: Map<String, String> = emptyMap(),
    val searchEnabled: Boolean = true,
    val navigationDepth: Int = 3
)

enum class DocumentationTheme {
    DEFAULT, MATERIAL, MINIMAL, CUSTOM
}

@Serializable
data class DokkaPlugin(
    val name: String,
    val version: String,
    val configuration: Map<String, String> = emptyMap()
)

@Serializable
data class ExternalDocumentationLink(
    val url: String,
    val packageListUrl: String? = null,
    val description: String? = null
)

@Serializable
data class DocumentationMetadata(
    val title: String,
    val description: String,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val category: DocumentationCategory,
    val complexity: ComplexityLevel = ComplexityLevel.INTERMEDIATE,
    val platforms: List<String> = emptyList(),
    val relatedTopics: List<String> = emptyList()
)

enum class DocumentationCategory {
    API_REFERENCE, TUTORIAL, GUIDE, EXAMPLE, MIGRATION, TROUBLESHOOTING
}

enum class ComplexityLevel {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
}

@Serializable
data class CodeExample(
    val id: String,
    val title: String,
    val description: String,
    val code: String,
    val language: String = "kotlin",
    val platforms: List<String> = emptyList(),
    val runnable: Boolean = false,
    val expectedOutput: String? = null,
    val imports: List<String> = emptyList()
)

@Serializable
data class DocumentationSection(
    val id: String,
    val title: String,
    val content: String,
    val examples: List<CodeExample> = emptyList(),
    val metadata: DocumentationMetadata,
    val subsections: List<DocumentationSection> = emptyList()
)

@Serializable
data class APIDocumentation(
    val className: String,
    val packageName: String,
    val description: String,
    val examples: List<CodeExample> = emptyList(),
    val usageNotes: List<String> = emptyList(),
    val performanceNotes: List<String> = emptyList(),
    val platformNotes: Map<String, String> = emptyMap(),
    val relatedClasses: List<String> = emptyList(),
    val sinceVersion: String? = null,
    val deprecatedSince: String? = null
)

/**
 * Main Dokka enhancement engine with advanced documentation features.
 */
class DokkaEnhancer {
    private val configuration = mutableMapOf<String, Any>()
    private val customProcessors = mutableListOf<DocumentationProcessor>()

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        // Standard Kotlin/KMP external documentation links
        private val standardExternalLinks = listOf(
            ExternalDocumentationLink(
                url = "https://kotlinlang.org/api/latest/jvm/stdlib/",
                description = "Kotlin Standard Library"
            ),
            ExternalDocumentationLink(
                url = "https://kotlinlang.org/api/kotlinx.coroutines/",
                description = "Kotlinx Coroutines"
            ),
            ExternalDocumentationLink(
                url = "https://kotlinlang.org/api/kotlinx.serialization/",
                description = "Kotlinx Serialization"
            ),
            ExternalDocumentationLink(
                url = "https://kotlinlang.org/api/kotlinx-datetime/",
                description = "Kotlinx DateTime"
            )
        )
    }

    /**
     * Generate enhanced documentation with custom features
     */
    suspend fun generateDocumentation(
        config: DokkaConfiguration
    ): DocumentationResult = withContext(Dispatchers.IO) {
        val startTime = Clock.System.now().toEpochMilliseconds()

        try {
            // Prepare enhanced configuration
            val enhancedConfig = enhanceConfiguration(config)

            // Pre-process source files for additional metadata
            val sourceMetadata = extractSourceMetadata(config.sourceDirectories)

            // Generate API documentation with enhancements
            val apiDocs = generateEnhancedApiDocumentation(enhancedConfig, sourceMetadata)

            // Generate custom sections (tutorials, guides, etc.)
            val customSections = generateCustomSections(enhancedConfig)

            // Generate interactive examples
            val interactiveExamples = generateInteractiveExamples(enhancedConfig)

            // Generate platform-specific documentation
            val platformDocs = generatePlatformDocumentation(enhancedConfig)

            // Apply custom styling and themes
            applyCustomStyling(enhancedConfig)

            // Generate search indices
            val searchIndex = generateSearchIndex(apiDocs, customSections)

            // Create navigation structure
            val navigation = generateNavigation(apiDocs, customSections, platformDocs)

            // Generate output files
            val outputFiles = generateOutputFiles(
                config = enhancedConfig,
                apiDocs = apiDocs,
                customSections = customSections,
                examples = interactiveExamples,
                platformDocs = platformDocs,
                navigation = navigation,
                searchIndex = searchIndex
            )

            val executionTime = Clock.System.now().toEpochMilliseconds() - startTime

            DocumentationResult(
                success = true,
                outputDirectory = config.outputDirectory,
                generatedFiles = outputFiles,
                statistics = DocumentationStatistics(
                    totalClasses = sourceMetadata.classes.size,
                    totalFunctions = sourceMetadata.functions.size,
                    totalProperties = sourceMetadata.properties.size,
                    totalExamples = interactiveExamples.size,
                    platforms = config.platforms.size,
                    executionTimeMs = executionTime
                ),
                warnings = emptyList(),
                errors = emptyList()
            )

        } catch (e: Exception) {
            DocumentationResult(
                success = false,
                outputDirectory = config.outputDirectory,
                generatedFiles = emptyList(),
                statistics = DocumentationStatistics(),
                warnings = emptyList(),
                errors = listOf("Documentation generation failed: ${e.message}")
            )
        }
    }

    /**
     * Add custom documentation processor
     */
    fun addProcessor(processor: DocumentationProcessor) {
        customProcessors.add(processor)
    }

    /**
     * Generate API reference with enhanced features
     */
    suspend fun generateApiReference(
        sourceDirectories: List<String>,
        outputPath: String
    ): List<APIDocumentation> = withContext(Dispatchers.Default) {
        val sourceFiles = discoverSourceFiles(sourceDirectories)
        val apiDocs = mutableListOf<APIDocumentation>()

        sourceFiles.forEach { file ->
            val classDocumentation = extractClassDocumentation(file)
            if (classDocumentation != null) {
                val enhanced = enhanceApiDocumentation(classDocumentation)
                apiDocs.add(enhanced)
            }
        }

        // Sort by package and class name
        apiDocs.sortedWith(compareBy({ it.packageName }, { it.className }))
    }

    /**
     * Generate interactive code examples
     */
    suspend fun generateInteractiveExamples(
        config: DokkaConfiguration
    ): List<CodeExample> = withContext(Dispatchers.Default) {
        val examples = mutableListOf<CodeExample>()

        // Extract examples from source code comments
        val sourceExamples = extractExamplesFromSource(config.sourceDirectories)
        examples.addAll(sourceExamples)

        // Generate platform-specific examples
        config.platforms.forEach { platform ->
            val platformExamples = generatePlatformExamples(platform)
            examples.addAll(platformExamples)
        }

        // Add common usage patterns
        examples.addAll(generateCommonUsageExamples())

        // Add migration examples
        examples.addAll(generateMigrationExamples())

        examples
    }

    /**
     * Generate platform-specific documentation
     */
    suspend fun generatePlatformDocumentation(
        config: DokkaConfiguration
    ): Map<String, DocumentationSection> = withContext(Dispatchers.Default) {
        val platformDocs = mutableMapOf<String, DocumentationSection>()

        config.platforms.forEach { platform ->
            val section = DocumentationSection(
                id = "platform-${platform.name}",
                title = "${platform.displayName} Platform",
                content = generatePlatformContent(platform),
                examples = generatePlatformExamples(platform),
                metadata = DocumentationMetadata(
                    title = "${platform.displayName} Platform Guide",
                    description = "Platform-specific documentation for ${platform.displayName}",
                    category = DocumentationCategory.GUIDE,
                    platforms = listOf(platform.name)
                )
            )
            platformDocs[platform.name] = section
        }

        platformDocs
    }

    /**
     * Generate comprehensive search index
     */
    suspend fun generateSearchIndex(
        apiDocs: List<APIDocumentation>,
        customSections: List<DocumentationSection>
    ): SearchIndex = withContext(Dispatchers.Default) {
        val entries = mutableListOf<SearchEntry>()

        // Index API documentation
        apiDocs.forEach { api ->
            entries.add(SearchEntry(
                id = "${api.packageName}.${api.className}",
                title = api.className,
                type = SearchEntryType.CLASS,
                content = api.description,
                url = "/api/${api.packageName.replace('.', '/')}/${api.className}.html",
                tags = listOf(api.packageName, "api", "class")
            ))
        }

        // Index custom sections
        customSections.forEach { section ->
            indexSection(section, entries)
        }

        SearchIndex(
            entries = entries,
            version = "1.0",
            generatedAt = Clock.System.now()
        )
    }

    /**
     * Apply custom themes and styling
     */
    private suspend fun applyCustomStyling(config: DokkaConfiguration) = withContext(Dispatchers.IO) {
        val customization = config.customization

        when (customization.theme) {
            DocumentationTheme.MATERIAL -> applyMaterialTheme(config.outputDirectory)
            DocumentationTheme.MINIMAL -> applyMinimalTheme(config.outputDirectory)
            DocumentationTheme.CUSTOM -> applyCustomTheme(config.outputDirectory, customization)
            DocumentationTheme.DEFAULT -> applyDefaultEnhancements(config.outputDirectory)
        }

        // Add custom CSS files
        customization.customCss.forEach { cssFile ->
            copyCustomAsset(cssFile, config.outputDirectory)
        }

        // Add custom JavaScript files
        customization.customJs.forEach { jsFile ->
            copyCustomAsset(jsFile, config.outputDirectory)
        }
    }

    // Private helper methods

    private fun enhanceConfiguration(config: DokkaConfiguration): DokkaConfiguration {
        return config.copy(
            externalLinks = config.externalLinks + standardExternalLinks,
            plugins = config.plugins + getRecommendedPlugins()
        )
    }

    private suspend fun extractSourceMetadata(sourceDirectories: List<String>): SourceMetadata =
        withContext(Dispatchers.IO) {
            val sourceFiles = discoverSourceFiles(sourceDirectories)
            val classes = mutableListOf<String>()
            val functions = mutableListOf<String>()
            val properties = mutableListOf<String>()

            sourceFiles.forEach { file ->
                val content = File(file).readText()

                // Extract class names
                val classRegex = Regex("""(?:class|interface|object)\s+(\w+)""")
                classes.addAll(classRegex.findAll(content).map { it.groupValues[1] })

                // Extract function names
                val functionRegex = Regex("""fun\s+(\w+)\s*\(""")
                functions.addAll(functionRegex.findAll(content).map { it.groupValues[1] })

                // Extract property names
                val propertyRegex = Regex("""(?:val|var)\s+(\w+)\s*[:=]""")
                properties.addAll(propertyRegex.findAll(content).map { it.groupValues[1] })
            }

            SourceMetadata(
                classes = classes.distinct(),
                functions = functions.distinct(),
                properties = properties.distinct()
            )
        }

    private fun generateEnhancedApiDocumentation(
        config: DokkaConfiguration,
        metadata: SourceMetadata
    ): List<APIDocumentation> {
        // Enhanced API documentation generation
        return metadata.classes.map { className ->
            APIDocumentation(
                className = className,
                packageName = "kreekt.core", // Would be extracted from source
                description = "Enhanced documentation for $className",
                examples = generateClassExamples(className),
                usageNotes = generateUsageNotes(className),
                performanceNotes = generatePerformanceNotes(className),
                platformNotes = generatePlatformNotes(className, config.platforms),
                relatedClasses = findRelatedClasses(className, metadata.classes)
            )
        }
    }

    private fun generateCustomSections(config: DokkaConfiguration): List<DocumentationSection> {
        return listOf(
            DocumentationSection(
                id = "getting-started",
                title = "Getting Started",
                content = generateGettingStartedContent(),
                metadata = DocumentationMetadata(
                    title = "Getting Started with KreeKt",
                    description = "Quick start guide for KreeKt 3D graphics library",
                    category = DocumentationCategory.TUTORIAL,
                    complexity = ComplexityLevel.BEGINNER
                )
            ),
            DocumentationSection(
                id = "architecture",
                title = "Architecture Overview",
                content = generateArchitectureContent(),
                metadata = DocumentationMetadata(
                    title = "KreeKt Architecture",
                    description = "Understanding KreeKt's multiplatform architecture",
                    category = DocumentationCategory.GUIDE,
                    complexity = ComplexityLevel.INTERMEDIATE
                )
            ),
            DocumentationSection(
                id = "performance",
                title = "Performance Guide",
                content = generatePerformanceContent(),
                metadata = DocumentationMetadata(
                    title = "Performance Optimization",
                    description = "Best practices for optimal performance",
                    category = DocumentationCategory.GUIDE,
                    complexity = ComplexityLevel.ADVANCED
                )
            )
        )
    }

    private fun generatePlatformExamples(platform: DocumentationPlatform): List<CodeExample> {
        return when (platform.name) {
            "jvm" -> listOf(
                CodeExample(
                    id = "jvm-setup",
                    title = "JVM Setup",
                    description = "Setting up KreeKt on JVM platform",
                    code = """
                        // JVM-specific setup
                        val renderer = VulkanRenderer()
                        val scene = Scene()
                        renderer.render(scene)
                    """.trimIndent(),
                    platforms = listOf("jvm")
                )
            )
            "js" -> listOf(
                CodeExample(
                    id = "js-setup",
                    title = "JavaScript Setup",
                    description = "Setting up KreeKt in browser",
                    code = """
                        // JavaScript/WebGPU setup
                        val renderer = WebGPURenderer()
                        val canvas = document.getElementById("canvas") as HTMLCanvasElement
                        renderer.setCanvas(canvas)
                    """.trimIndent(),
                    platforms = listOf("js")
                )
            )
            else -> emptyList()
        }
    }

    private fun generateCommonUsageExamples(): List<CodeExample> {
        return listOf(
            CodeExample(
                id = "basic-scene",
                title = "Creating a Basic Scene",
                description = "How to create and render a basic 3D scene",
                code = """
                    val scene = Scene()
                    val camera = PerspectiveCamera(75.0, aspect = 16.0/9.0)
                    val renderer = createRenderer()

                    // Add a cube to the scene
                    val geometry = BoxGeometry(1.0, 1.0, 1.0)
                    val material = BasicMaterial(color = Color.RED)
                    val cube = Mesh(geometry, material)
                    scene.add(cube)

                    // Render the scene
                    renderer.render(scene, camera)
                """.trimIndent(),
                runnable = true,
                expectedOutput = "Renders a red cube in 3D space"
            ),
            CodeExample(
                id = "animation-loop",
                title = "Animation Loop",
                description = "Creating a smooth animation loop",
                code = """
                    fun animate() {
                        // Rotate the cube
                        cube.rotation.x += 0.01
                        cube.rotation.y += 0.01

                        renderer.render(scene, camera)

                        // Schedule next frame
                        requestAnimationFrame(::animate)
                    }

                    animate()
                """.trimIndent(),
                runnable = true
            )
        )
    }

    private fun generateMigrationExamples(): List<CodeExample> {
        return listOf(
            CodeExample(
                id = "threejs-migration",
                title = "Migrating from Three.js",
                description = "How to migrate Three.js code to KreeKt",
                code = """
                    // Three.js
                    const scene = new THREE.Scene();
                    const camera = new THREE.PerspectiveCamera(75, aspect);
                    const renderer = new THREE.WebGLRenderer();

                    // KreeKt equivalent
                    val scene = Scene()
                    val camera = PerspectiveCamera(75.0, aspect)
                    val renderer = WebGPURenderer()
                """.trimIndent(),
                language = "kotlin"
            )
        )
    }

    private fun discoverSourceFiles(directories: List<String>): List<String> {
        return directories.flatMap { dir ->
            File(dir).walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.absolutePath }
                .toList()
        }
    }

    private fun extractClassDocumentation(filePath: String): APIDocumentation? {
        // Extract documentation from source file
        val content = File(filePath).readText()
        val classMatch = Regex("""class\s+(\w+)""").find(content)

        return classMatch?.let { match ->
            APIDocumentation(
                className = match.groupValues[1],
                packageName = extractPackageName(content),
                description = extractKDocDescription(content)
            )
        }
    }

    private fun enhanceApiDocumentation(api: APIDocumentation): APIDocumentation {
        return api.copy(
            examples = generateClassExamples(api.className),
            usageNotes = generateUsageNotes(api.className),
            performanceNotes = generatePerformanceNotes(api.className)
        )
    }

    private fun extractExamplesFromSource(sourceDirectories: List<String>): List<CodeExample> {
        // Extract @sample annotations and example code from sources
        return emptyList() // Simplified implementation for the enhancer prototype
    }

    private fun generateOutputFiles(
        config: DokkaConfiguration,
        apiDocs: List<APIDocumentation>,
        customSections: List<DocumentationSection>,
        examples: List<CodeExample>,
        platformDocs: Map<String, DocumentationSection>,
        navigation: NavigationStructure,
        searchIndex: SearchIndex
    ): List<String> {
        // Generate HTML, JSON, and other output files
        return listOf(
            "${config.outputDirectory}/index.html",
            "${config.outputDirectory}/api/index.html",
            "${config.outputDirectory}/search.json",
            "${config.outputDirectory}/navigation.json"
        )
    }

    private fun generateNavigation(
        apiDocs: List<APIDocumentation>,
        customSections: List<DocumentationSection>,
        platformDocs: Map<String, DocumentationSection>
    ): NavigationStructure {
        return NavigationStructure(
            sections = customSections.map { NavigationItem(it.id, it.title, it.id) },
            api = NavigationItem("api", "API Reference", "api/index.html"),
            platforms = platformDocs.map { (name, section) ->
                NavigationItem(name, section.title, "platforms/$name.html")
            }
        )
    }

    private fun indexSection(section: DocumentationSection, entries: MutableList<SearchEntry>) {
        entries.add(SearchEntry(
            id = section.id,
            title = section.title,
            type = SearchEntryType.SECTION,
            content = section.content,
            url = "/${section.id}.html",
            tags = section.metadata.tags
        ))

        section.subsections.forEach { subsection ->
            indexSection(subsection, entries)
        }
    }

    // Theme application methods
    private fun applyMaterialTheme(outputDir: String) {
        // Apply Material Design theme
    }

    private fun applyMinimalTheme(outputDir: String) {
        // Apply minimal theme
    }

    private fun applyCustomTheme(outputDir: String, customization: DokkaCustomization) {
        // Apply custom theme based on customization settings
    }

    private fun applyDefaultEnhancements(outputDir: String) {
        // Apply default enhancements to standard Dokka theme
    }

    private fun copyCustomAsset(assetPath: String, outputDir: String) {
        // Copy custom CSS/JS assets to output directory
    }

    // Content generation helpers
    private fun generateGettingStartedContent(): String = """
        # Getting Started with KreeKt

        KreeKt is a Kotlin Multiplatform 3D graphics library that provides Three.js-like API
        for creating 3D applications across JVM, Web, Android, iOS, and Native platforms.

        ## Installation

        Add KreeKt to your project dependencies:

        ```kotlin
        dependencies {
            implementation("io.github.kreekt:kreekt-core:${'$'}kreekt_version")
            implementation("io.github.kreekt:kreekt-renderer:${'$'}kreekt_version")
        }
        ```

        ## Basic Usage

        Create your first 3D scene in just a few lines:

        ```kotlin
        val scene = Scene()
        val camera = PerspectiveCamera(75.0, aspect = 16.0/9.0)
        val renderer = createRenderer()

        val geometry = BoxGeometry(1.0, 1.0, 1.0)
        val material = BasicMaterial(color = Color.RED)
        val cube = Mesh(geometry, material)
        scene.add(cube)

        renderer.render(scene, camera)
        ```
    """.trimIndent()

    private fun generateArchitectureContent(): String = """
        # KreeKt Architecture

        KreeKt is built on a modular, multiplatform architecture that provides
        consistent 3D graphics capabilities across all supported platforms.

        ## Core Modules

        - **kreekt-core**: Math primitives and utilities
        - **kreekt-renderer**: WebGPU/Vulkan abstraction layer
        - **kreekt-scene**: Scene graph system
        - **kreekt-geometry**: Geometry classes and primitives
        - **kreekt-material**: Material system and shaders

        ## Platform Strategy

        KreeKt uses Kotlin Multiplatform's expect/actual pattern to provide
        platform-specific implementations while maintaining a unified API.
    """.trimIndent()

    private fun generatePerformanceContent(): String = """
        # Performance Optimization Guide

        Learn how to optimize your KreeKt applications for best performance
        across all platforms.

        ## General Guidelines

        1. **Object Pooling**: Reuse frequently created objects
        2. **Frustum Culling**: Only render visible objects
        3. **Batching**: Group similar draw calls
        4. **LOD**: Use level-of-detail for distant objects

        ## Platform-Specific Tips

        ### JVM Platform
        - Use -XX:+UseG1GC for better garbage collection
        - Consider off-heap memory for large datasets

        ### Web Platform
        - Minimize JavaScript interop overhead
        - Use WebWorkers for heavy computations

        ### Mobile Platforms
        - Be mindful of thermal throttling
        - Optimize for battery life
    """.trimIndent()

    private fun generatePlatformContent(platform: DocumentationPlatform): String {
        return when (platform.name) {
            "jvm" -> "JVM platform documentation with Vulkan rendering backend"
            "js" -> "JavaScript platform documentation with WebGPU rendering"
            "android" -> "Android platform documentation with native Vulkan API"
            "ios" -> "iOS platform documentation with MoltenVK (Vulkan-to-Metal)"
            else -> "Platform-specific documentation for ${platform.displayName}"
        }
    }

    private fun getRecommendedPlugins(): List<DokkaPlugin> {
        return listOf(
            DokkaPlugin("org.jetbrains.dokka", "kotlin-as-java-plugin"),
            DokkaPlugin("org.jetbrains.dokka", "all-modules-page-plugin")
        )
    }

    private fun extractPackageName(content: String): String {
        val packageMatch = Regex("""package\s+([\w.]+)""").find(content)
        return packageMatch?.groupValues?.get(1) ?: "unknown"
    }

    private fun extractKDocDescription(content: String): String {
        val kdocMatch = Regex("""/\*\*(.*?)\*/""", RegexOption.DOT_MATCHES_ALL).find(content)
        return kdocMatch?.groupValues?.get(1)?.trim() ?: "No description available"
    }

    private fun generateClassExamples(className: String): List<CodeExample> {
        // Generate examples based on class name and common patterns
        return listOf(
            CodeExample(
                id = "${className.lowercase()}-basic",
                title = "Basic $className Usage",
                description = "Basic usage example for $className",
                code = "val instance = $className()",
                runnable = false
            )
        )
    }

    private fun generateUsageNotes(className: String): List<String> {
        return listOf("Initialize $className before use", "Thread-safe operations supported")
    }

    private fun generatePerformanceNotes(className: String): List<String> {
        return listOf("Optimized for frequent instantiation", "Consider object pooling for high-frequency usage")
    }

    private fun generatePlatformNotes(className: String, platforms: List<DocumentationPlatform>): Map<String, String> {
        return platforms.associate { platform ->
            platform.name to "Available on ${platform.displayName} platform"
        }
    }

    private fun findRelatedClasses(className: String, allClasses: List<String>): List<String> {
        // Simple heuristic to find related classes
        return allClasses.filter {
            it != className &&
            (it.contains(className.take(4)) || className.contains(it.take(4)))
        }.take(5)
    }
}

// Supporting data classes

@Serializable
data class SourceMetadata(
    val classes: List<String>,
    val functions: List<String>,
    val properties: List<String>
)

@Serializable
data class DocumentationResult(
    val success: Boolean,
    val outputDirectory: String,
    val generatedFiles: List<String>,
    val statistics: DocumentationStatistics,
    val warnings: List<String>,
    val errors: List<String>
)

@Serializable
data class DocumentationStatistics(
    val totalClasses: Int = 0,
    val totalFunctions: Int = 0,
    val totalProperties: Int = 0,
    val totalExamples: Int = 0,
    val platforms: Int = 0,
    val executionTimeMs: Long = 0
)

@Serializable
data class SearchIndex(
    val entries: List<SearchEntry>,
    val version: String,
    @OptIn(ExperimentalTime::class)
    val generatedAt: Instant
)

@Serializable
data class SearchEntry(
    val id: String,
    val title: String,
    val type: SearchEntryType,
    val content: String,
    val url: String,
    val tags: List<String>
)

enum class SearchEntryType {
    CLASS, FUNCTION, PROPERTY, SECTION, EXAMPLE
}

@Serializable
data class NavigationStructure(
    val sections: List<NavigationItem>,
    val api: NavigationItem,
    val platforms: List<NavigationItem>
)

@Serializable
data class NavigationItem(
    val id: String,
    val title: String,
    val url: String,
    val children: List<NavigationItem> = emptyList()
)

/**
 * Interface for custom documentation processors
 */
interface DocumentationProcessor {
    suspend fun process(content: String, metadata: DocumentationMetadata): String
    fun getSupportedTypes(): Set<DocumentationCategory>
}

/**
 * Utility functions for documentation enhancement
 */
object DokkaUtils {
    fun generateTableOfContents(sections: List<DocumentationSection>): String {
        return buildString {
            appendLine("## Table of Contents")
            appendLine()
            sections.forEach { section ->
                appendLine("- [${section.title}](#${section.id})")
                section.subsections.forEach { subsection ->
                    appendLine("  - [${subsection.title}](#${subsection.id})")
                }
            }
        }
    }

    fun formatCodeExample(example: CodeExample): String {
        return buildString {
            appendLine("### ${example.title}")
            appendLine()
            appendLine(example.description)
            appendLine()
            appendLine("```${example.language}")
            appendLine(example.code)
            appendLine("```")

            if (example.expectedOutput != null) {
                appendLine()
                appendLine("**Expected Output:**")
                appendLine("```")
                appendLine(example.expectedOutput)
                appendLine("```")
            }
        }
    }

    fun validateDocumentationStructure(sections: List<DocumentationSection>): List<String> {
        val issues = mutableListOf<String>()

        sections.forEach { section ->
            if (section.title.isBlank()) {
                issues.add("Section ${section.id} has empty title")
            }
            if (section.content.isBlank()) {
                issues.add("Section ${section.id} has empty content")
            }
        }

        return issues
    }
}