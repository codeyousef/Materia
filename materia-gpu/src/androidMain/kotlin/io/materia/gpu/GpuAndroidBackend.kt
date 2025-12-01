package io.materia.gpu

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import io.materia.BuildConfig
import io.materia.gpu.bridge.VulkanBridge
import io.materia.renderer.RenderSurface

private const val ANDROID_VULKAN_TAG = "MateriaVk"

private const val VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT = 0x00000002
private const val VK_MEMORY_PROPERTY_HOST_COHERENT_BIT = 0x00000004

private const val VK_BUFFER_USAGE_TRANSFER_SRC_BIT = 0x00000001
private const val VK_BUFFER_USAGE_TRANSFER_DST_BIT = 0x00000002
private const val VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT = 0x00000010
private const val VK_BUFFER_USAGE_STORAGE_BUFFER_BIT = 0x00000020
private const val VK_BUFFER_USAGE_INDEX_BUFFER_BIT = 0x00000040
private const val VK_BUFFER_USAGE_VERTEX_BUFFER_BIT = 0x00000080
private const val VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT = 0x00000100

private const val VK_IMAGE_USAGE_TRANSFER_SRC_BIT = 0x00000001
private const val VK_IMAGE_USAGE_TRANSFER_DST_BIT = 0x00000002
private const val VK_IMAGE_USAGE_SAMPLED_BIT = 0x00000004
private const val VK_IMAGE_USAGE_STORAGE_BIT = 0x00000008
private const val VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT = 0x00000010

private const val VK_FILTER_NEAREST = 0
private const val VK_FILTER_LINEAR = 1
private const val VK_IMAGE_VIEW_TYPE_2D = 1

private fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("Android Vulkan backend not yet implemented ($feature)")

internal data class AndroidSwapchainState(
    val instanceHandle: Long,
    val deviceHandle: Long,
    val surfaceId: Long,
    var swapchainId: Long,
    var width: Int,
    var height: Int
)

private fun FloatArray.componentOrDefault(index: Int, fallback: Float): Float =
    if (index in indices) this[index] else fallback

/**
 * Utility object so Kotlin actuals can load SPIR-V assets on Android.
 */
object AndroidVulkanAssets {
    private const val VULKAN_1_0 = (1 shl 22)

    @Volatile
    private var assetManager: AssetManager? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var supportCache: Boolean? = null

    fun initialise(context: Context) {
        assetManager = context.assets
        appContext = context.applicationContext
        supportCache = null
    }

    fun loadShader(label: String): ByteArray {
        val manager = assetManager ?: error("AndroidVulkanAssets not initialised")
        val baseLabel = label.removeSuffix(".spv")
        val candidateFiles = linkedSetOf(
            "$label.spv",
            "$label.main.spv",
            "$baseLabel.spv",
            "$baseLabel.main.spv"
        )

        var lastError: Exception? = null
        for (candidate in candidateFiles) {
            val path = "shaders/$candidate"
            try {
                manager.open(path).use { stream ->
                    Log.d(ANDROID_VULKAN_TAG, "Loaded shader asset '$path' for label '$label'")
                    return stream.readBytes()
                }
            } catch (error: Exception) {
                lastError = error
            }
        }

        Log.e(
            ANDROID_VULKAN_TAG,
            "Failed to load shader '$label' from assets. Tried: ${candidateFiles.joinToString()}",
            lastError
        )
        throw lastError ?: IllegalStateException("Shader asset for '$label' not found.")
    }

    fun hasVulkanSupport(): Boolean {
        supportCache?.let { return it }
        val context = appContext ?: return false
        val pm = context.packageManager

        val hasLevel = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val hasCompute = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)
        val hasVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_0)
        } else {
            false
        }

        val supported = hasLevel || hasCompute || hasVersion
        supportCache = supported

        if (!supported) {
            Log.w(
                ANDROID_VULKAN_TAG,
                "Device ${Build.MANUFACTURER} ${Build.MODEL} does not advertise Vulkan support"
            )
        } else {
            Log.i(
                ANDROID_VULKAN_TAG,
                "Vulkan advertised (level=$hasLevel, compute=$hasCompute, version=$hasVersion)"
            )
        }
        return supported
    }
}

