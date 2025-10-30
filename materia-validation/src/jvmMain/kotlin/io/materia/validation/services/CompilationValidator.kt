package io.materia.validation.services

import io.materia.validation.api.ValidationContext
import io.materia.validation.api.ValidationException
import io.materia.validation.api.Validator
import io.materia.validation.models.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.measureTimeMillis

/**
 * JVM-specific implementation of the CompilationValidator.
 *
 * Uses ProcessBuilder to execute Gradle compilation tasks and captures
 * compilation output for error and warning analysis.
 */
actual class CompilationValidator actual constructor() : Validator<CompilationResult> {

    actual override val name: String = "JVM Compilation Validator"

    private val helper = CompilationValidatorHelper()

    /**
     * Convenience method to validate compilation for a given project path.
     *
     * @param projectPath The path to the project to validate
     * @param platforms The list of platforms to validate (null means all)
     * @param timeoutMillis Optional timeout in milliseconds
     * @return CompilationResult containing the validation results
     */
    suspend fun validateCompilation(
        projectPath: String,
        platforms: List<io.materia.validation.models.Platform>? = null,
        timeoutMillis: Long? = null
    ): CompilationResult {
        val platformStrings = platforms?.map { it.name.lowercase() }
        val config = if (timeoutMillis != null) {
            mapOf("timeoutMillis" to timeoutMillis)
        } else {
            emptyMap()
        }

        val context = ValidationContext(
            projectPath = projectPath,
            platforms = platformStrings,
            configuration = config
        )
        return validate(context)
    }

    actual override suspend fun validate(context: ValidationContext): CompilationResult =
        withContext(Dispatchers.IO) {
            val projectDir = File(context.projectPath)
            if (!projectDir.exists() || !projectDir.isDirectory) {
                return@withContext CompilationResult(
                    status = ValidationStatus.FAILED,
                    score = 0.0f,
                    message = "Project directory does not exist: ${context.projectPath}",
                    platformResults = emptyMap(),
                    errors = listOf(
                        CompilationError(
                            file = context.projectPath,
                            line = 0,
                            column = 0,
                            message = "Project directory does not exist: ${context.projectPath}",
                            severity = "ERROR"
                        )
                    ),
                    warnings = emptyList(),
                    compilationTime = 0L
                )
            }

            val platforms = helper.validatePlatforms(context.platforms)
            val platformResults = mutableMapOf<String, PlatformCompilationStatus>()

            val totalTime = measureTimeMillis {
                platforms.forEach { platform ->
                    platformResults[platform] = compilePlatform(projectDir, platform)
                }
            }

            CompilationResult(
                status = helper.determineStatus(platformResults),
                score = helper.calculateScore(platformResults),
                message = helper.generateMessage(platformResults),
                platformResults = platformResults,
                errors = helper.collectErrors(platformResults),
                warnings = helper.collectWarnings(platformResults),
                compilationTime = totalTime
            )
        }

    /**
     * Compiles a specific platform target using Gradle.
     */
    private suspend fun compilePlatform(
        projectDir: File,
        platform: String
    ): PlatformCompilationStatus = withContext(Dispatchers.IO) {
        val gradleTask = getGradleTaskForPlatform(platform)
        var platformStatus: PlatformCompilationStatus? = null
        val duration = measureTimeMillis {
            platformStatus = try {
                val processBuilder = ProcessBuilder().apply {
                    directory(projectDir)

                    // Determine the Gradle wrapper to use
                    val gradleWrapper = when {
                        File(projectDir, "gradlew").exists() -> "./gradlew"
                        File(projectDir, "gradlew.bat").exists() && isWindows() -> "./gradlew.bat"
                        else -> "gradle"
                    }

                    command(
                        gradleWrapper,
                        gradleTask,
                        "--no-daemon",
                        "--stacktrace",
                        "--warning-mode=all"
                    )

                    // Capture both stdout and stderr
                    redirectErrorStream(true)
                }

                val process = processBuilder.start()
                val output = StringBuilder()
                val errors = mutableListOf<String>()
                val warnings = mutableListOf<String>()

                // Read process output
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        output.appendLine(line)

                        // Detect errors and warnings
                        when {
                            line.contains("error:", ignoreCase = true) ||
                                    line.contains("FAILED", ignoreCase = false) -> {
                                errors.add(line)
                            }

                            line.contains("warning:", ignoreCase = true) ||
                                    line.contains("deprecated", ignoreCase = true) -> {
                                warnings.add(line)
                            }
                        }
                    }
                }

                val exitCode = process.waitFor()

                PlatformCompilationStatus(
                    platform = platform,
                    success = exitCode == 0,
                    errorMessages = if (exitCode != 0) parseErrors(output.toString()) else emptyList(),
                    warningMessages = parseWarnings(output.toString()),
                    duration = 0L // Will be set below
                )

            } catch (e: Exception) {
                PlatformCompilationStatus(
                    platform = platform,
                    success = false,
                    errorMessages = listOf("Failed to execute compilation: ${e.message}"),
                    warningMessages = emptyList(),
                    duration = 0L
                )
            }
        }

        return@withContext platformStatus!!.copy(duration = duration)
    }

    /**
     * Maps platform names to appropriate Gradle tasks.
     */
    private fun getGradleTaskForPlatform(platform: String): String {
        return when (platform.lowercase()) {
            "jvm" -> ":compileKotlinJvm"
            "js" -> ":compileKotlinJs"
            "native" -> ":compileKotlinNative"
            "android" -> ":compileDebugKotlin"
            "ios", "iosarm64" -> ":compileKotlinIosArm64"
            "iosx64" -> ":compileKotlinIosX64"
            "iossimulatorarm64" -> ":compileKotlinIosSimulatorArm64"
            "linuxx64" -> ":compileKotlinLinuxX64"
            "linuxarm64" -> ":compileKotlinLinuxArm64"
            "mingwx64" -> ":compileKotlinMingwX64"
            "macosx64" -> ":compileKotlinMacosX64"
            "macosarm64" -> ":compileKotlinMacosArm64"
            else -> ":compile${platform.replaceFirstChar { it.titlecase() }}Kotlin"
        }
    }

    /**
     * Parses compilation errors from Gradle output.
     */
    private fun parseErrors(output: String): List<String> {
        val errors = mutableListOf<String>()
        val lines = output.lines()

        for ((index, line) in lines.withIndex()) {
            when {
                // Kotlin compiler errors
                line.contains("e: ") && line.contains(".kt:") -> {
                    errors.add(line.substringAfter("e: ").trim())
                }
                // Gradle build failures
                line.contains("FAILURE: Build failed") -> {
                    if (index + 1 < lines.size) {
                        errors.add(lines[index + 1].trim())
                    }
                }
                // Task failures
                line.contains("Execution failed for task") -> {
                    errors.add(line.trim())
                }
            }
        }

        return errors.ifEmpty {
            if (output.contains("BUILD FAILED")) {
                listOf("Build failed - see output for details")
            } else {
                emptyList()
            }
        }
    }

    /**
     * Parses compilation warnings from Gradle output.
     */
    private fun parseWarnings(output: String): List<String> {
        val warnings = mutableListOf<String>()
        val lines = output.lines()

        lines.forEach { line ->
            when {
                // Kotlin compiler warnings
                line.contains("w: ") && line.contains(".kt:") -> {
                    warnings.add(line.substringAfter("w: ").trim())
                }
                // Deprecation warnings
                line.contains("is deprecated", ignoreCase = true) -> {
                    warnings.add(line.trim())
                }
                // Other warnings
                line.contains("WARNING:", ignoreCase = true) -> {
                    warnings.add(line.substringAfter("WARNING:").trim())
                }
            }
        }

        return warnings
    }

    /**
     * Checks if the current system is Windows.
     */
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
}