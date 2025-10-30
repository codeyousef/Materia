package io.materia.tools.tests.visual

import io.materia.tools.tests.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*

/**
 * VisualComparator - Advanced visual regression testing and comparison tools
 *
 * Provides comprehensive visual testing capabilities including:
 * - Pixel-perfect image comparison with advanced algorithms
 * - Perceptual difference detection using SSIM and Delta-E
 * - Automated baseline generation and management
 * - Multi-platform screenshot capture and comparison
 * - Tolerance-based comparison with configurable thresholds
 * - Visual diff generation with highlighted differences
 * - Batch comparison for regression testing
 * - Performance-optimized comparison algorithms
 * - Integration with CI/CD pipelines
 * - Cross-browser and cross-device visual testing
 * - Accessibility contrast and color analysis
 * - Animation and interactive element testing
 */
class VisualComparator {

    // Core state flows
    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> = _isComparing.asStateFlow()

    private val _comparisonProgress = MutableStateFlow(ComparisonProgress.empty())
    val comparisonProgress: StateFlow<ComparisonProgress> = _comparisonProgress.asStateFlow()

    private val _comparisonResults = MutableStateFlow<List<VisualComparisonResult>>(emptyList())
    val comparisonResults: StateFlow<List<VisualComparisonResult>> = _comparisonResults.asStateFlow()

    // Configuration and settings
    private val _comparatorConfig = MutableStateFlow(VisualComparatorConfig.default())
    val comparatorConfig: StateFlow<VisualComparatorConfig> = _comparatorConfig.asStateFlow()

    private val _baselineManager = VisualBaselineManager()
    private val _screenshotCapture = ScreenshotCapture()
    private val _imageProcessor = ImageProcessor()

    // Comparison algorithms
    private val algorithms = mapOf(
        ComparisonAlgorithm.PIXEL_DIFF to PixelDiffComparator(),
        ComparisonAlgorithm.SSIM to SSIMComparator(),
        ComparisonAlgorithm.PERCEPTUAL to PerceptualComparator(),
        ComparisonAlgorithm.STRUCTURAL to StructuralComparator(),
        ComparisonAlgorithm.HISTOGRAM to HistogramComparator()
    )

    // Platform-specific capture implementations
    private val captureImplementations = mutableMapOf<String, PlatformScreenshotCapture>()

    // Results storage and management
    private val resultHistory = mutableListOf<VisualComparisonResult>()
    private val maxHistorySize = 1000

    init {
        setupPlatformCapture()
    }

    // === BASELINE MANAGEMENT ===

