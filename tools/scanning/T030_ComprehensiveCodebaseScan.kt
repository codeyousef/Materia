#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

package tools.scanning

import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * T030: Comprehensive Codebase Scan for Placeholder Patterns
 *
 * This script executes a comprehensive scan of the entire Materia project
 * to identify all placeholder patterns that need to be replaced with
 * production-ready implementations.
 *
 * Usage: kotlin T030_ComprehensiveCodebaseScan.kt [project-root]
 */

// Copy the scanner and data types locally for script execution
enum class Platform(val sourceDir: String) {
    JVM("jvmMain"),
    JS("jsMain"),
    ANDROID("androidMain"),
    IOS("iosMain"),
    NATIVE("nativeMain"),
    UNSUPPORTED("unsupported")
}

data class ScanResult(
    val scanTimestamp: Long,
    val scannedPaths: List<String>,
    val placeholders: List<PlaceholderInstance>,
    val totalFilesScanned: Int,
    val scanDurationMs: Long
)

data class PlaceholderInstance(
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val pattern: String,
    val context: String,
    val type: PlaceholderType,
    val criticality: CriticalityLevel,
    val module: String,
    val platform: String?
)

enum class PlaceholderType {
    TODO, FIXME, STUB, PLACEHOLDER, TEMPORARY, MOCK
}

enum class CriticalityLevel {
    CRITICAL, HIGH, MEDIUM, LOW
}

enum class EffortLevel {
    TRIVIAL, SMALL, MEDIUM, LARGE
}

