package io.materia.validation.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * JavaScript implementation of the file scanner factory.
 */
actual object FileScannerFactory {
    actual fun createFileScanner(): FileScanner {
        return JsFileScanner()
    }
}

/**
 * Basic JavaScript file scanner implementation.
 * Limited by browser security constraints.
 */
class JsFileScanner : FileScanner {

    override suspend fun scanDirectory(
        path: String,
        pattern: String?,
        recursive: Boolean
    ): Flow<FileInfo> {
        // Browser environment has limited file system access
        return emptyFlow()
    }

    override suspend fun findFiles(
        baseDir: String,
        patterns: List<String>
    ): List<FileInfo> {
        // Limited file system access in browser
        return emptyList()
    }

    override suspend fun getFileInfo(path: String): FileInfo? {
        // Browser cannot directly access file system
        return null
    }

    override suspend fun readFileContent(path: String): String? {
        // Browser cannot read arbitrary files
        return null
    }

    override suspend fun writeFileContent(path: String, content: String): Boolean {
        // Browser cannot write arbitrary files
        return false
    }

    override suspend fun exists(path: String): Boolean {
        // Browser cannot check file existence
        return false
    }

    override suspend fun delete(path: String): Boolean {
        // Browser cannot delete files
        return false
    }

    override suspend fun createDirectory(path: String): Boolean {
        // Browser cannot create directories
        return false
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        // Browser cannot copy files
        return false
    }

    override suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        // Browser cannot move files
        return false
    }
}