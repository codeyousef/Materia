/**
 * Materia Tools - API Documentation Reviewer
 * Reviews and validates API documentation for completeness, accuracy, and quality
 */

package io.materia.tools.validation

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Reviews API documentation to ensure it meets quality standards
 */
class ApiDocumentationReviewer {
    private val logger = Logger("ApiDocReviewer")
    private val projectRoot = File(".").canonicalPath
    private val reviewResults = mutableListOf<DocumentationReview>()

    suspend fun reviewApiDocumentation(config: DocumentationReviewConfig): DocumentationReviewReport = coroutineScope {
        logger.info("Starting API documentation review...")
        logger.info("Project root: $projectRoot")

        val reviewJobs = listOf(
            async { reviewKotlinDocumentation() },
            async { reviewWebAPIDocumentation() },
            async { reviewToolsDocumentation() },
            async { reviewExampleDocumentation() },
            async { reviewArchitectureDocumentation() },
            async { reviewMigrationGuides() },
            async { reviewTutorials() },
            async { reviewAPIReference() }
        )

        reviewJobs.awaitAll()

        val passedCount = reviewResults.count { it.status == ReviewStatus.PASS }
        val warningsCount = reviewResults.count { it.status == ReviewStatus.WARNING }
        val failuresCount = reviewResults.count { it.status == ReviewStatus.FAIL }

        logger.info("Documentation review completed: $passedCount passed, $warningsCount warnings, $failuresCount failures")

        DocumentationReviewReport(
            timestamp = Instant.now(),
            config = config,
            totalReviews = reviewResults.size,
            passedReviews = passedCount,
            warningReviews = warningsCount,
            failedReviews = failuresCount,
            reviews = reviewResults,
            summary = DocumentationSummary(
                overallQuality = calculateOverallQuality(),
                completenessScore = calculateCompletenessScore(),
                accuracyScore = calculateAccuracyScore(),
                recommendedActions = generateRecommendations()
            )
        )
    }

