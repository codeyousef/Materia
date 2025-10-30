package io.materia.tools.docs.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * DocumentationArtifact - Data model for generated documentation with versioning
 *
 * This data class represents a complete documentation artifact that has been
 * generated from source code and other inputs. It includes versioning information,
 * module documentation, search indices, and associated assets.
 *
 * Documentation can be generated in multiple formats (HTML, Markdown, JSON).
 */
@Serializable
data class DocumentationArtifact @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val version: String,
    val generated: Instant,
    val format: DocFormat,
    val modules: List<ModuleDoc>,
    val searchIndex: SearchIndex,
    val assets: List<DocAsset>,
    val metadata: DocumentationMetadata,
    val configuration: DocGenerationConfig
) {
    init {
        require(version.matches(Regex("""\d+\.\d+\.\d+(-\w+)?"""))) {
            "Version must follow semantic versioning format (x.y.z[-suffix])"
        }
        require(modules.isNotEmpty()) { "Documentation must contain at least one module" }

        // Validate that all modules have unique names
        val moduleNames = modules.map { it.name }
        require(moduleNames.size == moduleNames.toSet().size) {
            "Module names must be unique"
        }

        // Validate search index references existing content
        val allSearchableItems = modules.flatMap { module ->
            module.packages.flatMap { pkg ->
                listOf(pkg.name) + pkg.classes.map { it.name } + pkg.functions.map { it.name }
            }
        }
        val missingReferences = searchIndex.entries.filter { entry ->
            entry.type != SearchEntryType.EXTERNAL &&
            !allSearchableItems.any { item -> entry.title.contains(item, ignoreCase = true) }
        }
        require(missingReferences.isEmpty()) {
            "Search index contains references to non-existent items: ${missingReferences.map { it.title }}"
        }
    }

    /**
     * Total number of documented items
     */
    val totalItems: Int
        get() = modules.sumOf { module ->
            module.packages.sumOf { pkg ->
                pkg.classes.size + pkg.functions.size + pkg.properties.size
            }
        }

    /**
     * Finds a module by name
     */
    fun findModule(name: String): ModuleDoc? {
        return modules.find { it.name == name }
    }

    /**
     * Finds a package across all modules
     */
    fun findPackage(packageName: String): PackageDoc? {
        return modules.flatMap { it.packages }.find { it.name == packageName }
    }

    /**
     * Searches documentation content
     */
    fun search(query: String, limit: Int = 10): List<SearchResult> {
        return searchIndex.search(query, limit)
    }

    /**
     * Gets all documentation assets of a specific type
     */
    fun getAssetsByType(type: AssetType): List<DocAsset> {
        return assets.filter { it.type == type }
    }

    /**
     * Generates a table of contents for the documentation
     */
    fun generateTableOfContents(): TableOfContents {
        val sections = modules.map { module ->
            TableOfContentsSection(
                title = module.name,
                level = 1,
                anchor = "module-${module.name.lowercase()}",
                subsections = module.packages.map { pkg ->
                    TableOfContentsSection(
                        title = pkg.name,
                        level = 2,
                        anchor = "package-${pkg.name.replace(".", "-")}",
                        subsections = pkg.classes.map { cls ->
                            TableOfContentsSection(
                                title = cls.name,
                                level = 3,
                                anchor = "class-${cls.name.lowercase()}"
                            )
                        }
                    )
                }
            )
        }

        return TableOfContents(sections)
    }

    /**
     * Validates the documentation for completeness and quality
     */
    fun validate(): DocumentationValidationResult {
        val issues = mutableListOf<DocumentationIssue>()

        // Check for missing documentation
        modules.forEach { module ->
            if (module.readme.isNullOrBlank()) {
                issues.add(DocumentationIssue(
                    severity = IssueSeverity.WARNING,
                    message = "Module '${module.name}' has no README",
                    location = "module:${module.name}"
                ))
            }

            module.packages.forEach { pkg ->
                val undocumentedClasses = pkg.classes.filter { it.description.isBlank() }
                if (undocumentedClasses.isNotEmpty()) {
                    issues.add(DocumentationIssue(
                        severity = IssueSeverity.INFO,
                        message = "Package '${pkg.name}' has ${undocumentedClasses.size} undocumented classes",
                        location = "package:${pkg.name}"
                    ))
                }

                val undocumentedFunctions = pkg.functions.filter { it.description.isBlank() }
                if (undocumentedFunctions.isNotEmpty()) {
                    issues.add(DocumentationIssue(
                        severity = IssueSeverity.INFO,
                        message = "Package '${pkg.name}' has ${undocumentedFunctions.size} undocumented functions",
                        location = "package:${pkg.name}"
                    ))
                }
            }
        }

        // Check for broken links
        val brokenLinks = findBrokenLinks()
        brokenLinks.forEach { link ->
            issues.add(DocumentationIssue(
                severity = IssueSeverity.ERROR,
                message = "Broken link: $link",
                location = "links"
            ))
        }

        // Check search index quality
        if (searchIndex.entries.isEmpty()) {
            issues.add(DocumentationIssue(
                severity = IssueSeverity.ERROR,
                message = "Search index is empty",
                location = "search"
            ))
        }

        val qualityScore = calculateQualityScore(issues)

        return DocumentationValidationResult(
            isValid = issues.none { it.severity == IssueSeverity.ERROR },
            issues = issues,
            qualityScore = qualityScore
        )
    }

    private fun findBrokenLinks(): List<String> {
        // Simplified implementation - would check actual link validity
        return emptyList()
    }

    private fun calculateQualityScore(issues: List<DocumentationIssue>): Float {
        val errorPenalty = issues.count { it.severity == IssueSeverity.ERROR } * 20
        val warningPenalty = issues.count { it.severity == IssueSeverity.WARNING } * 5
        val infoPenalty = issues.count { it.severity == IssueSeverity.INFO } * 1

        val totalPenalty = errorPenalty + warningPenalty + infoPenalty
        return (100 - totalPenalty).coerceAtLeast(0).toFloat()
    }

    companion object {
        /**
         * Creates a minimal documentation artifact for testing
         */
        fun createMinimal(version: String, moduleName: String): DocumentationArtifact {
            val now = kotlinx.datetime.Clock.System.now()

            val moduleDoc = ModuleDoc(
                name = moduleName,
                packages = listOf(
                    PackageDoc(
                        name = "$moduleName.core",
                        classes = emptyList(),
                        functions = emptyList(),
                        properties = emptyList()
                    )
                ),
                readme = "# $moduleName\n\nMinimal documentation for $moduleName module.",
                examples = emptyList()
            )

            return DocumentationArtifact(
                version = version,
                generated = now,
                format = DocFormat.HTML,
                modules = listOf(moduleDoc),
                searchIndex = SearchIndex.createEmpty(),
                assets = emptyList(),
                metadata = DocumentationMetadata(
                    title = "$moduleName Documentation",
                    description = "Generated documentation for $moduleName",
                    author = "System",
                    generator = "Materia Docs",
                    generatorVersion = "1.0.0",
                    sourceCommit = "unknown",
                    buildNumber = 1
                ),
                configuration = DocGenerationConfig.default()
            )
        }

        /**
         * Creates a comprehensive documentation artifact
         */
        fun createComprehensive(
            version: String,
            modules: List<ModuleDoc>,
            searchIndex: SearchIndex,
            assets: List<DocAsset>
        ): DocumentationArtifact {
            val now = kotlinx.datetime.Clock.System.now()

            return DocumentationArtifact(
                version = version,
                generated = now,
                format = DocFormat.HTML,
                modules = modules,
                searchIndex = searchIndex,
                assets = assets,
                metadata = DocumentationMetadata(
                    title = "Materia Library Documentation",
                    description = "Complete API documentation for Materia 3D graphics library",
                    author = "Materia Team",
                    generator = "Materia Docs",
                    generatorVersion = "1.0.0",
                    sourceCommit = "latest",
                    buildNumber = 1
                ),
                configuration = DocGenerationConfig.default()
            )
        }
    }
}

