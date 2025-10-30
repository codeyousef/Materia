@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Simple Production Readiness Scanner
 *
 * A standalone script that scans for placeholder patterns and missing implementations
 * without dependencies on the project's validation system.
 */

data class PlaceholderMatch(
    val filePath: String,
    val lineNumber: Int,
    val pattern: String,
    val context: String,
    val module: String,
    val platform: String?,
    val criticality: String
)

data class ScanSummary(
    val totalFiles: Int,
    val totalPlaceholders: Int,
    val criticalPlaceholders: Int,
    val highPlaceholders: Int,
    val moduleBreakdown: Map<String, Int>,
    val platformBreakdown: Map<String, Int>
)

class SimpleScanner {

    companion object {
        // Placeholder patterns to detect
        private val PLACEHOLDER_PATTERNS = listOf(
            "(?i)\\bTODO\\b",
            "(?i)\\bFIXME\\b",
            "(?i)\\bplaceholder\\b",
            "(?i)\\bstub\\b(?!\\s*\\(\\))",
            "(?i)\\bin\\s+the\\s+meantime\\b",
            "(?i)\\bfor\\s+now\\b",
            "(?i)\\bin\\s+a\\s+real\\s+implementation\\b",
            "(?i)\\bunimplemented\\b",
            "(?i)\\bnot\\s+implemented\\b",
            "(?i)\\bnotImplemented\\(\\)",
            "(?i)\\breturn\\s+null\\s*//.*stub",
            "(?i)\\breturn\\s+emptyList\\(\\)\\s*//.*stub",
            "(?i)\\breturn\\s+false\\s*//.*stub",
            "(?i)\\breturn\\s+true\\s*//.*stub",
            "(?i)\\breturn\\s+\"\"\\s*//.*stub"
        )

        // Critical modules that must be production ready
        private val CRITICAL_MODULES = setOf("core", "renderer", "scene", "geometry", "material")

        // Files to exclude
        private val EXCLUDE_PATTERNS = listOf(
            "**/build/**",
            "**/node_modules/**",
            "**/.git/**",
            "**/.gradle/**",
            "**/tools/scanning/**",
            "**/docs/private/**"
        )
    }

    suspend fun scanProject(projectRoot: String): ScanSummary = withContext(Dispatchers.IO) {
        println("🔍 Scanning Materia project for placeholder patterns...")
        println("📁 Project root: $projectRoot")

        val startTime = System.currentTimeMillis()
        val placeholders = mutableListOf<PlaceholderMatch>()
        var totalFiles = 0

        // Find all Kotlin files
        val kotlinFiles = findKotlinFiles(File(projectRoot))
        totalFiles = kotlinFiles.size

        println("📊 Found $totalFiles Kotlin files to scan")

        // Scan each file
        for ((index, file) in kotlinFiles.withIndex()) {
            if (index % 50 == 0) {
                println("🔍 Scanning file ${index + 1}/$totalFiles")
            }

            val fileMatches = scanFile(file)
            placeholders.addAll(fileMatches)
        }

        val duration = System.currentTimeMillis() - startTime
        println("✅ Scan completed in ${duration}ms")
        println("🔍 Found ${placeholders.size} placeholder instances")

        // Generate summary
        val criticalCount = placeholders.count { it.criticality == "CRITICAL" }
        val highCount = placeholders.count { it.criticality == "HIGH" }

        val moduleBreakdown = placeholders.groupBy { it.module }.mapValues { it.value.size }
        val platformBreakdown = placeholders.groupBy { it.platform ?: "common" }.mapValues { it.value.size }

        println("🔴 Critical: $criticalCount")
        println("🟠 High: $highCount")

        // Save detailed results
        saveResults(placeholders, File(projectRoot, "docs/private/scan-results"))

        ScanSummary(
            totalFiles = totalFiles,
            totalPlaceholders = placeholders.size,
            criticalPlaceholders = criticalCount,
            highPlaceholders = highCount,
            moduleBreakdown = moduleBreakdown,
            platformBreakdown = platformBreakdown
        )
    }

