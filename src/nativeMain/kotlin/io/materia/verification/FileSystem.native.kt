package io.materia.verification

import okio.FileSystem as OkioFileSystem
import okio.Path.Companion.toPath

actual object FileSystem {

    actual suspend fun readFile(filePath: String): String {
        val path = filePath.toPath()
        if (!OkioFileSystem.SYSTEM.exists(path)) {
            throw Exception("Cannot open file: $filePath")
        }
        return OkioFileSystem.SYSTEM.read(path) {
            readUtf8()
        }
    }

    actual suspend fun fileExists(filePath: String): Boolean {
        return OkioFileSystem.SYSTEM.exists(filePath.toPath())
    }

    actual suspend fun listFilesRecursively(
        directoryPath: String,
        extensions: List<String>
    ): List<String> {
        return emptyList()
    }

    actual suspend fun getLastModified(filePath: String): Long {
        return OkioFileSystem.SYSTEM.metadataOrNull(filePath.toPath())?.lastModifiedAtMillis ?: 0L
    }

    actual fun shouldExclude(filePath: String, excludePatterns: List<String>): Boolean {
        val normalizedPath = filePath.replace('\\', '/')

        return excludePatterns.any { pattern ->
            when {
                pattern.contains("*") -> {
                    val regexPattern = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".")
                    normalizedPath.matches(Regex(regexPattern))
                }

                pattern.endsWith("/") -> {
                    normalizedPath.contains(pattern.removeSuffix("/"))
                }

                else -> {
                    normalizedPath.contains(pattern)
                }
            }
        }
    }
}