private fun GpuBufferUsageFlags.toNativeBufferUsage(): Int {
    var flags = 0
    if (this and GpuBufferUsage.COPY_SRC.mask != 0) flags =
        flags or VK_BUFFER_USAGE_TRANSFER_SRC_BIT
    if (this and GpuBufferUsage.COPY_DST.mask != 0) flags =
        flags or VK_BUFFER_USAGE_TRANSFER_DST_BIT
    if (this and GpuBufferUsage.UNIFORM.mask != 0) flags =
        flags or VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
    if (this and GpuBufferUsage.STORAGE.mask != 0) flags =
        flags or VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
    if (this and GpuBufferUsage.INDEX.mask != 0) flags = flags or VK_BUFFER_USAGE_INDEX_BUFFER_BIT
    if (this and GpuBufferUsage.VERTEX.mask != 0) flags = flags or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
    if (this and GpuBufferUsage.INDIRECT.mask != 0) flags =
        flags or VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
    return flags
}

private fun GpuTextureUsageFlags.toNativeTextureUsage(): Int {
    var flags = 0
    if (this and GpuTextureUsage.COPY_SRC.mask != 0) flags =
        flags or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
    if (this and GpuTextureUsage.COPY_DST.mask != 0) flags =
        flags or VK_IMAGE_USAGE_TRANSFER_DST_BIT
    if (this and GpuTextureUsage.TEXTURE_BINDING.mask != 0) flags =
        flags or VK_IMAGE_USAGE_SAMPLED_BIT
    if (this and GpuTextureUsage.STORAGE_BINDING.mask != 0) flags =
        flags or VK_IMAGE_USAGE_STORAGE_BIT
    if (this and GpuTextureUsage.RENDER_ATTACHMENT.mask != 0) flags =
        flags or VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
    return flags
}

private fun GpuTextureFormat.toNativeFormat(): Int = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> 0
    GpuTextureFormat.BGRA8_UNORM -> 1
    GpuTextureFormat.RGBA16_FLOAT -> 2
    GpuTextureFormat.DEPTH24_PLUS -> 3
}

private fun GpuFilterMode.toNative(): Int = when (this) {
    GpuFilterMode.NEAREST -> VK_FILTER_NEAREST
    GpuFilterMode.LINEAR -> VK_FILTER_LINEAR
}

private fun GpuPrimitiveTopology.toNativeTopology(): Int = when (this) {
    GpuPrimitiveTopology.POINT_LIST -> 0
    GpuPrimitiveTopology.LINE_LIST -> 1
    GpuPrimitiveTopology.LINE_STRIP -> 2
    GpuPrimitiveTopology.TRIANGLE_LIST -> 3
    GpuPrimitiveTopology.TRIANGLE_STRIP -> 4
}

private fun GpuCullMode.toNativeCullMode(): Int = when (this) {
    GpuCullMode.NONE -> 0
    GpuCullMode.FRONT -> 1
    GpuCullMode.BACK -> 2
}

private fun Set<GpuShaderStage>.toVisibilityMask(): Int = fold(0) { acc, stage ->
    acc or when (stage) {
        GpuShaderStage.VERTEX -> 0x1
        GpuShaderStage.FRAGMENT -> 0x2
        GpuShaderStage.COMPUTE -> 0x4
    }
}

private fun GpuBindingResourceType.toNativeDescriptorType(): Int = when (this) {
    GpuBindingResourceType.UNIFORM_BUFFER -> 0
    GpuBindingResourceType.STORAGE_BUFFER -> 1
    GpuBindingResourceType.TEXTURE -> 2
    GpuBindingResourceType.SAMPLER -> 3
}

