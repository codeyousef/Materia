package io.materia.tools.profiler.ui

import io.materia.tools.profiler.data.*
import io.materia.tools.profiler.metrics.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * PerformanceUI - Comprehensive performance visualization and monitoring interface
 *
 * Provides real-time performance visualization including:
 * - Live performance charts (frame time, FPS, memory, CPU, GPU)
 * - Historical trend analysis with customizable time ranges
 * - Performance alerts and notifications system
 * - Bottleneck identification with automated suggestions
 * - Interactive charts with zoom, pan, and filtering
 * - Performance comparison tools for A/B testing
 * - Export capabilities for analysis and reporting
 * - Customizable dashboard layouts
 * - Multi-platform performance monitoring
 * - Real-time performance scoring and grading
 */
class PerformanceUI {

    // Core state flows
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _activeTab = MutableStateFlow(PerformanceTab.OVERVIEW)
    val activeTab: StateFlow<PerformanceTab> = _activeTab.asStateFlow()

    private val _timeRange = MutableStateFlow(TimeRangeSelection.LAST_MINUTE)
    val timeRange: StateFlow<TimeRangeSelection> = _timeRange.asStateFlow()

    private val _chartConfigurations = MutableStateFlow(getDefaultChartConfigurations())
    val chartConfigurations: StateFlow<Map<ChartType, ChartConfiguration>> = _chartConfigurations.asStateFlow()

    // Alert system
    private val _activeAlerts = MutableStateFlow<List<PerformanceAlert>>(emptyList())
    val activeAlerts: StateFlow<List<PerformanceAlert>> = _activeAlerts.asStateFlow()

    private val _alertSettings = MutableStateFlow(AlertSettings.default())
    val alertSettings: StateFlow<AlertSettings> = _alertSettings.asStateFlow()

    // Dashboard layout
    private val _dashboardLayout = MutableStateFlow(DashboardLayout.default())
    val dashboardLayout: StateFlow<DashboardLayout> = _dashboardLayout.asStateFlow()

    private val _isCompactMode = MutableStateFlow(false)
    val isCompactMode: StateFlow<Boolean> = _isCompactMode.asStateFlow()

    // Chart interaction
    private val _selectedDataPoint = MutableStateFlow<ChartDataPoint?>(null)
    val selectedDataPoint: StateFlow<ChartDataPoint?> = _selectedDataPoint.asStateFlow()

    private val _chartZoom = MutableStateFlow<ChartZoomState?>(null)
    val chartZoom: StateFlow<ChartZoomState?> = _chartZoom.asStateFlow()

    // Performance analysis
    private val _analysisResults = MutableStateFlow<PerformanceAnalysis?>(null)
    val analysisResults: StateFlow<PerformanceAnalysis?> = _analysisResults.asStateFlow()

    private val _performanceComparison = MutableStateFlow<PerformanceComparison?>(null)
    val performanceComparison: StateFlow<PerformanceComparison?> = _performanceComparison.asStateFlow()

    // Data sources
    private var metricsCollector: MetricsCollector? = null
    private val chartDataHistory = mutableMapOf<ChartType, CircularBuffer<ChartDataPoint>>(
        ChartType.FRAME_TIME to CircularBuffer(1000),
        ChartType.FPS to CircularBuffer(1000),
        ChartType.MEMORY to CircularBuffer(500),
        ChartType.CPU to CircularBuffer(500),
        ChartType.GPU to CircularBuffer(500),
        ChartType.NETWORK to CircularBuffer(300)
    )

    // Chart colors and themes
    private val chartTheme = ChartTheme.default()

    init {
        setupDefaultDashboard()
    }

    // === UI CONTROL ===

    /**
     * Shows the performance UI
     */
    fun show() {
        _isVisible.value = true
    }

    /**
     * Hides the performance UI
     */
    fun hide() {
        _isVisible.value = false
    }

    /**
     * Toggles performance UI visibility
     */
    fun toggle() {
        _isVisible.value = !_isVisible.value
    }

