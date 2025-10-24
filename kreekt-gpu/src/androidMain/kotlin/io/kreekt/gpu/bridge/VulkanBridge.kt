package io.kreekt.gpu.bridge

internal object VulkanBridge {

    init {
        System.loadLibrary("kreekt_vk")
    }

    external fun vkInit(appName: String, enableValidation: Boolean): Long

    external fun vkCreateSurface(instanceId: Long, surface: Any): Long

    external fun vkCreateDevice(instanceId: Long): Long

    external fun vkCreateSwapchain(
        deviceId: Long,
        surfaceId: Long,
        width: Int,
        height: Int
    ): Long

    external fun vkDrawFrame(
        deviceId: Long,
        swapchainId: Long,
        clearR: Float,
        clearG: Float,
        clearB: Float,
        clearA: Float
    ): Boolean

    external fun vkResizeSwapchain(
        deviceId: Long,
        surfaceId: Long,
        swapchainId: Long,
        width: Int,
        height: Int
    )

    external fun vkDestroySwapchain(deviceId: Long, swapchainId: Long)

    external fun vkDestroySurface(instanceId: Long, surfaceId: Long)

    external fun vkDestroyDevice(instanceId: Long)

    external fun vkDestroyInstance(instanceId: Long)

    external fun vkDestroyAll()
}