private fun GpuVertexFormat.toNative(): Int = when (this) {
    GpuVertexFormat.FLOAT32 -> 0
    GpuVertexFormat.FLOAT32x2 -> 1
    GpuVertexFormat.FLOAT32x3 -> 2
    GpuVertexFormat.FLOAT32x4 -> 3
    else -> error("Vertex format $this not supported on Android Vulkan")
}

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance {
    val supportAdvertised = AndroidVulkanAssets.hasVulkanSupport()
    if (!supportAdvertised) {
        Log.w(
            ANDROID_VULKAN_TAG,
            "Vulkan support not advertised; attempting to initialise via SwiftShader/system loader"
        )
    }
    return try {
        Log.i(ANDROID_VULKAN_TAG, "Creating GPU instance (label=${descriptor.label ?: "Materia"}, advertised=$supportAdvertised)")
        GpuInstance(descriptor)
    } catch (error: UnsatisfiedLinkError) {
        Log.e(ANDROID_VULKAN_TAG, "Unable to load materia_vk native bridge", error)
        throw UnsupportedOperationException(
            "Vulkan native bridge could not be loaded (${error.message ?: "unknown error"}).",
            error
        )
    } catch (error: RuntimeException) {
        val message = error.message ?: error::class.simpleName ?: "Unknown error"
        Log.e(ANDROID_VULKAN_TAG, "Vulkan initialisation failed: $message", error)
        throw UnsupportedOperationException(
            "Failed to initialise Vulkan: $message",
            error
        )
    }
}

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    internal val handle: Long = VulkanBridge.vkInitSafe(
        descriptor.label ?: "Materia",
        BuildConfig.VK_ENABLE_VALIDATION
    ).also {
        Log.i(ANDROID_VULKAN_TAG, "Vulkan instance initialised (handle=$it)")
    }
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter {
        ensureActive()
        val info = GpuAdapterInfo(
            name = android.os.Build.MODEL ?: "Android Device",
            vendor = android.os.Build.MANUFACTURER,
            architecture = android.os.Build.HARDWARE,
            driverVersion = android.os.Build.VERSION.RELEASE
        )
        Log.i(
            ANDROID_VULKAN_TAG,
            "Providing Vulkan adapter (name=${info.name}, vendor=${info.vendor}, powerPreference=${options.powerPreference})"
        )
        return GpuAdapter(GpuBackend.VULKAN, options, info).also { adapter ->
            adapter.instanceHandle = handle
            adapter.ownerInstance = this
        }
    }

    actual fun dispose() {
        if (!disposed) {
            VulkanBridge.vkDestroyInstance(handle)
            disposed = true
        }
    }

    private fun ensureActive() {
        check(!disposed) { "GpuInstance has been disposed." }
    }
}

