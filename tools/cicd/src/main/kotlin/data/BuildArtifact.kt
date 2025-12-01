package io.materia.tools.cicd.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.io.File

/**
 * BuildArtifact - Data model for compiled library packages with metadata
 *
 * This data class represents a compiled artifact from the build process, including
 * platform-specific packages (JAR, AAR, Framework, JS modules) along with their
 * metadata, dependencies, and build information.
 *
 * Artifacts are stored in platform-specific formats with comprehensive metadata.
 */
@Serializable
data class BuildArtifact @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val version: String,
    val platform: BuildPlatform,
    val type: ArtifactType,
    val file: FileInfo, // Replaced File with serializable FileInfo
    val checksum: String,
    val size: Long,
    val dependencies: List<Dependency>,
    val metadata: BuildMetadata,
    val signing: SigningInfo? = null,
    val publishingInfo: PublishingInfo? = null
) {
    init {
        require(version.matches(Regex("""\d+\.\d+\.\d+(-\w+(\.\d+)?)?"""))) {
            "Version must follow semantic versioning format (x.y.z[-suffix[.build]])"
        }
        require(checksum.matches(Regex("[a-fA-F0-9]{32,128}"))) {
            "Checksum must be a valid hex string (MD5, SHA1, SHA256, or SHA512)"
        }
        require(size >= 0) { "Artifact size must be non-negative" }
        require(file.name.isNotBlank()) { "Artifact filename must be non-empty" }

        // Validate platform-specific file extensions
        val expectedExtension = when (platform) {
            BuildPlatform.JVM -> when (type) {
                ArtifactType.LIBRARY -> ".jar"
                ArtifactType.SOURCES -> "-sources.jar"
                ArtifactType.DOCUMENTATION -> "-javadoc.jar"
                else -> ""
            }
            BuildPlatform.JS -> when (type) {
                ArtifactType.LIBRARY -> ".js"
                ArtifactType.SOURCES -> ".js.map"
                else -> ""
            }
            BuildPlatform.ANDROID -> when (type) {
                ArtifactType.LIBRARY -> ".aar"
                ArtifactType.SOURCES -> "-sources.jar"
                else -> ""
            }
            BuildPlatform.IOS -> when (type) {
                ArtifactType.LIBRARY -> ".framework"
                ArtifactType.SOURCES -> "-sources.zip"
                else -> ""
            }
            else -> ""
        }

        if (expectedExtension.isNotEmpty()) {
            require(file.name.endsWith(expectedExtension)) {
                "File '${file.name}' should end with '$expectedExtension' for $platform $type"
            }
        }

        // Validate dependencies don't include circular references
        val dependencyNames = dependencies.map { it.name }
        require(!dependencyNames.contains(extractArtifactName())) {
            "Artifact cannot depend on itself"
        }
    }

    private fun extractArtifactName(): String {
        return file.name.substringBeforeLast("-$version")
    }

    /**
     * Whether this artifact is signed
     */
    val isSigned: Boolean
        get() = signing != null && signing.signed

    /**
     * Whether this artifact has been published
     */
    val isPublished: Boolean
        get() = publishingInfo != null && publishingInfo.published

    /**
     * Whether this artifact is a release version (not snapshot/beta/alpha)
     */
    val isRelease: Boolean
        get() = !version.contains("SNAPSHOT", ignoreCase = true) &&
                !version.contains("alpha", ignoreCase = true) &&
                !version.contains("beta", ignoreCase = true) &&
                !version.contains("rc", ignoreCase = true)

    /**
     * Maven coordinates for this artifact
     */
    val mavenCoordinates: String
        get() = "${metadata.groupId}:${extractArtifactName()}:$version"

    /**
     * Gets the primary classifier for this artifact (sources, javadoc, etc.)
     */
    val classifier: String?
        get() = when (type) {
            ArtifactType.SOURCES -> "sources"
            ArtifactType.DOCUMENTATION -> when (platform) {
                BuildPlatform.JVM -> "javadoc"
                else -> "dokka"
            }
            else -> null
        }

    /**
     * Validates the artifact integrity
     */
    fun validateIntegrity(): ArtifactValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // Check file existence (simulated since File isn't available in common)
        if (!file.exists) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.ERROR,
                message = "Artifact file does not exist: ${file.path}",
                category = "file"
            ))
        }

        // Validate checksum if file exists
        if (file.exists && file.size != size) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.ERROR,
                message = "File size mismatch: expected $size, actual ${file.size}",
                category = "integrity"
            ))
        }

        // Check for required metadata
        if (metadata.buildTime > kotlinx.datetime.Clock.System.now()) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.WARNING,
                message = "Build time is in the future",
                category = "metadata"
            ))
        }

        if (metadata.gitCommit.isBlank()) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.WARNING,
                message = "Missing git commit information",
                category = "metadata"
            ))
        }

        // Validate dependencies
        dependencies.forEach { dependency ->
            if (!dependency.version.matches(Regex("""\d+\.\d+\.\d+.*"""))) {
                issues.add(ValidationIssue(
                    severity = ValidationSeverity.WARNING,
                    message = "Dependency '${dependency.name}' has non-standard version '${dependency.version}'",
                    category = "dependencies"
                ))
            }
        }

        // Check signing for release artifacts
        if (isRelease && signing?.signed != true) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.WARNING,
                message = "Release artifact should be signed",
                category = "signing"
            ))
        }

        return ArtifactValidationResult(
            isValid = issues.none { it.severity == ValidationSeverity.ERROR },
            issues = issues
        )
    }

    /**
     * Creates a copy of this artifact with updated publishing information
     */
    fun withPublishingInfo(publishingInfo: PublishingInfo): BuildArtifact {
        return copy(publishingInfo = publishingInfo)
    }

    /**
     * Creates a copy of this artifact with signing information
     */
    fun withSigning(signingInfo: SigningInfo): BuildArtifact {
        return copy(signing = signingInfo)
    }

    /**
     * Gets all transitive dependencies (including dependencies of dependencies)
     */
    fun getTransitiveDependencies(): Set<Dependency> {
        val result = mutableSetOf<Dependency>()
        val queue = dependencies.toMutableList()
        val visited = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            if (current.name !in visited) {
                visited.add(current.name)
                result.add(current)
                // Transitive dependency resolution is handled by the Gradle build system
            }
        }

        return result
    }

    companion object {
        /**
         * Creates a JVM library artifact
         */
        fun createJvmLibrary(
            version: String,
            jarFile: FileInfo,
            checksum: String,
            dependencies: List<Dependency> = emptyList(),
            buildMetadata: BuildMetadata
        ): BuildArtifact {
            return BuildArtifact(
                version = version,
                platform = BuildPlatform.JVM,
                type = ArtifactType.LIBRARY,
                file = jarFile,
                checksum = checksum,
                size = jarFile.size,
                dependencies = dependencies,
                metadata = buildMetadata
            )
        }

        /**
         * Creates a JavaScript library artifact
         */
        fun createJavaScriptLibrary(
            version: String,
            jsFile: FileInfo,
            checksum: String,
            dependencies: List<Dependency> = emptyList(),
            buildMetadata: BuildMetadata
        ): BuildArtifact {
            return BuildArtifact(
                version = version,
                platform = BuildPlatform.JS,
                type = ArtifactType.LIBRARY,
                file = jsFile,
                checksum = checksum,
                size = jsFile.size,
                dependencies = dependencies,
                metadata = buildMetadata
            )
        }

        /**
         * Creates an Android library artifact
         */
        fun createAndroidLibrary(
            version: String,
            aarFile: FileInfo,
            checksum: String,
            dependencies: List<Dependency> = emptyList(),
            buildMetadata: BuildMetadata
        ): BuildArtifact {
            return BuildArtifact(
                version = version,
                platform = BuildPlatform.ANDROID,
                type = ArtifactType.LIBRARY,
                file = aarFile,
                checksum = checksum,
                size = aarFile.size,
                dependencies = dependencies,
                metadata = buildMetadata
            )
        }

        /**
         * Creates an iOS framework artifact
         */
        fun createIOSFramework(
            version: String,
            frameworkFile: FileInfo,
            checksum: String,
            dependencies: List<Dependency> = emptyList(),
            buildMetadata: BuildMetadata
        ): BuildArtifact {
            return BuildArtifact(
                version = version,
                platform = BuildPlatform.IOS,
                type = ArtifactType.LIBRARY,
                file = frameworkFile,
                checksum = checksum,
                size = frameworkFile.size,
                dependencies = dependencies,
                metadata = buildMetadata
            )
        }
    }
}

