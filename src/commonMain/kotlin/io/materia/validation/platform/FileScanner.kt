package io.materia.validation.platform

import kotlinx.coroutines.flow.Flow

/**
 * Cross-platform file system scanning interface.
 *
 * Provides unified file system operations across JVM, JS, Native, and mobile platforms
 * with appropriate fallbacks for platform limitations (e.g., browser security restrictions).
 */
interface FileScanner {

    /**
     * Scans a directory for files matching the given pattern.
     *
     * @param path Directory path to scan
     * @param pattern Optional glob pattern to match files (null = all files)
     * @param recursive Whether to scan subdirectories recursively
     * @return Flow of matching FileInfo objects
     */
    suspend fun scanDirectory(
        path: String,
        pattern: String? = null,
        recursive: Boolean = true
    ): Flow<FileInfo>

    /**
     * Finds files matching multiple patterns in a base directory.
     *
     * @param baseDir Base directory to search
     * @param patterns List of patterns to match
     * @return List of matching FileInfo objects
     */
    suspend fun findFiles(
        baseDir: String,
        patterns: List<String>
    ): List<FileInfo>

    /**
     * Gets file information for a specific path.
     *
     * @param path File path to examine
     * @return FileInfo if file exists, null otherwise
     */
    suspend fun getFileInfo(path: String): FileInfo?

    /**
     * Reads the content of a file.
     *
     * @param path File path to read
     * @return File content as string, null if file doesn't exist or can't be read
     */
    suspend fun readFileContent(path: String): String?

    /**
     * Writes content to a file.
     *
     * @param path File path to write
     * @param content Content to write
     * @return true if successful, false otherwise
     */
    suspend fun writeFileContent(path: String, content: String): Boolean

    /**
     * Checks if a file or directory exists.
     *
     * @param path Path to check
     * @return true if exists, false otherwise
     */
    suspend fun exists(path: String): Boolean

    /**
     * Deletes a file or directory.
     *
     * @param path Path to delete
     * @return true if successful, false otherwise
     */
    suspend fun delete(path: String): Boolean

    /**
     * Creates a directory and any necessary parent directories.
     *
     * @param path Directory path to create
     * @return true if successful, false otherwise
     */
    suspend fun createDirectory(path: String): Boolean

    /**
     * Copies a file from source to destination.
     *
     * @param sourcePath Source file path
     * @param destinationPath Destination file path
     * @return true if successful, false otherwise
     */
    suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean

    /**
     * Moves a file from source to destination.
     *
     * @param sourcePath Source file path
     * @param destinationPath Destination file path
     * @return true if successful, false otherwise
     */
    suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean
}

/**
 * File information container for cross-platform file system operations.
 */
data class FileInfo(
    val path: String,
    val name: String,
    val extension: String?,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val mimeType: String? = null,
    val permissions: String? = null
) {
    val absolutePath: String get() = path
    val parent: String? get() = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
    val nameWithoutExtension: String get() = name.substringBeforeLast('.', name)
}

/**
 * Platform-specific file scanner factory.
 */
expect object FileScannerFactory {
    /**
     * Creates a platform-appropriate file scanner instance.
     */
    fun createFileScanner(): FileScanner
}