actual class GpuAdapter actual constructor(
    actual val backend: GpuBackend,
    actual val options: GpuRequestAdapterOptions,
    actual val info: GpuAdapterInfo
) {
    internal lateinit var ownerInstance: GpuInstance
    internal var instanceHandle: Long = 0L

    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice {
        Log.i(
            ANDROID_VULKAN_TAG,
            "Requesting Vulkan device (instanceHandle=$instanceHandle, label=${descriptor.label ?: "materia-device"})"
        )
        val deviceHandle = VulkanBridge.vkCreateDevice(instanceHandle)
        Log.i(
            ANDROID_VULKAN_TAG,
            "Vulkan device created (handle=$deviceHandle)"
        )
        return GpuDevice(this, descriptor).also { device ->
            device.instanceHandle = instanceHandle
            device.handle = deviceHandle
        }
    }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    internal var instanceHandle: Long = 0L
    internal var handle: Long = 0L

    actual val queue: GpuQueue = GpuQueue(descriptor.label).also { it.ownerDevice = this }

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        val usage = descriptor.usage.toNativeBufferUsage()
        val bufferHandle = VulkanBridge.vkCreateBuffer(
            instanceHandle,
            handle,
            descriptor.size,
            usage,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        return GpuBuffer(this, descriptor).also { it.handle = bufferHandle }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val textureHandle = VulkanBridge.vkCreateTexture(
            instanceHandle,
            handle,
            descriptor.format.toNativeFormat(),
            descriptor.size.first,
            descriptor.size.second,
            descriptor.usage.toNativeTextureUsage()
        )
        return GpuTexture(this, descriptor).also { it.handle = textureHandle }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val samplerHandle = VulkanBridge.vkCreateSampler(
            instanceHandle,
            handle,
            descriptor.minFilter.toNative(),
            descriptor.magFilter.toNative()
        )
        return GpuSampler(this, descriptor).also { it.handle = samplerHandle }
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        val bindings = descriptor.entries.map { it.binding }.toIntArray()
        val resourceTypes =
            descriptor.entries.map { it.resourceType.toNativeDescriptorType() }.toIntArray()
        val visibility = descriptor.entries.map { it.visibility.toVisibilityMask() }.toIntArray()
        val layoutHandle = VulkanBridge.vkCreateBindGroupLayout(
            instanceHandle,
            handle,
            bindings,
            resourceTypes,
            visibility
        )
        return GpuBindGroupLayout(this, descriptor).also { it.handle = layoutHandle }
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        val entries = descriptor.entries
        val bindings = IntArray(entries.size)
        val buffers = LongArray(entries.size)
        val offsets = LongArray(entries.size)
        val sizes = LongArray(entries.size)
        val textureViews = LongArray(entries.size)
        val samplers = LongArray(entries.size)

        entries.forEachIndexed { index, entry ->
            bindings[index] = entry.binding
            when (val resource = entry.resource) {
                is GpuBindingResource.Buffer -> {
                    val buffer = resource.buffer as GpuBuffer
                    buffers[index] = buffer.handle
                    offsets[index] = resource.offset
                    sizes[index] = resource.size ?: buffer.descriptor.size
                }

                is GpuBindingResource.Texture -> {
                    val view = resource.textureView as GpuTextureView
                    textureViews[index] = view.handle
                }

                is GpuBindingResource.Sampler -> {
                    val samplerObject = resource.sampler as GpuSampler
                    samplers[index] = samplerObject.handle
                }
            }
        }

        val layoutHandle = (descriptor.layout as GpuBindGroupLayout).handle
        val bindGroupHandle = VulkanBridge.vkCreateBindGroup(
            instanceHandle,
            handle,
            layoutHandle,
            bindings,
            buffers,
            offsets,
            sizes,
            textureViews,
            samplers
        )
        return GpuBindGroup(descriptor.layout, descriptor).also { it.handle = bindGroupHandle }
    }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder {
        val encoderHandle = VulkanBridge.vkCreateCommandEncoder(instanceHandle, handle)
        return GpuCommandEncoder(this, descriptor).also { encoder ->
            encoder.handle = encoderHandle
        }
    }

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule {
        val label = descriptor.label ?: error("Shader module label required on Android Vulkan")
        val spirv = AndroidVulkanAssets.loadShader(label)
        val handle = VulkanBridge.vkCreateShaderModule(instanceHandle, handle, spirv)
        return GpuShaderModule(this, descriptor).also { it.handle = handle }
    }

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline {
        val vertexShader = descriptor.vertexShader as? GpuShaderModule
            ?: error("Vertex shader module required for render pipeline")
        val fragmentShader = descriptor.fragmentShader as? GpuShaderModule
            ?: error("Fragment shader module required for Android Vulkan pipeline")

        val layoutHandles = descriptor.bindGroupLayouts
            .map { (it as GpuBindGroupLayout).handle }
            .toLongArray()

        val pipelineLayoutHandle = VulkanBridge.vkCreatePipelineLayout(
            instanceHandle,
            handle,
            layoutHandles
        )

        val vertexBuffers = descriptor.vertexBuffers
        val bindingIndices = IntArray(vertexBuffers.size) { it }
        val strides = vertexBuffers.map { it.arrayStride }.toIntArray()
        val stepModes = vertexBuffers
            .map { if (it.stepMode == GpuVertexStepMode.INSTANCE) 1 else 0 }
            .toIntArray()

        val attributePairs = vertexBuffers.flatMapIndexed { bindingIndex, layout ->
            layout.attributes.map { bindingIndex to it }
        }

        val attributeLocations = attributePairs.map { it.second.shaderLocation }.toIntArray()
        val attributeBindings = attributePairs.map { it.first }.toIntArray()
        val attributeFormats = attributePairs.map { it.second.format.toNative() }.toIntArray()
        val attributeOffsets = attributePairs.map { it.second.offset }.toIntArray()

        val colorFormat = (descriptor.colorFormats.firstOrNull()
            ?: GpuTextureFormat.BGRA8_UNORM).toNativeFormat()

        val enableBlend = descriptor.blendMode != GpuBlendMode.DISABLED

        val pipelineHandle = VulkanBridge.vkCreateRenderPipeline(
            instanceHandle,
            handle,
            pipelineLayoutHandle,
            vertexShader.handle,
            fragmentShader.handle,
            bindingIndices,
            strides,
            stepModes,
            attributeLocations,
            attributeBindings,
            attributeFormats,
            attributeOffsets,
            descriptor.primitiveTopology.toNativeTopology(),
            descriptor.cullMode.toNativeCullMode(),
            enableBlend,
            colorFormat,
            0L
        )

        return GpuRenderPipeline(this, descriptor).also { pipeline ->
            pipeline.handle = pipelineHandle
            pipeline.pipelineLayoutHandle = pipelineLayoutHandle
        }
    }

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline =
        unsupported("createComputePipeline")

    actual fun destroy() {
        if (handle != 0L) {
            VulkanBridge.vkDestroyDevice(instanceHandle, handle)
            handle = 0L
        }
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    internal lateinit var ownerDevice: GpuDevice

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (!::ownerDevice.isInitialized || commandBuffers.isEmpty()) return
        val device = ownerDevice
        val instanceHandle = device.instanceHandle
        val deviceHandle = device.handle

        commandBuffers.forEach { buffer ->
            val hasSwapchain = buffer.swapchainState != null
            val imageIndex = buffer.swapchainImageIndex
            try {
                if (hasSwapchain) {
                    require(imageIndex >= 0) { "Command buffer missing swapchain image index" }
                }
                VulkanBridge.vkQueueSubmit(
                    instanceHandle,
                    deviceHandle,
                    buffer.handle,
                    hasSwapchain,
                    imageIndex
                )

                if (hasSwapchain) {
                    val swapchainState = buffer.swapchainState
                        ?: error("Swapchain state missing for command buffer")
                    val presentResult = VulkanBridge.vkSwapchainPresentFrame(
                        instanceHandle,
                        deviceHandle,
                        swapchainState.surfaceId,
                        swapchainState.swapchainId,
                        buffer.handle,
                        imageIndex
                    )
                    // Handle swapchain recreation if needed
                    if (presentResult == 1) {
                        // Swapchain needs recreation - the surface will handle this on next frame
                        android.util.Log.i("GpuQueue", "Present returned OUT_OF_DATE, surface will recreate swapchain")
                    } else if (presentResult < 0) {
                        android.util.Log.e("GpuQueue", "Present failed with result $presentResult")
                    }
                }
            } finally {
                VulkanBridge.vkDestroyCommandBuffer(instanceHandle, deviceHandle, buffer.handle)
                buffer.handle = 0L
                buffer.swapchainState = null
                buffer.swapchainImageIndex = -1
            }
        }
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    internal var attachedSurface: RenderSurface? = null
    internal var configuration: GpuSurfaceConfiguration? = null
    internal var configuredDevice: GpuDevice? = null
    internal var preferredFormat: GpuTextureFormat = GpuTextureFormat.BGRA8_UNORM
    internal var swapchainState: AndroidSwapchainState? = null
    internal var fallbackTexture: GpuTexture? = null
    internal var fallbackView: GpuTextureView? = null

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        configuredDevice = device
        this.configuration = configuration
        preferredFormat = configuration.format

        val renderSurface = attachedSurface ?: return
        val resolvedWidth = configuration.width.takeIf { it > 0 }
            ?: renderSurface.width.coerceAtLeast(1)
        val resolvedHeight = configuration.height.takeIf { it > 0 }
            ?: renderSurface.height.coerceAtLeast(1)

        val nativeSurface: Surface = when (val handle = renderSurface.getHandle()) {
            is SurfaceHolder -> handle.surface
            is Surface -> handle
            else -> error("Unsupported render surface handle: ${handle?.javaClass}")
        }
        require(nativeSurface.isValid) { "Android Surface is not valid for Vulkan presentation" }

        Log.i(
            ANDROID_VULKAN_TAG,
            "Configuring Vulkan surface (label=${label ?: "surface"}, width=$resolvedWidth, height=$resolvedHeight, reuseExisting=${swapchainState != null})"
        )

        val instanceHandle = device.instanceHandle
        val deviceHandle = device.handle

        val surfaceId = swapchainState?.surfaceId ?: VulkanBridge.vkCreateSurface(
            instanceHandle,
            nativeSurface
        )

        swapchainState?.let {
            VulkanBridge.vkDestroySwapchain(
                instanceHandle,
                deviceHandle,
                surfaceId,
                it.swapchainId
            )
        }

        val swapchainId = VulkanBridge.vkCreateSwapchain(
            instanceHandle,
            deviceHandle,
            surfaceId,
            resolvedWidth,
            resolvedHeight
        )
        Log.i(
            ANDROID_VULKAN_TAG,
            "Vulkan swapchain configured (surfaceId=$surfaceId, swapchainId=$swapchainId, size=${resolvedWidth}x$resolvedHeight)"
        )

        swapchainState = AndroidSwapchainState(
            instanceHandle = instanceHandle,
            deviceHandle = deviceHandle,
            surfaceId = surfaceId,
            swapchainId = swapchainId,
            width = resolvedWidth,
            height = resolvedHeight
        )
        fallbackTexture = null
        fallbackView = null
    }

    @Suppress("UNUSED_PARAMETER")
    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat {
        return preferredFormat
    }

    actual fun acquireFrame(): GpuSurfaceFrame {
        val device = configuredDevice ?: error("GpuSurface not configured with a device")
        val config = configuration ?: error("Surface configuration missing")
        val swapchain = swapchainState

        if (swapchain == null) {
            val existingTexture = fallbackTexture
            val existingView = fallbackView
            if (existingTexture != null && existingView != null) {
                Log.w(
                    ANDROID_VULKAN_TAG,
                    "Swapchain unavailable; reusing fallback texture for ${label ?: "surface"}"
                )
                return GpuSurfaceFrame(existingTexture, existingView)
            }
            val fallbackWidth = config.width.takeIf { it > 0 } ?: 1
            val fallbackHeight = config.height.takeIf { it > 0 } ?: 1
            val descriptor = GpuTextureDescriptor(
                label = "${label ?: "surface"}-fallback",
                size = Triple(fallbackWidth, fallbackHeight, 1),
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = preferredFormat,
                usage = config.usage
            )
            val texture = device.createTexture(descriptor)
            val view = texture.createView()
            fallbackTexture = texture
            fallbackView = view
            return GpuSurfaceFrame(texture, view)
        }

        val handles = try {
            Log.d(
                ANDROID_VULKAN_TAG,
                "Acquiring swapchain frame (surfaceId=${swapchain.surfaceId}, swapchainId=${swapchain.swapchainId})"
            )
            VulkanBridge.vkSwapchainAcquireFrame(
                swapchain.instanceHandle,
                swapchain.deviceHandle,
                swapchain.surfaceId,
                swapchain.swapchainId
            )
        } catch (error: RuntimeException) {
            swapchainState = null
            Log.w(
                ANDROID_VULKAN_TAG,
                "Swapchain acquire failed; marking surface for reconfigure",
                error
            )
            throw IllegalStateException("Swapchain out of date; reconfigure required", error)
        }

        // Empty array means swapchain needs recreation
        if (handles.isEmpty()) {
            Log.i(ANDROID_VULKAN_TAG, "Swapchain reported OUT_OF_DATE, recreating...")
            // Destroy old swapchain and create new one
            VulkanBridge.vkDestroySwapchain(
                swapchain.instanceHandle,
                swapchain.deviceHandle,
                swapchain.surfaceId,
                swapchain.swapchainId
            )
            val newSwapchainId = VulkanBridge.vkCreateSwapchain(
                swapchain.instanceHandle,
                swapchain.deviceHandle,
                swapchain.surfaceId,
                swapchain.width,
                swapchain.height
            )
            Log.i(ANDROID_VULKAN_TAG, "Swapchain recreated (new swapchainId=$newSwapchainId)")
            swapchainState = AndroidSwapchainState(
                instanceHandle = swapchain.instanceHandle,
                deviceHandle = swapchain.deviceHandle,
                surfaceId = swapchain.surfaceId,
                swapchainId = newSwapchainId,
                width = swapchain.width,
                height = swapchain.height
            )
            // Return fallback for this frame, next frame will use new swapchain
            val fallbackTex = fallbackTexture
            val fallbackV = fallbackView
            if (fallbackTex != null && fallbackV != null) {
                return GpuSurfaceFrame(fallbackTex, fallbackV)
            }
            // If no fallback, create one
            val fallbackDescriptor = GpuTextureDescriptor(
                label = "${label ?: "surface"}-fallback",
                size = Triple(swapchain.width, swapchain.height, 1),
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = preferredFormat,
                usage = config.usage
            )
            val newFallback = device.createTexture(fallbackDescriptor)
            val newFallbackView = newFallback.createView()
            fallbackTexture = newFallback
            fallbackView = newFallbackView
            return GpuSurfaceFrame(newFallback, newFallbackView)
        }

        val imageIndex = handles[0].toInt()
        val textureHandle = handles[1]
        val viewHandle = handles[2]

        Log.d(
            ANDROID_VULKAN_TAG,
            "Swapchain frame acquired (imageIndex=$imageIndex, textureHandle=$textureHandle, viewHandle=$viewHandle)"
        )

        val descriptor = GpuTextureDescriptor(
            label = "${label ?: "surface"}-swapchain-$imageIndex",
            size = Triple(swapchain.width, swapchain.height, 1),
            mipLevelCount = 1,
            sampleCount = 1,
            dimension = GpuTextureDimension.D2,
            format = preferredFormat,
            usage = config.usage
        )

        val texture = GpuTexture(device, descriptor).also {
            it.handle = textureHandle
        }
        val viewDescriptor = GpuTextureViewDescriptor(label = "${descriptor.label}-view")
        val view = GpuTextureView(texture, viewDescriptor).also {
            it.handle = viewHandle
            it.imageIndex = imageIndex
            it.swapchainState = swapchain
        }

        fallbackTexture = null
        fallbackView = null

        return GpuSurfaceFrame(texture, view)
    }

    actual fun present(frame: GpuSurfaceFrame) {
        if (frame.view.swapchainState == null) {
            fallbackTexture = null
            fallbackView = null
            return
        }
    }

    actual fun resize(width: Int, height: Int) {
        val device = configuredDevice
        val config = configuration
        if (device != null && config != null) {
            val updated = config.copy(width = width, height = height)
            configure(device, updated)
        } else {
            configuration = GpuSurfaceConfiguration(
                format = preferredFormat,
                usage = gpuTextureUsage(
                    GpuTextureUsage.RENDER_ATTACHMENT,
                    GpuTextureUsage.COPY_SRC
                ),
                width = width,
                height = height,
                presentMode = "fifo"
            )
        }
    }
}