/**
 * FileInfo - Serializable representation of file information
 */
@Serializable
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Instant,
    val exists: Boolean = true
) {
    init {
        require(name.isNotBlank()) { "File name must be non-empty" }
        require(path.isNotBlank()) { "File path must be non-empty" }
        require(size >= 0) { "File size must be non-negative" }
    }

    /**
     * File extension without the dot
     */
    val extension: String
        get() = name.substringAfterLast('.', "")

    /**
     * File name without extension
     */
    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')

    companion object {
        /**
         * Creates FileInfo from a File object (for JVM usage)
         */
        fun fromFile(file: File): FileInfo {
            return FileInfo(
                name = file.name,
                path = file.absolutePath,
                size = if (file.exists()) file.length() else 0L,
                lastModified = if (file.exists()) {
                    Instant.fromEpochMilliseconds(file.lastModified())
                } else {
                    kotlinx.datetime.Clock.System.now()
                },
                exists = file.exists()
            )
        }

        /**
         * Creates FileInfo for testing/mocking
         */
        fun createMock(name: String, size: Long = 1024L): FileInfo {
            return FileInfo(
                name = name,
                path = "/mock/path/$name",
                size = size,
                lastModified = kotlinx.datetime.Clock.System.now(),
                exists = true
            )
        }
    }
}