    /**
     * Sets the active tab
     */
    fun setActiveTab(tab: PerformanceTab) {
        _activeTab.value = tab
    }

    /**
     * Sets the time range for charts
     */
    fun setTimeRange(range: TimeRangeSelection) {
        _timeRange.value = range
        refreshChartData()
    }

    /**
     * Connects to metrics collector for data
     */
    fun connectToMetricsCollector(collector: MetricsCollector) {
        metricsCollector = collector

        // Start collecting chart data
        startDataCollection()
    }

    // === CHART MANAGEMENT ===

    /**
     * Updates chart configuration
     */
    fun updateChartConfiguration(chartType: ChartType, config: ChartConfiguration) {
        val updatedConfigs = _chartConfigurations.value.toMutableMap()
        updatedConfigs[chartType] = config
        _chartConfigurations.value = updatedConfigs
    }

    /**
     * Enables or disables a chart
     */
    fun setChartEnabled(chartType: ChartType, enabled: Boolean) {
        val currentConfig = _chartConfigurations.value[chartType] ?: return
        updateChartConfiguration(chartType, currentConfig.copy(enabled = enabled))
    }

    /**
     * Sets chart smoothing level
     */
    fun setChartSmoothing(chartType: ChartType, smoothing: SmoothingLevel) {
        val currentConfig = _chartConfigurations.value[chartType] ?: return
        updateChartConfiguration(chartType, currentConfig.copy(smoothing = smoothing))
    }

    /**
     * Resets chart zoom
     */
    fun resetChartZoom() {
        _chartZoom.value = null
    }

    /**
     * Zooms chart to specific time range
     */
    fun zoomChart(startTime: Instant, endTime: Instant) {
        _chartZoom.value = ChartZoomState(startTime, endTime)
    }

    /**
     * Gets current chart data for rendering
     */
    fun getChartData(chartType: ChartType): List<ChartDataPoint> {
        val history = chartDataHistory[chartType] ?: return emptyList()
        val data = history.toList()

        // Apply time range filtering
        val timeRange = getTimeRangeDuration()
        val cutoffTime = getCurrentTime() - timeRange

        val filteredData = data.filter { it.timestamp >= cutoffTime }

        // Apply zoom if active
        val zoomState = _chartZoom.value
        return if (zoomState != null) {
            filteredData.filter { it.timestamp >= zoomState.startTime && it.timestamp <= zoomState.endTime }
        } else {
            filteredData
        }
    }

    /**
     * Gets chart rendering parameters
     */
    fun getChartRenderingParams(chartType: ChartType): ChartRenderingParams {
        val config = _chartConfigurations.value[chartType] ?: ChartConfiguration.default()
        val data = getChartData(chartType)

        val minValue = data.minOfOrNull { it.value } ?: 0.0f
        val maxValue = data.maxOfOrNull { it.value } ?: 1.0f

        return ChartRenderingParams(
            chartType = chartType,
            color = getChartColor(chartType),
            lineWidth = config.lineWidth,
            showPoints = config.showDataPoints,
            smoothing = config.smoothing,
            yAxisMin = if (config.autoScale) minValue else config.manualMin,
            yAxisMax = if (config.autoScale) maxValue else config.manualMax,
            showGrid = config.showGrid,
            gridColor = chartTheme.gridColor,
            backgroundColor = chartTheme.backgroundColor
        )
    }

    // === ALERT MANAGEMENT ===

    /**
     * Updates alert settings
     */
    fun updateAlertSettings(settings: AlertSettings) {
        _alertSettings.value = settings
    }

    /**
     * Dismisses an alert
     */
    fun dismissAlert(alertId: String) {
        _activeAlerts.value = _activeAlerts.value.filter { it.id != alertId }
    }

    /**
     * Dismisses all alerts
     */
    fun dismissAllAlerts() {
        _activeAlerts.value = emptyList()
    }

