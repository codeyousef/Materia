package io.materia.renderer.gpu

import io.materia.util.MateriaLogger
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRPortabilityEnumeration
import org.lwjgl.vulkan.KHRPortabilitySubset
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_MAKE_VERSION
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_CPU
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_OTHER
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU
import org.lwjgl.vulkan.VK10.VK_QUEUE_COMPUTE_BIT
import org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.lwjgl.vulkan.VK10.VK_VERSION_MAJOR
import org.lwjgl.vulkan.VK10.VK_VERSION_MINOR
import org.lwjgl.vulkan.VK10.VK_VERSION_PATCH
import org.lwjgl.vulkan.VK10.vkCreateCommandPool
import org.lwjgl.vulkan.VK10.vkCreateDescriptorPool
import org.lwjgl.vulkan.VK10.vkCreateDevice
import org.lwjgl.vulkan.VK10.vkCreateInstance
import org.lwjgl.vulkan.VK10.vkDestroyDevice
import org.lwjgl.vulkan.VK10.vkDestroyInstance
import org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties
import org.lwjgl.vulkan.VK10.vkEnumerateInstanceExtensionProperties
import org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices
import org.lwjgl.vulkan.VK10.vkGetDeviceQueue
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkInstanceCreateInfo
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.lwjgl.vulkan.VkPhysicalDeviceLimits
import org.lwjgl.vulkan.VkPhysicalDeviceProperties
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkQueueFamilyProperties
import java.nio.FloatBuffer

internal data class VulkanBootstrapResult(
    val instance: VkInstance,
    val physicalDevice: VkPhysicalDevice,
    val device: VkDevice,
    val queue: VkQueue,
    val queueFamilyIndex: Int,
    val descriptorPool: Long,
    val commandPool: Long,
    val info: GpuDeviceInfo,
    val limits: GpuLimits
)

internal object VulkanBootstrap {
    private const val TAG = "VulkanBootstrap"

    fun createContext(config: GpuRequestConfig): VulkanBootstrapResult {
        ensureStackCapacity()
        MemoryStack.stackPush().use { stack ->
            val availableInstanceExtensions = queryInstanceExtensions(stack)
            val instance = createInstance(stack, config.label, availableInstanceExtensions)
            try {
                val physicalDevice = selectPhysicalDevice(instance, config)
                val queueFamilyIndex = selectQueueFamily(physicalDevice)
                val deviceExtensions = queryDeviceExtensions(physicalDevice)
                val logicalDevice = createLogicalDevice(
                    queueFamilyIndex = queueFamilyIndex,
                    physicalDevice = physicalDevice,
                    label = config.label,
                    deviceExtensions = deviceExtensions
                )
                try {
                    val queue = obtainQueue(logicalDevice, queueFamilyIndex)
                    val descriptorPool = createDescriptorPool(logicalDevice)
                    val commandPool = createCommandPool(logicalDevice, queueFamilyIndex)
                    val info = buildDeviceInfo(physicalDevice)
                    val limits = buildLimits(physicalDevice)

                    MateriaLogger.info(
                        TAG,
                        "Using physical device '${info.name}' (vendor=${info.vendor ?: "unknown"}, type=${info.architecture ?: "unknown"})"
                    )

                    return VulkanBootstrapResult(
                        instance = instance,
                        physicalDevice = physicalDevice,
                        device = logicalDevice,
                        queue = queue,
                        queueFamilyIndex = queueFamilyIndex,
                        descriptorPool = descriptorPool,
                        commandPool = commandPool,
                        info = info,
                        limits = limits
                    )
                } catch (t: Throwable) {
                    vkDestroyDevice(logicalDevice, null)
                    throw t
                }
            } catch (t: Throwable) {
                vkDestroyInstance(instance, null)
                throw t
            }
        }
    }

