package io.materia.tests

import io.materia.renderer.backend.*
import io.materia.renderer.metrics.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Test harness for backend integration testing.
 * Loads fixtures, orchestrates parity matrix snapshots, and captures artifacts.
 */
class BackendTestHarness {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load device capability fixtures from resources.
     */
    fun loadDeviceCapabilityFixtures(resourcePath: String = "tests/common/resources/device-capabilities.json"): List<DeviceCapabilityFixture> {
        val fixturesFile = File(resourcePath)
        if (!fixturesFile.exists()) {
            println("Warning: Fixtures file not found at $resourcePath")
            return emptyList()
        }

        val content = fixturesFile.readText()
        val parsed = json.decodeFromString<DeviceCapabilitiesJson>(content)

        return parsed.profiles.map { profile ->
            DeviceCapabilityFixture(
                profileId = profile.profileId,
                deviceName = profile.deviceName,
                report = DeviceCapabilityReport(
                    deviceId = "${profile.vendorId}:${profile.productId}",
                    driverVersion = profile.driverVersion,
                    osBuild = profile.osBuild,
                    featureFlags = profile.capabilities.mapKeys { entry ->
                        BackendFeature.valueOf(entry.key)
                    }.mapValues { entry ->
                        FeatureStatus.valueOf(entry.value)
                    },
                    preferredBackend = profile.preferredBackend?.let { BackendId.valueOf(it) },
                    limitations = listOf(profile.notes),
                    timestamp = "2025-10-06T00:00:00Z"
                )
            )
        }
    }

    /**
     * Simulate backend negotiation with a fixture.
     */
    suspend fun simulateNegotiationWithFixture(fixture: DeviceCapabilityFixture): BackendSelection {
        val negotiator = createBackendNegotiator()

        val profiles = listOf(
            RenderingBackendProfile(
                backendId = BackendId.WEBGPU,
                supportedFeatures = setOf(
                    BackendFeature.COMPUTE,
                    BackendFeature.RAY_TRACING,
                    BackendFeature.XR_SURFACE
                ),
                performanceBudget = PerformanceBudget(),
                fallbackPriority = 1,
                apiVersion = "WebGPU 1.0",
                platformTargets = listOf(PlatformTarget.WEB)
            ),
            RenderingBackendProfile(
                backendId = BackendId.VULKAN,
                supportedFeatures = setOf(
                    BackendFeature.COMPUTE,
                    BackendFeature.RAY_TRACING,
                    BackendFeature.XR_SURFACE
                ),
                performanceBudget = PerformanceBudget(),
                fallbackPriority = 2,
                apiVersion = "Vulkan 1.3",
                platformTargets = listOf(
                    PlatformTarget.DESKTOP,
                    PlatformTarget.ANDROID,
                    PlatformTarget.IOS
                )
            )
        )

        return negotiator.selectBackend(fixture.report, profiles)
    }

    /**
     * Capture parity matrix snapshot for documentation.
     */
    fun captureParitySnapshot(report: ParityReport, outputPath: String) {
        val snapshot = buildString {
            appendLine("# Feature Parity Snapshot")
            appendLine()
            appendLine(report.summary())
            appendLine()
            appendLine("## Details")
            report.details.forEach { feature ->
                appendLine("- **${feature.featureId}**: WebGPU=${feature.webgpuStatus}, Vulkan=${feature.vulkanStatus}")
                if (feature.notes.isNotBlank()) {
                    appendLine("  - ${feature.notes}")
                }
            }
        }

        File(outputPath).writeText(snapshot)
        println("Parity snapshot saved to: $outputPath")
    }
}

/**
 * Device capability fixture loaded from test resources.
 */
data class DeviceCapabilityFixture(
    val profileId: String,
    val deviceName: String,
    val report: DeviceCapabilityReport
)

/**
 * JSON structure for parsing device capabilities from fixtures.
 */
@kotlinx.serialization.Serializable
private data class DeviceCapabilitiesJson(
    val profiles: List<DeviceProfileJson>
)

@kotlinx.serialization.Serializable
private data class DeviceProfileJson(
    val profileId: String,
    val deviceName: String,
    val vendorId: String,
    val productId: String,
    val driverVersion: String,
    val osBuild: String,
    val gpuFamily: String,
    val capabilities: Map<String, String>,
    val performanceBaseline: PerformanceBaselineJson,
    val preferredBackend: String?,
    val notes: String
)

@kotlinx.serialization.Serializable
private data class PerformanceBaselineJson(
    val targetFps: Int,
    val minFps: Int,
    val initBudgetMs: Int
)