    private fun findKotlinFiles(dir: File): List<File> {
        val files = mutableListOf<File>()

        fun collectRecursively(current: File) {
            if (!current.exists()) return

            // Check if path should be excluded
            val pathStr = current.absolutePath.replace("\\", "/")
            for (excludePattern in EXCLUDE_PATTERNS) {
                val pattern = excludePattern.replace("**", ".*").replace("*", "[^/]*")
                if (Regex(pattern).containsMatchIn(pathStr)) {
                    return
                }
            }

            if (current.isDirectory) {
                current.listFiles()?.forEach { child ->
                    collectRecursively(child)
                }
            } else if (current.name.endsWith(".kt") || current.name.endsWith(".md")) {
                files.add(current)
            }
        }

        collectRecursively(dir)
        return files
    }

    private fun scanFile(file: File): List<PlaceholderMatch> {
        try {
            val content = file.readText()
            val lines = content.lines()
            val matches = mutableListOf<PlaceholderMatch>()

            val module = extractModule(file.absolutePath)
            val platform = extractPlatform(file.absolutePath)

            for ((lineIndex, line) in lines.withIndex()) {
                for (pattern in PLACEHOLDER_PATTERNS) {
                    val regex = Regex(pattern)
                    val regexMatches = regex.findAll(line)

                    for (match in regexMatches) {
                        // Skip false positives
                        if (isDocumentation(line, lines, lineIndex)) continue

                        val criticality = assessCriticality(module, line, match.value)
                        val context = extractContext(lines, lineIndex)

                        matches.add(
                            PlaceholderMatch(
                                filePath = file.absolutePath,
                                lineNumber = lineIndex + 1,
                                pattern = match.value,
                                context = context,
                                module = module,
                                platform = platform,
                                criticality = criticality
                            )
                        )
                    }
                }
            }

            return matches
        } catch (e: Exception) {
            println("⚠️ Error scanning ${file.absolutePath}: ${e.message}")
            return emptyList()
        }
    }