    private fun ensureStackCapacity() {
        val desired = 512 * 1024 // 512 KiB to comfortably enumerate extensions
        val current = Configuration.STACK_SIZE.get()
        if (current == null || current < desired) {
            Configuration.STACK_SIZE.set(desired)
        }
    }

    private fun createCommandPool(device: VkDevice, queueFamilyIndex: Int): Long {
        return MemoryStack.stackPush().use { stack ->
            val createInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex)

            val pCommandPool = stack.mallocLong(1)
            val result = vkCreateCommandPool(device, createInfo, null, pCommandPool)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("Failed to create command pool (vkCreateCommandPool=$result)")
            }
            pCommandPool[0]
        }
    }

    private fun queryInstanceExtensions(stack: MemoryStack): Set<String> {
        val countBuffer = stack.mallocInt(1)
        vkEnumerateInstanceExtensionProperties(null as String?, countBuffer, null)
        val count = countBuffer[0]
        if (count == 0) return emptySet()

        val props = VkExtensionProperties.malloc(count)
        try {
            countBuffer.put(0, count)
            vkEnumerateInstanceExtensionProperties(null as String?, countBuffer, props)
            return buildSet {
                for (i in 0 until count) {
                    add(props[i].extensionNameString())
                }
            }
        } finally {
            props.free()
        }
    }

    private fun createInstance(
        stack: MemoryStack,
        label: String?,
        availableExtensions: Set<String>
    ): VkInstance {
        val appInfo = VkApplicationInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(stack.UTF8(label ?: "Materia Renderer"))
            .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
            .pEngineName(stack.UTF8("Materia"))
            .engineVersion(VK_MAKE_VERSION(1, 0, 0))
            .apiVersion(VK_API_VERSION_1_1)

        val extensions = mutableListOf<String>()
        if (availableExtensions.contains(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)) {
            extensions += KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME
        }

        val glfwExtensions = org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions()
            ?: throw IllegalStateException("GLFW did not provide required Vulkan instance extensions")

        for (i in 0 until glfwExtensions.capacity()) {
            val name = glfwExtensions.getStringUTF8(i)
            if (!extensions.contains(name)) {
                extensions += name
            }
        }

        val ppExtensions = if (extensions.isNotEmpty()) {
            val buffer = stack.mallocPointer(extensions.size)
            extensions.forEachIndexed { index, name ->
                buffer.put(index, stack.UTF8(name))
            }
            buffer
        } else {
            null
        }

        val createInfo = VkInstanceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            .pApplicationInfo(appInfo)
            .ppEnabledExtensionNames(ppExtensions)

        if (extensions.contains(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)) {
            createInfo.flags(createInfo.flags() or KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
        }

        val pInstance = stack.mallocPointer(1)
        val result = vkCreateInstance(createInfo, null, pInstance)
        if (result != VK_SUCCESS) {
            throw IllegalStateException("vkCreateInstance failed with error $result")
        }

        return VkInstance(pInstance[0], createInfo)
    }

    private fun selectPhysicalDevice(
        instance: VkInstance,
        config: GpuRequestConfig
    ): VkPhysicalDevice {
        MemoryStack.stackPush().use { stack ->
            val deviceCountBuf = stack.mallocInt(1)
            vkEnumeratePhysicalDevices(instance, deviceCountBuf, null)
            val deviceCount = deviceCountBuf[0]
            if (deviceCount == 0) {
                throw IllegalStateException("No Vulkan physical devices available")
            }

            val devicesPtr = stack.mallocPointer(deviceCount)
            vkEnumeratePhysicalDevices(instance, deviceCountBuf, devicesPtr)

            val devices = (0 until deviceCount).map { idx ->
                VkPhysicalDevice(devicesPtr[idx], instance)
            }.filter { device ->
                hasGraphicsQueue(device)
            }

            if (devices.isEmpty()) {
                throw IllegalStateException("No Vulkan physical device provides a graphics queue family")
            }

            val preferLowPower =
                config.powerPreference == GpuPowerPreference.LOW_POWER || config.forceFallbackAdapter
            return devices.minByOrNull { device ->
                val rank = devicePerformanceRank(device)
                if (preferLowPower) rank else -rank
            }!!
        }
    }

    private fun hasGraphicsQueue(device: VkPhysicalDevice): Boolean =
        MemoryStack.stackPush().use { stack ->
            val countBuf = stack.mallocInt(1)
            vkGetPhysicalDeviceQueueFamilyProperties(
                device,
                countBuf,
                null as VkQueueFamilyProperties.Buffer?
            )
            val count = countBuf[0]
            if (count == 0) return@use false
            val properties = VkQueueFamilyProperties.calloc(count, stack)
            vkGetPhysicalDeviceQueueFamilyProperties(device, countBuf, properties)
            (0 until count).any { index ->
                (properties[index].queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0
            }
        }

    private fun devicePerformanceRank(device: VkPhysicalDevice): Int =
        MemoryStack.stackPush().use { stack ->
            val props = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(device, props)
            when (props.deviceType()) {
                VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 0
                VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 1
                VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 2
                VK_PHYSICAL_DEVICE_TYPE_CPU -> 3
                else -> 4
            }
        }

    private fun selectQueueFamily(device: VkPhysicalDevice): Int =
        MemoryStack.stackPush().use { stack ->
            val countBuf = stack.mallocInt(1)
            vkGetPhysicalDeviceQueueFamilyProperties(
                device,
                countBuf,
                null as VkQueueFamilyProperties.Buffer?
            )
            val count = countBuf[0]
            if (count == 0) {
                throw IllegalStateException("Physical device exposes no queue families")
            }
            val properties = VkQueueFamilyProperties.calloc(count, stack)
            vkGetPhysicalDeviceQueueFamilyProperties(device, countBuf, properties)

            var graphicsIndex = -1
            var computeFallback = -1
            for (index in 0 until count) {
                val flags = properties[index].queueFlags()
                if ((flags and VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsIndex = index
                    break
                }
                if (computeFallback == -1 && (flags and VK_QUEUE_COMPUTE_BIT) != 0) {
                    computeFallback = index
                }
            }

            when {
                graphicsIndex >= 0 -> graphicsIndex
                computeFallback >= 0 -> computeFallback
                else -> throw IllegalStateException("Failed to locate graphics or compute capable queue family")
            }
        }

    private fun queryDeviceExtensions(device: VkPhysicalDevice): Set<String> =
        MemoryStack.stackPush().use { stack ->
            val countBuf = stack.mallocInt(1)
            vkEnumerateDeviceExtensionProperties(device, null as String?, countBuf, null)
            val count = countBuf[0]
            if (count == 0) return@use emptySet()

            val props = VkExtensionProperties.malloc(count)
            try {
                countBuf.put(0, count)
                vkEnumerateDeviceExtensionProperties(device, null as String?, countBuf, props)
                return@use buildSet {
                    for (i in 0 until count) {
                        add(props[i].extensionNameString())
                    }
                }
            } finally {
                props.free()
            }
        }

    private fun createLogicalDevice(
        queueFamilyIndex: Int,
        physicalDevice: VkPhysicalDevice,
        label: String?,
        deviceExtensions: Set<String>
    ): VkDevice {
        MemoryStack.stackPush().use { stack ->
            val queuePriority: FloatBuffer = stack.floats(1.0f)
            val queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamilyIndex)
                .pQueuePriorities(queuePriority)

            val features = VkPhysicalDeviceFeatures.calloc(stack)

            val requiredExtensions = listOf(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
            val optionalExtensions = buildList {
                if (deviceExtensions.contains(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME)) {
                    add(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME)
                }
            }

            val missingRequired = requiredExtensions.filterNot(deviceExtensions::contains)
            if (missingRequired.isNotEmpty()) {
                throw IllegalStateException(
                    "Physical device is missing required Vulkan extensions: $missingRequired"
                )
            }

            val enabledExtensions = (requiredExtensions + optionalExtensions).distinct()

            val ppExtensions = if (enabledExtensions.isNotEmpty()) {
                val buffer = stack.mallocPointer(enabledExtensions.size)
                enabledExtensions.forEachIndexed { index, ext ->
                    buffer.put(index, stack.UTF8(ext))
                }
                buffer
            } else {
                null
            }

            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfo)
                .ppEnabledExtensionNames(ppExtensions)
                .pEnabledFeatures(features)

            label?.let {
                // Future: use debug utils to label device
            }

            val pDevice = stack.mallocPointer(1)
            val result = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("vkCreateDevice failed with error $result")
            }
            return VkDevice(pDevice[0], physicalDevice, deviceCreateInfo)
        }
    }

    private fun obtainQueue(device: VkDevice, queueFamilyIndex: Int): VkQueue =
        MemoryStack.stackPush().use { stack ->
            val pQueue = stack.mallocPointer(1)
            vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue)
            VkQueue(pQueue[0], device)
        }

    private fun createDescriptorPool(device: VkDevice): Long =
        MemoryStack.stackPush().use { stack ->
            val poolSizes = VkDescriptorPoolSize.calloc(3, stack)
            poolSizes[0]
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(64)
            poolSizes[1]
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(64)
            poolSizes[2]
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(64)

            val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pPoolSizes(poolSizes)
                .maxSets(128)
                .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)

            val pPool = stack.mallocLong(1)
            val result = vkCreateDescriptorPool(device, poolInfo, null, pPool)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("vkCreateDescriptorPool failed with error $result")
            }
            pPool[0]
        }

    private fun buildDeviceInfo(device: VkPhysicalDevice): GpuDeviceInfo =
        MemoryStack.stackPush().use { stack ->
            val props = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(device, props)
            val vendorName = vendorName(props.vendorID())
            val driverVersion = formatVersion(props.driverVersion())
            val architecture = deviceTypeString(props.deviceType())
            GpuDeviceInfo(
                name = props.deviceNameString(),
                vendor = vendorName,
                driverVersion = driverVersion,
                architecture = architecture
            )
        }

    private fun buildLimits(device: VkPhysicalDevice): GpuLimits =
        MemoryStack.stackPush().use { stack ->
            val props = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(device, props)
            val limits: VkPhysicalDeviceLimits = props.limits()
            GpuLimits(
                maxTextureDimension1D = limits.maxImageDimension1D(),
                maxTextureDimension2D = limits.maxImageDimension2D(),
                maxTextureDimension3D = limits.maxImageDimension3D(),
                maxTextureArrayLayers = limits.maxImageArrayLayers(),
                maxBindGroups = limits.maxBoundDescriptorSets(),
                maxUniformBuffersPerStage = limits.maxPerStageDescriptorUniformBuffers(),
                maxStorageBuffersPerStage = limits.maxPerStageDescriptorStorageBuffers(),
                maxBufferSize = limits.maxStorageBufferRange().toLong()
            )
        }

    private fun vendorName(vendorId: Int): String? = when (vendorId) {
        0x10DE -> "NVIDIA"
        0x1002, 0x1022 -> "AMD"
        0x8086 -> "Intel"
        0x13B5 -> "ARM"
        0x5143 -> "Qualcomm"
        else -> "0x${vendorId.toString(16)}"
    }

    private fun formatVersion(version: Int): String =
        "${VK_VERSION_MAJOR(version)}.${VK_VERSION_MINOR(version)}.${VK_VERSION_PATCH(version)}"

    private fun deviceTypeString(type: Int): String = when (type) {
        VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> "Discrete GPU"
        VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> "Integrated GPU"
        VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> "Virtual GPU"
        VK_PHYSICAL_DEVICE_TYPE_CPU -> "CPU"
        VK_PHYSICAL_DEVICE_TYPE_OTHER -> "Other"
        else -> "Unknown"
    }
}
