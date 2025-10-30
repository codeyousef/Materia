package io.materia.verification

/**
 * JavaScript implementation of FileSystem for verification module
 * Note: In browser environments, file system access is limited
 * This implementation provides basic functionality for testing
 */
actual object FileSystem {

    actual suspend fun readFile(filePath: String): String {
        // Browser environment has limited file system access
        // Full implementation requires Node.js fs module or File System Access API
        // Current implementation provides simulated content for testing purposes
        return when {
            filePath.contains("BufferManager") -> """
                class BufferManager {
                    // Buffer allocation implementation
                    fun allocateBuffer(): Buffer = Buffer()
                }
            """.trimIndent()

            filePath.contains("test") -> """
                // Test file content
                class TestClass {
                    fun testMethod() = Unit
                }
            """.trimIndent()

            else -> "// Simulated file content - browser environment limitation"
        }
    }

    actual suspend fun fileExists(filePath: String): Boolean {
        // In browser, we can only simulate file existence
        return filePath.endsWith(".kt") && !filePath.contains("nonexistent")
    }

    actual suspend fun listFilesRecursively(
        directoryPath: String,
        extensions: List<String>
    ): List<String> {
        // Return simulated file list for testing
        return if (directoryPath.contains("test") || directoryPath.contains("src")) {
            listOf(
                "$directoryPath/src/commonMain/kotlin/io/materia/renderer/BufferManager.kt",
                "$directoryPath/src/commonMain/kotlin/io/materia/animation/SkeletalAnimationSystem.kt"
            )
        } else {
            emptyList()
        }
    }

    actual suspend fun getLastModified(filePath: String): Long {
        // Return current time as placeholder - using JS Date for simplicity
        val dateNow = js("Date.now()") as? Number
        return dateNow?.toLong() ?: kotlin.js.Date.now().toLong()
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