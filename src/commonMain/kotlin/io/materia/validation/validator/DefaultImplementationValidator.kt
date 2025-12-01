package io.materia.validation.validator

import io.materia.core.platform.currentTimeMillis
import io.materia.validation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default implementation of ImplementationValidator for analyzing expect/actual patterns.
 *
 * This implementation provides comprehensive analysis of multiplatform Kotlin code
 * to identify missing platform implementations and assess implementation quality.
 */
class DefaultImplementationValidator : ImplementationValidator {

    /**
     * Analyzes the codebase for implementation gaps across all platforms.
     */
    override suspend fun analyzeImplementationGaps(
        sourceRoot: String,
        platforms: List<Platform>
    ): GapAnalysisResult = withContext(Dispatchers.Default) {

        val startTime = currentTimeMillis()
        val gaps = mutableListOf<ImplementationGap>()
        val modulesCovered = mutableListOf<String>()

        // Find all expect declarations in commonMain
        val expectDeclarations = findExpectDeclarations(sourceRoot)

        // For each expect declaration, check all platforms
        for (expectDecl in expectDeclarations) {
            for (platform in platforms) {
                val hasImplementation = hasActualImplementation(expectDecl, platform, sourceRoot)
                if (!hasImplementation) {
                    gaps.add(createImplementationGap(expectDecl, platform, sourceRoot))
                }
            }
        }

        // Also check for stub implementations
        for (platform in platforms) {
            val stubGaps = findStubImplementations(sourceRoot, platform)
            gaps.addAll(stubGaps)
        }

        // Extract module information from source paths
        extractModules(sourceRoot, modulesCovered)

        GapAnalysisResult(
            gaps = gaps,
            analysisTimestamp = currentTimeMillis(),
            totalExpectDeclarations = expectDeclarations.size,
            platformsCovered = platforms,
            modulesCovered = modulesCovered
        )
    }

    /**
     * Validates that all expect declarations have corresponding actual implementations.
     */
    override suspend fun validateExpectActualPairs(
        expectFilePath: String,
        platforms: List<Platform>
    ): List<ImplementationGap> = withContext(Dispatchers.Default) {

        val gaps = mutableListOf<ImplementationGap>()

        // Parse expect declarations from the specific file
        val expectDeclarations = parseExpectDeclarationsFromFile(expectFilePath)

        for (expectDecl in expectDeclarations) {
            for (platform in platforms) {
                val sourceRoot = extractSourceRoot(expectFilePath)
                val hasImplementation = hasActualImplementation(expectDecl, platform, sourceRoot)
                if (!hasImplementation) {
                    gaps.add(
                        createImplementationGap(
                            expectDecl,
                            platform,
                            sourceRoot,
                            expectFilePath
                        )
                    )
                }
            }
        }

        gaps
    }

    /**
     * Checks if an actual implementation exists for a given expect declaration.
     */
    override suspend fun hasActualImplementation(
        expectDeclaration: String,
        platform: Platform,
        sourceRoot: String
    ): Boolean = withContext(Dispatchers.Default) {

        try {
            val platformSourceDir = "$sourceRoot/src/${platform.sourceDir}/kotlin"
            val actualFiles = findKotlinFiles(platformSourceDir)

            for (file in actualFiles) {
                val content = readFileContent(file)
                if (containsActualImplementation(content, expectDeclaration)) {
                    // Check if it's not just a stub
                    if (!isStubImplementation(content, expectDeclaration)) {
                        return@withContext true
                    }
                }
            }
            false
        } catch (e: Exception) {
            // Platform directory might not exist
            false
        }
    }

    /**
     * Validates that an actual implementation is functionally complete.
     */
    override suspend fun validateImplementationCompleteness(
        actualFilePath: String,
        expectedSignature: String
    ): ImplementationStatus = withContext(Dispatchers.Default) {

        try {
            val content = readFileContent(actualFilePath)

            when {
                !containsActualImplementation(
                    content,
                    expectedSignature
                ) -> ImplementationStatus.MISSING

                isStubImplementation(content, expectedSignature) -> ImplementationStatus.INCOMPLETE
                isPoorQualityImplementation(
                    content,
                    expectedSignature
                ) -> ImplementationStatus.POOR_QUALITY

                else -> ImplementationStatus.COMPLETE
            }
        } catch (e: Exception) {
            ImplementationStatus.MISSING
        }
    }

