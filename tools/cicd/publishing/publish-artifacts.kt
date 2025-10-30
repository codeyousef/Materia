/**
 * Materia Tools - Publishing Automation
 * Handles publishing to Maven Central, NPM, and other repositories
 */

package io.materia.tools.cicd.publishing

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/**
 * Main publishing orchestrator
 */
class PublishingOrchestrator(
    private val config: PublishingConfig,
    private val dryRun: Boolean = false
) {
    private val logger = Logger("PublishingOrchestrator")
    private val publishingResults = ConcurrentHashMap<String, PublishingResult>()

    suspend fun publishAll(): PublishingReport = coroutineScope {
        logger.info("Starting publishing process...")
        logger.info("Dry run: $dryRun")
        logger.info("Platforms: ${config.platforms.joinToString()}")

        val jobs = mutableListOf<Deferred<PublishingResult>>()

        // Maven Central publishing (core library)
        if (config.publishToMaven) {
            jobs.add(async { publishToMavenCentral() })
        }

        // NPM publishing (web tools)
        if (config.publishToNpm) {
            jobs.add(async { publishToNpm() })
        }

        // Docker Hub publishing (containerized tools)
        if (config.publishToDocker) {
            jobs.add(async { publishToDockerHub() })
        }

        // GitHub Releases (desktop packages)
        if (config.publishToGitHub) {
            jobs.add(async { publishToGitHubReleases() })
        }

        // Gradle Plugin Portal (if applicable)
        if (config.publishGradlePlugin) {
            jobs.add(async { publishGradlePlugin() })
        }

        // Wait for all publishing jobs to complete
        val results = jobs.awaitAll()
        results.forEach { result ->
            publishingResults[result.target] = result
        }

        generateReport()
    }

    private suspend fun publishToMavenCentral(): PublishingResult {
        return withContext(Dispatchers.IO) {
            val logger = Logger("MavenCentral")
            val startTime = Instant.now()

            try {
                logger.info("Publishing to Maven Central...")

                if (dryRun) {
                    logger.info("DRY RUN: Would publish to Maven Central")
                    return@withContext PublishingResult(
                        target = "maven-central",
                        success = true,
                        message = "Dry run successful",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf(
                            "materia-core",
                            "materia-renderer",
                            "materia-scene",
                            "tools"
                        )
                    )
                }

                // Validate signing configuration
                validateSigningConfig()

                // Execute Gradle publish task
                val publishCommand = buildList {
                    add("./gradlew")
                    add("publishToSonatype")
                    add("closeSonatypeStagingRepository")
                    if (config.autoRelease) {
                        add("releaseSonatypeStagingRepository")
                    }
                    add("--no-daemon")
                    add("--stacktrace")
                }

                val result = executeCommand(publishCommand, config.projectRoot)

                if (result.exitCode == 0) {
                    logger.info("Successfully published to Maven Central")
                    PublishingResult(
                        target = "maven-central",
                        success = true,
                        message = "Published successfully",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = extractMavenArtifacts()
                    )
                } else {
                    logger.error("Maven Central publishing failed: ${result.stderr}")
                    PublishingResult(
                        target = "maven-central",
                        success = false,
                        message = "Publishing failed: ${result.stderr}",
                        duration = java.time.Duration.between(startTime, Instant.now())
                    )
                }
            } catch (e: Exception) {
                logger.error("Maven Central publishing error", e)
                PublishingResult(
                    target = "maven-central",
                    success = false,
                    message = "Exception: ${e.message}",
                    duration = java.time.Duration.between(startTime, Instant.now())
                )
            }
        }
    }

    private suspend fun publishToNpm(): PublishingResult {
        return withContext(Dispatchers.IO) {
            val logger = Logger("NPM")
            val startTime = Instant.now()

            try {
                logger.info("Publishing to NPM...")

                val webToolsPath = Paths.get(config.projectRoot, "tools", "editor", "web")
                if (!Files.exists(webToolsPath.resolve("package.json"))) {
                    return@withContext PublishingResult(
                        target = "npm",
                        success = false,
                        message = "package.json not found",
                        duration = java.time.Duration.between(startTime, Instant.now())
                    )
                }

                if (dryRun) {
                    logger.info("DRY RUN: Would publish to NPM")
                    return@withContext PublishingResult(
                        target = "npm",
                        success = true,
                        message = "Dry run successful",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf("@materia/web-editor")
                    )
                }

                // Set up NPM authentication
                setupNpmAuth(webToolsPath)

                // Build the package
                val buildResult = executeCommand(
                    listOf("npm", "run", "build"),
                    webToolsPath.toString()
                )

                if (buildResult.exitCode != 0) {
                    throw RuntimeException("NPM build failed: ${buildResult.stderr}")
                }

                // Publish to NPM
                val publishResult = executeCommand(
                    listOf("npm", "publish", "--access", "public"),
                    webToolsPath.toString()
                )

                if (publishResult.exitCode == 0) {
                    logger.info("Successfully published to NPM")
                    PublishingResult(
                        target = "npm",
                        success = true,
                        message = "Published successfully",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf("@materia/web-editor")
                    )
                } else {
                    logger.error("NPM publishing failed: ${publishResult.stderr}")
                    PublishingResult(
                        target = "npm",
                        success = false,
                        message = "Publishing failed: ${publishResult.stderr}",
                        duration = java.time.Duration.between(startTime, Instant.now())
                    )
                }
            } catch (e: Exception) {
                logger.error("NPM publishing error", e)
                PublishingResult(
                    target = "npm",
                    success = false,
                    message = "Exception: ${e.message}",
                    duration = java.time.Duration.between(startTime, Instant.now())
                )
            }
        }
    }

    private suspend fun publishToDockerHub(): PublishingResult {
        return withContext(Dispatchers.IO) {
            val logger = Logger("Docker")
            val startTime = Instant.now()

            try {
                logger.info("Publishing to Docker Hub...")

                if (dryRun) {
                    logger.info("DRY RUN: Would publish to Docker Hub")
                    return@withContext PublishingResult(
                        target = "docker-hub",
                        success = true,
                        message = "Dry run successful",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf("materia/tools-api", "materia/web-tools")
                    )
                }

                val images = listOf(
                    DockerImage("materia/tools-api", "tools/api-server/Dockerfile"),
                    DockerImage("materia/web-tools", "tools/web-host/Dockerfile")
                )

                val results = images.map { image ->
                    async { publishDockerImage(image) }
                }.awaitAll()

                val allSuccess = results.all { it }
                val artifacts = if (allSuccess) images.map { it.name } else emptyList()

                PublishingResult(
                    target = "docker-hub",
                    success = allSuccess,
                    message = if (allSuccess) "All images published" else "Some images failed",
                    duration = java.time.Duration.between(startTime, Instant.now()),
                    artifacts = artifacts
                )
            } catch (e: Exception) {
                logger.error("Docker publishing error", e)
                PublishingResult(
                    target = "docker-hub",
                    success = false,
                    message = "Exception: ${e.message}",
                    duration = java.time.Duration.between(startTime, Instant.now())
                )
            }
        }
    }

    private suspend fun publishToGitHubReleases(): PublishingResult {
        return withContext(Dispatchers.IO) {
            val logger = Logger("GitHub")
            val startTime = Instant.now()

            try {
                logger.info("Publishing to GitHub Releases...")

                if (dryRun) {
                    logger.info("DRY RUN: Would publish to GitHub Releases")
                    return@withContext PublishingResult(
                        target = "github-releases",
                        success = true,
                        message = "Dry run successful",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf("windows-installer", "macos-dmg", "linux-appimage")
                    )
                }

                // Find release artifacts
                val releaseAssets = findReleaseAssets()

                if (releaseAssets.isEmpty()) {
                    return@withContext PublishingResult(
                        target = "github-releases",
                        success = false,
                        message = "No release assets found",
                        duration = java.time.Duration.between(startTime, Instant.now())
                    )
                }

                // Create GitHub release using gh CLI
                val releaseTag = config.version
                val createReleaseCommand = listOf(
                    "gh", "release", "create", releaseTag,
                    "--title", "Materia Tools v$releaseTag",
                    "--notes-file", "CHANGELOG.md",
                    *releaseAssets.toTypedArray()
                )

                val result = executeCommand(createReleaseCommand, config.projectRoot)

                if (result.exitCode == 0) {
                    logger.info("Successfully created GitHub release")
                    PublishingResult(
                        target = "github-releases",
                        success = true,
                        message = "Release created successfully",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = releaseAssets
                    )
                } else {
                    logger.error("GitHub release creation failed: ${result.stderr}")
                    PublishingResult(
                        target = "github-releases",
                        success = false,
                        message = "Release creation failed: ${result.stderr}",
                        duration = java.time.Duration.between(startTime, Instant.now())
                    )
                }
            } catch (e: Exception) {
                logger.error("GitHub publishing error", e)
                PublishingResult(
                    target = "github-releases",
                    success = false,
                    message = "Exception: ${e.message}",
                    duration = java.time.Duration.between(startTime, Instant.now())
                )
            }
        }
    }

    private suspend fun publishGradlePlugin(): PublishingResult {
        return withContext(Dispatchers.IO) {
            val logger = Logger("GradlePlugin")
            val startTime = Instant.now()

            try {
                logger.info("Publishing Gradle plugin...")

                if (dryRun) {
                    logger.info("DRY RUN: Would publish Gradle plugin")
                    return@withContext PublishingResult(
                        target = "gradle-plugin-portal",
                        success = true,
                        message = "Dry run successful",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf("materia-gradle-plugin")
                    )
                }

                val publishCommand = listOf(
                    "./gradlew",
                    "publishPlugins",
                    "--no-daemon",
                    "--stacktrace"
                )

                val result = executeCommand(publishCommand, config.projectRoot)

                if (result.exitCode == 0) {
                    logger.info("Successfully published Gradle plugin")
                    PublishingResult(
                        target = "gradle-plugin-portal",
                        success = true,
                        message = "Plugin published successfully",
                        duration = java.time.Duration.between(startTime, Instant.now()),
                        artifacts = listOf("materia-gradle-plugin")
                    )
                } else {
                    logger.error("Gradle plugin publishing failed: ${result.stderr}")
                    PublishingResult(
                        target = "gradle-plugin-portal",
                        success = false,
                        message = "Publishing failed: ${result.stderr}",
                        duration = java.time.Duration.between(startTime, Instant.now())
                    )
                }
            } catch (e: Exception) {
                logger.error("Gradle plugin publishing error", e)
                PublishingResult(
                    target = "gradle-plugin-portal",
                    success = false,
                    message = "Exception: ${e.message}",
                    duration = java.time.Duration.between(startTime, Instant.now())
                )
            }
        }
    }

    private fun generateReport(): PublishingReport {
        val successCount = publishingResults.values.count { it.success }
        val totalCount = publishingResults.size
        val totalDuration = publishingResults.values.map { it.duration }.fold(java.time.Duration.ZERO) { acc, duration ->
            acc.plus(duration)
        }

        return PublishingReport(
            timestamp = Instant.now(),
            success = successCount == totalCount,
            results = publishingResults.toMap(),
            summary = PublishingSummary(
                total = totalCount,
                successful = successCount,
                failed = totalCount - successCount,
                duration = totalDuration
            )
        )
    }

    // Helper methods
    private fun validateSigningConfig() {
        val requiredProps = listOf("signing.keyId", "signing.password", "signing.secretKeyRingFile")
        val missingProps = requiredProps.filter { prop ->
            System.getProperty(prop) == null && System.getenv(prop.replace(".", "_").uppercase()) == null
        }

        if (missingProps.isNotEmpty()) {
            throw RuntimeException("Missing signing configuration: ${missingProps.joinToString()}")
        }
    }

    private fun setupNpmAuth(packagePath: Path) {
        val npmToken = System.getenv("NPM_TOKEN")
            ?: throw RuntimeException("NPM_TOKEN environment variable is required")

        val npmrcContent = "//registry.npmjs.org/:_authToken=$npmToken"
        Files.write(packagePath.resolve(".npmrc"), npmrcContent.toByteArray())
    }

    private suspend fun publishDockerImage(image: DockerImage): Boolean {
        val logger = Logger("Docker-${image.name}")

        try {
            // Build image
            val buildCommand = listOf(
                "docker", "build",
                "-t", "${image.name}:${config.version}",
                "-t", "${image.name}:latest",
                "-f", image.dockerfile,
                "."
            )

            val buildResult = executeCommand(buildCommand, config.projectRoot)
            if (buildResult.exitCode != 0) {
                logger.error("Docker build failed: ${buildResult.stderr}")
                return false
            }

            // Push image
            val pushResults = listOf(
                executeCommand(listOf("docker", "push", "${image.name}:${config.version}"), config.projectRoot),
                executeCommand(listOf("docker", "push", "${image.name}:latest"), config.projectRoot)
            )

            return pushResults.all { it.exitCode == 0 }
        } catch (e: Exception) {
            logger.error("Docker image publishing failed", e)
            return false
        }
    }

    private fun findReleaseAssets(): List<String> {
        val assetsDir = Paths.get(config.projectRoot, "tools", "packaging")
        val assets = mutableListOf<String>()

        // Look for platform-specific packages
        listOf("windows", "macos", "linux").forEach { platform ->
            val platformDir = assetsDir.resolve(platform).resolve("dist")
            if (Files.exists(platformDir)) {
                Files.list(platformDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .map { it.toString() }
                        .forEach { assets.add(it) }
                }
            }
        }

        return assets
    }

    private fun extractMavenArtifacts(): List<String> {
        // Extract artifact names from build output or configuration
        return listOf(
            "io.materia:materia-core",
            "io.materia:materia-renderer",
            "io.materia:materia-scene",
            "io.materia:materia-geometry",
            "io.materia:materia-material",
            "io.materia:materia-animation",
            "io.materia:materia-tools"
        )
    }
}

