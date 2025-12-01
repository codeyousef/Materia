package io.materia.gpu.bridge

import android.util.Log

internal object VulkanBridge {

    private const val TAG = "VulkanBridge"
    private var libraryLoaded = false
    private var loadError: Throwable? = null

    init {
        try {
            Log.i(TAG, "Loading native library 'materia_vk'...")
            System.loadLibrary("materia_vk")
            libraryLoaded = true
            Log.i(TAG, "Native library 'materia_vk' loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            loadError = e
            Log.e(TAG, "Failed to load native library 'materia_vk': ${e.message}", e)
            Log.e(TAG, "Library search path: ${System.getProperty("java.library.path")}")
        } catch (e: Exception) {
            loadError = e
            Log.e(TAG, "Unexpected error loading native library: ${e.message}", e)
        }
    }

    private fun checkLibraryLoaded() {
        if (!libraryLoaded) {
            val error = loadError ?: IllegalStateException("Native library not loaded for unknown reason")
            throw UnsatisfiedLinkError("materia_vk native library not available: ${error.message}").apply {
                initCause(error)
            }
        }
    }

    external fun vkInit(appName: String, enableValidation: Boolean): Long

    fun vkInitSafe(appName: String, enableValidation: Boolean): Long {
        checkLibraryLoaded()
        return try {
            Log.i(TAG, "Calling vkInit(appName='$appName', validation=$enableValidation)...")
            val handle = vkInit(appName, enableValidation)
            Log.i(TAG, "vkInit returned handle: $handle")
            handle
        } catch (e: Exception) {
            Log.e(TAG, "vkInit failed: ${e.message}", e)
            throw e
        }
    }

    external fun vkCreateSurface(instanceId: Long, surface: Any): Long

    external fun vkCreateDevice(instanceId: Long): Long

    external fun vkCreateSwapchain(
        instanceId: Long,
        deviceId: Long,
        surfaceId: Long,
        width: Int,
        height: Int
    ): Long

    external fun vkSwapchainAcquireFrame(
        instanceId: Long,
        deviceId: Long,
        surfaceId: Long,
        swapchainId: Long
    ): LongArray

    /**
     * Present a frame to the swapchain.
     * @return 0 = success, 1 = needs swapchain recreation, -1 = error
     */
    external fun vkSwapchainPresentFrame(
        instanceId: Long,
        deviceId: Long,
        surfaceId: Long,
        swapchainId: Long,
        commandBufferId: Long,
        imageIndex: Int
    ): Int

    external fun vkCreateBuffer(
        instanceId: Long,
        deviceId: Long,
        size: Long,
        usageFlags: Int,
        memoryProperties: Int
    ): Long

    external fun vkWriteBuffer(
        instanceId: Long,
        deviceId: Long,
        bufferId: Long,
        data: ByteArray,
        offset: Int
    )

    external fun vkWriteBufferFloats(
        instanceId: Long,
        deviceId: Long,
        bufferId: Long,
        data: FloatArray,
        offset: Int
    )

    external fun vkCreateShaderModule(
        instanceId: Long,
        deviceId: Long,
        spirv: ByteArray
    ): Long

    external fun vkCreateSampler(
        instanceId: Long,
        deviceId: Long,
        minFilter: Int,
        magFilter: Int
    ): Long

    external fun vkCreateTexture(
        instanceId: Long,
        deviceId: Long,
        format: Int,
        width: Int,
        height: Int,
        usageFlags: Int
    ): Long

    external fun vkCreateTextureView(
        instanceId: Long,
        deviceId: Long,
        textureId: Long,
        viewType: Int,
        overrideFormat: Int
    ): Long

    external fun vkCreateBindGroupLayout(
        instanceId: Long,
        deviceId: Long,
        bindings: IntArray,
        resourceTypes: IntArray,
        visibilityMask: IntArray
    ): Long

    external fun vkCreateBindGroup(
        instanceId: Long,
        deviceId: Long,
        layoutId: Long,
        bindings: IntArray,
        buffers: LongArray,
        offsets: LongArray,
        sizes: LongArray,
        textureViews: LongArray,
        samplers: LongArray
    ): Long

    external fun vkCreatePipelineLayout(
        instanceId: Long,
        deviceId: Long,
        layoutHandles: LongArray
    ): Long

    external fun vkCreateRenderPipeline(
        instanceId: Long,
        deviceId: Long,
        pipelineLayoutId: Long,
        vertexShaderId: Long,
        fragmentShaderId: Long,
        bindingIndices: IntArray,
        strides: IntArray,
        stepModes: IntArray,
        attributeLocations: IntArray,
        attributeBindings: IntArray,
        attributeFormats: IntArray,
        attributeOffsets: IntArray,
        topology: Int,
        cullMode: Int,
        enableBlend: Boolean,
        colorFormat: Int,
        renderPassHandle: Long
    ): Long

    external fun vkCreateCommandEncoder(
        instanceId: Long,
        deviceId: Long
    ): Long

    external fun vkCommandEncoderBeginRenderPass(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        textureViewId: Long,
        isSwapchain: Boolean,
        swapchainImageIndex: Int,
        clearR: Float,
        clearG: Float,
        clearB: Float,
        clearA: Float
    ): Long

    external fun vkCommandEncoderEndRenderPass(
        instanceId: Long,
        deviceId: Long,
        renderPassEncoderId: Long
    )

    external fun vkCommandEncoderSetPipeline(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        pipelineId: Long
    )

    external fun vkCommandEncoderSetVertexBuffer(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        slot: Int,
        bufferId: Long,
        offset: Long
    )

    external fun vkCommandEncoderSetIndexBuffer(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        bufferId: Long,
        indexType: Int,
        offset: Long
    )

    external fun vkCommandEncoderSetBindGroup(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        index: Int,
        bindGroupId: Long
    )

    external fun vkCommandEncoderDraw(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        vertexCount: Int,
        instanceCount: Int,
        firstVertex: Int,
        firstInstance: Int
    )

    external fun vkCommandEncoderDrawIndexed(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long,
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int
    )

    external fun vkCommandEncoderFinish(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long
    ): Long

    external fun vkQueueSubmit(
        instanceId: Long,
        deviceId: Long,
        commandBufferId: Long,
        hasSwapchain: Boolean,
        imageIndex: Int
    )

    external fun vkDestroyCommandBuffer(
        instanceId: Long,
        deviceId: Long,
        commandBufferId: Long
    )

    external fun vkDestroyCommandEncoder(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long
    )

    external fun vkDestroySwapchain(
        instanceId: Long,
        deviceId: Long,
        surfaceId: Long,
        swapchainId: Long
    )

    external fun vkDestroySurface(
        instanceId: Long,
        surfaceId: Long
    )

    external fun vkDestroyDevice(
        instanceId: Long,
        deviceId: Long
    )

    external fun vkDestroyInstance(instanceId: Long)

    external fun vkDestroyAll()
}