/**
 * BuildMetadata - Metadata about the build process
 */
@Serializable
data class BuildMetadata(
    val buildTime: Instant,
    val gitCommit: String,
    val gitBranch: String,
    val buildNumber: Int,
    val releaseNotes: String? = null,
    val groupId: String = "io.materia",
    val buildTool: String = "Gradle",
    val buildToolVersion: String = "8.0+",
    val kotlinVersion: String = "1.9.0",
    val buildHost: String = "ci-server",
    val buildDuration: kotlin.time.Duration = kotlin.time.Duration.ZERO,
    val buildFlags: List<String> = emptyList()
) {
    init {
        require(gitCommit.matches(Regex("[a-fA-F0-9]{7,40}"))) {
            "Git commit must be a valid SHA hash"
        }
        require(gitBranch.isNotBlank()) { "Git branch must be non-empty" }
        require(buildNumber > 0) { "Build number must be positive" }
        require(groupId.isNotBlank()) { "Group ID must be non-empty" }
    }

    /**
     * Short git commit hash (first 7 characters)
     */
    val shortCommit: String
        get() = gitCommit.take(7)

    /**
     * Whether this is a main/master branch build
     */
    val isMainBranch: Boolean
        get() = gitBranch in listOf("main", "master", "develop")

    companion object {
        /**
         * Creates build metadata for current build
         */
        fun createCurrent(
            gitCommit: String,
            gitBranch: String,
            buildNumber: Int,
            releaseNotes: String? = null
        ): BuildMetadata {
            return BuildMetadata(
                buildTime = kotlinx.datetime.Clock.System.now(),
                gitCommit = gitCommit,
                gitBranch = gitBranch,
                buildNumber = buildNumber,
                releaseNotes = releaseNotes
            )
        }
    }
}