actual fun GpuSurface.attachRenderSurface(surface: RenderSurface) {
    attachedSurface = surface
    val device = configuredDevice
    val config = configuration
    if (device != null && config != null) {
        configure(device, config)
    }
}

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = handle
actual fun GpuBindGroup.unwrapHandle(): Any? = handle

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    internal var handle: Long = 0L

    actual fun write(data: ByteArray, offset: Int) {
        val androidDevice = device as GpuDevice
        VulkanBridge.vkWriteBuffer(
            androidDevice.instanceHandle,
            androidDevice.handle,
            handle,
            data,
            offset
        )
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        val androidDevice = device as GpuDevice
        VulkanBridge.vkWriteBufferFloats(
            androidDevice.instanceHandle,
            androidDevice.handle,
            handle,
            data,
            offset
        )
    }

    actual fun destroy() {}
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    internal var handle: Long = 0L

    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView {
        val androidDevice = device as GpuDevice
        val formatOverride = descriptor.format?.toNativeFormat() ?: -1
        val viewHandle = VulkanBridge.vkCreateTextureView(
            androidDevice.instanceHandle,
            androidDevice.handle,
            handle,
            VK_IMAGE_VIEW_TYPE_2D,
            formatOverride
        )
        return GpuTextureView(this, descriptor).also {
            it.handle = viewHandle
        }
    }

    actual fun destroy() {}
}

