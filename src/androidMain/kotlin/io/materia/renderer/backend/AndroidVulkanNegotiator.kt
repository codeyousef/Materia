package io.materia.renderer.backend

import kotlinx.datetime.Clock

/**
 * Android Vulkan backend negotiator for Android API 33+ devices.
 * Uses native Vulkan API with Android-specific extensions.
 */
class AndroidVulkanNegotiator : AbstractBackendNegotiator() {

    override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport {
        // Android Vulkan capability detection
        // Would use Android NDK Vulkan APIs here

        val features = mutableMapOf<BackendFeature, FeatureStatus>()

        // Most Android API 33+ devices support compute shaders
        features[BackendFeature.COMPUTE] = FeatureStatus.SUPPORTED

        // Ray tracing on mobile is limited (Adreno 7xx, Mali G710)
        features[BackendFeature.RAY_TRACING] = if (hasRayTracingSupport()) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.MISSING
        }

        // XR support via ARCore
        features[BackendFeature.XR_SURFACE] = if (hasARCoreSupport()) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.MISSING
        }

        return DeviceCapabilityReport(
            deviceId = getAndroidDeviceId(),
            driverVersion = getVulkanDriverVersion(),
            osBuild = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            featureFlags = features,
            preferredBackend = BackendId.VULKAN,
            limitations = detectLimitations(features),
            timestamp = java.lang.System.currentTimeMillis().toString()
        )
    }

    override suspend fun performPlatformInitialization(
        backendId: BackendId,
        surface: SurfaceConfig
    ): RenderSurfaceDescriptor {
        require(backendId == BackendId.VULKAN) {
            "Android negotiator can only initialize VULKAN backend, got $backendId"
        }

        // Create Android native Vulkan surface
        // Would use ANativeWindow and VK_KHR_android_surface extension

        val surfaceId = "android-vulkan-surface-${java.lang.System.currentTimeMillis()}"
        return RenderSurfaceDescriptor(
            surfaceId = surfaceId,
            backendId = BackendId.VULKAN,
            width = surface.width,
            height = surface.height,
            colorFormat = surface.colorFormat,
            depthFormat = surface.depthFormat,
            presentMode = surface.presentMode,
            isXRSurface = surface.isXRSurface
        )
    }

    private fun hasRayTracingSupport(): Boolean {
        // Check for GPU family that supports ray tracing
        // Adreno 7xx series has limited ray tracing support
        val gpuRenderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
        return gpuRenderer?.contains("Adreno 7", ignoreCase = true) == true ||
                gpuRenderer?.contains("Mali-G710", ignoreCase = true) == true
    }

    private fun hasARCoreSupport(): Boolean {
        // Check if ARCore is available on this device
        try {
            val arCoreClass = Class.forName("com.google.ar.core.ArCoreApk")
            return true
        } catch (e: ClassNotFoundException) {
            return false
        }
    }

    private fun getAndroidDeviceId(): String {
        return "${android.os.Build.MANUFACTURER}:${android.os.Build.MODEL}"
    }

    private fun getVulkanDriverVersion(): String {
        // Would query Vulkan API for actual driver version
        // For now, return placeholder
        return "Vulkan 1.3"
    }

    private fun detectLimitations(features: Map<BackendFeature, FeatureStatus>): List<String> {
        val limitations = mutableListOf<String>()

        features.forEach { (feature, status) ->
            if (status != FeatureStatus.SUPPORTED) {
                limitations.add("$feature: ${status.name.lowercase()}")
            }
        }

        return limitations
    }
}

/**
 * Factory function for creating Android Vulkan negotiator.
 */
actual fun createBackendNegotiator(): BackendNegotiator {
    return AndroidVulkanNegotiator()
}
