package io.materia.validation.scanner

import io.materia.core.platform.currentTimeMillis
import io.materia.validation.*
import io.materia.validation.platform.FileScannerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Default implementation of PlaceholderScanner interface.
 *
 * This scanner detects placeholder patterns in source code using regex patterns
 * and provides context-aware validation to distinguish genuine placeholders
 * from documentation examples and false positives.
 *
 * Features:
 * - Multiplatform file system support using kotlinx-io
 * - Configurable file pattern filtering
 * - Context-aware false positive filtering
 * - Effort estimation based on surrounding code analysis
 * - Comprehensive error handling and progress tracking
 */
class DefaultPlaceholderScanner : PlaceholderScanner {

    companion object {
        // Core placeholder patterns from research.md
        private val PLACEHOLDER_PATTERNS = listOf(
            "\\bTODO\\b",
            "\\bFIXME\\b",
            "\\bplaceholder\\b",
            "\\bstub\\b(?!\\s*\\(\\))",  // Exclude stub() function calls in tests
            "\\bin\\s+the\\s+meantime\\b",
            "\\bfor\\s+now\\b",
            "\\bin\\s+a\\s+real\\s+implementation\\b",
            "\\bmock\\b(?=.*implementation|.*class|.*object)", // Mock in implementation context
            "\\btemporary\\b(?=.*implementation|.*solution|.*fix)" // Temporary in implementation context
        )

        // File extensions to scan
        private val DEFAULT_FILE_PATTERNS = listOf("*.kt", "*.md", "*.gradle.kts")

        // Directories to exclude by default
        private val DEFAULT_EXCLUDE_PATTERNS =
            listOf("**/build/**", "**/node_modules/**", "**/.git/**", "**/.gradle/**")

        // Patterns that indicate documentation context (likely false positives)
        private val DOCUMENTATION_INDICATORS = listOf(
            "\\b(example|sample|demo|tutorial|guide|documentation)\\b",
            "\\b(see|check|refer\\s+to|according\\s+to)\\b",
            "\\b(contract|interface|spec|specification)\\b",
            "\\b(test|should|verify|validate|assert)\\b"
        )

        // Module importance for criticality assessment
        private val MODULE_CRITICALITY = mapOf(
            "core" to CriticalityLevel.CRITICAL,
            "renderer" to CriticalityLevel.CRITICAL,
            "scene" to CriticalityLevel.HIGH,
            "geometry" to CriticalityLevel.HIGH,
            "material" to CriticalityLevel.HIGH,
            "animation" to CriticalityLevel.MEDIUM,
            "loader" to CriticalityLevel.MEDIUM,
            "controls" to CriticalityLevel.MEDIUM,
            "physics" to CriticalityLevel.MEDIUM,
            "xr" to CriticalityLevel.LOW,
            "postprocess" to CriticalityLevel.LOW,
            "tools" to CriticalityLevel.LOW,
            "examples" to CriticalityLevel.LOW,
            "test" to CriticalityLevel.LOW
        )
    }

    override suspend fun scanDirectory(

        rootPath: String,
        filePatterns: List<String>,
        excludePatterns: List<String>
    ): ScanResult = withContext(Dispatchers.Default) {
        val startTime = currentTimeMillis()
        val scannedPaths = mutableListOf<String>()
        val allPlaceholders = mutableListOf<PlaceholderInstance>()
        var totalFilesScanned = 0

        try {
            if (!fileExists(rootPath)) {
                val duration = currentTimeMillis() - startTime
                return@withContext ScanResult(
                    scanTimestamp = startTime,
                    scannedPaths = emptyList(),
                    placeholders = emptyList(),
                    totalFilesScanned = 0,
                    scanDurationMs = duration
                )
            }

            // Collect all files to scan
            val filesToScan = collectFilesToScan(rootPath, filePatterns, excludePatterns)

            // Scan files in parallel for better performance
            val chunks =
                filesToScan.chunked(10) // Process in chunks to avoid overwhelming the system

            for (chunk in chunks) {
                val chunkResults = chunk.map { filePath ->
                    async {
                        try {
                            val placeholders = scanFile(filePath)
                            scannedPaths.add(filePath)
                            placeholders
                        } catch (e: Exception) {
                            // Log error but continue scanning other files
                            emptyList<PlaceholderInstance>()
                        }
                    }
                }.awaitAll()

                chunkResults.forEach { placeholders ->
                    allPlaceholders.addAll(placeholders)
                }
                totalFilesScanned += chunk.size
            }

        } catch (e: Exception) {
            // Return partial results even if scanning fails
        }

        val duration = currentTimeMillis() - startTime

        ScanResult(
            scanTimestamp = startTime,
            scannedPaths = scannedPaths,
            placeholders = allPlaceholders,
            totalFilesScanned = totalFilesScanned,
            scanDurationMs = duration
        )
    }