/**
 * ModuleDoc - Documentation for a single module
 */
@Serializable
data class ModuleDoc(
    val name: String,
    val packages: List<PackageDoc>,
    val readme: String? = null,
    val examples: List<CodeExample>,
    val dependencies: List<ModuleDependency> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(name.isNotBlank()) { "Module name must be non-empty" }

        // Validate unique package names within module
        val packageNames = packages.map { it.name }
        require(packageNames.size == packageNames.toSet().size) {
            "Package names must be unique within module"
        }
    }

    /**
     * Total number of documented items in this module
     */
    val itemCount: Int
        get() = packages.sumOf { it.classes.size + it.functions.size + it.properties.size }

    /**
     * Gets all public API items in this module
     */
    fun getPublicAPI(): List<APIItem> {
        return packages.flatMap { pkg ->
            pkg.classes.filter { it.visibility == Visibility.PUBLIC }.map { APIItem.Class(it) } +
            pkg.functions.filter { it.visibility == Visibility.PUBLIC }.map { APIItem.Function(it) } +
            pkg.properties.filter { it.visibility == Visibility.PUBLIC }.map { APIItem.Property(it) }
        }
    }
}

/**
 * PackageDoc - Documentation for a package within a module
 */
@Serializable
data class PackageDoc(
    val name: String,
    val classes: List<ClassDoc>,
    val functions: List<FunctionDoc>,
    val properties: List<PropertyDoc>,
    val description: String = "",
    val since: String? = null,
    val deprecated: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "Package name must be non-empty" }
    }

    /**
     * Total number of items in this package
     */
    val itemCount: Int
        get() = classes.size + functions.size + properties.size

    /**
     * Gets all deprecated items in this package
     */
    fun getDeprecatedItems(): List<String> {
        return classes.filter { it.deprecated }.map { "class ${it.name}" } +
               functions.filter { it.deprecated }.map { "function ${it.name}" } +
               properties.filter { it.deprecated }.map { "property ${it.name}" }
    }
}