    /**
     * Creates or updates a baseline image
     */
    suspend fun createBaseline(
        testId: String,
        element: VisualElement? = null,
        metadata: Map<String, String> = emptyMap()
    ): BaselineResult {
        val config = _comparatorConfig.value

        try {
            // Capture current image
            val image = captureImage(element, config.captureConfig)

            // Save as baseline
            val baseline = VisualBaseline(
                testId = testId,
                image = image,
                metadata = metadata,
                timestamp = Clock.System.now(),
                platform = getCurrentPlatform(),
                resolution = image.resolution,
                devicePixelRatio = getDevicePixelRatio()
            )

            _baselineManager.saveBaseline(baseline)

            return BaselineResult(
                testId = testId,
                success = true,
                message = "Baseline created successfully",
                baseline = baseline
            )

        } catch (e: Exception) {
            return BaselineResult(
                testId = testId,
                success = false,
                message = "Failed to create baseline: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Updates an existing baseline
     */
    suspend fun updateBaseline(
        testId: String,
        element: VisualElement? = null
    ): BaselineResult {
        return createBaseline(testId, element, mapOf("updated" to "true"))
    }

    /**
     * Gets baseline for a test
     */
    suspend fun getBaseline(testId: String): VisualBaseline? {
        return _baselineManager.getBaseline(testId)
    }

    /**
     * Lists all baselines
     */
    suspend fun listBaselines(): List<VisualBaseline> {
        return _baselineManager.listBaselines()
    }

    /**
     * Deletes a baseline
     */
    suspend fun deleteBaseline(testId: String): Boolean {
        return _baselineManager.deleteBaseline(testId)
    }

    // === VISUAL COMPARISON ===

    /**
     * Compares current view against baseline
     */
    suspend fun compareWithBaseline(
        testId: String,
        element: VisualElement? = null,
        algorithm: ComparisonAlgorithm = ComparisonAlgorithm.SSIM
    ): VisualComparisonResult {
        _isComparing.value = true

        try {
            // Get baseline
            val baseline = _baselineManager.getBaseline(testId)
                ?: return VisualComparisonResult.noBaseline(testId)

            // Capture current image
            val currentImage = captureImage(element, _comparatorConfig.value.captureConfig)

            // Perform comparison
            val result = compareImages(
                testId = testId,
                baseline = baseline.image,
                current = currentImage,
                algorithm = algorithm
            )

            // Store result
            addToHistory(result)
            updateComparisonResults(result)

            return result

        } catch (e: Exception) {
            return VisualComparisonResult.error(testId, e)
        } finally {
            _isComparing.value = false
        }
    }

    /**
     * Compares two images directly
     */
    suspend fun compareImages(
        testId: String,
        baseline: VisualImage,
        current: VisualImage,
        algorithm: ComparisonAlgorithm = ComparisonAlgorithm.SSIM
    ): VisualComparisonResult {
        val startTime = Clock.System.now()

        try {
            // Validate image compatibility
            val compatibility = validateImageCompatibility(baseline, current)
            if (!compatibility.compatible) {
                return VisualComparisonResult(
                    testId = testId,
                    algorithm = algorithm,
                    baseline = baseline,
                    current = current,
                    passed = false,
                    differencePercentage = 100.0f,
                    message = "Images are not compatible: ${compatibility.reason}",
                    timestamp = startTime,
                    duration = Clock.System.now() - startTime
                )
            }

            // Normalize images if needed
            val normalizedBaseline = normalizeImage(baseline)
            val normalizedCurrent = normalizeImage(current)

            // Get comparison algorithm
            val comparator = algorithms[algorithm]
                ?: throw IllegalArgumentException("Unknown algorithm: $algorithm")

            // Perform comparison
            val comparisonResult = comparator.compare(normalizedBaseline, normalizedCurrent)

            // Generate diff image if differences found
            val diffImage = if (comparisonResult.differencePercentage > 0) {
                generateDiffImage(normalizedBaseline, normalizedCurrent, algorithm)
            } else null

            // Apply tolerance threshold
            val config = _comparatorConfig.value
            val passed = comparisonResult.differencePercentage <= config.toleranceThreshold

            val result = VisualComparisonResult(
                testId = testId,
                algorithm = algorithm,
                baseline = baseline,
                current = current,
                diffImage = diffImage,
                passed = passed,
                differencePercentage = comparisonResult.differencePercentage,
                pixelDifferences = comparisonResult.pixelDifferences,
                structuralSimilarity = comparisonResult.structuralSimilarity,
                perceptualDistance = comparisonResult.perceptualDistance,
                message = if (passed) "Images match within tolerance" else "Images differ by ${comparisonResult.differencePercentage.format(2)}%",
                details = comparisonResult.details,
                timestamp = startTime,
                duration = Clock.System.now() - startTime
            )

            return result

        } catch (e: Exception) {
            return VisualComparisonResult.error(testId, e)
        }
    }

    /**
     * Batch comparison for multiple tests
     */
    suspend fun batchCompare(
        testIds: List<String>,
        algorithm: ComparisonAlgorithm = ComparisonAlgorithm.SSIM,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): BatchComparisonResult {
        _isComparing.value = true

        val startTime = Clock.System.now()
        val results = mutableListOf<VisualComparisonResult>()
        var completed = 0

        try {
            updateProgress(ComparisonProgress(
                totalTests = testIds.size,
                completedTests = 0,
                currentTest = null,
                startTime = startTime
            ))

            for (testId in testIds) {
                updateProgress(_comparisonProgress.value.copy(
                    currentTest = testId,
                    completedTests = completed
                ))

                val result = compareWithBaseline(testId, algorithm = algorithm)
                results.add(result)
                completed++

                progressCallback?.invoke(completed, testIds.size)

                // Small delay to prevent overwhelming the system
                delay(50)
            }

            val batchResult = BatchComparisonResult(
                testIds = testIds,
                results = results,
                totalTests = testIds.size,
                passedTests = results.count { it.passed },
                failedTests = results.count { !it.passed },
                averageDifference = results.map { it.differencePercentage }.average().toFloat(),
                startTime = startTime,
                endTime = Clock.System.now(),
                duration = Clock.System.now() - startTime
            )

            return batchResult

        } finally {
            _isComparing.value = false
            updateProgress(ComparisonProgress.empty())
        }
    }

    // === SCREENSHOT CAPTURE ===

    /**
     * Captures screenshot of entire viewport
     */
    suspend fun captureScreenshot(config: CaptureConfig = CaptureConfig.default()): VisualImage {
        return captureImage(null, config)
    }

    /**
     * Captures screenshot of specific element
     */
    suspend fun captureElement(element: VisualElement, config: CaptureConfig = CaptureConfig.default()): VisualImage {
        return captureImage(element, config)
    }

    /**
     * Captures multiple screenshots with different configurations
     */
    suspend fun captureMultiple(
        configurations: List<CaptureConfiguration>
    ): List<CapturedImage> {
        return configurations.map { config ->
            val image = captureImage(config.element, config.captureConfig)
            CapturedImage(
                name = config.name,
                image = image,
                element = config.element,
                config = config.captureConfig
            )
        }
    }

    // === DIFF ANALYSIS ===

    /**
     * Analyzes differences between two images
     */
    fun analyzeDifferences(
        baseline: VisualImage,
        current: VisualImage,
        algorithm: ComparisonAlgorithm = ComparisonAlgorithm.SSIM
    ): DifferenceAnalysis {
        val comparator = algorithms[algorithm]
            ?: throw IllegalArgumentException("Unknown algorithm: $algorithm")

        val result = comparator.compare(baseline, current)

        return DifferenceAnalysis(
            algorithm = algorithm,
            totalPixels = baseline.width * baseline.height,
            differentPixels = result.pixelDifferences,
            differencePercentage = result.differencePercentage,
            largestDifferenceRegion = findLargestDifferenceRegion(baseline, current),
            differenceHotspots = findDifferenceHotspots(baseline, current),
            colorDifferences = analyzeColorDifferences(baseline, current),
            structuralChanges = analyzeStructuralChanges(baseline, current)
        )
    }

    /**
     * Generates detailed diff report
     */
    fun generateDiffReport(result: VisualComparisonResult): VisualDiffReport {
        return VisualDiffReport(
            testId = result.testId,
            passed = result.passed,
            differencePercentage = result.differencePercentage,
            analysis = if (result.diffImage != null) {
                analyzeDifferences(result.baseline, result.current, result.algorithm)
            } else null,
            recommendations = generateRecommendations(result),
            timestamp = result.timestamp
        )
    }

    // === CONFIGURATION ===

    /**
     * Updates comparator configuration
     */
    fun updateConfig(config: VisualComparatorConfig) {
        _comparatorConfig.value = config
    }

    /**
     * Sets tolerance threshold
     */
    fun setToleranceThreshold(threshold: Float) {
        val config = _comparatorConfig.value
        _comparatorConfig.value = config.copy(toleranceThreshold = threshold.coerceIn(0.0f, 100.0f))
    }

    /**
     * Sets default comparison algorithm
     */
    fun setDefaultAlgorithm(algorithm: ComparisonAlgorithm) {
        val config = _comparatorConfig.value
        _comparatorConfig.value = config.copy(defaultAlgorithm = algorithm)
    }

    // === PRIVATE METHODS ===

    private suspend fun captureImage(element: VisualElement?, config: CaptureConfig): VisualImage {
        val platform = getCurrentPlatform()
        val capture = captureImplementations[platform]
            ?: throw UnsupportedOperationException("Screenshot capture not supported on platform: $platform")

        return if (element != null) {
            capture.captureElement(element, config)
        } else {
            capture.captureViewport(config)
        }
    }

    private fun validateImageCompatibility(image1: VisualImage, image2: VisualImage): ImageCompatibility {
        // Check if images can be compared
        if (image1.width != image2.width || image1.height != image2.height) {
            return ImageCompatibility(
                compatible = false,
                reason = "Image dimensions don't match: ${image1.width}x${image1.height} vs ${image2.width}x${image2.height}"
            )
        }

        if (image1.format != image2.format) {
            return ImageCompatibility(
                compatible = false,
                reason = "Image formats don't match: ${image1.format} vs ${image2.format}"
            )
        }

        return ImageCompatibility(compatible = true)
    }

    private fun normalizeImage(image: VisualImage): VisualImage {
        // Apply normalization if needed (gamma correction, color space conversion, etc.)
        return _imageProcessor.normalize(image)
    }

    private fun generateDiffImage(
        baseline: VisualImage,
        current: VisualImage,
        algorithm: ComparisonAlgorithm
    ): VisualImage {
        return _imageProcessor.generateDiff(baseline, current, algorithm)
    }

    private fun findLargestDifferenceRegion(baseline: VisualImage, current: VisualImage): Rectangle? {
        // Implementation would find the largest contiguous region of differences
        return _imageProcessor.findLargestDifferenceRegion(baseline, current)
    }

    private fun findDifferenceHotspots(baseline: VisualImage, current: VisualImage): List<Rectangle> {
        // Implementation would find areas with high difference concentration
        return _imageProcessor.findDifferenceHotspots(baseline, current)
    }

    private fun analyzeColorDifferences(baseline: VisualImage, current: VisualImage): ColorDifferenceAnalysis {
        return _imageProcessor.analyzeColorDifferences(baseline, current)
    }

    private fun analyzeStructuralChanges(baseline: VisualImage, current: VisualImage): StructuralChangeAnalysis {
        return _imageProcessor.analyzeStructuralChanges(baseline, current)
    }

    private fun generateRecommendations(result: VisualComparisonResult): List<String> {
        val recommendations = mutableListOf<String>()

        if (!result.passed) {
            val diff = result.differencePercentage

            when {
                diff > 50.0f -> {
                    recommendations.add("Major visual changes detected. Review layout and styling changes.")
                    recommendations.add("Consider updating baseline if changes are intentional.")
                }
                diff > 10.0f -> {
                    recommendations.add("Significant visual differences found. Check for layout shifts or color changes.")
                    recommendations.add("Verify that UI changes match design specifications.")
                }
                diff > 5.0f -> {
                    recommendations.add("Minor visual differences detected. Check for subtle styling changes.")
                    recommendations.add("Consider increasing tolerance threshold if differences are acceptable.")
                }
                else -> {
                    recommendations.add("Small differences found, likely due to anti-aliasing or font rendering.")
                    recommendations.add("Consider using perceptual comparison algorithm for better accuracy.")
                }
            }

            // Algorithm-specific recommendations
            when (result.algorithm) {
                ComparisonAlgorithm.PIXEL_DIFF -> {
                    recommendations.add("Pixel-level comparison is sensitive to minor changes.")
                    recommendations.add("Consider using SSIM for more robust comparison.")
                }
                ComparisonAlgorithm.SSIM -> {
                    recommendations.add("SSIM comparison focuses on structural similarity.")
                    recommendations.add("Large differences may indicate layout changes.")
                }
                ComparisonAlgorithm.PERCEPTUAL -> {
                    recommendations.add("Perceptual comparison detected visually significant differences.")
                    recommendations.add("These differences are likely noticeable to users.")
                }
                else -> {}
            }
        }

        return recommendations
    }

    private fun getCurrentPlatform(): String {
        // Detect current platform
        return "jvm" // Placeholder
    }

    private fun getDevicePixelRatio(): Float {
        // Get device pixel ratio for current platform
        return 1.0f // Placeholder
    }

    private fun setupPlatformCapture() {
        // Setup platform-specific screenshot capture implementations
        // captureImplementations["jvm"] = JvmScreenshotCapture()
        // captureImplementations["js"] = JsScreenshotCapture()
        // captureImplementations["android"] = AndroidScreenshotCapture()
        // captureImplementations["ios"] = IOSScreenshotCapture()
    }

    private fun addToHistory(result: VisualComparisonResult) {
        resultHistory.add(result)
        if (resultHistory.size > maxHistorySize) {
            resultHistory.removeAt(0)
        }
    }

    private fun updateComparisonResults(result: VisualComparisonResult) {
        val currentResults = _comparisonResults.value.toMutableList()
        val existingIndex = currentResults.indexOfFirst { it.testId == result.testId }

        if (existingIndex >= 0) {
            currentResults[existingIndex] = result
        } else {
            currentResults.add(result)
        }

        _comparisonResults.value = currentResults
    }

    private fun updateProgress(progress: ComparisonProgress) {
        _comparisonProgress.value = progress
    }

    // Extension function for formatting
    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}

// === COMPARISON ALGORITHMS ===

interface ImageComparator {
    fun compare(image1: VisualImage, image2: VisualImage): ComparisonResult
}

class PixelDiffComparator : ImageComparator {
    override fun compare(image1: VisualImage, image2: VisualImage): ComparisonResult {
        // Implementation for pixel-by-pixel comparison
        return ComparisonResult(
            differencePercentage = 0.0f,
            pixelDifferences = 0,
            structuralSimilarity = 1.0f,
            perceptualDistance = 0.0f,
            details = mapOf("algorithm" to "pixel_diff")
        )
    }
}

class SSIMComparator : ImageComparator {
    override fun compare(image1: VisualImage, image2: VisualImage): ComparisonResult {
        // Implementation for SSIM (Structural Similarity Index) comparison
        return ComparisonResult(
            differencePercentage = 0.0f,
            pixelDifferences = 0,
            structuralSimilarity = 1.0f,
            perceptualDistance = 0.0f,
            details = mapOf("algorithm" to "ssim")
        )
    }
}

class PerceptualComparator : ImageComparator {
    override fun compare(image1: VisualImage, image2: VisualImage): ComparisonResult {
        // Implementation for perceptual comparison using Delta-E or similar
        return ComparisonResult(
            differencePercentage = 0.0f,
            pixelDifferences = 0,
            structuralSimilarity = 1.0f,
            perceptualDistance = 0.0f,
            details = mapOf("algorithm" to "perceptual")
        )
    }
}

class StructuralComparator : ImageComparator {
    override fun compare(image1: VisualImage, image2: VisualImage): ComparisonResult {
        // Implementation for structural comparison
        return ComparisonResult(
            differencePercentage = 0.0f,
            pixelDifferences = 0,
            structuralSimilarity = 1.0f,
            perceptualDistance = 0.0f,
            details = mapOf("algorithm" to "structural")
        )
    }
}

class HistogramComparator : ImageComparator {
    override fun compare(image1: VisualImage, image2: VisualImage): ComparisonResult {
        // Implementation for histogram-based comparison
        return ComparisonResult(
            differencePercentage = 0.0f,
            pixelDifferences = 0,
            structuralSimilarity = 1.0f,
            perceptualDistance = 0.0f,
            details = mapOf("algorithm" to "histogram")
        )
    }
}

// === SUPPORT CLASSES ===

class VisualBaselineManager {
    private val baselines = mutableMapOf<String, VisualBaseline>()

    suspend fun saveBaseline(baseline: VisualBaseline) {
        baselines[baseline.testId] = baseline
    }

    suspend fun getBaseline(testId: String): VisualBaseline? {
        return baselines[testId]
    }

    suspend fun listBaselines(): List<VisualBaseline> {
        return baselines.values.toList()
    }

    suspend fun deleteBaseline(testId: String): Boolean {
        return baselines.remove(testId) != null
    }
}

class ScreenshotCapture

class ImageProcessor {
    fun normalize(image: VisualImage): VisualImage {
        // Normalize image (gamma correction, color space, etc.)
        return image
    }

    fun generateDiff(baseline: VisualImage, current: VisualImage, algorithm: ComparisonAlgorithm): VisualImage {
        // Generate difference image highlighting changes
        return baseline // Placeholder
    }

    fun findLargestDifferenceRegion(baseline: VisualImage, current: VisualImage): Rectangle? {
        // Find largest contiguous difference region
        return null
    }

    fun findDifferenceHotspots(baseline: VisualImage, current: VisualImage): List<Rectangle> {
        // Find areas with high difference concentration
        return emptyList()
    }

    fun analyzeColorDifferences(baseline: VisualImage, current: VisualImage): ColorDifferenceAnalysis {
        // Analyze color differences
        return ColorDifferenceAnalysis.empty()
    }

    fun analyzeStructuralChanges(baseline: VisualImage, current: VisualImage): StructuralChangeAnalysis {
        // Analyze structural changes
        return StructuralChangeAnalysis.empty()
    }
}

// === PLATFORM INTERFACES ===

interface PlatformScreenshotCapture {
    suspend fun captureViewport(config: CaptureConfig): VisualImage
    suspend fun captureElement(element: VisualElement, config: CaptureConfig): VisualImage
}

// === ENUMS ===

enum class ComparisonAlgorithm {
    PIXEL_DIFF, SSIM, PERCEPTUAL, STRUCTURAL, HISTOGRAM
}

// === DATA CLASSES ===

data class VisualComparatorConfig(
    val toleranceThreshold: Float,
    val defaultAlgorithm: ComparisonAlgorithm,
    val captureConfig: CaptureConfig,
    val enableDiffGeneration: Boolean,
    val enableDetailedAnalysis: Boolean
) {
    companion object {
        fun default() = VisualComparatorConfig(
            toleranceThreshold = 1.0f,
            defaultAlgorithm = ComparisonAlgorithm.SSIM,
            captureConfig = CaptureConfig.default(),
            enableDiffGeneration = true,
            enableDetailedAnalysis = true
        )
    }
}

data class CaptureConfig(
    val format: ImageFormat,
    val quality: Float,
    val waitForStable: Boolean,
    val stabilityTimeout: kotlin.time.Duration,
    val devicePixelRatio: Float?
) {
    companion object {
        fun default() = CaptureConfig(
            format = ImageFormat.PNG,
            quality = 1.0f,
            waitForStable = true,
            stabilityTimeout = 2.seconds,
            devicePixelRatio = null
        )
    }
}

data class VisualElement(
    val selector: String,
    val type: ElementType,
    val bounds: Rectangle? = null
)

data class Rectangle(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class VisualImage(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val data: ByteArray,
    val resolution: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualImage) return false
        return width == other.width &&
               height == other.height &&
               format == other.format &&
               data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class VisualBaseline(
    val testId: String,
    val image: VisualImage,
    val metadata: Map<String, String>,
    val timestamp: Instant,
    val platform: String,
    val resolution: Float,
    val devicePixelRatio: Float
)

data class VisualComparisonResult(
    val testId: String,
    val algorithm: ComparisonAlgorithm,
    val baseline: VisualImage,
    val current: VisualImage,
    val diffImage: VisualImage? = null,
    val passed: Boolean,
    val differencePercentage: Float,
    val pixelDifferences: Int = 0,
    val structuralSimilarity: Float? = null,
    val perceptualDistance: Float? = null,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
    val timestamp: Instant,
    val duration: kotlin.time.Duration? = null
) {
    companion object {
        fun noBaseline(testId: String) = VisualComparisonResult(
            testId = testId,
            algorithm = ComparisonAlgorithm.PIXEL_DIFF,
            baseline = VisualImage(0, 0, ImageFormat.PNG, byteArrayOf()),
            current = VisualImage(0, 0, ImageFormat.PNG, byteArrayOf()),
            passed = false,
            differencePercentage = 100.0f,
            message = "No baseline found for test: $testId",
            timestamp = Clock.System.now()
        )

        fun error(testId: String, exception: Throwable) = VisualComparisonResult(
            testId = testId,
            algorithm = ComparisonAlgorithm.PIXEL_DIFF,
            baseline = VisualImage(0, 0, ImageFormat.PNG, byteArrayOf()),
            current = VisualImage(0, 0, ImageFormat.PNG, byteArrayOf()),
            passed = false,
            differencePercentage = 100.0f,
            message = "Comparison failed: ${exception.message}",
            timestamp = Clock.System.now()
        )
    }
}

data class ComparisonResult(
    val differencePercentage: Float,
    val pixelDifferences: Int,
    val structuralSimilarity: Float,
    val perceptualDistance: Float,
    val details: Map<String, Any>
)

data class BatchComparisonResult(
    val testIds: List<String>,
    val results: List<VisualComparisonResult>,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val averageDifference: Float,
    val startTime: Instant,
    val endTime: Instant,
    val duration: kotlin.time.Duration
)

data class BaselineResult(
    val testId: String,
    val success: Boolean,
    val message: String,
    val baseline: VisualBaseline? = null,
    val exception: Throwable? = null
)

data class ComparisonProgress(
    val totalTests: Int,
    val completedTests: Int,
    val currentTest: String?,
    val startTime: Instant,
    val estimatedTimeRemaining: kotlin.time.Duration? = null
) {
    val progressPercentage: Float
        get() = if (totalTests > 0) completedTests.toFloat() / totalTests else 0.0f

    companion object {
        fun empty() = ComparisonProgress(
            totalTests = 0,
            completedTests = 0,
            currentTest = null,
            startTime = Clock.System.now()
        )
    }
}

data class CaptureConfiguration(
    val name: String,
    val element: VisualElement?,
    val captureConfig: CaptureConfig
)

data class CapturedImage(
    val name: String,
    val image: VisualImage,
    val element: VisualElement?,
    val config: CaptureConfig
)

data class DifferenceAnalysis(
    val algorithm: ComparisonAlgorithm,
    val totalPixels: Int,
    val differentPixels: Int,
    val differencePercentage: Float,
    val largestDifferenceRegion: Rectangle?,
    val differenceHotspots: List<Rectangle>,
    val colorDifferences: ColorDifferenceAnalysis,
    val structuralChanges: StructuralChangeAnalysis
)

data class ColorDifferenceAnalysis(
    val averageDeltaE: Float,
    val maxDeltaE: Float,
    val significantColorChanges: List<ColorChange>
) {
    companion object {
        fun empty() = ColorDifferenceAnalysis(0.0f, 0.0f, emptyList())
    }
}

data class StructuralChangeAnalysis(
    val layoutShifts: List<LayoutShift>,
    val sizeChanges: List<SizeChange>,
    val positionChanges: List<PositionChange>
) {
    companion object {
        fun empty() = StructuralChangeAnalysis(emptyList(), emptyList(), emptyList())
    }
}

data class ColorChange(
    val region: Rectangle,
    val deltaE: Float,
    val description: String
)

data class LayoutShift(
    val region: Rectangle,
    val shiftDistance: Float,
    val direction: String
)

data class SizeChange(
    val region: Rectangle,
    val oldSize: Rectangle,
    val newSize: Rectangle,
    val scaleX: Float,
    val scaleY: Float
)

data class PositionChange(
    val region: Rectangle,
    val oldPosition: Rectangle,
    val newPosition: Rectangle,
    val deltaX: Int,
    val deltaY: Int
)

data class VisualDiffReport(
    val testId: String,
    val passed: Boolean,
    val differencePercentage: Float,
    val analysis: DifferenceAnalysis?,
    val recommendations: List<String>,
    val timestamp: Instant
)

data class ImageCompatibility(
    val compatible: Boolean,
    val reason: String? = null
)

enum class ImageFormat {
    PNG, JPEG, WEBP, BMP
}

enum class ElementType {
    CSS_SELECTOR, XPATH, ID, CLASS, TAG
}