actual class GpuTextureView actual constructor(
    actual val texture: GpuTexture,
    actual val descriptor: GpuTextureViewDescriptor
) {
    internal var handle: Long = 0L
    internal var imageIndex: Int = -1
    internal var swapchainState: AndroidSwapchainState? = null
}

actual class GpuSampler actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuSamplerDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuCommandEncoder actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    internal var handle: Long = 0L
    private var finished = false
    internal var swapchainState: AndroidSwapchainState? = null
    internal var swapchainImageIndex: Int = -1

    actual fun finish(label: String?): GpuCommandBuffer {
        check(!finished) { "Command encoder already finished" }
        val commandBufferHandle = try {
            VulkanBridge.vkCommandEncoderFinish(
                device.instanceHandle,
                device.handle,
                handle
            )
        } catch (error: Throwable) {
            VulkanBridge.vkDestroyCommandEncoder(device.instanceHandle, device.handle, handle)
            handle = 0L
            swapchainState = null
            swapchainImageIndex = -1
            throw error
        }

        VulkanBridge.vkDestroyCommandEncoder(device.instanceHandle, device.handle, handle)
        finished = true
        handle = 0L

        return GpuCommandBuffer(device, label ?: descriptor?.label).also { buffer ->
            buffer.handle = commandBufferHandle
            buffer.swapchainState = swapchainState
            buffer.swapchainImageIndex = swapchainImageIndex
        }.also {
            swapchainState = null
            swapchainImageIndex = -1
        }
    }

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder {
        check(!finished) { "Cannot begin render pass after encoder has been finished" }
        return GpuRenderPassEncoder(this, descriptor)
    }

    internal fun registerSwapchain(state: AndroidSwapchainState?, imageIndex: Int) {
        if (state == null) return
        val existing = swapchainState
        if (existing != null && existing !== state) {
            error("Command encoder already targets a different swapchain")
        }
        swapchainState = state
        swapchainImageIndex = imageIndex
    }
}