/**
 * ClassDoc - Documentation for a class
 */
@Serializable
data class ClassDoc(
    val name: String,
    val fullName: String,
    val description: String,
    val type: ClassType,
    val visibility: Visibility,
    val modifiers: List<ClassModifier> = emptyList(),
    val constructors: List<ConstructorDoc> = emptyList(),
    val methods: List<MethodDoc> = emptyList(),
    val properties: List<PropertyDoc> = emptyList(),
    val inheritance: InheritanceInfo? = null,
    val annotations: List<AnnotationDoc> = emptyList(),
    val examples: List<CodeExample> = emptyList(),
    val since: String? = null,
    val deprecated: Boolean = false,
    val deprecationMessage: String? = null
) {
    init {
        require(name.isNotBlank()) { "Class name must be non-empty" }
        require(fullName.isNotBlank()) { "Class full name must be non-empty" }
    }
}

/**
 * FunctionDoc - Documentation for a top-level function
 */
@Serializable
data class FunctionDoc(
    val name: String,
    val description: String,
    val signature: String,
    val visibility: Visibility,
    val parameters: List<ParameterDoc>,
    val returnType: TypeInfo,
    val returnDescription: String = "",
    val throwsExceptions: List<ExceptionDoc> = emptyList(),
    val annotations: List<AnnotationDoc> = emptyList(),
    val examples: List<CodeExample> = emptyList(),
    val since: String? = null,
    val deprecated: Boolean = false,
    val deprecationMessage: String? = null
) {
    init {
        require(name.isNotBlank()) { "Function name must be non-empty" }
        require(signature.isNotBlank()) { "Function signature must be non-empty" }
    }
}

/**
 * PropertyDoc - Documentation for a property
 */