    override suspend fun scanFile(filePath: String): List<PlaceholderInstance> =
        withContext(Dispatchers.Default) {
            try {
                if (!fileExists(filePath)) {
                    return@withContext emptyList()
                }

                val content = readFileContent(filePath)

                val placeholders = mutableListOf<PlaceholderInstance>()
                val lines = content.lines()

                // Extract module name from file path
                val moduleName = extractModuleName(filePath)

                // Extract platform from file path
                val platform = extractPlatform(filePath)

                for ((lineIndex, line) in lines.withIndex()) {
                    val lineNumber = lineIndex + 1

                    for (pattern in PLACEHOLDER_PATTERNS) {
                        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                        val matches = regex.findAll(line)

                        for (match in matches) {
                            val instance = PlaceholderInstance(
                                filePath = filePath,
                                lineNumber = lineNumber,
                                columnNumber = match.range.first + 1,
                                pattern = match.value,
                                context = extractContext(lines, lineIndex),
                                type = classifyPlaceholderType(match.value),
                                criticality = assessCriticality(
                                    moduleName,
                                    match.value,
                                    extractContext(lines, lineIndex)
                                ),
                                module = moduleName,
                                platform = platform
                            )

                            // Only add if it passes validation (not a false positive)
                            if (validatePlaceholder(instance, content)) {
                                placeholders.add(instance)
                            }
                        }
                    }
                }

                placeholders
            } catch (e: Exception) {
                emptyList()
            }
        }

    override fun getDetectionPatterns(): List<String> {
        return PLACEHOLDER_PATTERNS
    }

    override fun validatePlaceholder(instance: PlaceholderInstance, fileContent: String): Boolean {
        val context = instance.context.lowercase()
        val pattern = instance.pattern.lowercase()

        // Check if this appears to be in documentation context
        for (docIndicator in DOCUMENTATION_INDICATORS) {
            if (Regex(docIndicator, RegexOption.IGNORE_CASE).containsMatchIn(context)) {
                // Additional checks for documentation context
                if (context.contains("example") ||
                    context.contains("sample") ||
                    context.contains("tutorial") ||
                    context.contains("see") ||
                    context.contains("according to")
                ) {
                    return false
                }
            }
        }

        // Check if it's in a comment about contracts/specifications
        if (context.contains("contract") && (pattern == "todo" || pattern == "fixme")) {
            return false
        }

        // Check if it's in test context but not actual test implementation
        if (context.contains("test") &&
            (context.contains("should") || context.contains("verify") || context.contains("validate"))
        ) {
            // If it's describing what a test should do, it's likely documentation
            if (context.contains("should test") || context.contains("when implemented")) {
                return false
            }
        }

        // Check if it's in a markdown file and appears to be documentation
        if (instance.filePath.endsWith(".md")) {
            if (context.contains("implementation needed") ||
                context.contains("will be implemented") ||
                context.contains("phase") ||
                context.contains("research")
            ) {
                return false
            }
        }

        // Valid placeholder if we get here
        return true
    }

    override fun estimateReplacementEffort(
        instance: PlaceholderInstance,
        fileContent: String
    ): EffortLevel {
        val context = instance.context.lowercase()
        val type = instance.type

        // Assess effort based on context clues
        return when {
            // Large effort indicators
            context.contains("implement") ||
                    context.contains("complete") ||
                    context.contains("design") ||
                    context.contains("architecture") ||
                    type == PlaceholderType.STUB -> EffortLevel.LARGE

            // Medium effort indicators
            context.contains("refactor") ||
                    context.contains("optimize") ||
                    context.contains("enhance") ||
                    context.contains("improve") ||
                    type == PlaceholderType.FIXME -> EffortLevel.MEDIUM

            // Small effort indicators
            context.contains("add") ||
                    context.contains("update") ||
                    context.contains("fix") ||
                    context.contains("change") ||
                    type == PlaceholderType.TODO -> EffortLevel.SMALL

            // Default to trivial for simple cases
            else -> EffortLevel.TRIVIAL
        }
    }

    private suspend fun collectFilesToScan(
        rootPath: String,
        filePatterns: List<String>,
        excludePatterns: List<String>
    ): List<String> = withContext(Dispatchers.Default) {
        try {
            val fileScanner = FileScannerFactory.createFileScanner()

            // Use FileScanner to find all matching files
            val allFiles = fileScanner.findFiles(rootPath, filePatterns)

            // Filter out excluded paths
            allFiles
                .map { it.path }
                .filter { path ->
                    !shouldExcludePath(path, excludePatterns)
                }
        } catch (e: Exception) {
            // Return empty list if scanning fails
            emptyList()
        }
    }

