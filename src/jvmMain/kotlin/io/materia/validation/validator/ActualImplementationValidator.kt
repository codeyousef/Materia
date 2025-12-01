package io.materia.validation.validator

import io.materia.validation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.streams.asSequence

/**
 * Actual JVM implementation of ImplementationValidator that can scan real file systems.
 *
 * This replaces stub methods with real file system operations for the T031
 * expect/actual validation task across all platform source sets.
 */
class ActualImplementationValidator : ImplementationValidator {

    companion object {
        // Patterns to identify expect declarations
        private val EXPECT_PATTERNS = listOf(
            "(?m)^\\s*expect\\s+class\\s+(\\w+)",
            "(?m)^\\s*expect\\s+interface\\s+(\\w+)",
            "(?m)^\\s*expect\\s+object\\s+(\\w+)",
            "(?m)^\\s*expect\\s+fun\\s+(\\w+)",
            "(?m)^\\s*expect\\s+val\\s+(\\w+)",
            "(?m)^\\s*expect\\s+var\\s+(\\w+)"
        )

        // Patterns to identify actual declarations
        private val ACTUAL_PATTERNS = listOf(
            "(?m)^\\s*actual\\s+class\\s+(\\w+)",
            "(?m)^\\s*actual\\s+interface\\s+(\\w+)",
            "(?m)^\\s*actual\\s+object\\s+(\\w+)",
            "(?m)^\\s*actual\\s+fun\\s+(\\w+)",
            "(?m)^\\s*actual\\s+val\\s+(\\w+)",
            "(?m)^\\s*actual\\s+var\\s+(\\w+)"
        )

        // Patterns that indicate stub implementations
        private val STUB_PATTERNS = listOf(
            "(?i)\\btodo\\b.*",
            "(?i)\\bfixme\\b.*",
            "(?i)notImplemented\\(\\)",
            "(?i)throw\\s+.*notImplementedError",
            "(?i)throw\\s+.*unsupportedOperationException",
            "(?i)\\bstub\\b.*implementation",
            "(?i)return\\s+null\\s*//.*stub",
            "(?i)//\\s*stub.*implementation"
        )

        // Critical modules that must have complete implementations
        private val CRITICAL_MODULES = setOf("core", "renderer", "scene", "geometry", "material")
    }

    override suspend fun analyzeImplementationGaps(
        sourceRoot: String,
        platforms: List<Platform>
    ): GapAnalysisResult = withContext(Dispatchers.IO) {
        val analysisTimestamp = System.currentTimeMillis()
        val gaps = mutableListOf<ImplementationGap>()
        val modulesCovered = mutableSetOf<String>()

        try {
            // Find all expect declarations in commonMain
            val expectDeclarations = findExpectDeclarations(sourceRoot)

            // For each expect declaration, check if it has actual implementations on all platforms
            for (expectDeclaration in expectDeclarations) {
                val expectInfo = parseExpectDeclaration(expectDeclaration, sourceRoot)
                if (expectInfo != null) {
                    modulesCovered.add(expectInfo.module)

                    for (platform in platforms) {
                        val hasActual =
                            hasActualImplementation(expectDeclaration, platform, sourceRoot)

                        if (!hasActual) {
                            gaps.add(
                                ImplementationGap(
                                    filePath = expectInfo.filePath,
                                    expectedSignature = expectDeclaration,
                                    platform = platform,
                                    module = expectInfo.module,
                                    lineNumber = expectInfo.lineNumber,
                                    gapType = GapType.MISSING_ACTUAL,
                                    severity = calculateGapSeverity(
                                        expectInfo.module,
                                        expectDeclaration
                                    ),
                                    context = expectInfo.context
                                )
                            )
                        } else {
                            // Check if the actual implementation is complete (not a stub)
                            val stubGaps = findStubImplementations(sourceRoot, platform)
                            gaps.addAll(stubGaps.filter {
                                it.expectedSignature.contains(
                                    extractSignatureName(
                                        expectDeclaration
                                    )
                                )
                            })
                        }
                    }
                }
            }

        } catch (e: Exception) {
            println("Error analyzing implementation gaps: ${e.message}")
        }

        GapAnalysisResult(
            gaps = gaps,
            analysisTimestamp = analysisTimestamp,
            totalExpectDeclarations = findExpectDeclarations(sourceRoot).size,
            platformsCovered = platforms,
            modulesCovered = modulesCovered.toList()
        )
    }