@Serializable
data class PropertyDoc(
    val name: String,
    val description: String,
    val type: TypeInfo,
    val visibility: Visibility,
    val mutable: Boolean,
    val getter: AccessorDoc? = null,
    val setter: AccessorDoc? = null,
    val annotations: List<AnnotationDoc> = emptyList(),
    val since: String? = null,
    val deprecated: Boolean = false,
    val deprecationMessage: String? = null
) {
    init {
        require(name.isNotBlank()) { "Property name must be non-empty" }
    }
}

/**
 * SearchIndex - Search index for documentation content
 */
@Serializable
data class SearchIndex(
    val entries: List<SearchEntry>,
    val version: Int,
    val checksum: String,
    val statistics: SearchStatistics
) {
    init {
        require(version > 0) { "Search index version must be positive" }
        require(checksum.isNotBlank()) { "Search index checksum must be non-empty" }
    }

    /**
     * Searches the index for entries matching the query
     */
    fun search(query: String, limit: Int = 10): List<SearchResult> {
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val results = entries.mapNotNull { entry ->
            val titleScore = calculateRelevanceScore(normalizedQuery, entry.title.lowercase())
            val contentScore = calculateRelevanceScore(normalizedQuery, entry.content.lowercase()) * 0.5f
            val tagScore = entry.tags.sumOf { tag ->
                calculateRelevanceScore(normalizedQuery, tag.lowercase())
            } * 0.3f

            val totalScore = titleScore + contentScore + tagScore
            if (totalScore > 0.1f) {
                SearchResult(
                    entry = entry,
                    relevanceScore = totalScore,
                    matchType = determineMatchType(normalizedQuery, entry)
                )
            } else {
                null
            }
        }.sortedByDescending { it.relevanceScore }.take(limit)

        return results
    }

    private fun calculateRelevanceScore(query: String, text: String): Float {
        return when {
            text.contains(query) -> {
                if (text.startsWith(query)) 1.0f
                else if (text.contains(" $query")) 0.8f
                else 0.6f
            }
            // Simple fuzzy matching
            query.length > 3 && text.contains(query.substring(0, query.length - 1)) -> 0.3f
            else -> 0.0f
        }
    }

    private fun determineMatchType(query: String, entry: SearchEntry): MatchType {
        return when {
            entry.title.lowercase().contains(query) -> MatchType.TITLE
            entry.content.lowercase().contains(query) -> MatchType.CONTENT
            entry.tags.any { it.lowercase().contains(query) } -> MatchType.TAG
            else -> MatchType.FUZZY
        }
    }

    companion object {
        fun createEmpty(): SearchIndex = SearchIndex(
            entries = emptyList(),
            version = 1,
            checksum = "empty",
            statistics = SearchStatistics(
                totalEntries = 0,
                totalWords = 0,
                averageWordsPerEntry = 0.0f
            )
        )
    }
}

/**
 * SearchEntry - Single entry in the search index
 */
@Serializable
data class SearchEntry(
    val id: String,
    val title: String,
    val content: String,
    val url: String,
    val type: SearchEntryType,
    val tags: List<String> = emptyList(),
    val weight: Float = 1.0f
)

/**
 * SearchResult - Result from a search query
 */
data class SearchResult(
    val entry: SearchEntry,
    val relevanceScore: Float,
    val matchType: MatchType
)

/**
 * DocAsset - Asset file associated with documentation
 */
@Serializable
data class DocAsset(
    val name: String,
    val path: String,
    val type: AssetType,
    val size: Long,
    val checksum: String,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(name.isNotBlank()) { "Asset name must be non-empty" }
        require(path.isNotBlank()) { "Asset path must be non-empty" }
        require(size >= 0) { "Asset size must be non-negative" }
        require(checksum.isNotBlank()) { "Asset checksum must be non-empty" }
    }
}

// Supporting data classes and enums

@Serializable
data class CodeExample(
    val title: String,
    val code: String,
    val language: String = "kotlin",
    val description: String = "",
    val runnable: Boolean = false
)

@Serializable
data class ModuleDependency(
    val name: String,
    val version: String,
    val scope: DependencyScope
)