    /**
     * Gets formatted alert message
     */
    fun getFormattedAlert(alert: PerformanceAlert): FormattedAlert {
        return FormattedAlert(
            id = alert.id,
            title = getAlertTitle(alert.type),
            message = alert.message,
            severity = alert.severity,
            timestamp = alert.timestamp,
            color = getAlertColor(alert.severity),
            icon = getAlertIcon(alert.type),
            action = getAlertAction(alert.type)
        )
    }

    // === DASHBOARD LAYOUT ===

    /**
     * Updates dashboard layout
     */
    fun updateDashboardLayout(layout: DashboardLayout) {
        _dashboardLayout.value = layout
    }

    /**
     * Toggles compact mode
     */
    fun toggleCompactMode() {
        _isCompactMode.value = !_isCompactMode.value
    }

    /**
     * Resets dashboard to default layout
     */
    fun resetDashboard() {
        _dashboardLayout.value = DashboardLayout.default()
        _chartConfigurations.value = getDefaultChartConfigurations()
        _isCompactMode.value = false
    }

    /**
     * Gets dashboard widgets for current layout
     */
    fun getDashboardWidgets(): List<DashboardWidget> {
        val layout = _dashboardLayout.value
        val isCompact = _isCompactMode.value

        return layout.widgets.filter { widget ->
            if (isCompact) widget.showInCompactMode else true
        }.map { widget ->
            widget.copy(
                data = getWidgetData(widget.type),
                size = if (isCompact) widget.compactSize else widget.normalSize
            )
        }
    }

    // === PERFORMANCE ANALYSIS ===

    /**
     * Runs performance analysis on current data
     */
    fun runPerformanceAnalysis(): PerformanceAnalysis {
        val collector = metricsCollector ?: return PerformanceAnalysis.empty()

        val timeRange = getTimeRangeDuration()
        val summary = collector.getPerformanceSummary(timeRange)
        val bottlenecks = collector.getBottlenecks()

        val analysis = PerformanceAnalysis(
            timestamp = getCurrentTime(),
            timeRange = timeRange,
            summary = summary,
            bottlenecks = bottlenecks,
            recommendations = generateRecommendations(bottlenecks),
            performanceGrade = calculatePerformanceGrade(summary.overallScore),
            regressions = detectPerformanceRegressions(),
            optimizationSuggestions = generateOptimizationSuggestions(summary)
        )

        _analysisResults.value = analysis
        return analysis
    }

    /**
     * Compares performance between two time periods
     */
    fun comparePerformance(
        baselineStart: Instant,
        baselineEnd: Instant,
        currentStart: Instant,
        currentEnd: Instant
    ): PerformanceComparison {
        val collector = metricsCollector ?: return PerformanceComparison.empty()

        // This would collect metrics for both periods and compare them
        val baselineData = getMetricsForPeriod(baselineStart, baselineEnd)
        val currentData = getMetricsForPeriod(currentStart, currentEnd)

        val comparison = PerformanceComparison(
            baselinePeriod = TimeRange(baselineStart, baselineEnd),
            currentPeriod = TimeRange(currentStart, currentEnd),
            frameTimeChange = calculatePercentageChange(
                baselineData.averageFrameTime,
                currentData.averageFrameTime
            ),
            memoryChange = calculatePercentageChange(
                baselineData.averageMemoryUsage.toFloat(),
                currentData.averageMemoryUsage.toFloat()
            ),
            cpuChange = calculatePercentageChange(
                baselineData.averageCpuUsage,
                currentData.averageCpuUsage
            ),
            overallImprovement = calculateOverallImprovement(baselineData, currentData),
            significantChanges = findSignificantChanges(baselineData, currentData)
        )

        _performanceComparison.value = comparison
        return comparison
    }

    // === DATA EXPORT ===

    /**
     * Exports current performance data
     */
    fun exportPerformanceData(format: ExportFormat, timeRange: Duration = 60.seconds): String {
        return when (format) {
            ExportFormat.JSON -> exportToJSON(timeRange)
            ExportFormat.CSV -> exportToCSV(timeRange)
            ExportFormat.PNG -> exportChartsToPNG(timeRange)
            ExportFormat.PDF -> exportToPDF(timeRange)
        }
    }