    private fun extractModule(filePath: String): String {
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
            filePath.contains("/commonMain/") -> null
            else -> null
        }
    }

    private fun isDocumentation(line: String, lines: List<String>, lineIndex: Int): Boolean {
        val context = extractContext(lines, lineIndex).lowercase()

        // Skip documentation examples
        if (context.contains("example") ||
            context.contains("sample") ||
            context.contains("tutorial") ||
            context.contains("according to") ||
            context.contains("see also")
        ) {
            return true
        }

        // Skip test descriptions
        if (context.contains("should test") ||
            context.contains("when implemented") ||
            context.contains("will implement")
        ) {
            return true
        }

        return false
    }

    private fun assessCriticality(module: String, line: String, pattern: String): String {
        val lineLower = line.lowercase()
        val patternLower = pattern.lowercase()

        // Critical indicators
        if (lineLower.contains("render") ||
            lineLower.contains("shader") ||
            lineLower.contains("gpu") ||
            lineLower.contains("crash") ||
            lineLower.contains("security") ||
            patternLower == "fixme"
        ) {
            return "CRITICAL"
        }

        // High priority for critical modules
        if (module in CRITICAL_MODULES) {
            return when {
                lineLower.contains("init") || lineLower.contains("create") -> "HIGH"
                else -> "MEDIUM"
            }
        }

        return "LOW"
    }

    private fun extractContext(lines: List<String>, lineIndex: Int): String {
        val start = maxOf(0, lineIndex - 1)
        val end = minOf(lines.size - 1, lineIndex + 1)
        return lines.subList(start, end + 1).joinToString("\\n")
    }

    private fun saveResults(placeholders: List<PlaceholderMatch>, outputDir: File) {
        outputDir.mkdirs()

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        // Detailed report
        val reportFile = File(outputDir, "SIMPLE_SCAN_REPORT.md")
        val report = generateReport(placeholders, timestamp)
        reportFile.writeText(report)

        // Raw data
        val dataFile = File(outputDir, "SIMPLE_SCAN_DATA.txt")
        val data = generateDataFile(placeholders, timestamp)
        dataFile.writeText(data)

        println("📊 Results saved to: ${outputDir.absolutePath}")
        println("📋 Report: ${reportFile.name}")
        println("💾 Data: ${dataFile.name}")
    }

    private fun generateReport(placeholders: List<PlaceholderMatch>, timestamp: String): String {
        val criticalCount = placeholders.count { it.criticality == "CRITICAL" }
        val highCount = placeholders.count { it.criticality == "HIGH" }
        val mediumCount = placeholders.count { it.criticality == "MEDIUM" }
        val lowCount = placeholders.count { it.criticality == "LOW" }

        val moduleBreakdown = placeholders.groupBy { it.module }.mapValues { it.value.size }
            .toList().sortedByDescending { it.second }

        val platformBreakdown = placeholders.groupBy { it.platform ?: "common" }.mapValues { it.value.size }
            .toList().sortedByDescending { it.second }

        val criticalIssues = placeholders.filter { it.criticality == "CRITICAL" }.take(20)
        val highIssues = placeholders.filter { it.criticality == "HIGH" }.take(15)

        return """
# Materia Production Readiness Scan Report
Generated: $timestamp

## Executive Summary
- **Total placeholder instances:** ${placeholders.size}
- **Files scanned:** Multiple Kotlin source files
- **Production ready:** ${if (criticalCount == 0) "✅ YES" else "❌ NO"}

## Criticality Breakdown
- 🔴 **CRITICAL:** $criticalCount placeholders (blocks deployment)
- 🟠 **HIGH:** $highCount placeholders (important features)
- 🟡 **MEDIUM:** $mediumCount placeholders (nice-to-have)
- 🟢 **LOW:** $lowCount placeholders (optional)

## Module Breakdown
${moduleBreakdown.take(10).joinToString("\n") { "- **${it.first}:** ${it.second} placeholders" }}

## Platform Breakdown
${platformBreakdown.joinToString("\n") { "- **${it.first}:** ${it.second} placeholders" }}

## Critical Issues (Immediate Action Required)
${
            if (criticalIssues.isEmpty()) "✅ No critical issues found!" else
                criticalIssues.joinToString("\n") { issue ->
                    "### ${issue.filePath}:${issue.lineNumber}\n" +
                            "- **Pattern:** ${issue.pattern}\n" +
                            "- **Module:** ${issue.module}\n" +
                            "- **Platform:** ${issue.platform ?: "common"}\n" +
                            "- **Context:** ${issue.context.replace("\\n", " ")}\n"
                }
        }

## High Priority Issues
${
            if (highIssues.isEmpty()) "✅ No high priority issues found!" else
                highIssues.joinToString("\n") { issue ->
                    "### ${issue.filePath}:${issue.lineNumber}\n" +
                            "- **Pattern:** ${issue.pattern}\n" +
                            "- **Module:** ${issue.module}\n"
                }
        }

## Production Readiness Assessment

${
            if (criticalCount == 0) {
                """
🎉 **PRODUCTION READY!**

✅ Zero critical placeholders found
✅ Core functionality appears complete
🚀 Ready for staging deployment

### Recommended Next Steps:
1. Address high priority placeholders for enhanced functionality
2. Run integration tests to verify behavior
3. Monitor JavaScript renderer performance
4. Deploy to staging environment
"""
            } else {
                """
⚠️ **NOT PRODUCTION READY**

❌ $criticalCount critical placeholders must be fixed
🚫 Deployment blocked until critical issues resolved

### Required Actions:
1. Fix all CRITICAL placeholders immediately
2. Focus on renderer and core modules
3. Address JavaScript black screen issues
4. Re-run scan after fixes applied

### Critical Modules Needing Attention:
${criticalIssues.groupBy { it.module }.keys.joinToString(", ")}
"""
            }
        }

## Focus Areas for JavaScript Renderer

The JavaScript renderer black screen issue requires immediate attention.
Key areas to investigate:

1. **WebGPU Initialization:** Ensure proper device and adapter creation
2. **Canvas Context:** Verify canvas element exists and context is valid
3. **Shader Compilation:** Check for compilation errors and fallbacks
4. **Resource Binding:** Validate texture and buffer binding
5. **Render Loop:** Ensure proper frame rendering and presentation

## Recommendations

### Immediate (Critical)
- Fix all $criticalCount critical placeholders
- Focus on renderer module issues
- Address core functionality gaps

### Short-term (High Priority)
- Complete $highCount high priority items
- Implement missing platform-specific code
- Enhance error handling and validation

### Medium-term (Quality)
- Address $mediumCount medium priority items
- Improve code documentation
- Add comprehensive error messages

### Long-term (Polish)
- Complete $lowCount low priority items
- Optimize performance
- Add advanced features

## Re-scan Instructions

After addressing issues, re-run the scan:

\`\`\`bash
# From project root
kotlin tools/scanning/SimpleProductionScanner.kt
\`\`\`

---
*Generated by Materia Simple Production Scanner*
*Ensuring zero placeholder patterns for production deployment*
        """.trimIndent()
    }

    private fun generateDataFile(placeholders: List<PlaceholderMatch>, timestamp: String): String {
        return buildString {
            appendLine("# Materia Production Scan Data")
            appendLine("# Generated: $timestamp")
            appendLine("# Total placeholders: ${placeholders.size}")
            appendLine("")

            for (placeholder in placeholders) {
                appendLine("FILE: ${placeholder.filePath}")
                appendLine("LINE: ${placeholder.lineNumber}")
                appendLine("PATTERN: ${placeholder.pattern}")
                appendLine("MODULE: ${placeholder.module}")
                appendLine("PLATFORM: ${placeholder.platform ?: "common"}")
                appendLine("CRITICALITY: ${placeholder.criticality}")
                appendLine("CONTEXT: ${placeholder.context}")
                appendLine("---")
            }
        }
    }
}

