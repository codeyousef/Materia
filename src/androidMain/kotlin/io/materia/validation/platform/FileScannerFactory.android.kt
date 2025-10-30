package io.materia.validation.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.bufferedReader
import kotlin.io.path.extension

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
        val root = Paths.get(path)
        if (!Files.exists(root)) return@flow

        val matcher = pattern?.let { FileSystems.getDefault().getPathMatcher("glob:$it") }

        if (recursive) {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): java.nio.file.FileVisitResult {
                    if (matcher == null || matcher.matches(file.fileName)) {
                        runBlocking { emit(createFileInfo(file, attrs)) }
                    }
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            })
        } else {
            Files.newDirectoryStream(root, pattern ?: "*").use { stream ->
                stream.forEach { file ->
                    if (Files.isRegularFile(file)) {
                        emit(createFileInfo(file))
                    }
                }
            }
        }
    }.flowOn(dispatcher)

    override suspend fun findFiles(baseDir: String, patterns: List<String>): List<FileInfo> =
        withContext(dispatcher) {
            val dir = Paths.get(baseDir)
            if (!Files.exists(dir)) return@withContext emptyList<FileInfo>()

            val results = mutableListOf<FileInfo>()
            Files.walk(dir).use { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { path ->
                    if (patterns.isEmpty() || patterns.any { pattern ->
                            path.fileName.toString().matchesGlob(pattern)
                        }) {
                        createFileInfo(path)?.let { results += it }
                    }
                }
            }
            results
        }

    override suspend fun getFileInfo(path: String): FileInfo? = withContext(dispatcher) {
        val file = Paths.get(path)
        if (Files.exists(file)) createFileInfo(file) else null
    }

    override suspend fun readFileContent(path: String): String? = withContext(dispatcher) {
        val file = Paths.get(path)
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            null
        } else {
            file.bufferedReader().use { it.readText() }
        }
    }

    override suspend fun writeFileContent(path: String, content: String): Boolean =
        withContext(dispatcher) {
            try {
                val file = Paths.get(path)
                Files.createDirectories(file.parent)
                Files.write(file, content.toByteArray())
                true
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun exists(path: String): Boolean = withContext(dispatcher) {
        Files.exists(Paths.get(path))
    }

    override suspend fun delete(path: String): Boolean = withContext(dispatcher) {
        runCatching { Files.deleteIfExists(Paths.get(path)) }.getOrElse { false }
    }

    override suspend fun createDirectory(path: String): Boolean = withContext(dispatcher) {
        try {
            Files.createDirectories(Paths.get(path))
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean =
        withContext(dispatcher) {
            try {
                val source = Paths.get(sourcePath)
                val destination = Paths.get(destinationPath)
                Files.createDirectories(destination.parent)
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean =
        withContext(dispatcher) {
            try {
                val source = Paths.get(sourcePath)
                val destination = Paths.get(destinationPath)
                Files.createDirectories(destination.parent)
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun createFileInfo(path: Path, attrs: BasicFileAttributes? = null): FileInfo {
        val attributes = attrs ?: Files.readAttributes(path, BasicFileAttributes::class.java)
        return FileInfo(
            path = path.toAbsolutePath().toString(),
            name = path.fileName?.toString() ?: "",
            extension = path.extension.takeIf { it.isNotEmpty() },
            size = attributes.size(),
            lastModified = attributes.lastModifiedTime().toMillis(),
            isDirectory = attributes.isDirectory,
            isFile = attributes.isRegularFile,
            isReadable = Files.isReadable(path),
            isWritable = Files.isWritable(path)
        )
    }
}

private fun String.matchesGlob(pattern: String): Boolean {
    val regex = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".")
    return this.matches(Regex(regex))
}