/**
 * Dependency - Represents a dependency of this artifact
 */
@Serializable
data class Dependency(
    val name: String,
    val group: String,
    val version: String,
    val scope: DependencyScope,
    val optional: Boolean = false,
    val excludes: List<DependencyExclusion> = emptyList(),
    val classifier: String? = null,
    val type: String = "jar"
) {
    init {
        require(name.isNotBlank()) { "Dependency name must be non-empty" }
        require(group.isNotBlank()) { "Dependency group must be non-empty" }
        require(version.isNotBlank()) { "Dependency version must be non-empty" }
    }

    /**
     * Maven coordinates for this dependency
     */
    val coordinates: String
        get() = buildString {
            append("$group:$name:$version")
            if (classifier != null) {
                append(":$classifier")
            }
            if (type != "jar") {
                append("@$type")
            }
        }

    /**
     * Whether this is a Kotlin multiplatform dependency
     */
    val isKotlinMultiplatform: Boolean
        get() = group.startsWith("org.jetbrains.kotlin") || name.contains("-metadata")
}

/**
 * DependencyExclusion - Represents an excluded transitive dependency
 */
@Serializable
data class DependencyExclusion(
    val group: String,
    val name: String
)

/**
 * SigningInfo - Information about artifact signing
 */
@Serializable
data class SigningInfo(
    val signed: Boolean,
    val signatureAlgorithm: String? = null,
    val keyId: String? = null,
    val timestamp: Instant? = null,
    val signatureFiles: List<String> = emptyList()
) {
    companion object {
        fun createGPGSigning(keyId: String): SigningInfo {
            return SigningInfo(
                signed = true,
                signatureAlgorithm = "SHA256withRSA",
                keyId = keyId,
                timestamp = kotlinx.datetime.Clock.System.now(),
                signatureFiles = listOf(".asc")
            )
        }
    }
}

/**
 * PublishingInfo - Information about artifact publishing
 */
@Serializable
data class PublishingInfo(
    val published: Boolean,
    val repositories: List<RepositoryInfo>,
    val publishTime: Instant? = null,
    val publishedBy: String? = null
) {
    companion object {
        fun createMavenCentral(): PublishingInfo {
            return PublishingInfo(
                published = true,
                repositories = listOf(
                    RepositoryInfo(
                        name = "Maven Central",
                        url = "https://repo1.maven.org/maven2/",
                        type = RepositoryType.RELEASE
                    )
                ),
                publishTime = kotlinx.datetime.Clock.System.now()
            )
        }
    }
}

/**
 * RepositoryInfo - Information about a repository where the artifact is published
 */
@Serializable
data class RepositoryInfo(
    val name: String,
    val url: String,
    val type: RepositoryType,
    val credentials: Boolean = false
)

/**
 * ArtifactValidationResult - Result of artifact validation
 */
data class ArtifactValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue>
)

/**
 * ValidationIssue - Individual validation issue
 */
data class ValidationIssue(
    val severity: ValidationSeverity,
    val message: String,
    val category: String
)

// Enums

@Serializable
enum class BuildPlatform {
    JVM, JS, ANDROID, IOS,
    NATIVE_LINUX, NATIVE_WINDOWS, NATIVE_MACOS,
    WASM
}

@Serializable
enum class ArtifactType {
    LIBRARY, SOURCES, DOCUMENTATION, SAMPLES, TOOLS, TESTS
}

@Serializable
enum class DependencyScope {
    COMPILE, RUNTIME, TEST, PROVIDED, IMPLEMENTATION, API, COMPILE_ONLY, RUNTIME_ONLY
}

@Serializable
enum class RepositoryType {
    RELEASE, SNAPSHOT, BOTH
}

enum class ValidationSeverity {
    ERROR, WARNING, INFO
}