package io.kreekt.telemetry

import io.kreekt.renderer.backend.BackendFeature
import io.kreekt.renderer.backend.BackendId
import io.kreekt.renderer.backend.DeviceCapabilityReport
import io.kreekt.renderer.backend.FeatureStatus
import io.kreekt.renderer.metrics.InitializationStats
import io.kreekt.renderer.metrics.PerformanceAssessment
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TelemetryEventContractTest {

    private fun TestScope.newEmitter(
        transmitter: RecordingTransmitter,
        json: Json = Json { ignoreUnknownKeys = false }
    ): BackendTelemetryEmitter {
        return BackendTelemetryEmitter(
            transmitter = transmitter,
            json = json,
            scope = this,
            clock = { currentTime }
        )
    }

    @Test
    fun testDeniedEvent_PayloadCompleteness() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitDeniedEvent(
            report = sampleReport(limitations = listOf("Missing ray tracing")),
            reason = "Ray tracing unavailable"
        )

        advanceUntilIdle()
        emitter.shutdown()

        val payload = transmitter.latestPayload()
        assertEquals("DENIED", payload.eventType)
        assertEquals(BackendFeature.values().size, payload.featureFlags?.size)
        assertTrue(payload.limitations?.isNotEmpty() == true)
        assertTrue(payload.callStack.size >= 3)
    }

    @Test
    fun testInitializedEvent_IncludesPerformanceMetrics() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitInitializedEvent(
            backendId = BackendId.VULKAN,
            report = sampleReport(),
            stats = InitializationStats(
                backendId = BackendId.VULKAN,
                initTimeMs = 1_200,
                withinBudget = true
            )
        )

        advanceUntilIdle()
        emitter.shutdown()

        val payload = transmitter.latestPayload()
        assertEquals("INITIALIZED", payload.eventType)
        assertNotNull(payload.performance)
        assertEquals(1_200, payload.performance?.initMs)
    }

    @Test
    fun testPrivacyGuardrail_RejectsPersonalInfo() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        val report = sampleReport(deviceId = "user@example.com")
        emitter.emitDeniedEvent(report, reason = "Fallback requested")

        advanceUntilIdle()
        emitter.shutdown()

        val sessionId = transmitter.latestPayload().sessionId
        assertEquals(64, sessionId.length)
        assertFalse(sessionId.contains('@'))
    }

    @Test
    fun testSessionId_AnonymizedHash() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitInitializedEvent(
            backendId = BackendId.WEBGPU,
            report = sampleReport(),
            stats = InitializationStats(BackendId.WEBGPU, initTimeMs = 900, withinBudget = true)
        )

        advanceUntilIdle()
        emitter.shutdown()

        val sessionId = transmitter.latestPayload().sessionId
        assertEquals(64, sessionId.length)
        assertTrue(sessionId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun testFeatureFlags_AllParityFeatures() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitInitializedEvent(
            backendId = BackendId.VULKAN,
            report = sampleReport(),
            stats = InitializationStats(BackendId.VULKAN, initTimeMs = 800, withinBudget = true)
        )

        advanceUntilIdle()
        emitter.shutdown()

        val flags = transmitter.latestPayload().featureFlags
        assertNotNull(flags)
        BackendFeature.values().forEach { feature ->
            assertEquals("SUPPORTED", flags[feature.name])
        }
    }

    @Test
    fun testCallStack_MinimumThreeFrames() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitInitializedEvent(
            backendId = BackendId.VULKAN,
            report = sampleReport(),
            stats = InitializationStats(BackendId.VULKAN, initTimeMs = 750, withinBudget = true)
        )

        advanceUntilIdle()
        emitter.shutdown()

        val payload = transmitter.latestPayload()
        assertTrue(payload.callStack.size >= 3)
    }

    @Test
    fun testRetryLogic_ThreeAttempts() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime }, failuresBeforeSuccess = 2)
        val emitter = newEmitter(transmitter)

        emitter.emitInitializedEvent(
            backendId = BackendId.WEBGPU,
            report = sampleReport(),
            stats = InitializationStats(BackendId.WEBGPU, initTimeMs = 950, withinBudget = true)
        )

        advanceUntilIdle()
        emitter.shutdown()

        assertEquals(3, transmitter.attempts)
        assertTrue(emitter.pendingEvents().isEmpty())
    }

    @Test
    fun testEventTransmission_Within500ms() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        val start = currentTime
        emitter.emitInitializedEvent(
            backendId = BackendId.WEBGPU,
            report = sampleReport(),
            stats = InitializationStats(BackendId.WEBGPU, initTimeMs = 640, withinBudget = true)
        )

        advanceUntilIdle()
        emitter.shutdown()

        val firstSend = transmitter.firstSendTimestamp ?: error("No telemetry sent")
        assertTrue(firstSend - start <= 500)
    }

    @Test
    fun testPerformanceDegradedEvent_OptionalPerformanceObject() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitPerformanceDegradedEvent(
            backendId = BackendId.VULKAN,
            report = sampleReport(),
            assessment = PerformanceAssessment(
                backendId = BackendId.VULKAN,
                avgFps = 48.0,
                minFps = 27.0,
                maxFps = 90.0,
                withinBudget = false,
                notes = "Frame budget exceeded",
                frameCount = 120
            )
        )

        advanceUntilIdle()
        emitter.shutdown()

        val payload = transmitter.latestPayload()
        assertEquals("PERFORMANCE_DEGRADED", payload.eventType)
        val performance = payload.performance
        assertNotNull(performance)
        assertEquals(48.0, performance.avgFps)
        assertTrue(payload.limitations?.contains("Frame budget exceeded") == true)
    }

    @Test
    fun testPerformanceDegradation_EmitsEvent() = runTest {
        val transmitter = RecordingTransmitter(clock = { currentTime })
        val emitter = newEmitter(transmitter)

        emitter.emitPerformanceDegradedEvent(
            backendId = BackendId.WEBGPU,
            report = sampleReport(),
            assessment = PerformanceAssessment(
                backendId = BackendId.WEBGPU,
                avgFps = 50.0,
                minFps = 25.0,
                maxFps = 70.0,
                withinBudget = false,
                notes = "Average FPS below target",
                frameCount = 90
            )
        )

        advanceUntilIdle()
        emitter.shutdown()

        val payload = transmitter.latestPayload()
        assertEquals("PERFORMANCE_DEGRADED", payload.eventType)
        assertTrue(payload.limitations?.any { it.contains("Average FPS") } == true)
    }

    private fun sampleReport(
        deviceId: String = "10DE:2684",
        limitations: List<String> = emptyList()
    ): DeviceCapabilityReport {
        return DeviceCapabilityReport(
            deviceId = deviceId,
            driverVersion = "551.23",
            osBuild = "Windows 11 22631",
            featureFlags = mapOf(
                BackendFeature.COMPUTE to FeatureStatus.SUPPORTED,
                BackendFeature.RAY_TRACING to FeatureStatus.SUPPORTED,
                BackendFeature.XR_SURFACE to FeatureStatus.SUPPORTED
            ),
            preferredBackend = BackendId.VULKAN,
            limitations = limitations,
            timestamp = "2024-01-01T00:00:00Z"
        )
    }

    private class RecordingTransmitter(
        private val clock: () -> Long,
        private val failuresBeforeSuccess: Int = 0
    ) : TelemetryTransmitter {
        private val sentPayloads = mutableListOf<TelemetryPayload>()
        var attempts: Int = 0
            private set

        var firstSendTimestamp: Long? = null
            private set

        override suspend fun send(payload: TelemetryPayload) {
            attempts += 1
            firstSendTimestamp = firstSendTimestamp ?: clock()

            if (attempts <= failuresBeforeSuccess) {
                throw RuntimeException("Simulated telemetry failure")
            }

            sentPayloads += payload
        }

        fun latestPayload(): TelemetryPayload =
            sentPayloads.lastOrNull() ?: error("No payload transmitted")
    }
}
