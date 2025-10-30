package io.materia.validation

/**
 * Contract interface for validating implementation completeness across platforms.
 *
 * This interface defines the contract for analyzing expect/actual declarations
 * and identifying missing platform-specific implementations in the Materia
 * multiplatform codebase.
 */
interface ImplementationValidator {

    /**
     * Analyzes the codebase for implementation gaps across all platforms.
     *
     * @param sourceRoot Root directory of source code
     * @param platforms List of target platforms to validate
     * @return GapAnalysisResult containing all detected implementation gaps
     */
    suspend fun analyzeImplementationGaps(
        sourceRoot: String,
        platforms: List<Platform> = Platform.values().toList()
    ): GapAnalysisResult

    /**
     * Validates that all expect declarations have corresponding actual implementations.
     *
     * @param expectFilePath Path to file containing expect declarations
     * @param platforms Platforms to check for actual implementations
     * @return List of ImplementationGap for missing implementations
     */
    suspend fun validateExpectActualPairs(
        expectFilePath: String,
        platforms: List<Platform>
    ): List<ImplementationGap>

    /**
     * Checks if an actual implementation exists for a given expect declaration.
     *
     * @param expectDeclaration The expect declaration signature
     * @param platform Target platform to check
     * @param sourceRoot Source code root directory
     * @return True if actual implementation exists and is complete
     */
    suspend fun hasActualImplementation(
        expectDeclaration: String,
        platform: Platform,
        sourceRoot: String
    ): Boolean

    /**
     * Validates that an actual implementation is functionally complete.
     *
     * @param actualFilePath Path to actual implementation file
     * @param expectedSignature Expected function/class signature
     * @return ImplementationStatus indicating completeness level
     */
    suspend fun validateImplementationCompleteness(
        actualFilePath: String,
        expectedSignature: String
    ): ImplementationStatus

    /**
     * Gets all expect declarations from a source directory.
     *
     * @param sourceRoot Root directory to search
     * @return List of expect declaration signatures found
     */
    suspend fun findExpectDeclarations(sourceRoot: String): List<String>

    /**
     * Identifies stub implementations that need to be replaced.
     *
     * @param sourceRoot Root directory to search
     * @param platform Target platform
     * @return List of stub implementations found
     */
    suspend fun findStubImplementations(
        sourceRoot: String,
        platform: Platform
    ): List<ImplementationGap>
}