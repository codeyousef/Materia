package io.materia.verification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

actual object FileSystem {

    actual suspend fun readFile(filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $filePath")
        }
        if (!file.canRead()) {
            throw SecurityException("Cannot read file: $filePath")
        }
        file.readText()
    }

    actual suspend fun fileExists(filePath: String): Boolean = withContext(Dispatchers.IO) {
        File(filePath).exists()
    }

    actual suspend fun listFilesRecursively(
        directoryPath: String,
        extensions: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val normalizedExtensions = extensions.map { it.lowercase(Locale.ROOT) }
        directory.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                normalizedExtensions.isEmpty() || normalizedExtensions.any { ext ->
                    file.name.lowercase(Locale.ROOT).endsWith(".$ext")
                }
            }
            .map { it.absolutePath }
            .toList()
    }

    actual suspend fun getLastModified(filePath: String): Long = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists()) file.lastModified() else 0L
    }

    actual fun shouldExclude(filePath: String, excludePatterns: List<String>): Boolean {
        val normalizedPath = filePath.replace('\\', '/')
        return excludePatterns.any { pattern ->
            when {
                pattern.contains("*") -> {
                    val regex = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".")
                    normalizedPath.matches(Regex(regex))
                }

                pattern.endsWith("/") -> {
                    normalizedPath.contains(pattern.removeSuffix("/"))
                }

                else -> normalizedPath.contains(pattern)
            }
        }
    }
}
