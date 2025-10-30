package io.materia.renderer.backend

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * WebGPU backend negotiator for browser platforms.
 * Probes WebGPU adapter capabilities and creates WebGPU surfaces.
 */
class WebGPUBackendNegotiator : AbstractBackendNegotiator() {

    override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport {
        // Check if WebGPU is available
        if (!isWebGPUAvailable()) {
            return createUnsupportedReport("WebGPU not available in this browser")
        }

        try {
            // Request WebGPU adapter
            val adapter = requestWebGPUAdapter()
            val adapterInfo = getAdapterInfo(adapter)
            val features = detectWebGPUFeatures(adapter)

            return DeviceCapabilityReport(
                deviceId = adapterInfo.device ?: "unknown-webgpu-device",
                driverVersion = adapterInfo.driver ?: "WebGPU API",
                osBuild = adapterInfo.description ?: "Browser",
                featureFlags = features,
                preferredBackend = if (features.values.all { it == FeatureStatus.SUPPORTED }) {
                    BackendId.WEBGPU
                } else null,
                limitations = detectLimitations(features),
                timestamp = kotlin.js.Date().toISOString()
            )
        } catch (e: Exception) {
            return createUnsupportedReport("WebGPU adapter request failed: ${e.message}")
        }
    }

    override suspend fun performPlatformInitialization(
        backendId: BackendId,
        surface: SurfaceConfig
    ): RenderSurfaceDescriptor {
        require(backendId == BackendId.WEBGPU) {
            "WebGPU negotiator can only initialize WEBGPU backend, got $backendId"
        }

        // Create WebGPU surface from canvas
        val canvas = getOrCreateCanvas(surface.width, surface.height)
        val context = getWebGPUContext(canvas)

        // Configure swapchain
        configureWebGPUSwapchain(context, surface)

        return RenderSurfaceDescriptor(
            surfaceId = "webgpu-surface-${canvas.id}",
            backendId = BackendId.WEBGPU,
            width = surface.width,
            height = surface.height,
            colorFormat = surface.colorFormat,
            depthFormat = surface.depthFormat,
            presentMode = surface.presentMode,
            isXRSurface = surface.isXRSurface
        )
    }

    private fun isWebGPUAvailable(): Boolean {
        return js("typeof navigator !== 'undefined' && 'gpu' in navigator") as Boolean
    }

    private suspend fun requestWebGPUAdapter(): dynamic {
        val gpu = js("navigator.gpu")
        val promise: Promise<dynamic> = gpu.requestAdapter()
        return promise.await()
    }

    private fun getAdapterInfo(adapter: dynamic): AdapterInfo {
        // Note: WebGPU adapter info may be limited for privacy
        return AdapterInfo(
            device = adapter.name as? String,
            driver = "WebGPU API",
            description = "Browser WebGPU Implementation"
        )
    }

    private fun detectWebGPUFeatures(adapter: dynamic): Map<BackendFeature, FeatureStatus> {
        val features = mutableMapOf<BackendFeature, FeatureStatus>()

        // Check for compute shader support
        features[BackendFeature.COMPUTE] = if (supportsFeature(adapter, "shader-f16")) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.EMULATED
        }

        // Ray tracing in WebGPU is experimental/missing in most browsers
        features[BackendFeature.RAY_TRACING] = FeatureStatus.MISSING

        // XR surface support via WebXR
        features[BackendFeature.XR_SURFACE] = if (isWebXRAvailable()) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.MISSING
        }

        return features
    }

    private fun supportsFeature(adapter: dynamic, featureName: String): Boolean {
        return try {
            val features = adapter.features
            features.has(featureName) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    private fun isWebXRAvailable(): Boolean {
        return js("typeof navigator !== 'undefined' && 'xr' in navigator") as Boolean
    }

    private fun detectLimitations(features: Map<BackendFeature, FeatureStatus>): List<String> {
        val limitations = mutableListOf<String>()

        features.forEach { (feature, status) ->
            when (status) {
                FeatureStatus.MISSING -> limitations.add("$feature not supported")
                FeatureStatus.EMULATED -> limitations.add("$feature emulated (reduced performance)")
                FeatureStatus.SUPPORTED -> {} // No limitation
            }
        }

        return limitations
    }

    private fun createUnsupportedReport(reason: String): DeviceCapabilityReport {
        return DeviceCapabilityReport(
            deviceId = "unsupported",
            driverVersion = "N/A",
            osBuild = "Browser",
            featureFlags = mapOf(
                BackendFeature.COMPUTE to FeatureStatus.MISSING,
                BackendFeature.RAY_TRACING to FeatureStatus.MISSING,
                BackendFeature.XR_SURFACE to FeatureStatus.MISSING
            ),
            preferredBackend = null,
            limitations = listOf(reason),
            timestamp = kotlin.js.Date().toISOString()
        )
    }

    private fun getOrCreateCanvas(width: Int, height: Int): dynamic {
        val canvas =
            js("document.getElementById('materia-canvas') || document.createElement('canvas')")
        canvas.id = "materia-canvas"
        canvas.width = width
        canvas.height = height
        if (js("!document.getElementById('materia-canvas')") as Boolean) {
            js("document.body.appendChild(canvas)")
        }
        return canvas
    }

    private fun getWebGPUContext(canvas: dynamic): dynamic {
        return canvas.getContext("webgpu")
    }

    private fun configureWebGPUSwapchain(context: dynamic, surface: SurfaceConfig) {
        // Configure WebGPU context for rendering
        val config = js("{}")
        config.format = when (surface.colorFormat) {
            ColorFormat.BGRA8_UNORM -> "bgra8unorm"
            ColorFormat.RGBA16F -> "rgba16float"
        }
        config.usage = js("GPUTextureUsage.RENDER_ATTACHMENT")
        config.alphaMode = "opaque"

        context.configure(config)
    }

    private data class AdapterInfo(
        val device: String?,
        val driver: String?,
        val description: String?
    )
}

/**
 * Factory function for creating WebGPU backend negotiator.
 */
actual fun createBackendNegotiator(): BackendNegotiator {
    return WebGPUBackendNegotiator()
}