    private suspend fun reviewKotlinDocumentation(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing Kotlin API documentation...")

        val kotlinFiles = findKotlinFiles()
        logger.info("Found ${kotlinFiles.size} Kotlin files to review")

        var totalFunctions = 0
        var documentedFunctions = 0
        var totalClasses = 0
        var documentedClasses = 0
        var totalProperties = 0
        var documentedProperties = 0

        kotlinFiles.forEach { file ->
            val content = file.readText()
            val analysis = analyzeKotlinFile(content)

            totalFunctions += analysis.functions
            documentedFunctions += analysis.documentedFunctions
            totalClasses += analysis.classes
            documentedClasses += analysis.documentedClasses
            totalProperties += analysis.properties
            documentedProperties += analysis.documentedProperties
        }

        val functionDocumentationRate = if (totalFunctions > 0) (documentedFunctions * 100.0 / totalFunctions) else 100.0
        val classDocumentationRate = if (totalClasses > 0) (documentedClasses * 100.0 / totalClasses) else 100.0
        val propertyDocumentationRate = if (totalProperties > 0) (documentedProperties * 100.0 / totalProperties) else 100.0

        reviewResults.add(
            DocumentationReview(
                category = "Kotlin API",
                title = "Function Documentation Coverage",
                status = if (functionDocumentationRate >= 80.0) ReviewStatus.PASS
                         else if (functionDocumentationRate >= 60.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = functionDocumentationRate,
                details = "Functions: $documentedFunctions/$totalFunctions documented (${functionDocumentationRate.toInt()}%)",
                recommendations = if (functionDocumentationRate < 80.0)
                    listOf("Add KDoc comments to public functions", "Include parameter and return value descriptions")
                    else emptyList()
            )
        )

        reviewResults.add(
            DocumentationReview(
                category = "Kotlin API",
                title = "Class Documentation Coverage",
                status = if (classDocumentationRate >= 90.0) ReviewStatus.PASS
                         else if (classDocumentationRate >= 70.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = classDocumentationRate,
                details = "Classes: $documentedClasses/$totalClasses documented (${classDocumentationRate.toInt()}%)",
                recommendations = if (classDocumentationRate < 90.0)
                    listOf("Add class-level KDoc comments", "Document class purpose and usage examples")
                    else emptyList()
            )
        )

        reviewResults.add(
            DocumentationReview(
                category = "Kotlin API",
                title = "Property Documentation Coverage",
                status = if (propertyDocumentationRate >= 70.0) ReviewStatus.PASS
                         else if (propertyDocumentationRate >= 50.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = propertyDocumentationRate,
                details = "Properties: $documentedProperties/$totalProperties documented (${propertyDocumentationRate.toInt()}%)",
                recommendations = if (propertyDocumentationRate < 70.0)
                    listOf("Document public properties", "Add @see references for related properties")
                    else emptyList()
            )
        )
    }

    private suspend fun reviewWebAPIDocumentation(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing Web API documentation...")

        val webApiFiles = listOf(
            "tools/api-server/src/main/kotlin/routes",
            "tools/web-host/src",
            "tools/editor/web/src"
        ).map { File(it) }.filter { it.exists() }

        var endpointsFound = 0
        var documentedEndpoints = 0

        webApiFiles.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.extension in listOf("kt", "js", "ts") }.forEach { file ->
                    val content = file.readText()
                    val endpoints = extractAPIEndpoints(content)
                    endpointsFound += endpoints.size
                    documentedEndpoints += endpoints.count { it.documented }
                }
            }
        }

        val webApiDocumentationRate = if (endpointsFound > 0) (documentedEndpoints * 100.0 / endpointsFound) else 100.0

        reviewResults.add(
            DocumentationReview(
                category = "Web API",
                title = "API Endpoint Documentation",
                status = if (webApiDocumentationRate >= 85.0) ReviewStatus.PASS
                         else if (webApiDocumentationRate >= 65.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = webApiDocumentationRate,
                details = "API Endpoints: $documentedEndpoints/$endpointsFound documented (${webApiDocumentationRate.toInt()}%)",
                recommendations = if (webApiDocumentationRate < 85.0)
                    listOf("Add OpenAPI/Swagger documentation", "Document request/response schemas", "Add usage examples")
                    else emptyList()
            )
        )

        // Check for OpenAPI/Swagger documentation
        val openApiFiles = listOf(
            "tools/api-server/openapi.yaml",
            "tools/api-server/swagger.json",
            "docs/api/openapi.yaml"
        ).map { File(it) }

        val hasOpenApiDocs = openApiFiles.any { it.exists() }

        reviewResults.add(
            DocumentationReview(
                category = "Web API",
                title = "OpenAPI Documentation",
                status = if (hasOpenApiDocs) ReviewStatus.PASS else ReviewStatus.WARNING,
                score = if (hasOpenApiDocs) 100.0 else 0.0,
                details = if (hasOpenApiDocs) "OpenAPI documentation found" else "No OpenAPI documentation found",
                recommendations = if (!hasOpenApiDocs)
                    listOf("Generate OpenAPI specification", "Set up Swagger UI", "Document API schemas")
                    else emptyList()
            )
        )
    }

    private suspend fun reviewToolsDocumentation(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing tools documentation...")

        val toolDirectories = listOf("tools/editor", "tools/profiler", "tools/docs", "tools/api-server")
        var toolsWithDocs = 0
        var totalTools = 0

        toolDirectories.forEach { toolPath ->
            val toolDir = File(toolPath)
            if (toolDir.exists()) {
                totalTools++
                val hasReadme = File(toolDir, "README.md").exists()
                val hasDocumentation = File(toolDir, "docs").exists() || hasReadme

                if (hasDocumentation) {
                    toolsWithDocs++
                }

                reviewResults.add(
                    DocumentationReview(
                        category = "Tools",
                        title = "Tool Documentation: ${toolDir.name}",
                        status = if (hasDocumentation) ReviewStatus.PASS else ReviewStatus.WARNING,
                        score = if (hasDocumentation) 100.0 else 0.0,
                        details = if (hasDocumentation) "Documentation found" else "No documentation found",
                        recommendations = if (!hasDocumentation)
                            listOf("Create README.md", "Add usage examples", "Document configuration options")
                            else emptyList()
                    )
                )
            }
        }

        val toolsDocumentationRate = if (totalTools > 0) (toolsWithDocs * 100.0 / totalTools) else 100.0

        reviewResults.add(
            DocumentationReview(
                category = "Tools",
                title = "Overall Tools Documentation",
                status = if (toolsDocumentationRate >= 90.0) ReviewStatus.PASS
                         else if (toolsDocumentationRate >= 70.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = toolsDocumentationRate,
                details = "Tools with documentation: $toolsWithDocs/$totalTools (${toolsDocumentationRate.toInt()}%)",
                recommendations = if (toolsDocumentationRate < 90.0)
                    listOf("Ensure all tools have README files", "Add tool-specific documentation")
                    else emptyList()
            )
        )
    }

    private suspend fun reviewExampleDocumentation(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing example documentation...")

        val samplesDir = File("samples")
        var samplesWithDocs = 0
        var totalSamples = 0

        if (samplesDir.exists()) {
            samplesDir.listFiles()?.filter { it.isDirectory }?.forEach { sampleDir ->
                totalSamples++
                val hasReadme = File(sampleDir, "README.md").exists()
                val hasExplanation = hasReadme && File(sampleDir, "README.md").readText().length > 500

                if (hasExplanation) {
                    samplesWithDocs++
                }

                reviewResults.add(
                    DocumentationReview(
                        category = "Examples",
                        title = "Sample Documentation: ${sampleDir.name}",
                        status = if (hasExplanation) ReviewStatus.PASS
                                else if (hasReadme) ReviewStatus.WARNING
                                else ReviewStatus.FAIL,
                        score = if (hasExplanation) 100.0 else if (hasReadme) 50.0 else 0.0,
                        details = when {
                            hasExplanation -> "Comprehensive documentation found"
                            hasReadme -> "Basic README found, needs more detail"
                            else -> "No documentation found"
                        },
                        recommendations = when {
                            !hasReadme -> listOf("Create README.md", "Add code explanations", "Include screenshots")
                            !hasExplanation -> listOf("Expand documentation", "Add more detailed explanations", "Include usage instructions")
                            else -> emptyList()
                        }
                    )
                )
            }
        }

        val examplesDocumentationRate = if (totalSamples > 0) (samplesWithDocs * 100.0 / totalSamples) else 100.0

        reviewResults.add(
            DocumentationReview(
                category = "Examples",
                title = "Overall Examples Documentation",
                status = if (examplesDocumentationRate >= 80.0) ReviewStatus.PASS
                         else if (examplesDocumentationRate >= 60.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = examplesDocumentationRate,
                details = "Samples with good documentation: $samplesWithDocs/$totalSamples (${examplesDocumentationRate.toInt()}%)",
                recommendations = if (examplesDocumentationRate < 80.0)
                    listOf("Improve sample documentation", "Add step-by-step guides", "Include expected outputs")
                    else emptyList()
            )
        )
    }

    private suspend fun reviewArchitectureDocumentation(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing architecture documentation...")

        val architectureDocs = listOf(
            "docs/architecture.md",
            "docs/ARCHITECTURE.md",
            "ARCHITECTURE.md",
            "docs/design/architecture.md"
        ).map { File(it) }

        val hasArchitectureDocs = architectureDocs.any { it.exists() }
        val architectureFile = architectureDocs.firstOrNull { it.exists() }

        var architectureQuality = 0.0
        val recommendations = mutableListOf<String>()

        if (hasArchitectureDocs && architectureFile != null) {
            val content = architectureFile.readText()
            val contentLength = content.length

            when {
                contentLength > 5000 -> architectureQuality = 100.0
                contentLength > 2000 -> {
                    architectureQuality = 75.0
                    recommendations.add("Expand architecture documentation with more details")
                }
                contentLength > 500 -> {
                    architectureQuality = 50.0
                    recommendations.add("Add more comprehensive architecture documentation")
                }
                else -> {
                    architectureQuality = 25.0
                    recommendations.add("Create detailed architecture documentation")
                }
            }

            // Check for specific sections
            val hasDiagrams = content.contains("```mermaid") || content.contains("![")
            val hasModules = content.toLowerCase().contains("module")
            val hasPatterns = content.toLowerCase().contains("pattern")

            if (!hasDiagrams) recommendations.add("Add architecture diagrams")
            if (!hasModules) recommendations.add("Document module structure")
            if (!hasPatterns) recommendations.add("Document design patterns used")

        } else {
            recommendations.addAll(listOf(
                "Create architecture documentation",
                "Document system overview",
                "Add module dependencies diagram",
                "Explain design decisions"
            ))
        }

        reviewResults.add(
            DocumentationReview(
                category = "Architecture",
                title = "Architecture Documentation",
                status = if (architectureQuality >= 75.0) ReviewStatus.PASS
                         else if (architectureQuality >= 40.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = architectureQuality,
                details = if (hasArchitectureDocs) "Architecture documentation found" else "No architecture documentation found",
                recommendations = recommendations
            )
        )
    }

    private suspend fun reviewMigrationGuides(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing migration guides...")

        val migrationDocs = listOf(
            "docs/migration.md",
            "docs/MIGRATION.md",
            "MIGRATION.md",
            "docs/guides/migration.md"
        ).map { File(it) }

        val hasMigrationDocs = migrationDocs.any { it.exists() }
        val migrationFile = migrationDocs.firstOrNull { it.exists() }

        var migrationQuality = 0.0
        val recommendations = mutableListOf<String>()

        if (hasMigrationDocs && migrationFile != null) {
            val content = migrationFile.readText()

            // Check for Three.js migration content
            val hasThreeJsContent = content.toLowerCase().contains("three.js") || content.toLowerCase().contains("threejs")
            val hasCodeExamples = content.contains("```")
            val hasVersionInfo = content.contains("version") || content.contains("v1.") || content.contains("v2.")

            migrationQuality = when {
                hasThreeJsContent && hasCodeExamples && hasVersionInfo -> 100.0
                hasThreeJsContent && hasCodeExamples -> 75.0
                hasThreeJsContent -> 50.0
                else -> 25.0
            }

            if (!hasThreeJsContent) recommendations.add("Add Three.js migration guide")
            if (!hasCodeExamples) recommendations.add("Include code migration examples")
            if (!hasVersionInfo) recommendations.add("Document version-specific migration steps")

        } else {
            recommendations.addAll(listOf(
                "Create migration guide from Three.js",
                "Add version migration documentation",
                "Include code examples and comparisons",
                "Document breaking changes"
            ))
        }

        reviewResults.add(
            DocumentationReview(
                category = "Migration",
                title = "Migration Guides",
                status = if (migrationQuality >= 75.0) ReviewStatus.PASS
                         else if (migrationQuality >= 40.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = migrationQuality,
                details = if (hasMigrationDocs) "Migration documentation found" else "No migration documentation found",
                recommendations = recommendations
            )
        )
    }

    private suspend fun reviewTutorials(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing tutorials...")

        val tutorialDirs = listOf(
            "docs/tutorials",
            "tutorials",
            "docs/guides"
        ).map { File(it) }

        var totalTutorials = 0
        var goodTutorials = 0

        tutorialDirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.extension == "md" }.forEach { tutorialFile ->
                    totalTutorials++
                    val content = tutorialFile.readText()
                    val hasCodeExamples = content.contains("```")
                    val hasSteps = content.contains("step") || content.contains("Step") || content.contains("##")
                    val isDetailed = content.length > 1000

                    if (hasCodeExamples && hasSteps && isDetailed) {
                        goodTutorials++
                    }
                }
            }
        }

        val tutorialQuality = if (totalTutorials > 0) (goodTutorials * 100.0 / totalTutorials) else 0.0

        reviewResults.add(
            DocumentationReview(
                category = "Tutorials",
                title = "Tutorial Quality",
                status = if (tutorialQuality >= 70.0 && totalTutorials >= 3) ReviewStatus.PASS
                         else if (tutorialQuality >= 50.0 || totalTutorials >= 1) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = tutorialQuality,
                details = "Good tutorials: $goodTutorials/$totalTutorials (${tutorialQuality.toInt()}%)",
                recommendations = when {
                    totalTutorials == 0 -> listOf("Create beginner tutorials", "Add step-by-step guides", "Include complete examples")
                    tutorialQuality < 70.0 -> listOf("Improve existing tutorials", "Add more code examples", "Include expected outputs")
                    totalTutorials < 3 -> listOf("Create more tutorial content", "Cover different use cases")
                    else -> emptyList()
                }
            )
        )
    }

    private suspend fun reviewAPIReference(): Unit = withContext(Dispatchers.IO) {
        logger.info("Reviewing API reference documentation...")

        val apiRefDirs = listOf(
            "docs/api",
            "build/dokka/html",
            "docs/reference"
        ).map { File(it) }

        val hasApiReference = apiRefDirs.any { it.exists() && it.listFiles()?.isNotEmpty() == true }
        val apiRefDir = apiRefDirs.firstOrNull { it.exists() && it.listFiles()?.isNotEmpty() == true }

        var apiRefQuality = 0.0
        val recommendations = mutableListOf<String>()

        if (hasApiReference && apiRefDir != null) {
            val files = apiRefDir.walkTopDown().filter { it.extension == "html" }.count()
            apiRefQuality = when {
                files > 100 -> 100.0
                files > 50 -> 75.0
                files > 10 -> 50.0
                else -> 25.0
            }

            if (files < 50) {
                recommendations.add("Generate more comprehensive API documentation")
            }
        } else {
            recommendations.addAll(listOf(
                "Generate API reference documentation",
                "Set up Dokka for Kotlin documentation",
                "Ensure all public APIs are documented",
                "Create searchable API reference"
            ))
        }

        reviewResults.add(
            DocumentationReview(
                category = "API Reference",
                title = "API Reference Documentation",
                status = if (apiRefQuality >= 75.0) ReviewStatus.PASS
                         else if (apiRefQuality >= 40.0) ReviewStatus.WARNING
                         else ReviewStatus.FAIL,
                score = apiRefQuality,
                details = if (hasApiReference) "API reference documentation found" else "No API reference documentation found",
                recommendations = recommendations
            )
        )
    }

    // Helper methods
    private fun findKotlinFiles(): List<File> {
        val srcDirs = listOf("src", "tools").map { File(it) }.filter { it.exists() }
        return srcDirs.flatMap { dir ->
            dir.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/test/") }.toList()
        }
    }

    private fun analyzeKotlinFile(content: String): KotlinFileAnalysis {
        var functions = 0
        var documentedFunctions = 0
        var classes = 0
        var documentedClasses = 0
        var properties = 0
        var documentedProperties = 0

        val lines = content.lines()
        var inDocComment = false

        for (i in lines.indices) {
            val line = lines[i].trim()

            // Check for KDoc comment start
            if (line.startsWith("/**")) {
                inDocComment = true
                continue
            }

            // Check for KDoc comment end
            if (line.endsWith("*/")) {
                inDocComment = false
                continue
            }

            // Skip if in doc comment
            if (inDocComment) continue

            // Check for functions
            if (line.contains("fun ") && (line.contains("public") || !line.contains("private"))) {
                functions++
                if (i > 0 && hasDocumentationBefore(lines, i)) {
                    documentedFunctions++
                }
            }

            // Check for classes
            if ((line.contains("class ") || line.contains("interface ") || line.contains("object ")) &&
                (line.contains("public") || !line.contains("private"))) {
                classes++
                if (i > 0 && hasDocumentationBefore(lines, i)) {
                    documentedClasses++
                }
            }

            // Check for properties
            if ((line.contains("val ") || line.contains("var ")) &&
                (line.contains("public") || !line.contains("private"))) {
                properties++
                if (i > 0 && hasDocumentationBefore(lines, i)) {
                    documentedProperties++
                }
            }
        }

        return KotlinFileAnalysis(functions, documentedFunctions, classes, documentedClasses, properties, documentedProperties)
    }

    private fun hasDocumentationBefore(lines: List<String>, currentIndex: Int): Boolean {
        // Look for KDoc comment in the few lines before the current line
        for (i in (currentIndex - 5).coerceAtLeast(0) until currentIndex) {
            val line = lines[i].trim()
            if (line.startsWith("/**") || line.startsWith("*")) {
                return true
            }
        }
        return false
    }

    private fun extractAPIEndpoints(content: String): List<APIEndpoint> {
        val endpoints = mutableListOf<APIEndpoint>()
        val httpMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")

        httpMethods.forEach { method ->
            val regex = """@$method\s*\(\s*["']([^"']+)["']\s*\)""".toRegex()
            regex.findAll(content).forEach { match ->
                val path = match.groupValues[1]
                val hasDocumentation = content.contains("/**") && content.contains("@param") || content.contains("@return")
                endpoints.add(APIEndpoint(method, path, hasDocumentation))
            }
        }

        return endpoints
    }

    private fun calculateOverallQuality(): String {
        val avgScore = reviewResults.map { it.score }.average()
        return when {
            avgScore >= 85.0 -> "EXCELLENT"
            avgScore >= 70.0 -> "GOOD"
            avgScore >= 55.0 -> "FAIR"
            else -> "POOR"
        }
    }

    private fun calculateCompletenessScore(): Double {
        return reviewResults.map { it.score }.average()
    }

    private fun calculateAccuracyScore(): Double {
        // For this example, assume accuracy score is based on whether documentation exists and is substantial
        val substantialDocs = reviewResults.count { it.score >= 70.0 }
        val totalDocs = reviewResults.size
        return if (totalDocs > 0) (substantialDocs * 100.0 / totalDocs) else 0.0
    }

    private fun generateRecommendations(): List<String> {
        val allRecommendations = reviewResults.flatMap { it.recommendations }
        val priorityRecommendations = mutableListOf<String>()

        // High priority recommendations
        if (reviewResults.any { it.category == "Kotlin API" && it.status == ReviewStatus.FAIL }) {
            priorityRecommendations.add("Improve Kotlin API documentation coverage as highest priority")
        }

        if (reviewResults.any { it.category == "Architecture" && it.status == ReviewStatus.FAIL }) {
            priorityRecommendations.add("Create comprehensive architecture documentation")
        }

        if (reviewResults.any { it.category == "Migration" && it.status == ReviewStatus.FAIL }) {
            priorityRecommendations.add("Develop Three.js migration guide")
        }

        // Add most common recommendations
        val commonRecommendations = allRecommendations.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }.take(5).map { it.first }

        return (priorityRecommendations + commonRecommendations).distinct()
    }
}

// Data classes
@Serializable
data class DocumentationReviewConfig(
    val includeInternalDocs: Boolean = false,
    val minDocumentationThreshold: Double = 70.0,
    val requireExamples: Boolean = true,
    val checkLinks: Boolean = false
)

@Serializable
data class DocumentationReview(
    val category: String,
    val title: String,
    val status: ReviewStatus,
    val score: Double,
    val details: String,
    val recommendations: List<String> = emptyList()
)

@Serializable
data class DocumentationSummary(
    val overallQuality: String,
    val completenessScore: Double,
    val accuracyScore: Double,
    val recommendedActions: List<String>
)

@Serializable
data class DocumentationReviewReport(
    val timestamp: Instant,
    val config: DocumentationReviewConfig,
    val totalReviews: Int,
    val passedReviews: Int,
    val warningReviews: Int,
    val failedReviews: Int,
    val reviews: List<DocumentationReview>,
    val summary: DocumentationSummary
)

@Serializable
enum class ReviewStatus {
    PASS, WARNING, FAIL
}

data class KotlinFileAnalysis(
    val functions: Int,
    val documentedFunctions: Int,
    val classes: Int,
    val documentedClasses: Int,
    val properties: Int,
    val documentedProperties: Int
)

data class APIEndpoint(
    val method: String,
    val path: String,
    val documented: Boolean
)

class Logger(private val name: String) {
    fun info(message: String) = println("[$name] INFO: $message")
    fun warn(message: String) = println("[$name] WARN: $message")
    fun error(message: String, throwable: Throwable? = null) {
        println("[$name] ERROR: $message")
        throwable?.printStackTrace()
    }
}

// Main execution
suspend fun main(args: Array<String>) {
    val configFile = args.getOrNull(0) ?: "api-doc-review-config.json"
    val outputFile = args.getOrNull(1) ?: "api-documentation-review-report.json"

    try {
        val config = if (File(configFile).exists()) {
            Json.decodeFromString<DocumentationReviewConfig>(File(configFile).readText())
        } else {
            DocumentationReviewConfig()
        }

        val reviewer = ApiDocumentationReviewer()
        val report = reviewer.reviewApiDocumentation(config)

        // Write report
        val reportJson = Json.encodeToString(DocumentationReviewReport.serializer(), report)
        File(outputFile).writeText(reportJson)

        // Console output
        println("\n" + "=".repeat(70))
        println("API DOCUMENTATION REVIEW REPORT")
        println("=".repeat(70))
        println("Overall Quality: ${report.summary.overallQuality}")
        println("Completeness Score: ${report.summary.completenessScore.toInt()}%")
        println("Accuracy Score: ${report.summary.accuracyScore.toInt()}%")
        println("Total Reviews: ${report.totalReviews}")
        println("Passed: ${report.passedReviews}")
        println("Warnings: ${report.warningReviews}")
        println("Failed: ${report.failedReviews}")
        println("=".repeat(70))

        // Group reviews by category
        val reviewsByCategory = report.reviews.groupBy { it.category }
        reviewsByCategory.forEach { (category, reviews) ->
            println("\n$category:")
            reviews.forEach { review ->
                val status = when (review.status) {
                    ReviewStatus.PASS -> "✅"
                    ReviewStatus.WARNING -> "⚠️"
                    ReviewStatus.FAIL -> "❌"
                }
                println("  $status ${review.title} (${review.score.toInt()}%)")
                println("      ${review.details}")
                if (review.recommendations.isNotEmpty()) {
                    review.recommendations.forEach { rec ->
                        println("      → $rec")
                    }
                }
            }
        }

        if (report.summary.recommendedActions.isNotEmpty()) {
            println("\nTop Recommended Actions:")
            report.summary.recommendedActions.forEachIndexed { index, action ->
                println("${index + 1}. $action")
            }
        }

        val criticalIssues = report.reviews.count { it.status == ReviewStatus.FAIL }
        if (criticalIssues > 0) {
            println("\n❌ CRITICAL DOCUMENTATION ISSUES FOUND")
            println("$criticalIssues critical documentation issues need attention.")
        } else if (report.warningReviews > 0) {
            println("\n⚠️  Documentation has room for improvement.")
            println("${report.warningReviews} areas could be enhanced.")
        } else {
            println("\n✅ DOCUMENTATION QUALITY IS EXCELLENT")
            println("All documentation areas meet quality standards.")
        }

        println("\nDocumentation review report saved to: $outputFile")

        // Exit with warning if there are critical issues
        if (criticalIssues > 0) {
            exitProcess(1)
        }

    } catch (e: Exception) {
        println("API documentation review failed: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}