// Main execution
suspend fun main() {
    val projectRoot = System.getProperty("user.dir")

    println("🚀 Materia Simple Production Scanner")
    println("===================================")

    val scanner = SimpleScanner()
    val summary = scanner.scanProject(projectRoot)

    println("\n" + "=".repeat(60))
    println("📊 SCAN RESULTS SUMMARY")
    println("=".repeat(60))
    println("📁 Files scanned: ${summary.totalFiles}")
    println("🔍 Placeholders found: ${summary.totalPlaceholders}")
    println("🔴 Critical issues: ${summary.criticalPlaceholders}")
    println("🟠 High priority: ${summary.highPlaceholders}")

    if (summary.criticalPlaceholders == 0) {
        println("\n🎉 PRODUCTION READY!")
        println("✅ Zero critical placeholders found")
        println("🚀 Ready for deployment")
    } else {
        println("\n⚠️ NOT PRODUCTION READY")
        println("❌ ${summary.criticalPlaceholders} critical issues found")
        println("🚫 Fix critical issues before deployment")
    }

    println("\n📦 Top modules with placeholders:")
    summary.moduleBreakdown.toList().sortedByDescending { it.second }.take(5).forEach { (module, count) ->
        println("   - $module: $count placeholders")
    }

    println("\n📋 Next steps:")
    if (summary.criticalPlaceholders > 0) {
        println("   1. Review SIMPLE_SCAN_REPORT.md for critical issues")
        println("   2. Fix critical placeholders in renderer and core modules")
        println("   3. Address JavaScript black screen issues")
        println("   4. Re-run scan after fixes")
    } else {
        println("   1. Address high priority placeholders")
        println("   2. Run integration tests")
        println("   3. Deploy to staging environment")
    }

    println("\n📊 Detailed report saved to docs/private/scan-results/")
}

// For standalone execution
runBlocking {
    main()
}