class ComprehensiveCodebaseScanner {
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
            "(?i)\\brequires\\s+implementation\\b",
            "(?i)\\breturn\\s+null\\s*//.*stub",
            "(?i)\\breturn\\s+emptyList\\(\\)\\s*//.*stub",
            "(?i)\\breturn\\s+false\\s*//.*stub",
            "(?i)\\breturn\\s+true\\s*//.*stub"
        )

        // File extensions to scan
        private val DEFAULT_FILE_PATTERNS = listOf("*.kt", "*.md", "*.gradle.kts")

        // Directories to exclude by default
        private val DEFAULT_EXCLUDE_PATTERNS =
            listOf("**/build/**", "**/node_modules/**", "**/.git/**", "**/.gradle/**", "**/tools/scanning/**")

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

    suspend fun scanDirectory(rootPath: String): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val scannedPaths = mutableListOf<String>()
        val allPlaceholders = mutableListOf<PlaceholderInstance>()
        var totalFilesScanned = 0

        println("🔍 Starting comprehensive codebase scan...")
        println("📁 Root path: $rootPath")

        try {
            val rootFile = File(rootPath)
            if (!rootFile.exists()) {
                println("❌ Root path does not exist: $rootPath")
                return@withContext ScanResult(
                    scanTimestamp = startTime,
                    scannedPaths = emptyList(),
                    placeholders = emptyList(),
                    totalFilesScanned = 0,
                    scanDurationMs = System.currentTimeMillis() - startTime
                )
            }

            // Collect all files to scan
            val filesToScan = collectFilesToScan(rootPath)
            println("📊 Found ${filesToScan.size} files to scan")

            // Scan files in parallel for better performance
            val chunks = filesToScan.chunked(10) // Process in chunks to avoid overwhelming the system

            for ((chunkIndex, chunk) in chunks.withIndex()) {
                println("📦 Processing chunk ${chunkIndex + 1}/${chunks.size} (${chunk.size} files)")

                val chunkResults = chunk.map { filePath ->
                    async {
                        try {
                            val placeholders = scanFile(filePath)
                            scannedPaths.add(filePath)
                            placeholders
                        } catch (e: Exception) {
                            println("⚠️ Error scanning file $filePath: ${e.message}")
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
            println("❌ Error during directory scan: ${e.message}")
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        println("✅ Scan completed in ${duration}ms")
        println("📊 Scanned $totalFilesScanned files")
        println("🔍 Found ${allPlaceholders.size} placeholder instances")

        ScanResult(
            scanTimestamp = startTime,
            scannedPaths = scannedPaths,
            placeholders = allPlaceholders,
            totalFilesScanned = totalFilesScanned,
            scanDurationMs = duration
        )
    }

    private suspend fun scanFile(filePath: String): List<PlaceholderInstance> = withContext(Dispatchers.IO) {
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
                            criticality = assessCriticality(moduleName, match.value, extractContext(lines, lineIndex)),
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

    private fun collectFilesToScan(rootPath: String): List<String> {
        val files = mutableListOf<String>()

        fun collectRecursively(file: File) {
            try {
                if (!file.exists()) return

                // Check exclude patterns
                val pathStr = file.absolutePath.replace("\\", "/")
                for (excludePattern in DEFAULT_EXCLUDE_PATTERNS) {
                    val pattern = excludePattern.replace("**", ".*").replace("*", "[^/]*")
                    if (Regex(pattern).containsMatchIn(pathStr)) {
                        return
                    }
                }

                if (file.isDirectory) {
                    try {
                        file.listFiles()?.forEach { child ->
                            collectRecursively(child)
                        }
                    } catch (e: Exception) {
                        // Skip directories we can't read
                    }
                } else {
                    // Check if file matches any file pattern
                    val fileName = file.name
                    for (filePattern in DEFAULT_FILE_PATTERNS) {
                        val pattern = filePattern.replace("*", ".*")
                        if (Regex(pattern).matches(fileName)) {
                            files.add(file.absolutePath)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Skip files/directories we can't access
            }
        }

        collectRecursively(File(rootPath))
        return files
    }

    private fun validatePlaceholder(instance: PlaceholderInstance, fileContent: String): Boolean {
        val context = instance.context.lowercase()
        val pattern = instance.pattern.lowercase()

        // Skip if it's in documentation context
        if (context.contains("example") ||
            context.contains("sample") ||
            context.contains("tutorial") ||
            context.contains("according to")
        ) {
            return false
        }

        // Skip if it's in test descriptions
        if (context.contains("test") &&
            (context.contains("should") || context.contains("verify") || context.contains("validate"))
        ) {
            if (context.contains("should test") || context.contains("when implemented")) {
                return false
            }
        }

        // Skip if it's in markdown documentation
        if (instance.filePath.endsWith(".md")) {
            if (context.contains("implementation needed") ||
                context.contains("will be implemented") ||
                context.contains("phase") ||
                context.contains("research")
            ) {
                return false
            }
        }

        return true
    }

    private fun extractContext(lines: List<String>, lineIndex: Int): String {
        val startIndex = maxOf(0, lineIndex - 2)
        val endIndex = minOf(lines.size - 1, lineIndex + 2)
        return lines.subList(startIndex, endIndex + 1).joinToString("\\n")
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

    private fun assessCriticality(moduleName: String, pattern: String, context: String): CriticalityLevel {
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

// Report generation functions
fun generateDetailedReport(scanResult: ScanResult): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    val criticalCount = scanResult.placeholders.count { it.criticality == CriticalityLevel.CRITICAL }
    val highCount = scanResult.placeholders.count { it.criticality == CriticalityLevel.HIGH }
    val mediumCount = scanResult.placeholders.count { it.criticality == CriticalityLevel.MEDIUM }
    val lowCount = scanResult.placeholders.count { it.criticality == CriticalityLevel.LOW }

    val moduleBreakdown = scanResult.placeholders
        .groupBy { it.module }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val platformBreakdown = scanResult.placeholders
        .groupBy { it.platform ?: "common" }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    return """
# T030: Comprehensive Codebase Scan Report
Generated: $timestamp

## Executive Summary
- **Total files scanned:** ${scanResult.totalFilesScanned}
- **Total placeholder instances found:** ${scanResult.placeholders.size}
- **Scan duration:** ${scanResult.scanDurationMs}ms

## Criticality Breakdown
- 🔴 **CRITICAL:** $criticalCount placeholders
- 🟠 **HIGH:** $highCount placeholders
- 🟡 **MEDIUM:** $mediumCount placeholders
- 🟢 **LOW:** $lowCount placeholders

## Module Breakdown
${moduleBreakdown.take(10).joinToString("\n") { "- **${it.first}:** ${it.second} placeholders" }}

## Platform Breakdown
${platformBreakdown.joinToString("\n") { "- **${it.first}:** ${it.second} placeholders" }}

## Critical Issues (Immediate Action Required)
${
        scanResult.placeholders.filter { it.criticality == CriticalityLevel.CRITICAL }.take(20).joinToString("\n") {
            "- **${it.module}** (${it.platform ?: "common"}) - Line ${it.lineNumber}: ${it.pattern}\n  File: ${it.filePath}\n  Context: ${
                it.context.replace(
                    "\n",
                    " "
                )
            }\n"
        }
    }

## High Priority Issues
${
        scanResult.placeholders.filter { it.criticality == CriticalityLevel.HIGH }.take(20).joinToString("\n") {
            "- **${it.module}** (${it.platform ?: "common"}) - Line ${it.lineNumber}: ${it.pattern}\n  File: ${it.filePath}\n  Context: ${
                it.context.replace(
                    "\n",
                    " "
                )
            }\n"
        }
    }

## Recommendations
1. **Immediate:** Fix all CRITICAL placeholders (${criticalCount} items)
2. **Next:** Address HIGH priority placeholders in core and renderer modules
3. **Medium-term:** Complete MEDIUM priority items for production readiness
4. **Long-term:** Address LOW priority items for code quality

## Next Steps
1. Run T031 expect/actual validation
2. Execute T032 automated placeholder fixing
3. Validate all fixes maintain test suite (627 tests)
4. Focus on JavaScript renderer black screen issue
    """.trimIndent()
}

fun saveScanResults(scanResult: ScanResult, outputDir: String) {
    val outputFile = File(outputDir, "T030_scan_results.md")
    outputFile.parentFile.mkdirs()

    val report = generateDetailedReport(scanResult)
    outputFile.writeText(report)

    println("📊 Detailed report saved to: ${outputFile.absolutePath}")

    // Also save raw data as JSON-like format for further processing
    val dataFile = File(outputDir, "T030_scan_data.txt")
    val dataOutput = buildString {
        appendLine("# Raw Scan Data - T030")
        appendLine("# Total placeholders: ${scanResult.placeholders.size}")
        appendLine("# Scan timestamp: ${scanResult.scanTimestamp}")
        appendLine("")

        for (placeholder in scanResult.placeholders) {
            appendLine("FILE: ${placeholder.filePath}")
            appendLine("LINE: ${placeholder.lineNumber}")
            appendLine("PATTERN: ${placeholder.pattern}")
            appendLine("TYPE: ${placeholder.type}")
            appendLine("CRITICALITY: ${placeholder.criticality}")
            appendLine("MODULE: ${placeholder.module}")
            appendLine("PLATFORM: ${placeholder.platform ?: "common"}")
            appendLine("CONTEXT: ${placeholder.context.replace("\n", "\\n")}")
            appendLine("---")
        }
    }
    dataFile.writeText(dataOutput)

    println("💾 Raw data saved to: ${dataFile.absolutePath}")
}

// Main execution
suspend fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0) ?: File("").absolutePath
    val outputDir = File(projectRoot, "docs/private/scan-results").absolutePath

    println("🚀 T030: Comprehensive Codebase Scan Starting...")
    println("📁 Project root: $projectRoot")
    println("📊 Output directory: $outputDir")

    val scanner = ComprehensiveCodebaseScanner()
    val scanResult = scanner.scanDirectory(projectRoot)

    // Generate and save reports
    saveScanResults(scanResult, outputDir)

    // Print summary to console
    println("\n" + "=".repeat(80))
    println("📊 T030 SCAN COMPLETE")
    println("=".repeat(80))
    println("✅ Files scanned: ${scanResult.totalFilesScanned}")
    println("🔍 Placeholders found: ${scanResult.placeholders.size}")
    println("⏱️ Duration: ${scanResult.scanDurationMs}ms")
    println("🔴 Critical issues: ${scanResult.placeholders.count { it.criticality == CriticalityLevel.CRITICAL }}")
    println("🟠 High priority: ${scanResult.placeholders.count { it.criticality == CriticalityLevel.HIGH }}")

    val topModules = scanResult.placeholders
        .groupBy { it.module }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .take(5)

    println("\n📦 Top modules with placeholders:")
    topModules.forEach { (module, count) ->
        println("   - $module: $count placeholders")
    }

    println("\n📋 Next Steps:")
    println("   1. Review critical issues in report")
    println("   2. Run T031 expect/actual validation")
    println("   3. Execute T032 automated fixing")
    println("   4. Focus on JavaScript renderer issues")

    if (scanResult.placeholders.any { it.criticality == CriticalityLevel.CRITICAL }) {
        println("\n⚠️  CRITICAL placeholders found - production deployment blocked!")
    }
}

// Execute the main function
runBlocking {
    main(args)
}