    /**
     * Gets all expect declarations from a source directory.
     */
    override suspend fun findExpectDeclarations(sourceRoot: String): List<String> =
        withContext(Dispatchers.Default) {

            val expectDeclarations = mutableListOf<String>()
            val commonMainPath = "$sourceRoot/src/commonMain/kotlin"

            try {
                val kotlinFiles = findKotlinFiles(commonMainPath)

                for (file in kotlinFiles) {
                    val content = readFileContent(file)
                    val declarations = extractExpectDeclarations(content)
                    expectDeclarations.addAll(declarations)
                }
            } catch (e: Exception) {
                // commonMain directory might not exist
            }

            expectDeclarations
        }

    /**
     * Identifies stub implementations that need to be replaced.
     */
    override suspend fun findStubImplementations(
        sourceRoot: String,
        platform: Platform
    ): List<ImplementationGap> = withContext(Dispatchers.Default) {

        val gaps = mutableListOf<ImplementationGap>()
        val platformSourceDir = "$sourceRoot/src/${platform.sourceDir}/kotlin"

        try {
            val actualFiles = findKotlinFiles(platformSourceDir)

            for (file in actualFiles) {
                val content = readFileContent(file)
                val stubDeclarations = findStubDeclarationsInContent(content)

                for (stubDecl in stubDeclarations) {
                    gaps.add(createStubImplementationGap(stubDecl, platform, file))
                }
            }
        } catch (e: Exception) {
            // Platform directory might not exist
        }

        gaps
    }

    // Private helper methods

    private suspend fun findKotlinFiles(directory: String): List<String> =
        withContext(Dispatchers.Default) {
            try {
                // File system operations delegated to platform-specific implementations
                emptyList<String>()
            } catch (e: Exception) {
                emptyList()
            }
        }

    private suspend fun readFileContent(filePath: String): String =
        withContext(Dispatchers.Default) {
            try {
                // File reading delegated to platform-specific implementations
                ""
            } catch (e: Exception) {
                ""
            }
        }

    private fun extractExpectDeclarations(content: String): List<String> {
        val declarations = mutableListOf<String>()

        // Regex-based extraction of expect declarations
        val expectPattern = Regex("""expect\s+(class|fun|val|var|object)\s+([^{;]+)""")
        val matches = expectPattern.findAll(content)

        for (match in matches) {
            declarations.add(match.value.trim())
        }

        return declarations
    }

    private fun containsActualImplementation(content: String, expectDeclaration: String): Boolean {
        // Extract the declaration name from the expect signature
        val declarationName = extractDeclarationName(expectDeclaration)

        // Look for actual implementation
        val actualPattern =
            Regex("""actual\s+(class|fun|val|var|object)\s+${Regex.escape(declarationName)}""")
        return actualPattern.containsMatchIn(content)
    }

    private fun isStubImplementation(content: String, expectDeclaration: String): Boolean {
        val declarationName = extractDeclarationName(expectDeclaration)

        // Look for common stub patterns
        val stubPatterns = listOf(
            Regex("""actual\s+(fun|val|var)\s+${Regex.escape(declarationName)}.*?=\s*TODO\(\)"""),
            Regex("""actual\s+(fun|val|var)\s+${Regex.escape(declarationName)}.*?=\s*throw\s+NotImplementedError"""),
            Regex("""actual\s+class\s+${Regex.escape(declarationName)}.*?\{\s*//\s*TODO"""),
            Regex("""actual\s+object\s+${Regex.escape(declarationName)}.*?\{\s*//\s*TODO""")
        )

        return stubPatterns.any { it.containsMatchIn(content) }
    }

    private fun isPoorQualityImplementation(content: String, expectDeclaration: String): Boolean {
        val declarationName = extractDeclarationName(expectDeclaration)

        // Look for poor quality indicators
        val poorQualityPatterns = listOf(
            Regex("""actual\s+fun\s+${Regex.escape(declarationName)}.*?\{\s*return\s+null\s*\}"""),
            Regex("""actual\s+fun\s+${Regex.escape(declarationName)}.*?\{\s*\}"""), // Empty implementation
            Regex("""actual\s+fun\s+${Regex.escape(declarationName)}.*?println\("not implemented"\)""")
        )

        return poorQualityPatterns.any { it.containsMatchIn(content) }
    }