@Serializable
data class ConstructorDoc(
    val signature: String,
    val description: String,
    val parameters: List<ParameterDoc>,
    val visibility: Visibility
)

@Serializable
data class MethodDoc(
    val name: String,
    val description: String,
    val signature: String,
    val visibility: Visibility,
    val parameters: List<ParameterDoc>,
    val returnType: TypeInfo,
    val returnDescription: String = ""
)

@Serializable
data class ParameterDoc(
    val name: String,
    val type: TypeInfo,
    val description: String,
    val defaultValue: String? = null
)

@Serializable
data class TypeInfo(
    val name: String,
    val fullName: String,
    val nullable: Boolean = false,
    val typeParameters: List<TypeInfo> = emptyList()
)

@Serializable
data class InheritanceInfo(
    val superClass: String? = null,
    val interfaces: List<String> = emptyList()
)

@Serializable
data class AnnotationDoc(
    val name: String,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class AccessorDoc(
    val visibility: Visibility,
    val description: String = ""
)

@Serializable
data class ExceptionDoc(
    val type: String,
    val description: String
)

@Serializable
data class DocumentationMetadata(
    val title: String,
    val description: String,
    val author: String,
    val generator: String,
    val generatorVersion: String,
    val sourceCommit: String,
    val buildNumber: Int,
    val tags: List<String> = emptyList(),
    val license: String? = null,
    val website: String? = null
)

@Serializable
data class DocGenerationConfig(
    val includePrivate: Boolean = false,
    val includeInternal: Boolean = false,
    val generateSearchIndex: Boolean = true,
    val theme: DocTheme = DocTheme.DEFAULT,
    val customCSS: String? = null,
    val logoUrl: String? = null
) {
    companion object {
        fun default(): DocGenerationConfig = DocGenerationConfig()
    }
}

@Serializable
data class SearchStatistics(
    val totalEntries: Int,
    val totalWords: Int,
    val averageWordsPerEntry: Float
)

data class TableOfContents(
    val sections: List<TableOfContentsSection>
)

data class TableOfContentsSection(
    val title: String,
    val level: Int,
    val anchor: String,
    val subsections: List<TableOfContentsSection> = emptyList()
)

data class DocumentationValidationResult(
    val isValid: Boolean,
    val issues: List<DocumentationIssue>,
    val qualityScore: Float
)

data class DocumentationIssue(
    val severity: IssueSeverity,
    val message: String,
    val location: String
)

sealed class APIItem {
    data class Class(val classDoc: ClassDoc) : APIItem()
    data class Function(val functionDoc: FunctionDoc) : APIItem()
    data class Property(val propertyDoc: PropertyDoc) : APIItem()
}

// Enums

@Serializable
enum class DocFormat {
    HTML, MARKDOWN, JSON, PDF
}

@Serializable
enum class ClassType {
    CLASS, INTERFACE, OBJECT, ENUM, ANNOTATION, DATA_CLASS, SEALED_CLASS
}

@Serializable
enum class Visibility {
    PUBLIC, PRIVATE, INTERNAL, PROTECTED
}

@Serializable
enum class ClassModifier {
    ABSTRACT, FINAL, OPEN, SEALED, DATA, INLINE, VALUE
}

@Serializable
enum class SearchEntryType {
    CLASS, FUNCTION, PROPERTY, PACKAGE, MODULE, EXAMPLE, EXTERNAL
}

enum class MatchType {
    TITLE, CONTENT, TAG, FUZZY
}

@Serializable
enum class AssetType {
    IMAGE, STYLESHEET, SCRIPT, FONT, ICON, DIAGRAM, VIDEO
}

@Serializable
enum class DependencyScope {
    COMPILE, RUNTIME, TEST, PROVIDED
}

@Serializable
enum class DocTheme {
    DEFAULT, DARK, LIGHT, HIGH_CONTRAST, CUSTOM
}

enum class IssueSeverity {
    ERROR, WARNING, INFO
}