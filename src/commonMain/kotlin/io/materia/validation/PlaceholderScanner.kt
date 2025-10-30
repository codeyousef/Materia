package io.materia.validation

/**
 * Contract interface for detecting placeholder patterns in source code.
 *
 * This interface defines the contract for scanning the Materia codebase
 * to identify placeholder patterns that need to be replaced with
 * production-ready implementations.
 */
interface PlaceholderScanner {

    /**
     * Scans the specified directory for placeholder patterns.
     *
     * @param rootPath The root directory to scan
     * @param filePatterns File patterns to include (e.g., "*.kt", "*.md")
     * @param excludePatterns Patterns to exclude from scanning
     * @return ScanResult containing all detected placeholders
     */
    suspend fun scanDirectory(
        rootPath: String,
        filePatterns: List<String> = listOf("*.kt", "*.md", "*.gradle.kts"),
        excludePatterns: List<String> = listOf("**/build/**", "**/node_modules/**")
    ): ScanResult

    /**
     * Scans a single file for placeholder patterns.
     *
     * @param filePath Path to file to scan
     * @return List of PlaceholderInstance found in the file
     */
    suspend fun scanFile(filePath: String): List<PlaceholderInstance>

    /**
     * Gets the list of placeholder patterns this scanner detects.
     *
     * @return List of regex patterns used for detection
     */
    fun getDetectionPatterns(): List<String>

    /**
     * Validates that a potential placeholder is actually a placeholder
     * and not a false positive (e.g., in documentation about placeholders).
     *
     * @param instance The potential placeholder instance
     * @param fileContent Full file content for context analysis
     * @return True if this is a genuine placeholder to be replaced
     */
    fun validatePlaceholder(instance: PlaceholderInstance, fileContent: String): Boolean

    /**
     * Estimates the effort required to replace a placeholder.
     *
     * @param instance The placeholder instance
     * @param fileContent Full file content for context analysis
     * @return Estimated effort level for replacement
     */
    fun estimateReplacementEffort(instance: PlaceholderInstance, fileContent: String): EffortLevel
}
