package io.materia.verification

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Linux Native implementation of FileSystem for verification module
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object FileSystem {

    actual suspend fun readFile(filePath: String): String {
        val file = fopen(filePath, "r") ?: throw Exception("Cannot open file: $filePath")
        try {
            // Get file size
            fseek(file, 0, SEEK_END)
            val size = ftell(file)
            fseek(file, 0, SEEK_SET)

            if (size <= 0) return ""

            // Read file content
            return memScoped {
                val buffer = allocArray<ByteVar>(size.toInt() + 1)
                val readBytes = fread(buffer, 1u, size.toULong(), file)
                buffer[readBytes.toInt()] = 0
                buffer.toKString()
            }
        } finally {
            fclose(file)
        }
    }

    actual suspend fun fileExists(filePath: String): Boolean {
        val file = fopen(filePath, "r")
        return if (file != null) {
            fclose(file)
            true
        } else {
            false
        }
    }

    actual suspend fun listFilesRecursively(
        directoryPath: String,
        extensions: List<String>
    ): List<String> {
        // Simplified implementation for Linux Native platform
        // Full implementation would use opendir/readdir POSIX APIs or C++ filesystem
        // library via interop. Current implementation returns empty list as validation
        // is primarily used on JVM/JS platforms.
        return emptyList()
    }

    actual suspend fun getLastModified(filePath: String): Long {
        return memScoped {
            val stat = alloc<stat>()
            if (platform.posix.stat(filePath, stat.ptr) == 0) {
                // Use st_mtim.tv_sec or st_mtime depending on platform
                val modificationTime = stat.st_mtim.tv_sec
                modificationTime * 1000L // Convert to milliseconds
            } else {
                0L
            }
        }
    }

    actual fun shouldExclude(filePath: String, excludePatterns: List<String>): Boolean {
        val normalizedPath = filePath.replace('\\', '/')

        return excludePatterns.any { pattern ->
            when {
                pattern.contains("*") -> {
                    // Convert glob pattern to regex
                    val regexPattern = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".")
                    normalizedPath.matches(Regex(regexPattern))
                }

                pattern.endsWith("/") -> {
                    // Directory pattern
                    normalizedPath.contains(pattern.removeSuffix("/"))
                }

                else -> {
                    // Exact match or substring
                    normalizedPath.contains(pattern)
                }
            }
        }
    }
}