package io.materia.examples.benchmarks

import io.materia.core.platform.getMemoryUsage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLCanvasElement
import kotlin.coroutines.resume
import kotlin.js.Promise

data class BrowserBenchmarkParams(
    val repeatIndex: Int,
    val reportUrl: String,
    val maxDurationMs: Int,
    val runConfig: BenchmarkRunConfig
)

fun readBrowserBenchmarkParams(defaultConfig: BenchmarkRunConfig = BenchmarkDefaults.runConfig): BrowserBenchmarkParams? {
    val injectedParams = window.asDynamic().__materiaBenchmarkParams
    val params = if (js("typeof injectedParams !== 'undefined' && injectedParams !== null") as Boolean) {
        buildMap<String, String> {
            val keys = js("Object.keys(injectedParams)") as Array<String>
            keys.forEach { key ->
                val value = injectedParams[key]
                if (value != null) {
                    put(key, value.unsafeCast<String>())
                }
            }
        }
    } else {
        window.location.search
        .removePrefix("?")
        .split("&")
        .mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val separatorIndex = pair.indexOf('=')
            val rawKey = if (separatorIndex >= 0) pair.substring(0, separatorIndex) else pair
            val rawValue = if (separatorIndex >= 0) pair.substring(separatorIndex + 1) else ""
            js("decodeURIComponent")(rawKey).unsafeCast<String>() to js("decodeURIComponent")(rawValue).unsafeCast<String>()
        }
        .toMap()
    }
    val enabled = params["benchmark"] == "1"
    if (!enabled) return null

    val repeatIndex = params["repeatIndex"]?.toIntOrNull() ?: 1
    val reportUrl = params["reportUrl"]?.takeIf { it.isNotBlank() }
        ?: "/__benchmark_report__"
    val width = params["width"]?.toIntOrNull() ?: defaultConfig.width
    val height = params["height"]?.toIntOrNull() ?: defaultConfig.height
    val warmupFrames = params["warmupFrames"]?.toIntOrNull() ?: defaultConfig.warmupFrames
    val measuredFrames = params["measuredFrames"]?.toIntOrNull() ?: defaultConfig.measuredFrames
    val repeats = params["repeats"]?.toIntOrNull() ?: defaultConfig.repeats
    val maxDurationMs = params["maxDurationMs"]?.toIntOrNull() ?: 120_000

    return BrowserBenchmarkParams(
        repeatIndex = repeatIndex,
        reportUrl = reportUrl,
        maxDurationMs = maxDurationMs,
        runConfig = BenchmarkRunConfig(
            width = width,
            height = height,
            warmupFrames = warmupFrames,
            measuredFrames = measuredFrames,
            repeats = repeats
        )
    )
}

fun configureBenchmarkCanvas(canvas: HTMLCanvasElement, config: BenchmarkRunConfig) {
    canvas.width = config.width
    canvas.height = config.height
    canvas.style.width = "${config.width}px"
    canvas.style.height = "${config.height}px"
}

fun detectBrowserVersion(): String? {
    val userAgent = window.navigator.userAgent
    return Regex("(Chrome|Chromium)/([0-9.]+)")
        .find(userAgent)
        ?.groupValues
        ?.getOrNull(2)
        ?.let { "Chrome $it" }
}

fun currentJsHeapUsageSample(): Pair<Double?, BenchmarkMemoryMetricKind> {
    val hasPerformanceMemory = try {
        js("typeof performance !== 'undefined' && !!performance.memory") as Boolean
    } catch (_: Throwable) {
        false
    }

    if (!hasPerformanceMemory) {
        return null to BenchmarkMemoryMetricKind.UNAVAILABLE
    }

    val usedBytes = getMemoryUsage().used
    return (usedBytes / (1024.0 * 1024.0)) to BenchmarkMemoryMetricKind.JS_HEAP_DELTA_MB
}

suspend fun awaitAnimationFrameTimestamp(): Double =
    suspendCancellableCoroutine { continuation ->
        window.requestAnimationFrame { timestamp ->
            continuation.resume(timestamp)
        }
    }

suspend fun yieldBrowserFrame() {
    Promise<Unit> { resolve, _ ->
        window.setTimeout({ resolve(Unit) }, 0)
    }.await()
}

class BrowserBenchmarkWatchdog(
    private val reportUrl: String,
    private val scene: String,
    maxDurationMs: Int
) {
    private var currentStage: String = "startup"
    private var completed: Boolean = false
    private val timeoutHandle = window.setTimeout({
        if (completed) return@setTimeout
        val payloadObject = js("{}")
        payloadObject.scene = scene
        payloadObject.error = "Timed out during $currentStage after ${maxDurationMs}ms"
        val payload = JSON.stringify(payloadObject)
        window.fetch(
            reportUrl,
            jsonPostRequestInit(payload)
        )
    }, maxDurationMs)

    fun markStage(stage: String) {
        currentStage = stage
    }

    fun complete() {
        if (completed) return
        completed = true
        window.clearTimeout(timeoutHandle)
    }
}

suspend fun postBenchmarkCapture(reportUrl: String, capture: BenchmarkCapture) {
    val payload = BenchmarkJson.codec.encodeToString(capture)
    window.asDynamic().__materiaBenchmarkCapture = payload
    val init = jsonPostRequestInit(payload)
    val response = window.fetch(reportUrl, init).await()
    if (!response.ok) {
        error("Failed to post benchmark capture: ${response.status} ${response.statusText}")
    }
}

suspend fun postBenchmarkFailure(reportUrl: String, scene: String, message: String) {
    val payload = js("{}")
    payload.scene = scene
    payload.error = message
    val init = jsonPostRequestInit(JSON.stringify(payload))
    window.fetch(reportUrl, init).await()
}

private fun jsonPostRequestInit(body: String): dynamic {
    val init = js("{}")
    init.method = "POST"
    init.headers = js("({ 'Content-Type': 'application/json' })")
    init.body = body
    return init
}

fun buildWebBenchmarkEnvironment(
    backend: String,
    deviceName: String,
    driverVersion: String?,
    browserVersion: String = detectBrowserVersion() ?: "Chrome (unknown)",
    notes: List<String> = listOf("Chrome automation benchmark", "WebGPU requested")
): BenchmarkEnvironment {
    val resolvedDeviceName = deviceName.ifBlank { "Adapter name unavailable" }
    val resolvedDriverVersion = driverVersion?.takeIf { it.isNotBlank() }
    return BenchmarkEnvironment(
        platform = "Web",
        backend = backend,
        environmentLabel = "$browserVersion / $backend / $resolvedDeviceName",
        deviceName = resolvedDeviceName,
        driverVersion = resolvedDriverVersion,
        browserVersion = browserVersion,
        notes = notes
    )
}
