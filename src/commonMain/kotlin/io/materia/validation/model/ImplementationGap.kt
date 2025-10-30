package io.materia.validation.model

import kotlinx.serialization.Serializable

/**
 * Represents missing or incomplete implementations in the codebase.
 *
 * Implementation gaps occur when expect declarations exist but corresponding
 * actual implementations are missing or incomplete for specific platforms.
 */
@Serializable
data class ImplementationGap(
    /**
     * The expect declaration signature.
     */
    val expectDeclaration: String,

    /**
     * File containing the expect declaration.
     */
    val filePath: String,

    /**
     * Platforms missing actual implementations.
     */
    val platforms: List<String>,

    /**
     * Description of expected functionality.
     */
    val functionality: String,

    /**
     * Current implementation status.
     */
    val currentState: GapState,

    /**
     * Dependencies required for implementation.
     */
    val dependencies: List<String>,

    /**
     * Estimated implementation complexity.
     */
    val estimatedEffort: EffortLevel
) {

    /**
     * Validates that this implementation gap has valid data.
     * @return SimpleValidationResult indicating success or failure with details
     */
    fun validate(): SimpleValidationResult {
        val errors = mutableListOf<String>()

        if (expectDeclaration.isBlank()) {
            errors.add("Expect declaration cannot be blank")
        }

        if (filePath.isBlank()) {
            errors.add("File path cannot be blank")
        }

        if (platforms.isEmpty()) {
            errors.add("At least one platform must be specified")
        }

        if (functionality.isBlank()) {
            errors.add("Functionality description cannot be blank")
        }

        // Validate platforms are supported Materia targets
        val supportedPlatforms = Platform.values().map { it.name }
        val invalidPlatforms = platforms.filter { it !in supportedPlatforms }
        if (invalidPlatforms.isNotEmpty()) {
            errors.add("Unsupported platforms: ${invalidPlatforms.joinToString()}")
        }

        return if (errors.isEmpty()) {
            SimpleValidationResult.success()
        } else {
            SimpleValidationResult.failure(errors)
        }
    }

    /**
     * Checks if this gap affects all platforms.
     * @return true if all supported platforms are missing implementations
     */
    fun affectsAllPlatforms(): Boolean {
        val allPlatforms = Platform.values().map { it.name }
        return platforms.containsAll(allPlatforms)
    }

    /**
     * Checks if this gap is critical for production readiness.
     * @return true if gap blocks core functionality
     */
    fun isCritical(): Boolean {
        return currentState == GapState.MISSING ||
                (currentState == GapState.STUB && estimatedEffort == EffortLevel.LARGE)
    }

    /**
     * Gets the priority score for implementation ordering.
     * Higher scores indicate higher priority.
     * @return priority score (0-100)
     */
    fun getPriorityScore(): Int {
        val stateScore = when (currentState) {
            GapState.MISSING -> 40
            GapState.STUB -> 30
            GapState.INCOMPLETE -> 20
            GapState.PARTIAL -> 10
        }

        val effortScore = when (estimatedEffort) {
            EffortLevel.TRIVIAL -> 25
            EffortLevel.SMALL -> 20
            EffortLevel.MEDIUM -> 15
            EffortLevel.LARGE -> 5
        }

        val platformScore = when (platforms.size) {
            in 5..6 -> 20  // All or most platforms
            in 3..4 -> 15  // Multiple platforms
            in 2..2 -> 10  // Two platforms
            1 -> 5         // Single platform
            else -> 0
        }

        return stateScore + effortScore + platformScore
    }

    /**
     * Gets a human-readable description of this gap.
     * @return formatted description including functionality and affected platforms
     */
    fun getDescription(): String {
        val platformList = if (platforms.size <= 3) {
            platforms.joinToString(", ")
        } else {
            "${platforms.take(2).joinToString(", ")} and ${platforms.size - 2} others"
        }
        return "$functionality (${currentState.name}) - Missing on: $platformList"
    }

    /**
     * Creates an implementation plan for this gap.
     * @return ImplementationPlan with steps and estimated timeline
     */
    fun createImplementationPlan(): ImplementationPlan {
        val steps = mutableListOf<String>()

        // Add dependency resolution steps
        if (dependencies.isNotEmpty()) {
            steps.add("Resolve dependencies: ${dependencies.joinToString(", ")}")
        }

        // Add platform-specific implementation steps
        platforms.forEach { platform ->
            steps.add("Implement $expectDeclaration for $platform")
            steps.add("Write tests for $platform implementation")
        }

        // Add integration steps
        steps.add("Validate cross-platform compatibility")
        steps.add("Update documentation")

        return ImplementationPlan(
            gap = this,
            steps = steps,
            estimatedTimelineHours = when (estimatedEffort) {
                EffortLevel.TRIVIAL -> 1
                EffortLevel.SMALL -> 4
                EffortLevel.MEDIUM -> 16
                EffortLevel.LARGE -> 64
            } * platforms.size
        )
    }

    /**
     * Checks if this gap has any dependencies.
     * @return true if dependencies list is not empty
     */
    fun hasDependencies(): Boolean = dependencies.isNotEmpty()

    /**
     * Gets the subset of platforms that are critical for Materia core functionality.
     * @return list of critical platforms from the missing platforms
     */
    fun getCriticalPlatforms(): List<String> {
        val criticalPlatforms = setOf("JVM", "JAVASCRIPT")
        return platforms.filter { it in criticalPlatforms }
    }
}

/**
 * Current implementation status for expect/actual gaps.
 */
@Serializable
enum class GapState {
    /** No actual implementation found */
    MISSING,

    /** Some platforms implemented */
    PARTIAL,

    /** Stub implementation only */
    STUB,

    /** Implementation exists but non-functional */
    INCOMPLETE
}

/**
 * Supported Materia platform targets.
 */
@Serializable
enum class Platform {
    /** Java Virtual Machine target */
    JVM,

    /** Browser JavaScript target */
    JAVASCRIPT,

    /** Linux Native x64 target */
    LINUX_X64,

    /** Windows Native MinGW x64 target */
    WINDOWS_MINGW_X64,

    /** macOS Native x64 target */
    MACOS_X64,

    /** macOS Native ARM64 target */
    MACOS_ARM64
}

/**
 * Implementation plan for addressing a specific gap.
 */
@Serializable
data class ImplementationPlan(
    val gap: ImplementationGap,
    val steps: List<String>,
    val estimatedTimelineHours: Int
) {
    /**
     * Gets the estimated timeline in days (assuming 8 hours per day).
     * @return estimated days to complete implementation
     */
    fun getEstimatedDays(): Double = estimatedTimelineHours / 8.0

    /**
     * Checks if this is a quick fix (can be completed in one day).
     * @return true if estimated timeline is 8 hours or less
     */
    fun isQuickFix(): Boolean = estimatedTimelineHours <= 8
}