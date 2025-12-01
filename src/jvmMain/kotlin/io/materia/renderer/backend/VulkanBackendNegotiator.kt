package io.materia.renderer.backend

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*

/**
 * Vulkan backend negotiator for desktop platforms (Windows, Linux, macOS via MoltenVK).
 * Probes Vulkan physical device capabilities and creates Vulkan surfaces.
 */
class VulkanBackendNegotiator : AbstractBackendNegotiator() {

    override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport {
        try {
            MemoryStack.stackPush().use { stack ->
                // Initialize Vulkan instance (minimal for capability detection)
                val instance = createVulkanInstance(stack) ?: return createUnsupportedReport(
                    "Failed to create Vulkan instance"
                )

                try {
                    // Enumerate physical devices
                    val pDeviceCount = stack.mallocInt(1)
                    vkEnumeratePhysicalDevices(instance, pDeviceCount, null)

                    val deviceCount = pDeviceCount[0]
                    if (deviceCount == 0) {
                        return createUnsupportedReport("No Vulkan-compatible GPUs found")
                    }

                    val pDevices = stack.mallocPointer(deviceCount)
                    vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices)

                    // Use first device (or select based on preference)
                    val physicalDevice = VkPhysicalDevice(pDevices[0], instance)
                    val deviceProperties = VkPhysicalDeviceProperties.malloc(stack)
                    vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties)

                    val deviceFeatures = detectVulkanFeatures(physicalDevice, stack)
                    val vendorId = deviceProperties.vendorID()
                    val deviceIdNum = deviceProperties.deviceID()
                    val deviceId = "0x${vendorId.toString(16)}:0x${deviceIdNum.toString(16)}"

                    val osName = try {
                        java.lang.System.getProperty("os.name")
                    } catch (e: Exception) {
                        "Unknown"
                    }
                    val osVersion = try {
                        java.lang.System.getProperty("os.version")
                    } catch (e: Exception) {
                        ""
                    }

                    return DeviceCapabilityReport(
                        deviceId = deviceId,
                        driverVersion = decodeVulkanDriverVersion(deviceProperties.driverVersion()),
                        osBuild = "$osName $osVersion",
                        featureFlags = deviceFeatures,
                        preferredBackend = if (deviceFeatures.values.all { it == FeatureStatus.SUPPORTED }) {
                            BackendId.VULKAN
                        } else null,
                        limitations = detectLimitations(deviceFeatures),
                        timestamp = java.time.Instant.now().toString()
                    )
                } finally {
                    vkDestroyInstance(instance, null)
                }
            }
        } catch (e: Exception) {
            return createUnsupportedReport("Vulkan capability detection failed: ${e.message}")
        }
    }

    override suspend fun performPlatformInitialization(
        backendId: BackendId,
        surface: SurfaceConfig
    ): RenderSurfaceDescriptor {
        require(backendId == BackendId.VULKAN) {
            "Vulkan negotiator can only initialize VULKAN backend, got $backendId"
        }

        // Create Vulkan surface descriptor for GLFW/LWJGL window surface
        // Actual surface creation happens in renderer initialization

        return RenderSurfaceDescriptor(
            surfaceId = "vulkan-surface-${System.currentTimeMillis()}",
            backendId = BackendId.VULKAN,
            width = surface.width,
            height = surface.height,
            colorFormat = surface.colorFormat,
            depthFormat = surface.depthFormat,
            presentMode = surface.presentMode,
            isXRSurface = surface.isXRSurface
        )
    }

    private fun createVulkanInstance(stack: MemoryStack): VkInstance? {
        val appInfo = VkApplicationInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(stack.UTF8("Materia"))
            .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
            .pEngineName(stack.UTF8("Materia Engine"))
            .engineVersion(VK_MAKE_VERSION(0, 1, 0))
            .apiVersion(VK_MAKE_VERSION(1, 3, 0))

        val createInfo = VkInstanceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            .pApplicationInfo(appInfo)

        val pInstance = stack.mallocPointer(1)
        val result = vkCreateInstance(createInfo, null, pInstance)

        return if (result == VK_SUCCESS) {
            VkInstance(pInstance[0], createInfo)
        } else {
            null
        }
    }

    private fun detectVulkanFeatures(
        physicalDevice: VkPhysicalDevice,
        stack: MemoryStack
    ): Map<BackendFeature, FeatureStatus> {
        val features = mutableMapOf<BackendFeature, FeatureStatus>()

        // Query device features
        val deviceFeatures = VkPhysicalDeviceFeatures.malloc(stack)
        vkGetPhysicalDeviceFeatures(physicalDevice, deviceFeatures)

        // Compute shader support (required for Vulkan 1.0+)
        features[BackendFeature.COMPUTE] = FeatureStatus.SUPPORTED

        // Ray tracing support (check for VK_KHR_ray_tracing_pipeline)
        features[BackendFeature.RAY_TRACING] = if (supportsRayTracing(physicalDevice, stack)) {
            FeatureStatus.SUPPORTED
        } else {
            FeatureStatus.EMULATED
        }

        // XR surface support (Vulkan supports XR via extensions)
        features[BackendFeature.XR_SURFACE] = FeatureStatus.SUPPORTED

        return features
    }

    private fun supportsRayTracing(physicalDevice: VkPhysicalDevice, stack: MemoryStack): Boolean {
        val pExtensionCount = stack.mallocInt(1)
        vkEnumerateDeviceExtensionProperties(physicalDevice, null as String?, pExtensionCount, null)

        val extensionCount = pExtensionCount[0]
        if (extensionCount == 0) return false

        val extensions = VkExtensionProperties.malloc(extensionCount, stack)
        vkEnumerateDeviceExtensionProperties(
            physicalDevice,
            null as String?,
            pExtensionCount,
            extensions
        )

        // Check for ray tracing extension
        for (i in 0 until extensionCount) {
            val ext = extensions[i]
            if (ext.extensionNameString() == "VK_KHR_ray_tracing_pipeline") {
                return true
            }
        }

        return false
    }

    private fun decodeVulkanDriverVersion(version: Int): String {
        val major = (version shr 22) and 0x3FF
        val minor = (version shr 12) and 0x3FF
        val patch = version and 0xFFF
        return "$major.$minor.$patch"
    }

    private fun detectLimitations(features: Map<BackendFeature, FeatureStatus>): List<String> {
        val limitations = mutableListOf<String>()

        features.forEach { (feature, status) ->
            when (status) {
                FeatureStatus.MISSING -> limitations.add("$feature not supported")
                FeatureStatus.EMULATED -> limitations.add("$feature emulated (software ray tracing)")
                FeatureStatus.SUPPORTED -> {} // No limitation
            }
        }

        return limitations
    }

    private fun createUnsupportedReport(reason: String): DeviceCapabilityReport {
        return DeviceCapabilityReport(
            deviceId = "unsupported",
            driverVersion = "N/A",
            osBuild = (try {
                java.lang.System.getProperty("os.name")
            } catch (e: Exception) {
                "Unknown"
            }) + " " +
                    (try {
                        java.lang.System.getProperty("os.version")
                    } catch (e: Exception) {
                        ""
                    }),
            featureFlags = mapOf(
                BackendFeature.COMPUTE to FeatureStatus.MISSING,
                BackendFeature.RAY_TRACING to FeatureStatus.MISSING,
                BackendFeature.XR_SURFACE to FeatureStatus.MISSING
            ),
            preferredBackend = null,
            limitations = listOf(reason),
            timestamp = java.time.Instant.now().toString()
        )
    }
}

/**
 * Factory function for creating Vulkan backend negotiator.
 */
actual fun createBackendNegotiator(): BackendNegotiator {
    return VulkanBackendNegotiator()
}
