package io.materia.validation.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

actual object FileScannerFactory {
    actual fun createFileScanner(): FileScanner = AndroidFileScanner()
}

private class AndroidFileScanner : FileScanner {
    private val dispatcher = Dispatchers.IO

    override suspend fun scanDirectory(
        path: String,
        pattern: String?,
        recursive: Boolean
    ): Flow<FileInfo> = flow {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return@flow

        val matcher = pattern?.let(::globToRegex)
        val files = if (recursive) {
            root.walkTopDown().filter { it.isFile }
        } else {
            root.listFiles()?.asSequence()?.filter { it.isFile } ?: emptySequence()
        }

        for (file in files) {
            if (matcher == null || matcher.matches(file.name)) {
                createFileInfo(file)?.let { emit(it) }
            }
        }
    }.flowOn(dispatcher)

    override suspend fun findFiles(baseDir: String, patterns: List<String>): List<FileInfo> =
        withContext(dispatcher) {
            val root = File(baseDir)
            if (!root.exists() || !root.isDirectory) return@withContext emptyList()

            val matchers = patterns.map(::globToRegex)
            root.walkTopDown()
                .filter { it.isFile }
                .filter { file ->
                    matchers.isEmpty() || matchers.any { regex -> regex.matches(file.name) }
                }
                .mapNotNull(::createFileInfo)
                .toList()
        }

    override suspend fun getFileInfo(path: String): FileInfo? = withContext(dispatcher) {
        createFileInfo(File(path))
    }

    override suspend fun readFileContent(path: String): String? = withContext(dispatcher) {
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            null
        } else {
            runCatching { file.readText() }.getOrNull()
        }
    }

    override suspend fun writeFileContent(path: String, content: String): Boolean =
        withContext(dispatcher) {
            val file = File(path)
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(content)
            }.isSuccess
        }

    override suspend fun exists(path: String): Boolean = withContext(dispatcher) {
        File(path).exists()
    }

    override suspend fun delete(path: String): Boolean = withContext(dispatcher) {
        val file = File(path)
        file.exists() && runCatching { file.delete() }.getOrElse { false }
    }

    override suspend fun createDirectory(path: String): Boolean = withContext(dispatcher) {
        val dir = File(path)
        if (dir.exists()) dir.isDirectory else dir.mkdirs()
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean =
        withContext(dispatcher) {
            val source = File(sourcePath)
            val destination = File(destinationPath)
            if (!source.exists() || !source.isFile) return@withContext false

            runCatching {
                destination.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.isSuccess
        }

    override suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean =
        withContext(dispatcher) {
            val source = File(sourcePath)
            if (!source.exists()) return@withContext false

            val destination = File(destinationPath)
            destination.parentFile?.mkdirs()
            if (source.renameTo(destination)) {
                true
            } else {
                copyFile(sourcePath, destinationPath) && source.delete()
            }
        }

    private fun createFileInfo(file: File): FileInfo? {
        if (!file.exists()) return null

        val canonical = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        val size = if (canonical.isFile) canonical.length() else 0L
        val lastModified = canonical.lastModified()

        return FileInfo(
            path = canonical.absolutePath,
            name = canonical.name,
            extension = canonical.extension.takeIf { it.isNotEmpty() },
            size = size,
            lastModified = lastModified,
            isDirectory = canonical.isDirectory,
            isFile = canonical.isFile,
            isReadable = canonical.canRead(),
            isWritable = canonical.canWrite()
        )
    }
}

private fun globToRegex(pattern: String): Regex {
    val escaped = buildString {
        pattern.forEach { char ->
            when (char) {
                '*' -> append(".*")
                '?' -> append('.')
                else -> append(Regex.escape(char.toString()))
            }
        }
    }
    return Regex("^$escaped$", RegexOption.IGNORE_CASE)
}
