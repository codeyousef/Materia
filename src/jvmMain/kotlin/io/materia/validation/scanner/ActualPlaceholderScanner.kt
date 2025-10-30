package io.materia.validation.scanner

import io.materia.validation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * Actual JVM implementation of PlaceholderScanner that can scan real file systems.
 *
 * This replaces the stub methods in DefaultPlaceholderScanner with real file system
 * operations for the T030 comprehensive codebase scanning task.
 */
class ActualPlaceholderScanner : PlaceholderScanner {

    companion object {
        // Core placeholder patterns from research.md
        private val PLACEHOLDER_PATTERNS = listOf(
            "(?i)\\bTODO\\b",
            "(?i)\\bFIXME\\b",
            "(?i)\\bplaceholder\\b",
            "(?i)\\bstub\\b(?!\\s*\\(\\))",  // Exclude stub() function calls in tests
            "(?i)\\bin\\s+the\\s+meantime\\b",
            "(?i)\\bfor\\s+now\\b",
            "(?i)\\bin\\s+a\\s+real\\s+implementation\\b",
            "(?i)\\bmock\\b(?=.*implementation|.*class|.*object)", // Mock in implementation context
            "(?i)\\btemporary\\b(?=.*implementation|.*solution|.*fix)", // Temporary in implementation context
            "(?i)\\bunimplemented\\b",
            "(?i)\\bnot\\s+implemented\\b",
            "(?i)\\bnotImplemented\\(\\)",
            "(?i)\\bthrowing\\s+exception\\b",
            "(?i)\\brequires\\s+implementation\\b"
        )

        // File extensions to scan
        private val DEFAULT_FILE_PATTERNS = listOf("*.kt", "*.md", "*.gradle.kts")

        // Directories to exclude by default
        private val DEFAULT_EXCLUDE_PATTERNS =
            listOf("**/build/**", "**/node_modules/**", "**/.git/**", "**/.gradle/**")

        // Patterns that indicate documentation context (likely false positives)
        private val DOCUMENTATION_INDICATORS = listOf(
            "(?i)\\b(example|sample|demo|tutorial|guide|documentation)\\b",
            "(?i)\\b(see|check|refer\\s+to|according\\s+to)\\b",
            "(?i)\\b(contract|interface|spec|specification)\\b",
            "(?i)\\b(test|should|verify|validate|assert)\\b"
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
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val scannedPaths = mutableListOf<String>()
        val allPlaceholders = mutableListOf<PlaceholderInstance>()
        var totalFilesScanned = 0

        try {
            val rootFile = File(rootPath)
            if (!rootFile.exists()) {
                return@withContext ScanResult(
                    scanTimestamp = startTime,
                    scannedPaths = emptyList(),
                    placeholders = emptyList(),
                    totalFilesScanned = 0,
                    scanDurationMs = System.currentTimeMillis() - startTime
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
                            println("Error scanning file $filePath: ${e.message}")
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
            println("Error during directory scan: ${e.message}")
        }

        ScanResult(
            scanTimestamp = startTime,
            scannedPaths = scannedPaths,
            placeholders = allPlaceholders,
            totalFilesScanned = totalFilesScanned,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    override suspend fun scanFile(filePath: String): List<PlaceholderInstance> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || !file.isFile) {
                    return@withContext emptyList()
                }

                val content = file.readText()
                val placeholders = mutableListOf<PlaceholderInstance>()
                val lines = content.lines()

                // Extract module name from file path
                val moduleName = extractModuleName(filePath)

                // Extract platform from file path
                val platform = extractPlatform(filePath)

                for ((lineIndex, line) in lines.withIndex()) {
                    val lineNumber = lineIndex + 1

                    for (pattern in PLACEHOLDER_PATTERNS) {
                        val regex = Regex(pattern)
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
                println("Error scanning file $filePath: ${e.message}")
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
            if (Regex(docIndicator).containsMatchIn(context)) {
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
    ): List<String> = withContext(Dispatchers.IO) {
        val files = mutableListOf<String>()

        try {
            Files.walk(Paths.get(rootPath))
                .asSequence()
                .filter { path ->
                    val pathStr = path.toString()

                    // Check exclude patterns
                    val isExcluded = excludePatterns.any { excludePattern ->
                        val pattern = excludePattern.replace("**", ".*").replace("*", "[^/]*")
                        Regex(pattern).containsMatchIn(pathStr)
                    }

                    if (isExcluded) return@filter false

                    // Check if it's a file (not directory)
                    if (!Files.isRegularFile(path)) return@filter false

                    // Check if file matches any file pattern
                    val fileName = path.fileName.toString()
                    filePatterns.any { filePattern ->
                        val pattern = filePattern.replace("*", ".*")
                        Regex(pattern).matches(fileName)
                    }
                }
                .map { it.toString() }
                .toCollection(files)
        } catch (e: Exception) {
            println("Error collecting files: ${e.message}")
        }

        files
    }

    private fun extractContext(lines: List<String>, lineIndex: Int): String {
        val startIndex = maxOf(0, lineIndex - 2)
        val endIndex = minOf(lines.size - 1, lineIndex + 2)

        return lines.subList(startIndex, endIndex + 1).joinToString("\n")
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
}