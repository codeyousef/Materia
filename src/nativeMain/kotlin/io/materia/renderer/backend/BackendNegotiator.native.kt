package io.materia.renderer.backend

import io.materia.datetime.currentTimeMillis
import io.materia.datetime.currentTimeString
import io.materia.renderer.currentApplePlatformInfo

private class AppleMoltenVkNegotiator : AbstractBackendNegotiator() {
    override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport {
        val platformInfo = currentApplePlatformInfo()
        val limitations = buildList {
            if (!platformInfo.supportsRayTracing) {
                add("Ray tracing may fall back to Metal/MoltenVK emulation on ${platformInfo.deviceId}")
            }
            if (!platformInfo.supportsXrSurface && platformInfo.platformName == "macOS") {
                add("XR surface integration depends on an external headset runtime on macOS")
            }
        }

        return DeviceCapabilityReport(
            deviceId = platformInfo.deviceId,
            driverVersion = platformInfo.driverVersion,
            osBuild = platformInfo.osBuild,
            featureFlags = mapOf(
                BackendFeature.COMPUTE to FeatureStatus.SUPPORTED,
                BackendFeature.RAY_TRACING to FeatureStatus.SUPPORTED,
                BackendFeature.XR_SURFACE to FeatureStatus.SUPPORTED
            ),
            preferredBackend = BackendId.VULKAN,
            limitations = limitations,
            timestamp = currentTimeString()
        )
    }

    override suspend fun performPlatformInitialization(
        backendId: BackendId,
        surface: SurfaceConfig
    ): RenderSurfaceDescriptor {
        require(backendId == BackendId.VULKAN) {
            "Apple negotiator can only initialize VULKAN backend (via MoltenVK), got $backendId"
        }
        require(surface.width > 0 && surface.height > 0) {
            "Apple MoltenVK surface requires positive dimensions, got ${surface.width}x${surface.height}"
        }

        return RenderSurfaceDescriptor(
            surfaceId = "apple-moltenvk-surface-${currentTimeMillis()}",
            backendId = BackendId.VULKAN,
            width = surface.width,
            height = surface.height,
            colorFormat = surface.colorFormat,
            depthFormat = surface.depthFormat,
            presentMode = surface.presentMode,
            isXRSurface = surface.isXRSurface
        )
    }
}

actual fun createBackendNegotiator(): BackendNegotiator = AppleMoltenVkNegotiator()
