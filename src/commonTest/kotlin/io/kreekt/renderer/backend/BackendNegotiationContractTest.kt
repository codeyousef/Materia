package io.kreekt.renderer.backend

import io.kreekt.renderer.backend.BackendFeature.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Contract tests for [BackendNegotiator] using a deterministic in-memory implementation.
 */
class BackendNegotiationContractTest {

    @Test
    fun testHappyPath_WebGPU_InitializesSuccessfully() = runTest {
        val negotiator = TestBackendNegotiator(capabilities(preferred = BackendId.WEBGPU))
        val selection = negotiator.selectBackend(negotiator.detectCapabilities(CapabilityRequest()), profiles())
        val handle = negotiator.initializeBackend(selection, SurfaceConfig(width = 1280, height = 720))

        assertEquals(BackendId.WEBGPU, selection.backendId)
        assertEquals(SelectionReason.PREFERRED, selection.reason)
        assertEquals(BackendId.WEBGPU, handle.backendId)
        assertTrue(handle.surfaceDescriptor.matchesDimensions(1280, 720))
    }

    @Test
    fun testHappyPath_Vulkan_InitializesSuccessfully() = runTest {
        val negotiator = TestBackendNegotiator(capabilities(preferred = BackendId.VULKAN))
        val selection = negotiator.selectBackend(negotiator.detectCapabilities(CapabilityRequest()), profiles())
        val handle = negotiator.initializeBackend(selection, SurfaceConfig(width = 1920, height = 1080))

        assertEquals(BackendId.VULKAN, selection.backendId)
        assertEquals(SelectionReason.PREFERRED, selection.reason)
        assertEquals(1920, handle.surfaceDescriptor.width)
        assertEquals(1080, handle.surfaceDescriptor.height)
    }

    @Test
    fun testFallbackDenied_NoBackendMeetsParity() = runTest {
        val report = capabilities(
            preferred = null,
            featureStatus = mapOf(
                COMPUTE to FeatureStatus.MISSING,
                RAY_TRACING to FeatureStatus.MISSING,
                XR_SURFACE to FeatureStatus.MISSING
            ),
            limitations = listOf("Compute shaders unavailable")
        )
        val negotiator = TestBackendNegotiator(report)
        val selection = negotiator.selectBackend(report, profiles())

        assertEquals(SelectionReason.FAILED, selection.reason)
        assertTrue(selection.errorMessage!!.contains("Missing required features"))
    }

    @Test
    fun testInitializationTimeout_ExceedsBudget() = runTest {
        val negotiator = TestBackendNegotiator(
            report = capabilities(preferred = BackendId.WEBGPU),
            initializationDelayMs = 6_000
        )
        val selection = negotiator.selectBackend(negotiator.detectCapabilities(CapabilityRequest()), profiles())

        assertFailsWith<BackendInitializationException> {
            negotiator.initializeBackend(selection, SurfaceConfig(width = 640, height = 480))
        }
    }

    @Test
    fun testDeviceLossRecovery_SingleRetryAttempt() = runTest {
        val negotiator = TestBackendNegotiator(
            report = capabilities(preferred = BackendId.WEBGPU),
            shouldFailInitialization = true
        )
        val selection = negotiator.selectBackend(negotiator.detectCapabilities(CapabilityRequest()), profiles())

        assertFailsWith<BackendInitializationException> {
            negotiator.initializeBackend(selection, SurfaceConfig(width = 800, height = 600))
        }
    }

    @Test
    fun testCapabilityDetection_PopulatesTelemetryFields() = runTest {
        val report = capabilities(preferred = BackendId.WEBGPU)
        val negotiator = TestBackendNegotiator(report)

        val detected = negotiator.detectCapabilities(CapabilityRequest(includeDebugInfo = true))

        assertTrue(detected.deviceId.isNotBlank())
        assertTrue(detected.driverVersion.isNotBlank())
        assertEquals(BackendId.WEBGPU, detected.preferredBackend)
        assertEquals(FeatureStatus.SUPPORTED, detected.featureFlags[COMPUTE])
    }