    /**
     * Exports chart as image
     */
    fun exportChart(chartType: ChartType, format: ImageFormat): ByteArray {
        val data = getChartData(chartType)
        val params = getChartRenderingParams(chartType)

        return when (format) {
            ImageFormat.PNG -> renderChartToPNG(data, params)
            ImageFormat.SVG -> renderChartToSVG(data, params)
            ImageFormat.JPEG -> renderChartToJPEG(data, params)
        }
    }

    // === PRIVATE METHODS ===

    private fun startDataCollection() {
        val collector = metricsCollector ?: return

        // Collect frame metrics
        collector.frameMetrics.collectLatest { frameMetrics ->
            addChartDataPoint(ChartType.FRAME_TIME, frameMetrics.currentFrameTime)
            addChartDataPoint(ChartType.FPS, frameMetrics.frameRate)
        }

        // Collect memory metrics
        collector.memoryMetrics.collectLatest { memoryMetrics ->
            addChartDataPoint(ChartType.MEMORY, memoryMetrics.totalAllocated.toFloat() / (1024 * 1024)) // MB
        }

        // Collect CPU metrics
        collector.cpuMetrics.collectLatest { cpuMetrics ->
            addChartDataPoint(ChartType.CPU, cpuMetrics.usage)
        }

        // Collect GPU metrics
        collector.gpuMetrics.collectLatest { gpuMetrics ->
            addChartDataPoint(ChartType.GPU, gpuMetrics.memoryUtilization * 100)
        }

        // Collect network metrics
        collector.networkMetrics.collectLatest { networkMetrics ->
            val mbps = networkMetrics.bytesDownloaded.toFloat() / (1024 * 1024) // MB/s approximation
            addChartDataPoint(ChartType.NETWORK, mbps)
        }

        // Collect alerts
        collector.alerts.collectLatest { alerts ->
            _activeAlerts.value = alerts.map { alert ->
                alert.copy(id = generateAlertId())
            }
        }
    }

    private fun addChartDataPoint(chartType: ChartType, value: Float) {
        val history = chartDataHistory[chartType] ?: return
        val dataPoint = ChartDataPoint(
            timestamp = getCurrentTime(),
            value = value,
            chartType = chartType
        )
        history.add(dataPoint)
    }

    private fun refreshChartData() {
        // Refresh chart data based on new time range
        // This would trigger UI updates
    }

    private fun getTimeRangeDuration(): Duration {
        return when (_timeRange.value) {
            TimeRangeSelection.LAST_30_SECONDS -> 30.seconds
            TimeRangeSelection.LAST_MINUTE -> 60.seconds
            TimeRangeSelection.LAST_5_MINUTES -> Duration.parse("PT5M")
            TimeRangeSelection.LAST_15_MINUTES -> Duration.parse("PT15M")
            TimeRangeSelection.LAST_HOUR -> Duration.parse("PT1H")
            TimeRangeSelection.CUSTOM -> Duration.parse("PT5M") // Default for custom
        }
    }

    private fun getCurrentTime(): Instant {
        return kotlinx.datetime.Clock.System.now()
    }

    private fun getChartColor(chartType: ChartType): String {
        return when (chartType) {
            ChartType.FRAME_TIME -> "#FF6B6B"
            ChartType.FPS -> "#4ECDC4"
            ChartType.MEMORY -> "#45B7D1"
            ChartType.CPU -> "#96CEB4"
            ChartType.GPU -> "#FFEAA7"
            ChartType.NETWORK -> "#DDA0DD"
        }
    }

    private fun getAlertTitle(type: AlertType): String {
        return when (type) {
            AlertType.FRAME_TIME -> "Frame Time Warning"
            AlertType.MEMORY -> "Memory Usage Alert"
            AlertType.CPU -> "CPU Usage Alert"
            AlertType.GPU -> "GPU Alert"
            AlertType.NETWORK -> "Network Alert"
            AlertType.BATTERY -> "Battery Alert"
            AlertType.THERMAL -> "Thermal Alert"
        }
    }