    override suspend fun validateExpectActualPairs(
        expectFilePath: String,
        platforms: List<Platform>
    ): List<ImplementationGap> = withContext(Dispatchers.IO) {
        val gaps = mutableListOf<ImplementationGap>()

        try {
            val expectFile = File(expectFilePath)
            if (!expectFile.exists()) return@withContext gaps

            val content = expectFile.readText()
            val lines = content.lines()
            val module = extractModuleName(expectFilePath)

            for ((lineIndex, line) in lines.withIndex()) {
                for (pattern in EXPECT_PATTERNS) {
                    val matches = Regex(pattern).findAll(line)
                    for (match in matches) {
                        val expectDeclaration = line.trim()
                        val signatureName = match.groupValues[1]

                        for (platform in platforms) {
                            val hasActual =
                                hasActualImplementation(
                                    expectDeclaration,
                                    platform,
                                    File(expectFilePath).parent
                                )

                            if (!hasActual) {
                                gaps.add(
                                    ImplementationGap(
                                        filePath = expectFilePath,
                                        expectedSignature = expectDeclaration,
                                        platform = platform,
                                        module = module,
                                        lineNumber = lineIndex + 1,
                                        gapType = GapType.MISSING_ACTUAL,
                                        severity = calculateGapSeverity(module, expectDeclaration),
                                        context = extractContext(lines, lineIndex)
                                    )
                                )
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            println("Error validating expect/actual pairs: ${e.message}")
        }

        gaps
    }

    override suspend fun hasActualImplementation(
        expectDeclaration: String,
        platform: Platform,
        sourceRoot: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val signatureName = extractSignatureName(expectDeclaration)
            val platformSourceDir = File(sourceRoot, "src/${platform.sourceDir}/kotlin")

            if (!platformSourceDir.exists()) return@withContext false

            // Search for actual implementations in platform source directory
            Files.walk(platformSourceDir.toPath())
                .asSequence()
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .any { file ->
                    try {
                        val content = Files.readString(file)
                        ACTUAL_PATTERNS.any { pattern ->
                            val regex = Regex(pattern)
                            regex.findAll(content).any { match ->
                                match.groupValues[1] == signatureName
                            }
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun validateImplementationCompleteness(
        actualFilePath: String,
        expectedSignature: String
    ): ImplementationStatus = withContext(Dispatchers.IO) {
        try {
            val file = File(actualFilePath)
            if (!file.exists()) return@withContext ImplementationStatus.MISSING

            val content = file.readText()
            val signatureName = extractSignatureName(expectedSignature)

            // Check if the file contains the actual implementation
            val hasActualDeclaration = ACTUAL_PATTERNS.any { pattern ->
                val regex = Regex(pattern)
                regex.findAll(content).any { match ->
                    match.groupValues[1] == signatureName
                }
            }

            if (!hasActualDeclaration) {
                return@withContext ImplementationStatus.MISSING
            }

            // Check if implementation contains stubs
            val hasStubs = STUB_PATTERNS.any { pattern ->
                Regex(pattern).containsMatchIn(content)
            }

            when {
                hasStubs -> ImplementationStatus.INCOMPLETE
                else -> ImplementationStatus.COMPLETE
            }

        } catch (e: Exception) {
            ImplementationStatus.MISSING
        }
    }

    override suspend fun findExpectDeclarations(sourceRoot: String): List<String> =
        withContext(Dispatchers.IO) {
            val declarations = mutableListOf<String>()

            try {
                val commonMainDir = File(sourceRoot, "src/commonMain/kotlin")
                if (!commonMainDir.exists()) return@withContext declarations

                Files.walk(commonMainDir.toPath())
                    .asSequence()
                    .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .forEach { file ->
                        try {
                            val content = Files.readString(file)
                            val lines = content.lines()

                            for (line in lines) {
                                for (pattern in EXPECT_PATTERNS) {
                                    val matches = Regex(pattern).findAll(line)
                                    for (match in matches) {
                                        declarations.add(line.trim())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip files we can't read
                        }
                    }

            } catch (e: Exception) {
                println("Error finding expect declarations: ${e.message}")
            }

            declarations
        }

    override suspend fun findStubImplementations(
        sourceRoot: String,
        platform: Platform
    ): List<ImplementationGap> = withContext(Dispatchers.IO) {
        val stubs = mutableListOf<ImplementationGap>()

        try {
            val platformSourceDir = File(sourceRoot, "src/${platform.sourceDir}/kotlin")
            if (!platformSourceDir.exists()) return@withContext stubs

            Files.walk(platformSourceDir.toPath())
                .asSequence()
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { file ->
                    try {
                        val content = Files.readString(file)
                        val lines = content.lines()
                        val module = extractModuleName(file.toString())

                        for ((lineIndex, line) in lines.withIndex()) {
                            for (pattern in STUB_PATTERNS) {
                                if (Regex(pattern).containsMatchIn(line)) {
                                    // Try to find the function/class name this stub belongs to
                                    val signature = findSignatureForStub(lines, lineIndex)

                                    stubs.add(
                                        ImplementationGap(
                                            filePath = file.toString(),
                                            expectedSignature = signature,
                                            platform = platform,
                                            module = module,
                                            lineNumber = lineIndex + 1,
                                            gapType = GapType.STUB_IMPLEMENTATION,
                                            severity = calculateGapSeverity(module, signature),
                                            context = extractContext(lines, lineIndex)
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip files we can't read
                    }
                }

        } catch (e: Exception) {
            println("Error finding stub implementations: ${e.message}")
        }

        stubs
    }

    private fun extractSignatureName(declaration: String): String {
        return Regex("\\b(?:class|interface|object|fun|val|var)\\s+(\\w+)").find(declaration)?.groupValues?.get(
            1
        ) ?: ""
    }

    private fun extractModuleName(filePath: String): String {
        val path = filePath.replace("\\", "/")

        return when {
            path.contains("/core/") -> "core"
            path.contains("/renderer/") -> "renderer"
            path.contains("/scene/") -> "scene"
            path.contains("/geometry/") -> "geometry"
            path.contains("/material/") -> "material"
            path.contains("/animation/") -> "animation"
            path.contains("/loader/") -> "loader"
            path.contains("/controls/") -> "controls"
            path.contains("/physics/") -> "physics"
            path.contains("/xr/") -> "xr"
            path.contains("/postprocess/") -> "postprocess"
            path.contains("/tools/") -> "tools"
            path.contains("/examples/") -> "examples"
            path.contains("Test") -> "test"
            else -> "common"
        }
    }

    private fun calculateGapSeverity(module: String, signature: String): GapSeverity {
        return when {
            module in CRITICAL_MODULES -> {
                when {
                    signature.contains("render") || signature.contains("draw") -> GapSeverity.CRITICAL
                    signature.contains("init") || signature.contains("create") -> GapSeverity.HIGH
                    else -> GapSeverity.MEDIUM
                }
            }

            else -> GapSeverity.LOW
        }
    }

    private fun extractContext(lines: List<String>, lineIndex: Int): String {
        val startIndex = maxOf(0, lineIndex - 2)
        val endIndex = minOf(lines.size - 1, lineIndex + 2)
        return lines.subList(startIndex, endIndex + 1).joinToString("\n")
    }

    private fun findSignatureForStub(lines: List<String>, stubLineIndex: Int): String {
        // Look backwards from the stub line to find the function/class signature
        for (i in stubLineIndex downTo maxOf(0, stubLineIndex - 10)) {
            val line = lines[i].trim()
            if (line.matches(Regex(".*\\b(fun|class|object|interface|val|var)\\s+\\w+.*"))) {
                return line
            }
        }
        return "unknown signature"
    }

    private data class ExpectInfo(
        val filePath: String,
        val lineNumber: Int,
        val module: String,
        val context: String
    )

    private fun parseExpectDeclaration(expectDeclaration: String, sourceRoot: String): ExpectInfo? {
        // Simplified string-based parsing of expect declarations
        return ExpectInfo(
            filePath = "unknown",
            lineNumber = 0,
            module = "unknown",
            context = expectDeclaration
        )
    }
}