// Data classes
@Serializable
data class PublishingConfig(
    val projectRoot: String,
    val version: String,
    val platforms: List<String>,
    val publishToMaven: Boolean = true,
    val publishToNpm: Boolean = true,
    val publishToDocker: Boolean = true,
    val publishToGitHub: Boolean = true,
    val publishGradlePlugin: Boolean = false,
    val autoRelease: Boolean = false
)

@Serializable
data class PublishingResult(
    val target: String,
    val success: Boolean,
    val message: String,
    val duration: java.time.Duration,
    val artifacts: List<String> = emptyList()
)

@Serializable
data class PublishingReport(
    val timestamp: Instant,
    val success: Boolean,
    val results: Map<String, PublishingResult>,
    val summary: PublishingSummary
)

@Serializable
data class PublishingSummary(
    val total: Int,
    val successful: Int,
    val failed: Int,
    val duration: java.time.Duration
)

data class DockerImage(
    val name: String,
    val dockerfile: String
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

// Utility classes
class Logger(private val name: String) {
    fun info(message: String) = println("[$name] INFO: $message")
    fun error(message: String, throwable: Throwable? = null) {
        println("[$name] ERROR: $message")
        throwable?.printStackTrace()
    }
}

// Main execution
suspend fun main(args: Array<String>) {
    val configFile = args.getOrNull(0) ?: "publishing-config.json"
    val dryRun = args.contains("--dry-run")

    try {
        val configContent = File(configFile).readText()
        val config = Json.decodeFromString<PublishingConfig>(configContent)

        val orchestrator = PublishingOrchestrator(config, dryRun)
        val report = orchestrator.publishAll()

        // Write report
        val reportJson = Json.encodeToString(PublishingReport.serializer(), report)
        File("publishing-report.json").writeText(reportJson)

        println("\n" + "=".repeat(50))
        println("PUBLISHING REPORT")
        println("=".repeat(50))
        println("Success: ${report.success}")
        println("Total: ${report.summary.total}")
        println("Successful: ${report.summary.successful}")
        println("Failed: ${report.summary.failed}")
        println("Duration: ${report.summary.duration}")
        println("=".repeat(50))

        if (!report.success) {
            exitProcess(1)
        }
    } catch (e: Exception) {
        println("Publishing failed: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

// Command execution utility
suspend fun executeCommand(command: List<String>, workingDir: String): CommandResult {
    return withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(command)
            .directory(File(workingDir))
            .redirectErrorStream(false)

        val process = processBuilder.start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        CommandResult(exitCode, stdout, stderr)
    }
}