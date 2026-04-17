package io.materia.examples.benchmarks

import android.content.Intent
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.serialization.encodeToString

private const val BYTES_PER_MEGABYTE: Double = 1024.0 * 1024.0

object AndroidBenchmarkContract {
    const val extraBenchmarkMode: String = "io.materia.examples.benchmark.MODE"
    const val extraRepeatIndex: String = "io.materia.examples.benchmark.REPEAT_INDEX"
    const val extraAvdName: String = "io.materia.examples.benchmark.AVD_NAME"

    const val logTag: String = "MateriaBenchmark"
    const val resultPrefix: String = "MATERIA_BENCHMARK_RESULT:"
    const val resultPartPrefix: String = "MATERIA_BENCHMARK_RESULT_PART:"
    const val failurePrefix: String = "MATERIA_BENCHMARK_FAILURE:"
}

data class AndroidBenchmarkLaunch(
    val repeatIndex: Int,
    val avdName: String?,
    val runConfig: BenchmarkRunConfig
)

fun readAndroidBenchmarkLaunch(
    intent: Intent?,
    defaultConfig: BenchmarkRunConfig = BenchmarkDefaults.runConfig
): AndroidBenchmarkLaunch? {
    if (intent?.getBooleanExtra(AndroidBenchmarkContract.extraBenchmarkMode, false) != true) {
        return null
    }

    return AndroidBenchmarkLaunch(
        repeatIndex = intent.getIntExtra(AndroidBenchmarkContract.extraRepeatIndex, 1),
        avdName = intent.getStringExtra(AndroidBenchmarkContract.extraAvdName),
        runConfig = defaultConfig
    )
}

fun currentAndroidAppHeapEstimateMb(): Double {
    val runtime = Runtime.getRuntime()
    val managedBytes = runtime.totalMemory() - runtime.freeMemory()
    val nativeBytes = Debug.getNativeHeapAllocatedSize()
    return (managedBytes + nativeBytes) / BYTES_PER_MEGABYTE
}

fun buildAndroidBenchmarkEnvironment(
    backend: String,
    deviceName: String,
    driverVersion: String?,
    avdName: String?,
    notes: List<String> = listOf("Android emulator benchmark", "Host-GPU-assisted")
): BenchmarkEnvironment =
    BenchmarkEnvironment(
        platform = "Android",
        backend = backend,
        environmentLabel = "${avdName ?: "Android device"} / $backend / $deviceName",
        deviceName = deviceName,
        driverVersion = driverVersion,
        osName = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        runtimeVersion = "Android ${Build.VERSION.RELEASE}",
        emulatorName = avdName,
        notes = notes
    )

fun emitAndroidBenchmarkCapture(capture: BenchmarkCapture) {
    val payload = BenchmarkJson.compactCodec.encodeToString(capture)
    val chunks = payload.chunked(3_000)
    if (chunks.size == 1) {
        Log.i(
            AndroidBenchmarkContract.logTag,
            AndroidBenchmarkContract.resultPrefix + payload
        )
        return
    }

    chunks.forEachIndexed { index, chunk ->
        Log.i(
            AndroidBenchmarkContract.logTag,
            AndroidBenchmarkContract.resultPartPrefix + "${index + 1}/${chunks.size}:$chunk"
        )
    }
}

fun emitAndroidBenchmarkFailure(scene: String, message: String, throwable: Throwable? = null) {
    Log.e(
        AndroidBenchmarkContract.logTag,
        AndroidBenchmarkContract.failurePrefix + "$scene|$message",
        throwable
    )
}

class AndroidBenchmarkSession(
    repeatIndex: Int,
    private val workload: BenchmarkWorkload,
    private val environment: BenchmarkEnvironment,
    baselineHeapMb: Double?,
    private val bootToFirstFrameMs: Double,
    sampleNotes: List<String> = emptyList()
) {
    private val recorder = BenchmarkRecorder(
        repeatIndex = repeatIndex,
        baselineHeapMb = baselineHeapMb,
        memoryMetricKind = BenchmarkMemoryMetricKind.ANDROID_APP_HEAP_ESTIMATE_DELTA_MB,
        notes = sampleNotes
    )
    private var warmupFramesRemaining: Int = workload.warmupFrames
    private var measuredFramesRemaining: Int = workload.measuredFrames

    fun onFrame(frameTimeMs: Double): BenchmarkCapture? {
        val heapUsageMb = currentAndroidAppHeapEstimateMb()
        if (warmupFramesRemaining > 0) {
            recorder.observeHeapUsage(heapUsageMb)
            warmupFramesRemaining -= 1
            return null
        }

        recorder.recordFrame(frameTimeMs, heapUsageMb)
        measuredFramesRemaining -= 1

        return if (measuredFramesRemaining <= 0) {
            BenchmarkCapture(
                workload = workload,
                environment = environment,
                sample = recorder.build(bootToFirstFrameMs)
            )
        } else {
            null
        }
    }
}