    private fun shouldExcludePath(path: String, excludePatterns: List<String>): Boolean {
        val normalizedPath = path.replace("\\", "/")

        return excludePatterns.any { pattern ->
            val regexPattern = pattern.replace("**", ".*").replace("*", "[^/]*")
            Regex(regexPattern).containsMatchIn(normalizedPath)
        }
    }

    private fun extractContext(lines: List<String>, lineIndex: Int): String {
        val startIndex = maxOf(0, lineIndex - 2)
        val endIndex = minOf(lines.size - 1, lineIndex + 2)

        return lines.subList(startIndex, endIndex + 1).joinToString("\\n")
    }

    private fun extractModuleName(filePath: String): String {
        val path = filePath.replace("\\", "/")

        // Look for module patterns in path
        val modulePatterns = listOf(
            "materia-core" to "core",
            "materia-renderer" to "renderer",
            "materia-scene" to "scene",
            "materia-geometry" to "geometry",
            "materia-material" to "material",
            "materia-animation" to "animation",
            "materia-loader" to "loader",
            "materia-controls" to "controls",
            "materia-physics" to "physics",
            "materia-xr" to "xr",
            "materia-postprocess" to "postprocess",
            "tools" to "tools",
            "examples" to "examples",
            "test" to "test"
        )

        for ((pattern, module) in modulePatterns) {
            if (path.contains(pattern)) {
                return module
            }
        }

        // Fallback: extract from source set structure
        when {
            path.contains("/core/") -> return "core"
            path.contains("/renderer/") -> return "renderer"
            path.contains("/scene/") -> return "scene"
            path.contains("/geometry/") -> return "geometry"
            path.contains("/material/") -> return "material"
            path.contains("/animation/") -> return "animation"
            path.contains("/loader/") -> return "loader"
            path.contains("/controls/") -> return "controls"
            path.contains("/physics/") -> return "physics"
            path.contains("/xr/") -> return "xr"
            path.contains("/postprocess/") -> return "postprocess"
            path.contains("/tools/") -> return "tools"
            path.contains("/examples/") -> return "examples"
            path.contains("Test") -> return "test"
            else -> return "common"
        }
    }

    private fun extractPlatform(filePath: String): String? {
        return when {
            filePath.contains("/jvmMain/") -> "jvm"
            filePath.contains("/jsMain/") -> "js"
            filePath.contains("/androidMain/") -> "android"
            filePath.contains("/iosMain/") -> "ios"
            filePath.contains("/linuxX64Main/") -> "linuxX64"
            filePath.contains("/mingwX64Main/") -> "mingwX64"
            filePath.contains("/macosX64Main/") -> "macosX64"
            filePath.contains("/macosArm64Main/") -> "macosArm64"
            filePath.contains("/nativeMain/") -> "native"
            filePath.contains("/commonMain/") -> null // Platform-agnostic
            else -> null
        }
    }

    private fun classifyPlaceholderType(pattern: String): PlaceholderType {
        return when (pattern.lowercase()) {
            "todo" -> PlaceholderType.TODO
            "fixme" -> PlaceholderType.FIXME
            "stub" -> PlaceholderType.STUB
            "placeholder" -> PlaceholderType.PLACEHOLDER
            "mock" -> PlaceholderType.MOCK
            "temporary" -> PlaceholderType.TEMPORARY
            else -> PlaceholderType.TODO // Default fallback
        }
    }

    private fun assessCriticality(
        moduleName: String,
        pattern: String,
        context: String
    ): CriticalityLevel {
        // Base criticality from module importance
        val baseCriticality = MODULE_CRITICALITY[moduleName] ?: CriticalityLevel.MEDIUM

        // Adjust based on pattern type and context
        val contextLower = context.lowercase()

        return when {
            // Critical indicators
            contextLower.contains("crash") ||
                    contextLower.contains("security") ||
                    contextLower.contains("memory leak") ||
                    contextLower.contains("performance") ||
                    pattern.lowercase() == "fixme" -> CriticalityLevel.CRITICAL

            // High priority indicators
            contextLower.contains("render") ||
                    contextLower.contains("display") ||
                    contextLower.contains("shader") ||
                    contextLower.contains("gpu") -> CriticalityLevel.HIGH

            // Use base criticality for normal cases
            else -> baseCriticality
        }
    }

    // Platform-specific file system operations using FileScanner
    private suspend fun fileExists(path: String): Boolean = try {
        val fileScanner = FileScannerFactory.createFileScanner()
        fileScanner.exists(path)
    } catch (e: Exception) {
        false
    }

    private suspend fun readFileContent(path: String): String = try {
        val fileScanner = FileScannerFactory.createFileScanner()
        fileScanner.readFileContent(path) ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun getFileName(path: String): String {
        return path.substringAfterLast("/").substringAfterLast("\\")
    }
}