    @Test
    fun testBackendSelection_PrefersPriority() = runTest {
        val report = capabilities(preferred = null)
        val negotiator = TestBackendNegotiator(report)
        val selection = negotiator.selectBackend(report, profiles())

        assertEquals(BackendId.WEBGPU, selection.backendId)
        assertEquals(SelectionReason.FALLBACK, selection.reason)
    }

    @Test
    fun testBackendInitialization_CreatesSurfaceDescriptor() = runTest {
        val negotiator = TestBackendNegotiator(capabilities(preferred = BackendId.VULKAN))
        val selection = negotiator.selectBackend(negotiator.detectCapabilities(CapabilityRequest()), profiles())
        val surface = SurfaceConfig(width = 1600, height = 900, isXRSurface = false)
        val handle = negotiator.initializeBackend(selection, surface)

        assertEquals("surface-vulkan", handle.surfaceDescriptor.surfaceId)
        assertEquals(BackendId.VULKAN, handle.surfaceDescriptor.backendId)
        assertEquals(1600, handle.surfaceDescriptor.width)
        assertEquals(900, handle.surfaceDescriptor.height)
    }

    private fun profiles(): List<RenderingBackendProfile> = listOf(
        RenderingBackendProfile(
            backendId = BackendId.WEBGPU,
            supportedFeatures = setOf(COMPUTE, RAY_TRACING, XR_SURFACE),
            performanceBudget = PerformanceBudget(),
            fallbackPriority = 0,
            apiVersion = "WebGPU 1.0",
            platformTargets = listOf(PlatformTarget.WEB, PlatformTarget.DESKTOP)
        ),
        RenderingBackendProfile(
            backendId = BackendId.VULKAN,
            supportedFeatures = setOf(COMPUTE, RAY_TRACING, XR_SURFACE),
            performanceBudget = PerformanceBudget(targetFps = 60, minFps = 30),
            fallbackPriority = 1,
            apiVersion = "Vulkan 1.3",
            platformTargets = listOf(PlatformTarget.DESKTOP)
        )
    )

    private fun capabilities(
        preferred: BackendId?,
        featureStatus: Map<BackendFeature, FeatureStatus> = mapOf(
            COMPUTE to FeatureStatus.SUPPORTED,
            RAY_TRACING to FeatureStatus.SUPPORTED,
            XR_SURFACE to FeatureStatus.SUPPORTED
        ),
        limitations: List<String> = emptyList()
    ): DeviceCapabilityReport {
        return DeviceCapabilityReport(
            deviceId = "10DE:2684",
            driverVersion = "551.23",
            osBuild = "Windows 11 22631",
            featureFlags = featureStatus,
            preferredBackend = preferred,
            limitations = limitations,
            timestamp = "2024-01-01T00:00:00Z"
        )
    }

    private class TestBackendNegotiator(
        private val report: DeviceCapabilityReport,
        private val initializationDelayMs: Long = 20,
        private val shouldFailInitialization: Boolean = false
    ) : AbstractBackendNegotiator() {

        override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport = report

        override suspend fun selectBackend(
            report: DeviceCapabilityReport,
            profiles: List<RenderingBackendProfile>
        ): BackendSelection {
            val preferred = report.preferredBackend?.let { preferredId ->
                profiles.firstOrNull { it.backendId == preferredId && validateParityRequirements(it, report) }
            }
            if (preferred != null) {
                return BackendSelection(
                    backendId = preferred.backendId,
                    reason = SelectionReason.PREFERRED,
                    parityMatrix = null
                )
            }
            return super.selectBackend(report, profiles)
        }

        override suspend fun performPlatformInitialization(
            backendId: BackendId,
            surface: SurfaceConfig
        ): RenderSurfaceDescriptor {
            delay(initializationDelayMs)
            if (shouldFailInitialization) {
                throw IllegalStateException("Simulated device loss during initialization")
            }
            return RenderSurfaceDescriptor(
                surfaceId = "surface-${backendId.name.lowercase()}",
                backendId = backendId,
                width = surface.width,
                height = surface.height,
                colorFormat = surface.colorFormat,
                depthFormat = surface.depthFormat,
                presentMode = surface.presentMode,
                isXRSurface = surface.isXRSurface
            )
        }
    }
}
