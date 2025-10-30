package io.materia.verification

/**
 * Cross-platform file system operations for verification module
 * Uses expect/actual pattern to provide platform-specific implementations
 */
expect object FileSystem {
    /**
     * Reads the content of a file
     * @param filePath Path to the file to read
     * @return File content as string
     * @throws FileNotFoundException if file doesn't exist
     * @throws SecurityException if file cannot be read
     */
    suspend fun readFile(filePath: String): String

    /**
     * Checks if a file exists
     * @param filePath Path to check
     * @return true if file exists, false otherwise
     */
    suspend fun fileExists(filePath: String): Boolean

    /**
     * Lists all files in a directory recursively
     * @param directoryPath Path to directory
     * @param extensions File extensions to include (e.g., listOf("kt", "java"))
     * @return List of file paths
     */
    suspend fun listFilesRecursively(
        directoryPath: String,
        extensions: List<String> = listOf("kt")
    ): List<String>

    /**
     * Gets the last modified time of a file
     * @param filePath Path to file
     * @return Last modified timestamp as epoch milliseconds
     */
    suspend fun getLastModified(filePath: String): Long

    /**
     * Checks if a path matches any of the exclude patterns
     * @param filePath Path to check
     * @param excludePatterns List of patterns to exclude (supports wildcards)
     * @return true if path should be excluded
     */
    fun shouldExclude(filePath: String, excludePatterns: List<String>): Boolean
}