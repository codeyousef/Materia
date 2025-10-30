import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Simple Java-based Production Readiness Scanner
 *
 * Scans the Materia codebase for placeholder patterns that need to be replaced
 * with production-ready implementations.
 */
public class ProductionScanner {

    private static final String[] PLACEHOLDER_PATTERNS = {
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
        "(?i)\\breturn\\s+true\\s*//.*stub"
    };

    private static final Set<String> CRITICAL_MODULES = Set.of("core", "renderer", "scene", "geometry", "material");

    private static final String[] EXCLUDE_PATTERNS = {
        "**/build/**",
        "**/node_modules/**",
        "**/.git/**",
        "**/.gradle/**",
        "**/tools/scanning/**"
    };

    public static void main(String[] args) throws IOException {
        String projectRoot = args.length > 0 ? args[0] : System.getProperty("user.dir");

        System.out.println("🚀 Materia Production Readiness Scanner");
        System.out.println("======================================");
        System.out.println("📁 Project root: " + projectRoot);
        System.out.println();

        ProductionScanner scanner = new ProductionScanner();
        ScanResult result = scanner.scanProject(new File(projectRoot));

        scanner.generateReport(result, new File(projectRoot, "docs/private/scan-results"));
    }

    public ScanResult scanProject(File projectRoot) throws IOException {
        System.out.println("🔍 Scanning for placeholder patterns...");

        long startTime = System.currentTimeMillis();
        List<PlaceholderMatch> placeholders = new ArrayList<>();

        // Find all Kotlin files
        List<File> kotlinFiles = findKotlinFiles(projectRoot);
        System.out.println("📊 Found " + kotlinFiles.size() + " files to scan");

        // Scan each file
        for (int i = 0; i < kotlinFiles.size(); i++) {
            if (i % 50 == 0) {
                System.out.println("🔍 Scanning file " + (i + 1) + "/" + kotlinFiles.size());
            }

            File file = kotlinFiles.get(i);
            List<PlaceholderMatch> fileMatches = scanFile(file);
            placeholders.addAll(fileMatches);
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ Scan completed in " + duration + "ms");
        System.out.println("🔍 Found " + placeholders.size() + " placeholder instances");

        return new ScanResult(kotlinFiles.size(), placeholders, duration);
    }

    private List<File> findKotlinFiles(File dir) {
        List<File> files = new ArrayList<>();
        collectKotlinFiles(dir, files);
        return files;
    }

    private void collectKotlinFiles(File current, List<File> files) {
        if (!current.exists()) return;

        // Check if path should be excluded
        String pathStr = current.getAbsolutePath().replace("\\", "/");
        for (String excludePattern : EXCLUDE_PATTERNS) {
            String pattern = excludePattern.replace("**", ".*").replace("*", "[^/]*");
            if (Pattern.compile(pattern).matcher(pathStr).find()) {
                return;
            }
        }

        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectKotlinFiles(child, files);
                }
            }
        } else if (current.getName().endsWith(".kt") || current.getName().endsWith(".md")) {
            files.add(current);
        }
    }

    private List<PlaceholderMatch> scanFile(File file) {
        List<PlaceholderMatch> matches = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            String module = extractModule(file.getAbsolutePath());
            String platform = extractPlatform(file.getAbsolutePath());

            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex);

                for (String patternStr : PLACEHOLDER_PATTERNS) {
                    Pattern pattern = Pattern.compile(patternStr);
                    Matcher matcher = pattern.matcher(line);

                    while (matcher.find()) {
                        // Skip false positives
                        if (isDocumentation(line, lines, lineIndex)) continue;

                        String criticality = assessCriticality(module, line, matcher.group());
                        String context = extractContext(lines, lineIndex);

                        matches.add(new PlaceholderMatch(
                            file.getAbsolutePath(),
                            lineIndex + 1,
                            matcher.group(),
                            context,
                            module,
                            platform,
                            criticality
                        ));
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("⚠️ Error scanning " + file.getAbsolutePath() + ": " + e.getMessage());
        }

        return matches;
    }

    private String extractModule(String filePath) {
        String path = filePath.replace("\\", "/");
        if (path.contains("/core/")) return "core";
        if (path.contains("/renderer/")) return "renderer";
        if (path.contains("/scene/")) return "scene";
        if (path.contains("/geometry/")) return "geometry";
        if (path.contains("/material/")) return "material";
        if (path.contains("/animation/")) return "animation";
        if (path.contains("/loader/")) return "loader";
        if (path.contains("/controls/")) return "controls";
        if (path.contains("/physics/")) return "physics";
        if (path.contains("/xr/")) return "xr";
        if (path.contains("/postprocess/")) return "postprocess";
        if (path.contains("/tools/")) return "tools";
        if (path.contains("/examples/")) return "examples";
        if (path.contains("Test")) return "test";
        return "common";
    }

    private String extractPlatform(String filePath) {
        if (filePath.contains("/jvmMain/")) return "jvm";
        if (filePath.contains("/jsMain/")) return "js";
        if (filePath.contains("/androidMain/")) return "android";
        if (filePath.contains("/iosMain/")) return "ios";
        if (filePath.contains("/linuxX64Main/")) return "linuxX64";
        if (filePath.contains("/nativeMain/")) return "native";
        if (filePath.contains("/commonMain/")) return null;
        return null;
    }

    private boolean isDocumentation(String line, List<String> lines, int lineIndex) {
        String context = extractContext(lines, lineIndex).toLowerCase();

        return context.contains("example") ||
               context.contains("sample") ||
               context.contains("tutorial") ||
               context.contains("according to") ||
               context.contains("should test") ||
               context.contains("when implemented");
    }

    private String assessCriticality(String module, String line, String pattern) {
        String lineLower = line.toLowerCase();
        String patternLower = pattern.toLowerCase();

        // Critical indicators
        if (lineLower.contains("render") ||
            lineLower.contains("shader") ||
            lineLower.contains("gpu") ||
            lineLower.contains("crash") ||
            lineLower.contains("security") ||
            patternLower.equals("fixme")) {
            return "CRITICAL";
        }

        // High priority for critical modules
        if (CRITICAL_MODULES.contains(module)) {
            if (lineLower.contains("init") || lineLower.contains("create")) {
                return "HIGH";
            }
            return "MEDIUM";
        }

        return "LOW";
    }

    private String extractContext(List<String> lines, int lineIndex) {
        int start = Math.max(0, lineIndex - 1);
        int end = Math.min(lines.size() - 1, lineIndex + 1);
        return lines.subList(start, end + 1).stream()
                   .collect(Collectors.joining("\\n"));
    }

    private void generateReport(ScanResult result, File outputDir) throws IOException {
        outputDir.mkdirs();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Count by criticality
        long criticalCount = result.placeholders.stream().mapToLong(p -> "CRITICAL".equals(p.criticality) ? 1 : 0).sum();
        long highCount = result.placeholders.stream().mapToLong(p -> "HIGH".equals(p.criticality) ? 1 : 0).sum();
        long mediumCount = result.placeholders.stream().mapToLong(p -> "MEDIUM".equals(p.criticality) ? 1 : 0).sum();
        long lowCount = result.placeholders.stream().mapToLong(p -> "LOW".equals(p.criticality) ? 1 : 0).sum();

        // Module breakdown
        Map<String, Long> moduleBreakdown = result.placeholders.stream()
            .collect(Collectors.groupingBy(p -> p.module, Collectors.counting()));

        // Platform breakdown
        Map<String, Long> platformBreakdown = result.placeholders.stream()
            .collect(Collectors.groupingBy(p -> p.platform != null ? p.platform : "common", Collectors.counting()));

        // Generate report
        File reportFile = new File(outputDir, "PRODUCTION_SCAN_REPORT.md");
        StringBuilder report = new StringBuilder();

        report.append("# Materia Production Readiness Scan Report\\n");
        report.append("Generated: ").append(timestamp).append("\\n\\n");

        report.append("## Executive Summary\\n");
        report.append("- **Total files scanned:** ").append(result.totalFiles).append("\\n");
        report.append("- **Total placeholder instances:** ").append(result.placeholders.size()).append("\\n");
        report.append("- **Scan duration:** ").append(result.scanDurationMs).append("ms\\n");
        report.append("- **Production ready:** ").append(criticalCount == 0 ? "✅ YES" : "❌ NO").append("\\n\\n");

        report.append("## Criticality Breakdown\\n");
        report.append("- 🔴 **CRITICAL:** ").append(criticalCount).append(" placeholders (blocks deployment)\\n");
        report.append("- 🟠 **HIGH:** ").append(highCount).append(" placeholders (important features)\\n");
        report.append("- 🟡 **MEDIUM:** ").append(mediumCount).append(" placeholders (nice-to-have)\\n");
        report.append("- 🟢 **LOW:** ").append(lowCount).append(" placeholders (optional)\\n\\n");

        report.append("## Module Breakdown\\n");
        moduleBreakdown.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> report.append("- **").append(entry.getKey()).append(":** ").append(entry.getValue()).append(" placeholders\\n"));
        report.append("\\n");

        report.append("## Platform Breakdown\\n");
        platformBreakdown.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> report.append("- **").append(entry.getKey()).append(":** ").append(entry.getValue()).append(" placeholders\\n"));
        report.append("\\n");

        // Critical issues
        List<PlaceholderMatch> criticalIssues = result.placeholders.stream()
            .filter(p -> "CRITICAL".equals(p.criticality))
            .limit(20)
            .collect(Collectors.toList());

        report.append("## Critical Issues (Immediate Action Required)\\n");
        if (criticalIssues.isEmpty()) {
            report.append("✅ No critical issues found!\\n\\n");
        } else {
            for (PlaceholderMatch issue : criticalIssues) {
                report.append("### ").append(issue.filePath).append(":").append(issue.lineNumber).append("\\n");
                report.append("- **Pattern:** ").append(issue.pattern).append("\\n");
                report.append("- **Module:** ").append(issue.module).append("\\n");
                report.append("- **Platform:** ").append(issue.platform != null ? issue.platform : "common").append("\\n");
                report.append("- **Context:** ").append(issue.context.replace("\\n", " ")).append("\\n\\n");
            }
        }

        // Assessment
        if (criticalCount == 0) {
            report.append("## Production Readiness Assessment\\n\\n");
            report.append("🎉 **PRODUCTION READY!**\\n\\n");
            report.append("✅ Zero critical placeholders found\\n");
            report.append("✅ Core functionality appears complete\\n");
            report.append("🚀 Ready for staging deployment\\n\\n");

            report.append("### Recommended Next Steps:\\n");
            report.append("1. Address high priority placeholders for enhanced functionality\\n");
            report.append("2. Run integration tests to verify behavior\\n");
            report.append("3. Monitor JavaScript renderer performance\\n");
            report.append("4. Deploy to staging environment\\n\\n");
        } else {
            report.append("## Production Readiness Assessment\\n\\n");
            report.append("⚠️ **NOT PRODUCTION READY**\\n\\n");
            report.append("❌ ").append(criticalCount).append(" critical placeholders must be fixed\\n");
            report.append("🚫 Deployment blocked until critical issues resolved\\n\\n");

            report.append("### Required Actions:\\n");
            report.append("1. Fix all CRITICAL placeholders immediately\\n");
            report.append("2. Focus on renderer and core modules\\n");
            report.append("3. Address JavaScript black screen issues\\n");
            report.append("4. Re-run scan after fixes applied\\n\\n");

            Set<String> criticalModules = criticalIssues.stream()
                .map(p -> p.module)
                .collect(Collectors.toSet());
            report.append("### Critical Modules Needing Attention:\\n");
            report.append(String.join(", ", criticalModules)).append("\\n\\n");
        }

        report.append("---\\n");
        report.append("*Generated by Materia Production Scanner*\\n");
        report.append("*Ensuring zero placeholder patterns for production deployment*\\n");

        Files.write(reportFile.toPath(), report.toString().getBytes());

        // Save raw data
        File dataFile = new File(outputDir, "PRODUCTION_SCAN_DATA.txt");
        StringBuilder data = new StringBuilder();
        data.append("# Materia Production Scan Data\\n");
        data.append("# Generated: ").append(timestamp).append("\\n");
        data.append("# Total placeholders: ").append(result.placeholders.size()).append("\\n\\n");

        for (PlaceholderMatch placeholder : result.placeholders) {
            data.append("FILE: ").append(placeholder.filePath).append("\\n");
            data.append("LINE: ").append(placeholder.lineNumber).append("\\n");
            data.append("PATTERN: ").append(placeholder.pattern).append("\\n");
            data.append("MODULE: ").append(placeholder.module).append("\\n");
            data.append("PLATFORM: ").append(placeholder.platform != null ? placeholder.platform : "common").append("\\n");
            data.append("CRITICALITY: ").append(placeholder.criticality).append("\\n");
            data.append("CONTEXT: ").append(placeholder.context).append("\\n");
            data.append("---\\n");
        }

        Files.write(dataFile.toPath(), data.toString().getBytes());

        // Print summary
        System.out.println("\\n" + "=".repeat(60));
        System.out.println("📊 SCAN RESULTS SUMMARY");
        System.out.println("=".repeat(60));
        System.out.println("📁 Files scanned: " + result.totalFiles);
        System.out.println("🔍 Placeholders found: " + result.placeholders.size());
        System.out.println("🔴 Critical issues: " + criticalCount);
        System.out.println("🟠 High priority: " + highCount);

        if (criticalCount == 0) {
            System.out.println("\\n🎉 PRODUCTION READY!");
            System.out.println("✅ Zero critical placeholders found");
            System.out.println("🚀 Ready for deployment");
        } else {
            System.out.println("\\n⚠️ NOT PRODUCTION READY");
            System.out.println("❌ " + criticalCount + " critical issues found");
            System.out.println("🚫 Fix critical issues before deployment");
        }

        System.out.println("\\n📦 Top modules with placeholders:");
        moduleBreakdown.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> System.out.println("   - " + entry.getKey() + ": " + entry.getValue() + " placeholders"));

        System.out.println("\\n📊 Reports saved to: " + outputDir.getAbsolutePath());
        System.out.println("📋 Report: " + reportFile.getName());
        System.out.println("💾 Data: " + dataFile.getName());
    }

    // Data classes
    static class ScanResult {
        final int totalFiles;
        final List<PlaceholderMatch> placeholders;
        final long scanDurationMs;

        ScanResult(int totalFiles, List<PlaceholderMatch> placeholders, long scanDurationMs) {
            this.totalFiles = totalFiles;
            this.placeholders = placeholders;
            this.scanDurationMs = scanDurationMs;
        }
    }

    static class PlaceholderMatch {
        final String filePath;
        final int lineNumber;
        final String pattern;
        final String context;
        final String module;
        final String platform;
        final String criticality;

        PlaceholderMatch(String filePath, int lineNumber, String pattern, String context,
                        String module, String platform, String criticality) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.pattern = pattern;
            this.context = context;
            this.module = module;
            this.platform = platform;
            this.criticality = criticality;
        }
    }
}