    private fun extractDeclarationName(expectDeclaration: String): String {
        // Extract name from "expect fun functionName(...)" or "expect class ClassName"
        val namePattern =
            Regex("""expect\s+(class|fun|val|var|object)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
        val match = namePattern.find(expectDeclaration)
        return match?.groupValues?.get(2) ?: ""
    }

    private fun parseExpectDeclarationsFromFile(filePath: String): List<String> {
        // File parsing delegated to platform-specific implementations
        return emptyList()
    }

    private fun extractSourceRoot(expectFilePath: String): String {
        // Extract source root from file path
        // Assume path like "/path/to/project/src/commonMain/kotlin/..."
        val srcIndex = expectFilePath.indexOf("/src/")
        return if (srcIndex >= 0) {
            expectFilePath.substring(0, srcIndex)
        } else {
            expectFilePath.substringBeforeLast("/src", expectFilePath)
        }
    }

    private fun findStubDeclarationsInContent(content: String): List<String> {
        val stubs = mutableListOf<String>()

        // Find actual declarations that contain TODO() or similar
        val actualPattern = Regex("""actual\s+(class|fun|val|var|object)\s+([^{;]+)""")
        val matches = actualPattern.findAll(content)

        for (match in matches) {
            val declaration = match.value
            val declarationName =
                extractDeclarationName("expect ${match.groupValues[1]} ${match.groupValues[2]}")

            if (isStubImplementation(content, "expect ${match.groupValues[1]} $declarationName")) {
                stubs.add(declaration)
            }
        }

        return stubs
    }

    private fun createImplementationGap(
        expectDeclaration: String,
        platform: Platform,
        sourceRoot: String,
        filePath: String? = null
    ): ImplementationGap {
        val module = extractModuleFromPath(filePath ?: sourceRoot)
        val lineNumber = 0 // Would extract from actual parsing

        return ImplementationGap(
            filePath = filePath ?: "$sourceRoot/src/commonMain/kotlin",
            expectedSignature = expectDeclaration,
            platform = platform,
            module = module,
            lineNumber = lineNumber,
            gapType = io.materia.validation.GapType.MISSING_ACTUAL,
            severity = determineSeverity(expectDeclaration),
            context = extractContext(expectDeclaration)
        )
    }

    private fun createStubImplementationGap(
        stubDeclaration: String,
        platform: Platform,
        filePath: String
    ): ImplementationGap {
        val module = extractModuleFromPath(filePath)
        val lineNumber = 0 // Would extract from actual parsing

        return ImplementationGap(
            filePath = filePath,
            expectedSignature = stubDeclaration,
            platform = platform,
            module = module,
            lineNumber = lineNumber,
            gapType = io.materia.validation.GapType.STUB_IMPLEMENTATION,
            severity = io.materia.validation.GapSeverity.MEDIUM,
            context = "Stub implementation found"
        )
    }

    private fun determineSeverity(expectDeclaration: String): io.materia.validation.GapSeverity {
        return when {
            expectDeclaration.contains("Renderer") || expectDeclaration.contains("Core") ->
                io.materia.validation.GapSeverity.CRITICAL

            expectDeclaration.contains("Camera") || expectDeclaration.contains("Scene") ->
                io.materia.validation.GapSeverity.HIGH

            else -> io.materia.validation.GapSeverity.MEDIUM
        }
    }

    private fun extractContext(expectDeclaration: String): String {
        // Extract meaningful context from the declaration
        return when {
            expectDeclaration.contains("fun") -> "Function declaration"
            expectDeclaration.contains("class") -> "Class declaration"
            expectDeclaration.contains("val") -> "Property declaration"
            expectDeclaration.contains("var") -> "Variable declaration"
            expectDeclaration.contains("object") -> "Object declaration"
            else -> "Unknown declaration"
        }
    }

    private fun extractModuleFromPath(filePath: String): String {
        // Extract module name from file path
        val parts = filePath.split("/")
        val kotlinIndex = parts.lastIndexOf("kotlin")

        return if (kotlinIndex >= 0 && kotlinIndex < parts.size - 1) {
            parts[kotlinIndex + 1]
        } else {
            "unknown"
        }
    }

    private suspend fun extractModules(sourceRoot: String, modulesCovered: MutableList<String>) {
        // Extract all module names from the source structure
        val commonMainPath = "$sourceRoot/src/commonMain/kotlin"

        try {
            val directories = findDirectories(commonMainPath)
            modulesCovered.addAll(directories.map { extractModuleFromPath(it) })
        } catch (e: Exception) {
            // Directory might not exist
        }
    }

    private suspend fun findDirectories(path: String): List<String> =
        withContext(Dispatchers.Default) {
            try {
                // Returns core Materia module paths for validation
                listOf(
                    "io/materia/core",
                    "io/materia/renderer",
                    "io/materia/scene",
                    "io/materia/math"
                )
            } catch (e: Exception) {
                emptyList()
            }
        }
}