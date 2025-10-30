package io.materia.validation

/**
 * Contract interface for creating platform-specific renderer implementations.
 *
 * This interface defines the contract for creating and validating
 * renderer implementations across all supported platforms in Materia.
 */
interface RendererFactory {

    /**
     * Creates a renderer instance for the specified platform.
     *
     * @param platform Target platform for renderer
     * @param configuration Optional renderer configuration
     * @return Renderer instance or error if creation fails
     */
    suspend fun createRenderer(
        platform: Platform,
        configuration: RendererConfiguration = RendererConfiguration.default()
    ): RendererResult<Renderer>

    /**
     * Validates that a renderer implementation is production-ready.
     *
     * @param renderer Renderer instance to validate
     * @param validationSuite Test suite to run against renderer
     * @return RendererComponent with validation results
     */
    suspend fun validateRenderer(
        renderer: Renderer,
        validationSuite: RendererValidationSuite
    ): RendererComponent

    /**
     * Gets the capabilities of a renderer for a specific platform.
     *
     * @param platform Target platform
     * @return List of rendering capabilities supported
     */
    fun getRendererCapabilities(platform: Platform): List<String>

    /**
     * Checks if a renderer implementation exists for a platform.
     *
     * @param platform Target platform to check
     * @return True if production-ready implementation exists
     */
    fun hasProductionRenderer(platform: Platform): Boolean

    /**
     * Gets performance metrics for a renderer implementation.
     *
     * @param platform Target platform
     * @param testScene Scene to use for performance testing
     * @return PerformanceData with measured metrics
     */
    suspend fun measureRendererPerformance(
        platform: Platform,
        testScene: Scene
    ): PerformanceData

    /**
     * Identifies missing renderer features for a platform.
     *
     * @param platform Target platform
     * @param requiredFeatures List of required rendering features
     * @return List of missing features that need implementation
     */
    fun getMissingFeatures(
        platform: Platform,
        requiredFeatures: List<String>
    ): List<String>
}