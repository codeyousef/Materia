package io.materia.validation.platform

import io.materia.validation.api.ValidationContext
import io.materia.validation.api.Validator
import io.materia.validation.models.*
import kotlinx.coroutines.*

/**
 * JVM-specific platform validator for runtime performance and compatibility.
 */
class JvmPlatformValidator : Validator<PlatformValidationResult> {

    override val name: String = "JVM Platform Validator"

    override suspend fun validate(context: ValidationContext): PlatformValidationResult =
        withContext(Dispatchers.IO) {
            val jvmVersion = System.getProperty("java.version")
            val jvmVendor = System.getProperty("java.vendor")
            val osName = System.getProperty("os.name")
            val osArch = System.getProperty("os.arch")

            val issues = mutableListOf<String>()

            // Check JVM version compatibility
            val majorVersion = extractJavaMajorVersion(jvmVersion)
            if (majorVersion < 11) {
                issues.add("JVM version $jvmVersion is below minimum requirement (Java 11+)")
            }

            // Check memory settings
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024) // Convert to MB
            if (maxMemory < 512) {
                issues.add("Maximum heap size ${maxMemory}MB is below recommended 512MB")
            }

            // Check for required system properties
            val requiredProperties = listOf(
                "java.io.tmpdir",
                "user.home",
                "file.separator"
            )

            requiredProperties.forEach { prop ->
                if (System.getProperty(prop) == null) {
                    issues.add("Required system property '$prop' is not set")
                }
            }

            // Test ProcessBuilder availability (needed for compilation)
            val processBuilderAvailable = try {
                ProcessBuilder("echo", "test").start().waitFor()
                true
            } catch (e: Exception) {
                issues.add("ProcessBuilder not available: ${e.message}")
                false
            }

            PlatformValidationResult(
                platform = "JVM",
                isSupported = true,
                version = jvmVersion,
                issues = issues,
                capabilities = mapOf(
                    "jvmVendor" to jvmVendor,
                    "osName" to osName,
                    "osArch" to osArch,
                    "maxHeapMB" to maxMemory.toString(),
                    "processBuilder" to processBuilderAvailable.toString(),
                    "javaVersion" to jvmVersion
                ),
                status = if (issues.isEmpty()) ValidationStatus.PASSED else ValidationStatus.WARNING,
                score = if (issues.isEmpty()) 1.0f else 0.7f,
                message = if (issues.isEmpty()) {
                    "JVM platform validation passed"
                } else {
                    "JVM platform has ${issues.size} issue(s)"
                }
            )
        }

    /**
     * Extracts the major version number from a Java version string.
     */
    private fun extractJavaMajorVersion(versionString: String): Int {
        return try {
            val parts = versionString.split(".")
            val major = parts[0].toIntOrNull()
            if (major == 1 && parts.size > 1) {
                // Old versioning scheme (1.8, 1.7, etc.)
                parts[1].toIntOrNull() ?: 8
            } else {
                // New versioning scheme (11, 17, 21, etc.)
                major ?: 8
            }
        } catch (e: Exception) {
            8 // Default to Java 8 if parsing fails
        }
    }
}

/**
 * Platform-specific validation result.
 */
data class PlatformValidationResult(
    val platform: String,
    val isSupported: Boolean,
    val version: String?,
    val issues: List<String>,
    val capabilities: Map<String, String>,
    override val status: ValidationStatus,
    override val score: Float,
    override val message: String
) : io.materia.validation.api.ValidationResult {
    override val details: Map<String, Any>
        get() = mapOf(
            "platform" to platform,
            "isSupported" to isSupported,
            "version" to (version ?: "unknown"),
            "issueCount" to issues.size,
            "capabilities" to capabilities
        )
}