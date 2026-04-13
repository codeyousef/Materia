package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.Result
import io.materia.core.scene.Scene
import io.materia.datetime.currentTimeMillis
import io.materia.renderer.backend.BackendFeature
import io.materia.renderer.backend.BackendId
import io.materia.renderer.backend.CapabilityRequest
import io.materia.renderer.backend.DeviceCapabilityReport
import io.materia.renderer.backend.FeatureStatus
import io.materia.renderer.backend.PerformanceBudget
import io.materia.renderer.backend.PlatformTarget
import io.materia.renderer.backend.PresentMode
import io.materia.renderer.backend.RenderSurfaceDescriptor
import io.materia.renderer.backend.RenderingBackendProfile
import io.materia.renderer.backend.SurfaceConfig
import io.materia.renderer.backend.createBackendNegotiator
import io.materia.renderer.metrics.FrameMetrics
import io.materia.renderer.metrics.createPerformanceMonitor

internal class AppleMoltenVkRenderer(
    private val surface: RenderSurface
) : Renderer {
    private val platformInfo = currentApplePlatformInfo()
    private val negotiator = createBackendNegotiator()
    private val performanceMonitor = createPerformanceMonitor()

    private var initialized = false
    private var currentWidth: Int = surface.width
    private var currentHeight: Int = surface.height
    private var lastFrameTimestampMs: Long = currentTimeMillis()
    private var surfaceDescriptor: RenderSurfaceDescriptor? = null

    override val backend: BackendType = BackendType.VULKAN

    override var capabilities: RendererCapabilities = RendererCapabilities(
        backend = backend,
        deviceName = platformInfo.deviceId,
        driverVersion = platformInfo.driverVersion,
        supportsCompute = true,
        supportsRayTracing = true,
        maxSamples = 4,
        vendor = "Apple",
        renderer = "MoltenVK",
        version = "Vulkan 1.3 (MoltenVK)",
        shadingLanguageVersion = "Metal Shading Language via MoltenVK",
        extensions = setOf("VK_KHR_surface", "VK_EXT_metal_surface", "MoltenVK"),
        computeShaders = true,
        asyncOperations = true
    )

    override var stats: RenderStats = RenderStats(
        fps = 0.0,
        frameTime = 0.0,
        triangles = 0,
        drawCalls = 0,
        timestamp = lastFrameTimestampMs
    )

    override suspend fun initialize(config: RendererConfig): Result<Unit> {
        if (initialized) {
            return Result.Success(Unit)
        }
        if (surface.width <= 0 || surface.height <= 0) {
            return Result.Error(
                "Invalid Apple render surface dimensions: ${surface.width}x${surface.height}",
                RendererInitializationException.SurfaceCreationFailedException(
                    BackendType.VULKAN,
                    surface::class.simpleName ?: "RenderSurface"
                )
            )
        }

        return try {
            performanceMonitor.beginInitializationTrace(BackendId.VULKAN)

            val report = negotiator.detectCapabilities(
                CapabilityRequest(
                    preferredBackend = BackendId.VULKAN,
                    includeDebugInfo = config.enableValidation
                )
            )
            val selection = negotiator.selectBackend(report, listOf(appleMoltenVkProfile()))
            if (!selection.isSuccessful || selection.backendId != BackendId.VULKAN) {
                return Result.Error(
                    selection.errorMessage ?: "MoltenVK backend negotiation failed on ${platformInfo.platformName}",
                    RendererInitializationException.NoGraphicsSupportException(
                        platform = platformInfo.platformName,
                        availableBackends = listOf(BackendType.VULKAN),
                        requiredFeatures = listOf("MoltenVK", "Metal 3")
                    )
                )
            }

            surfaceDescriptor = negotiator.initializeBackend(
                selection,
                SurfaceConfig(
                    width = surface.width,
                    height = surface.height,
                    presentMode = if (config.vsync) PresentMode.FIFO else PresentMode.MAILBOX
                )
            ).surfaceDescriptor

            performanceMonitor.endInitializationTrace(BackendId.VULKAN)
            capabilities = report.toRendererCapabilities(config.msaaSamples)
            currentWidth = surface.width
            currentHeight = surface.height
            lastFrameTimestampMs = currentTimeMillis()
            stats = stats.copy(timestamp = lastFrameTimestampMs)
            initialized = true

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                e.message ?: "Failed to initialize MoltenVK renderer",
                if (e is RendererInitializationException) {
                    e
                } else {
                    RendererInitializationException.DeviceCreationFailedException(
                        BackendType.VULKAN,
                        platformInfo.deviceId,
                        e.message ?: "Failed to initialize MoltenVK renderer"
                    )
                }
            )
        }
    }

    override fun render(scene: Scene, camera: Camera) {
        if (!initialized) {
            return
        }

        val now = currentTimeMillis()
        val frameTimeMs = (now - lastFrameTimestampMs).coerceAtLeast(1L).toDouble()
        val fps = 1000.0 / frameTimeMs
        performanceMonitor.recordFrameMetrics(
            FrameMetrics(
                backendId = BackendId.VULKAN,
                frameTimeMs = frameTimeMs,
                gpuTimeMs = 0.0,
                cpuTimeMs = frameTimeMs,
                timestamp = now
            )
        )
        stats = RenderStats(
            fps = fps,
            frameTime = frameTimeMs,
            triangles = 0,
            drawCalls = 0,
            timestamp = now
        )
        lastFrameTimestampMs = now
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        currentWidth = width
        currentHeight = height
        if (surface is AppleRenderSurface) {
            surface.resize(width, height)
        }
        surfaceDescriptor = surfaceDescriptor?.copy(width = width, height = height)
    }

    override fun dispose() {
        initialized = false
        surfaceDescriptor = null
    }
}

private fun appleMoltenVkProfile(): RenderingBackendProfile {
    return RenderingBackendProfile(
        backendId = BackendId.VULKAN,
        supportedFeatures = setOf(
            BackendFeature.COMPUTE,
            BackendFeature.RAY_TRACING,
            BackendFeature.XR_SURFACE
        ),
        performanceBudget = PerformanceBudget(),
        fallbackPriority = 0,
        apiVersion = "Vulkan 1.3 / MoltenVK",
        platformTargets = listOf(PlatformTarget.DESKTOP, PlatformTarget.IOS)
    )
}

private fun DeviceCapabilityReport.toRendererCapabilities(msaaSamples: Int): RendererCapabilities {
    return RendererCapabilities(
        backend = BackendType.VULKAN,
        deviceName = deviceId,
        driverVersion = driverVersion,
        supportsCompute = featureFlags[BackendFeature.COMPUTE] == FeatureStatus.SUPPORTED,
        supportsRayTracing = featureFlags[BackendFeature.RAY_TRACING] == FeatureStatus.SUPPORTED,
        maxSamples = msaaSamples.coerceAtLeast(4),
        vendor = "Apple",
        renderer = "MoltenVK",
        version = "Vulkan 1.3 (MoltenVK)",
        shadingLanguageVersion = "Metal Shading Language via MoltenVK",
        extensions = setOf("VK_KHR_surface", "VK_EXT_metal_surface", "MoltenVK"),
        computeShaders = true,
        asyncOperations = true
    )
}