    private fun getAlertColor(severity: Severity): String {
        return when (severity) {
            Severity.LOW -> "#2ECC71"
            Severity.MEDIUM -> "#F39C12"
            Severity.HIGH -> "#E74C3C"
            Severity.CRITICAL -> "#8E44AD"
        }
    }

    private fun getAlertIcon(type: AlertType): String {
        return when (type) {
            AlertType.FRAME_TIME -> "🎬"
            AlertType.MEMORY -> "💾"
            AlertType.CPU -> "⚡"
            AlertType.GPU -> "🎮"
            AlertType.NETWORK -> "🌐"
            AlertType.BATTERY -> "🔋"
            AlertType.THERMAL -> "🌡️"
        }
    }

    private fun getAlertAction(type: AlertType): String? {
        return when (type) {
            AlertType.FRAME_TIME -> "Optimize Rendering"
            AlertType.MEMORY -> "Free Memory"
            AlertType.CPU -> "Reduce Load"
            AlertType.GPU -> "Optimize Graphics"
            else -> null
        }
    }

    private fun getWidgetData(type: WidgetType): Any {
        return when (type) {
            WidgetType.PERFORMANCE_SCORE -> metricsCollector?.currentMetrics?.value?.performanceScore ?: 0.0f
            WidgetType.FRAME_RATE -> metricsCollector?.frameMetrics?.value?.frameRate ?: 0.0f
            WidgetType.MEMORY_USAGE -> metricsCollector?.memoryMetrics?.value?.totalAllocated ?: 0L
            WidgetType.CPU_USAGE -> metricsCollector?.cpuMetrics?.value?.usage ?: 0.0f
            WidgetType.GPU_USAGE -> metricsCollector?.gpuMetrics?.value?.memoryUtilization ?: 0.0f
            WidgetType.ALERT_COUNT -> _activeAlerts.value.size
        }
    }

    private fun generateRecommendations(bottlenecks: List<PerformanceBottleneck>): List<String> {
        return bottlenecks.map { it.suggestion }.distinct()
    }

    private fun calculatePerformanceGrade(score: Float): PerformanceGrade {
        return when {
            score >= 90 -> PerformanceGrade.EXCELLENT
            score >= 75 -> PerformanceGrade.GOOD
            score >= 60 -> PerformanceGrade.FAIR
            score >= 40 -> PerformanceGrade.POOR
            else -> PerformanceGrade.CRITICAL
        }
    }

    private fun detectPerformanceRegressions(): List<PerformanceRegression> {
        // Implementation would analyze historical data for regressions
        return emptyList()
    }

    private fun generateOptimizationSuggestions(summary: PerformanceSummary): List<OptimizationSuggestion> {
        // Implementation would generate suggestions based on metrics
        return emptyList()
    }

    private fun getMetricsForPeriod(start: Instant, end: Instant): PeriodMetrics {
        // Implementation would aggregate metrics for the specified period
        return PeriodMetrics.empty()
    }

    private fun calculatePercentageChange(baseline: Float, current: Float): Float {
        if (baseline == 0.0f) return 0.0f
        return ((current - baseline) / baseline) * 100.0f
    }

    private fun calculateOverallImprovement(baseline: PeriodMetrics, current: PeriodMetrics): Float {
        // Implementation would calculate overall improvement score
        return 0.0f
    }

    private fun findSignificantChanges(baseline: PeriodMetrics, current: PeriodMetrics): List<SignificantChange> {
        // Implementation would identify significant performance changes
        return emptyList()
    }

    private fun exportToJSON(timeRange: Duration): String {
        // Implementation would export data to JSON format
        return "{}"
    }

    private fun exportToCSV(timeRange: Duration): String {
        // Implementation would export data to CSV format
        return ""
    }

    private fun exportChartsToPNG(timeRange: Duration): String {
        // Implementation would export charts as PNG images
        return ""
    }

    private fun exportToPDF(timeRange: Duration): String {
        // Implementation would export data to PDF report
        return ""
    }

    private fun renderChartToPNG(data: List<ChartDataPoint>, params: ChartRenderingParams): ByteArray {
        // Implementation would render chart to PNG
        return byteArrayOf()
    }