actual class GpuCommandBuffer actual constructor(
    actual val device: GpuDevice,
    actual val label: String?
) {
    internal var handle: Long = 0L
    internal var swapchainState: AndroidSwapchainState? = null
    internal var swapchainImageIndex: Int = -1
}

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    internal var handle: Long = 0L
    private var active = false
    private val primaryAttachment = descriptor.colorAttachments.firstOrNull()
        ?: error("Render pass requires at least one color attachment")
    private val targetView = primaryAttachment.view as? GpuTextureView
        ?: error("Color attachment must provide an Android texture view")

    init {
        beginPass()
    }

    private fun beginPass() {
        require(targetView.handle != 0L) { "Render target view handle is invalid" }
        val swapchainState = targetView.swapchainState
        if (swapchainState != null) {
            require(targetView.imageIndex >= 0) { "Swapchain texture view missing image index" }
        }
        encoder.registerSwapchain(swapchainState, targetView.imageIndex)

        val clear = primaryAttachment.clearColor
        handle = VulkanBridge.vkCommandEncoderBeginRenderPass(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            targetView.handle,
            swapchainState != null,
            if (swapchainState != null) targetView.imageIndex else 0,
            clear.componentOrDefault(0, 0f),
            clear.componentOrDefault(1, 0f),
            clear.componentOrDefault(2, 0f),
            clear.componentOrDefault(3, 1f)
        )
        active = true
    }

    private fun requireActive() {
        check(active) { "Render pass is not active" }
    }

    actual fun setPipeline(pipeline: GpuRenderPipeline) {
        requireActive()
        require(pipeline.handle != 0L) { "Render pipeline handle is invalid" }
        VulkanBridge.vkCommandEncoderSetPipeline(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            pipeline.handle
        )
    }

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {
        requireActive()
        require(buffer.handle != 0L) { "Vertex buffer handle is invalid" }
        VulkanBridge.vkCommandEncoderSetVertexBuffer(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            slot,
            buffer.handle,
            0L
        )
    }

    actual fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long) {
        requireActive()
        require(buffer.handle != 0L) { "Index buffer handle is invalid" }
        val indexType = when (format) {
            GpuIndexFormat.UINT16 -> 0
            GpuIndexFormat.UINT32 -> 1
        }
        VulkanBridge.vkCommandEncoderSetIndexBuffer(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            buffer.handle,
            indexType,
            offset
        )
    }

    actual fun setBindGroup(index: Int, bindGroup: GpuBindGroup) {
        requireActive()
        require(bindGroup.handle != 0L) { "Bind group handle is invalid" }
        VulkanBridge.vkCommandEncoderSetBindGroup(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            index,
            bindGroup.handle
        )
    }

    actual fun draw(
        vertexCount: Int,
        instanceCount: Int,
        firstVertex: Int,
        firstInstance: Int
    ) {
        requireActive()
        VulkanBridge.vkCommandEncoderDraw(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            vertexCount,
            instanceCount,
            firstVertex,
            firstInstance
        )
    }

    actual fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        baseVertex: Int,
        firstInstance: Int
    ) {
        requireActive()
        VulkanBridge.vkCommandEncoderDrawIndexed(
            encoder.device.instanceHandle,
            encoder.device.handle,
            encoder.handle,
            indexCount,
            instanceCount,
            firstIndex,
            baseVertex,
            firstInstance
        )
    }

    actual fun end() {
        if (!active) return
        VulkanBridge.vkCommandEncoderEndRenderPass(
            encoder.device.instanceHandle,
            encoder.device.handle,
            handle
        )
        handle = 0L
        active = false
    }
}

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuBindGroupLayout actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBindGroupLayoutDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuBindGroup actual constructor(
    actual val layout: GpuBindGroupLayout,
    actual val descriptor: GpuBindGroupDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuRenderPipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuRenderPipelineDescriptor
) {
    internal var handle: Long = 0L
    internal var pipelineLayoutHandle: Long = 0L
}

actual class GpuComputePipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuComputePipelineDescriptor
)
