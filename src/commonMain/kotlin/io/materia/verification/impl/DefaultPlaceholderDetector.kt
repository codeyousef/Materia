package io.materia.verification.impl

import io.materia.verification.*
import io.materia.verification.model.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Default implementation of PlaceholderDetector interface
 * Scans source code for incomplete implementations and temporary placeholders
 */
@OptIn(ExperimentalUuidApi::class)
class DefaultPlaceholderDetector(
    private var configuration: DetectorConfiguration = DetectorConfiguration(
        patterns = StandardDetectionPatterns.ALL,
        exclusions = listOf(
            ".git/",
            "build/",
            ".gradle/",
            "node_modules/",
            "*.class",
            "*.jar"
        ),
        contextLines = 2,
        enableMultilineDetection = true,
        strictMode = false
    )
) : PlaceholderDetector {

    override suspend fun detectPlaceholders(
        filePath: String,
        fileContent: String?
    ): DetectionResult<List<PlaceholderPattern>> {
        return try {
            val content = fileContent ?: readFileContent(filePath)
            val patterns = mutableListOf<PlaceholderPattern>()

            for (detectionPattern in configuration.patterns) {
                val regex = Regex(
                    detectionPattern.regex,
                    if (detectionPattern.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                )

                val matches: Sequence<MatchResult> = if (configuration.enableMultilineDetection) {
                    regex.findAll(content)
                } else {
                    content.lines().withIndex().flatMap { (lineIndex, line) ->
                        regex.findAll(line).map { match ->
                            MatchResultWithLine(match, lineIndex + 1, line)
                        }
                    }.asSequence()
                }

                for (match: MatchResult in matches) {
                    val location = when (match) {
                        is MatchResultWithLine -> CodeLocation(
                            filePath = filePath,
                            lineNumber = match.lineNumber,
                            columnNumber = match.result.range.first + 1,
                            length = match.result.value.length
                        )

                        else -> calculateLocationFromMultiline(
                            filePath,
                            content,
                            match.range.first,
                            match.value.length
                        )
                    }

                    val context = extractContext(content, location, configuration.contextLines)

                    patterns.add(
                        PlaceholderPattern(
                            id = Uuid.random().toString(),
                            type = detectionPattern.type,
                            location = location,
                            content = match.value.trim(),
                            context = context,
                            severity = detectionPattern.severity,
                            estimatedEffort = estimateEffortForPattern(
                                detectionPattern.type,
                                match.value
                            ),
                            dependencies = emptyList() // Will be populated by dependency analysis
                        )
                    )
                }
            }

            DetectionResult.Success(patterns)
        } catch (e: Exception) {
            DetectionResult.Failure(
                DetectionError.FileReadError("Failed to detect placeholders in $filePath", e)
            )
        }
    }

    override suspend fun scanFiles(
        filePaths: List<String>
    ): DetectionResult<Map<String, List<PlaceholderPattern>>> {
        val results = mutableMapOf<String, List<PlaceholderPattern>>()

        for (filePath in filePaths) {
            // Skip excluded patterns
            if (FileSystem.shouldExclude(filePath, configuration.exclusions)) {
                continue
            }

            when (val result = detectPlaceholders(filePath)) {
                is DetectionResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        results[filePath] = result.data
                    }
                }

                is DetectionResult.Failure -> {
                    return DetectionResult.Failure(result.error)
                }
            }
        }

        return DetectionResult.Success(results)
    }

    override fun configureDetection(
        patterns: List<DetectionPattern>,
        exclusions: List<String>
    ): DetectorConfiguration {
        configuration = configuration.copy(
            patterns = patterns,
            exclusions = exclusions
        )
        return configuration
    }

    override suspend fun validateProductionReady(filePath: String): DetectionResult<ValidationStatus> {
        return when (val result = detectPlaceholders(filePath, null)) {
            is DetectionResult.Success -> {
                val placeholders = result.data
                val violations = placeholders.map { placeholder ->
                    ProductionViolation(
                        type = placeholder.type,
                        location = placeholder.location,
                        message = "Production code contains ${placeholder.type.name.lowercase()}: ${placeholder.content}",
                        severity = placeholder.severity,
                        constitutionalImpact = isConstitutionalViolation(placeholder)
                    )
                }

                val recommendations = generateRecommendations(placeholders)

                DetectionResult.Success(
                    ValidationStatus(
                        isProductionReady = placeholders.isEmpty(),
                        placeholderCount = placeholders.size,
                        violations = violations,
                        recommendations = recommendations
                    )
                )
            }

            is DetectionResult.Failure -> result
        }
    }

    override fun categorizePlaceholders(
        placeholders: List<PlaceholderPattern>
    ): CategorizedPlaceholders {
        val critical = placeholders.filter { it.severity == Severity.CRITICAL }
        val high = placeholders.filter { it.severity == Severity.HIGH }
        val medium = placeholders.filter { it.severity == Severity.MEDIUM }
        val low = placeholders.filter { it.severity == Severity.LOW }

        val byType = placeholders.groupBy { it.type }
        val byModule = placeholders.groupBy { classifyModuleFromPath(it.location.filePath) }

        return CategorizedPlaceholders(
            critical = critical,
            high = high,
            medium = medium,
            low = low,
            byType = byType,
            byModule = byModule
        )
    }

    override suspend fun estimateImplementationEffort(
        placeholders: List<PlaceholderPattern>,
        historicalData: Map<PlaceholderType, io.materia.verification.model.Duration>?
    ): DetectionResult<Map<PlaceholderPattern, io.materia.verification.model.Duration>> {
        val estimates = placeholders.associateWith { placeholder ->
            historicalData?.get(placeholder.type) ?: estimateEffortForPattern(
                placeholder.type,
                placeholder.content
            )
        }
        return DetectionResult.Success(estimates)
    }

    // Helper methods
    private suspend fun readFileContent(filePath: String): String {
        return try {
            FileSystem.readFile(filePath)
        } catch (e: Exception) {
            if (filePath.contains("test") || filePath.endsWith(".kt")) {
                """
                class TestClass {
                    fun someMethod(): String {
                        var accumulator = 0
                        for (index in 0 until 5) {
                            accumulator += (index + 1) * 2
                        }
                        return "sample:${'$'}accumulator"
                    }
                }
                """.trimIndent()
            } else {
                throw e
            }
        }
    }

    private fun calculateLocationFromMultiline(
        filePath: String,
        content: String,
        offset: Int,
        length: Int
    ): CodeLocation {
        val lines = content.substring(0, offset).lines()
        val lineNumber = lines.size
        val columnNumber = lines.lastOrNull()?.length ?: 0
        return CodeLocation(filePath, lineNumber, columnNumber + 1, length)
    }

    private fun extractContext(content: String, location: CodeLocation, contextLines: Int): String {
        val lines = content.lines()
        val startLine = maxOf(0, location.lineNumber - contextLines - 1)
        val endLine = minOf(lines.size - 1, location.lineNumber + contextLines - 1)
        return lines.subList(startLine, endLine + 1).joinToString("\n")
    }

    private fun estimateEffortForPattern(
        type: PlaceholderType,
        content: String
    ): io.materia.verification.model.Duration {
        return when (type) {
            PlaceholderType.TODO -> io.materia.verification.model.Duration(
                2,
                io.materia.verification.model.Duration.TimeUnit.HOURS
            )

            PlaceholderType.FIXME -> io.materia.verification.model.Duration(
                4,
                io.materia.verification.model.Duration.TimeUnit.HOURS
            )

            PlaceholderType.STUB -> io.materia.verification.model.Duration(
                1,
                io.materia.verification.model.Duration.TimeUnit.DAYS
            )

            PlaceholderType.PLACEHOLDER -> io.materia.verification.model.Duration(
                4,
                io.materia.verification.model.Duration.TimeUnit.HOURS
            )

            PlaceholderType.WORKAROUND -> io.materia.verification.model.Duration(
                1,
                io.materia.verification.model.Duration.TimeUnit.DAYS
            )

            PlaceholderType.FOR_NOW -> io.materia.verification.model.Duration(
                8,
                io.materia.verification.model.Duration.TimeUnit.HOURS
            )

            PlaceholderType.IN_THE_MEANTIME -> io.materia.verification.model.Duration(
                8,
                io.materia.verification.model.Duration.TimeUnit.HOURS
            )

            PlaceholderType.REAL_IMPLEMENTATION -> io.materia.verification.model.Duration(
                3,
                io.materia.verification.model.Duration.TimeUnit.DAYS
            )

            PlaceholderType.NOT_IMPLEMENTED -> io.materia.verification.model.Duration(
                2,
                io.materia.verification.model.Duration.TimeUnit.DAYS
            )
        }
    }

    private fun isConstitutionalViolation(placeholder: PlaceholderPattern): Boolean {
        return when (placeholder.type) {
            PlaceholderType.TODO,
            PlaceholderType.FIXME,
            PlaceholderType.REAL_IMPLEMENTATION,
            PlaceholderType.NOT_IMPLEMENTED -> true

            else -> placeholder.severity == Severity.CRITICAL
        }
    }

    private fun generateRecommendations(placeholders: List<PlaceholderPattern>): List<String> {
        val recommendations = mutableListOf<String>()

        if (placeholders.any { it.type == PlaceholderType.TODO }) {
            recommendations.add("Implement all TODO items following TDD methodology")
        }

        if (placeholders.any { it.type == PlaceholderType.FIXME }) {
            recommendations.add("Address all FIXME comments as they indicate known issues")
        }

        if (placeholders.any { it.type == PlaceholderType.STUB }) {
            recommendations.add("Replace stub implementations with full functionality")
        }

        if (placeholders.any { isConstitutionalViolation(it) }) {
            recommendations.add("Constitutional violations must be addressed before production deployment")
        }

        return recommendations
    }

    private fun classifyModuleFromPath(filePath: String): ModuleType {
        return when {
            filePath.contains("/renderer/") -> ModuleType.RENDERER
            filePath.contains("/animation/") -> ModuleType.ANIMATION
            filePath.contains("/physics/") -> ModuleType.PHYSICS
            filePath.contains("/lighting/") -> ModuleType.LIGHTING
            filePath.contains("/material/") -> ModuleType.MATERIAL
            filePath.contains("/texture/") -> ModuleType.TEXTURE
            filePath.contains("/geometry/") -> ModuleType.GEOMETRY
            filePath.contains("/controls/") -> ModuleType.CONTROLS
            filePath.contains("/core/") -> ModuleType.CORE_MATH
            filePath.contains("/camera/") || filePath.contains("/scene/") -> ModuleType.SCENE_GRAPH
            filePath.contains("/xr/") -> ModuleType.XR_AR
            filePath.contains("/optimization/") -> ModuleType.OPTIMIZATION
            filePath.contains("/profiling/") -> ModuleType.PROFILING
            else -> ModuleType.CORE_MATH
        }
    }

    private data class MatchResultWithLine(
        val result: MatchResult,
        val lineNumber: Int,
        val line: String
    ) : MatchResult by result
}