    private fun renderChartToSVG(data: List<ChartDataPoint>, params: ChartRenderingParams): ByteArray {
        // Implementation would render chart to SVG
        return byteArrayOf()
    }

    private fun renderChartToJPEG(data: List<ChartDataPoint>, params: ChartRenderingParams): ByteArray {
        // Implementation would render chart to JPEG
        return byteArrayOf()
    }

    private fun generateAlertId(): String {
        return "alert_${getCurrentTime().toEpochMilliseconds()}"
    }

    private fun setupDefaultDashboard() {
        _dashboardLayout.value = DashboardLayout.default()
        _chartConfigurations.value = getDefaultChartConfigurations()
    }

    private fun getDefaultChartConfigurations(): Map<ChartType, ChartConfiguration> {
        return ChartType.values().associateWith { ChartConfiguration.default() }
    }
}

// === ENUMS ===

enum class PerformanceTab {
    OVERVIEW, FRAME_RATE, MEMORY, CPU, GPU, NETWORK, ALERTS, ANALYSIS
}

enum class TimeRangeSelection {
    LAST_30_SECONDS, LAST_MINUTE, LAST_5_MINUTES, LAST_15_MINUTES, LAST_HOUR, CUSTOM
}

enum class ChartType {
    FRAME_TIME, FPS, MEMORY, CPU, GPU, NETWORK
}

enum class SmoothingLevel {
    NONE, LOW, MEDIUM, HIGH
}

enum class ExportFormat {
    JSON, CSV, PNG, PDF
}

enum class ImageFormat {
    PNG, SVG, JPEG
}

enum class WidgetType {
    PERFORMANCE_SCORE, FRAME_RATE, MEMORY_USAGE, CPU_USAGE, GPU_USAGE, ALERT_COUNT
}

enum class PerformanceGrade {
    EXCELLENT, GOOD, FAIR, POOR, CRITICAL
}

// === DATA CLASSES ===

data class ChartConfiguration(
    val enabled: Boolean,
    val autoScale: Boolean,
    val manualMin: Float,
    val manualMax: Float,
    val lineWidth: Float,
    val showDataPoints: Boolean,
    val showGrid: Boolean,
    val smoothing: SmoothingLevel
) {
    companion object {
        fun default() = ChartConfiguration(
            enabled = true,
            autoScale = true,
            manualMin = 0.0f,
            manualMax = 100.0f,
            lineWidth = 2.0f,
            showDataPoints = false,
            showGrid = true,
            smoothing = SmoothingLevel.LOW
        )
    }
}

data class ChartDataPoint(
    val timestamp: Instant,
    val value: Float,
    val chartType: ChartType
)

data class ChartZoomState(
    val startTime: Instant,
    val endTime: Instant
)

data class ChartRenderingParams(
    val chartType: ChartType,
    val color: String,
    val lineWidth: Float,
    val showPoints: Boolean,
    val smoothing: SmoothingLevel,
    val yAxisMin: Float,
    val yAxisMax: Float,
    val showGrid: Boolean,
    val gridColor: String,
    val backgroundColor: String
)

data class ChartTheme(
    val backgroundColor: String,
    val gridColor: String,
    val textColor: String,
    val axisColor: String
) {
    companion object {
        fun default() = ChartTheme(
            backgroundColor = "#2C2C2C",
            gridColor = "#555555",
            textColor = "#FFFFFF",
            axisColor = "#CCCCCC"
        )
    }
}

data class AlertSettings(
    val enabledTypes: Set<AlertType>,
    val severityThreshold: Severity,
    val maxAlerts: Int,
    val autoDelete: Boolean,
    val deleteAfter: Duration
) {
    companion object {
        fun default() = AlertSettings(
            enabledTypes = AlertType.values().toSet(),
            severityThreshold = Severity.MEDIUM,
            maxAlerts = 10,
            autoDelete = true,
            deleteAfter = Duration.parse("PT5M")
        )
    }
}

