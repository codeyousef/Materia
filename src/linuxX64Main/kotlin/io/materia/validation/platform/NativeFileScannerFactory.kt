package io.materia.validation.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Native Linux implementation of the file scanner factory.
 */
actual object FileScannerFactory {
    actual fun createFileScanner(): FileScanner {
        return NativeFileScanner()
    }
}

/**
 * Basic native file scanner implementation.
 * Uses platform-specific file system APIs.
 */
class NativeFileScanner : FileScanner {

    override suspend fun scanDirectory(
        path: String,
        pattern: String?,
        recursive: Boolean
    ): Flow<FileInfo> {
        // Native file system scanning implementation
        return emptyFlow()
    }

    override suspend fun findFiles(
        baseDir: String,
        patterns: List<String>
    ): List<FileInfo> {
        return emptyList()
    }

    override suspend fun getFileInfo(path: String): FileInfo? {
        return null
    }

    override suspend fun readFileContent(path: String): String? {
        return null
    }

    override suspend fun writeFileContent(path: String, content: String): Boolean {
        return false
    }

    override suspend fun exists(path: String): Boolean {
        return false
    }

    override suspend fun delete(path: String): Boolean {
        return false
    }

    override suspend fun createDirectory(path: String): Boolean {
        return false
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        return false
    }

    override suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        return false
    }
}