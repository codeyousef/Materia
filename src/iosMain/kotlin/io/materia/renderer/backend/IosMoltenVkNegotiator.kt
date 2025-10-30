package io.materia.renderer.backend

import kotlinx.datetime.Clock
import platform.Foundation.NSProcessInfo

/**
 * iOS MoltenVK backend negotiator for iOS and visionOS.
 * Uses MoltenVK (Vulkan-to-Metal translation layer) with XR surface hooks.
 */
class IosMoltenVkNegotiator : AbstractBackendNegotiator() {

    override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport {
        // iOS MoltenVK capability detection
        // Would use Metal API and MoltenVK feature queries

        val features = mutableMapOf<BackendFeature, FeatureStatus>()

        // Apple Silicon has excellent compute shader support via Metal
        features[BackendFeature.COMPUTE] = FeatureStatus.SUPPORTED

        // Ray tracing on A15+/M1+ via Metal 3
        features[BackendFeature.RAY_TRACING] = if (supportsMetalRayTracing()) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.EMULATED
        }

        // XR support via ARKit (iOS) or native visionOS APIs
        features[BackendFeature.XR_SURFACE] = if (hasXRSupport()) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.MISSING
        }

        return DeviceCapabilityReport(
            deviceId = getIOSDeviceId(),
            driverVersion = "MoltenVK ${getMoltenVKVersion()} / Metal 3",
            osBuild = getIOSVersion(),
            featureFlags = features,
            preferredBackend = BackendId.VULKAN,
            limitations = detectLimitations(features),
            timestamp = Clock.System.now().toString()
        )
    }

    override suspend fun performPlatformInitialization(
        backendId: BackendId,
        surface: SurfaceConfig
    ): RenderSurfaceDescriptor {
        require(backendId == BackendId.VULKAN) {
            "iOS negotiator can only initialize VULKAN backend (via MoltenVK), got $backendId"
        }

        // Create iOS Metal surface via MoltenVK
        // Would use CAMetalLayer and VK_MVK_ios_surface or VK_MVK_macos_surface extension

        return RenderSurfaceDescriptor(
            surfaceId = "ios-moltenvk-surface-${Clock.System.now().toEpochMilliseconds()}",
            backendId = BackendId.VULKAN,
            width = surface.width,
            height = surface.height,
            colorFormat = surface.colorFormat,
            depthFormat = surface.depthFormat,
            presentMode = surface.presentMode,
            isXRSurface = surface.isXRSurface
        )
    }

    private fun supportsMetalRayTracing(): Boolean {
        // Check if device supports Metal 3 ray tracing
        // A15+ and M1+ chips support hardware ray tracing
        val deviceModel = getDeviceModel()

        // A15 Bionic (iPhone 13+), M1, M2 support ray tracing
        return deviceModel.contains("iPhone14", ignoreCase = true) || // iPhone 13
                deviceModel.contains("iPhone15", ignoreCase = true) || // iPhone 14
                deviceModel.contains("iPhone16", ignoreCase = true) || // iPhone 15
                deviceModel.contains("Mac14", ignoreCase = true) ||    // M1/M2 Macs
                deviceModel.contains("Mac15", ignoreCase = true)       // M3 Macs
    }

    private fun hasXRSupport(): Boolean {
        // Check for ARKit (iOS) or visionOS support
        val osVersion = getIOSVersion()

        // ARKit available on iOS 11+, native XR on visionOS
        return osVersion.contains("iOS") || osVersion.contains("visionOS")
    }

    private fun getIOSDeviceId(): String {
        return getDeviceModel()
    }

    private fun getDeviceModel(): String {
        // Would use UIDevice.currentDevice().model or similar
        // Placeholder implementation
        return "Apple-Device"
    }

    private fun getMoltenVKVersion(): String {
        // Would query MoltenVK runtime version
        return "1.2.x"
    }

    private fun getIOSVersion(): String {
        val processInfo = NSProcessInfo.processInfo
        val version = processInfo.operatingSystemVersion
        val versionString =
            "${version.majorVersion}.${version.minorVersion}.${version.patchVersion}"

        // Determine if iOS or visionOS
        return if (processInfo.operatingSystemVersionString.contains(
                "visionOS",
                ignoreCase = true
            )
        ) {
            "visionOS $versionString"
        } else {
            "iOS $versionString"
        }
    }

    private fun detectLimitations(features: Map<BackendFeature, FeatureStatus>): List<String> {
        val limitations = mutableListOf<String>()

        features.forEach { (feature, status) ->
            when (status) {
                FeatureStatus.EMULATED -> limitations.add("$feature: emulated via MoltenVK")
                FeatureStatus.MISSING -> limitations.add("$feature: not supported")
                FeatureStatus.SUPPORTED -> {} // No limitation
            }
        }

        return limitations
    }
}

/**
 * Factory function for creating iOS MoltenVK negotiator.
 */
actual fun createBackendNegotiator(): BackendNegotiator {
    return IosMoltenVkNegotiator()
}