data class FormattedAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: Severity,
    val timestamp: Instant,
    val color: String,
    val icon: String,
    val action: String?
)

data class DashboardLayout(
    val widgets: List<DashboardWidget>,
    val columns: Int,
    val spacing: Float
) {
    companion object {
        fun default() = DashboardLayout(
            widgets = listOf(
                DashboardWidget(WidgetType.PERFORMANCE_SCORE, 0, 0, 2, 1),
                DashboardWidget(WidgetType.FRAME_RATE, 2, 0, 1, 1),
                DashboardWidget(WidgetType.MEMORY_USAGE, 0, 1, 1, 1),
                DashboardWidget(WidgetType.CPU_USAGE, 1, 1, 1, 1),
                DashboardWidget(WidgetType.GPU_USAGE, 2, 1, 1, 1),
                DashboardWidget(WidgetType.ALERT_COUNT, 0, 2, 3, 1)
            ),
            columns = 3,
            spacing = 8.0f
        )
    }
}

data class DashboardWidget(
    val type: WidgetType,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val showInCompactMode: Boolean = true,
    val normalSize: WidgetSize = WidgetSize.MEDIUM,
    val compactSize: WidgetSize = WidgetSize.SMALL,
    val data: Any? = null
)

enum class WidgetSize {
    SMALL, MEDIUM, LARGE
}

data class PerformanceAnalysis(
    val timestamp: Instant,
    val timeRange: Duration,
    val summary: PerformanceSummary,
    val bottlenecks: List<PerformanceBottleneck>,
    val recommendations: List<String>,
    val performanceGrade: PerformanceGrade,
    val regressions: List<PerformanceRegression>,
    val optimizationSuggestions: List<OptimizationSuggestion>
) {
    companion object {
        fun empty() = PerformanceAnalysis(
            timestamp = kotlinx.datetime.Clock.System.now(),
            timeRange = 0.seconds,
            summary = PerformanceSummary(
                TimeRange(kotlinx.datetime.Clock.System.now(), kotlinx.datetime.Clock.System.now()),
                FrameStats.empty(),
                MemoryStats.empty(),
                GPUStats.empty(),
                CPUStats.empty(),
                emptyList(),
                0.0f
            ),
            bottlenecks = emptyList(),
            recommendations = emptyList(),
            performanceGrade = PerformanceGrade.FAIR,
            regressions = emptyList(),
            optimizationSuggestions = emptyList()
        )
    }
}

data class PerformanceComparison(
    val baselinePeriod: TimeRange,
    val currentPeriod: TimeRange,
    val frameTimeChange: Float,
    val memoryChange: Float,
    val cpuChange: Float,
    val overallImprovement: Float,
    val significantChanges: List<SignificantChange>
) {
    companion object {
        fun empty() = PerformanceComparison(
            baselinePeriod = TimeRange(kotlinx.datetime.Clock.System.now(), kotlinx.datetime.Clock.System.now()),
            currentPeriod = TimeRange(kotlinx.datetime.Clock.System.now(), kotlinx.datetime.Clock.System.now()),
            frameTimeChange = 0.0f,
            memoryChange = 0.0f,
            cpuChange = 0.0f,
            overallImprovement = 0.0f,
            significantChanges = emptyList()
        )
    }
}

data class PerformanceRegression(
    val metric: String,
    val changePercent: Float,
    val detectedAt: Instant,
    val description: String
)

data class OptimizationSuggestion(
    val category: String,
    val suggestion: String,
    val expectedImprovement: String,
    val difficulty: String
)

data class PeriodMetrics(
    val averageFrameTime: Float,
    val averageMemoryUsage: Long,
    val averageCpuUsage: Float,
    val averageGpuUsage: Float
) {
    companion object {
        fun empty() = PeriodMetrics(0.0f, 0L, 0.0f, 0.0f)
    }
}

data class SignificantChange(
    val metric: String,
    val changePercent: Float,
    val direction: ChangeDirection,
    val significance: Float
)

enum class ChangeDirection {
    IMPROVEMENT, REGRESSION, NEUTRAL
}