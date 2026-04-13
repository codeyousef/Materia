package io.materia.renderer.webgpu

import io.materia.renderer.webgpu.RenderStatsTracker
import io.materia.core.scene.Material as EngineMaterial
import io.materia.material.MeshBasicMaterial
import io.materia.material.MeshStandardMaterial
import io.materia.renderer.TextureFormat
import io.materia.renderer.gpu.GpuBindGroup
import io.materia.renderer.gpu.GpuBindGroupDescriptor
import io.materia.renderer.gpu.GpuBindGroupEntry
import io.materia.renderer.gpu.GpuBindGroupLayout
import io.materia.renderer.gpu.GpuBindGroupLayoutDescriptor
import io.materia.renderer.gpu.GpuBindGroupLayoutEntry
import io.materia.renderer.gpu.GpuBindingResource
import io.materia.renderer.gpu.GpuDevice
import io.materia.renderer.gpu.GpuSampler
import io.materia.renderer.gpu.GpuSamplerBindingLayout
import io.materia.renderer.gpu.GpuSamplerBindingType
import io.materia.renderer.gpu.GpuSamplerDescriptor
import io.materia.renderer.gpu.GpuShaderStage
import io.materia.renderer.gpu.GpuTexture
import io.materia.renderer.gpu.GpuTextureBindingLayout
import io.materia.renderer.gpu.GpuTextureDescriptor
import io.materia.renderer.gpu.GpuTextureDimension
import io.materia.renderer.gpu.GpuTextureSampleType
import io.materia.renderer.gpu.GpuTextureView
import io.materia.renderer.gpu.GpuTextureViewDescriptor
import io.materia.renderer.gpu.GpuTextureViewDimension
import io.materia.renderer.gpu.GpuTextureUsage
import io.materia.renderer.gpu.unwrapHandle
import io.materia.renderer.material.MaterialBindingSource
import io.materia.renderer.material.MaterialBindingType
import io.materia.renderer.material.MaterialDescriptor
import io.materia.texture.Data3DTexture
import io.materia.texture.Texture
import io.materia.texture.Texture2D
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array

internal data class MaterialTextureBinding(
    val bindGroup: GpuBindGroup,
    val layout: GpuBindGroupLayout
)

internal class WebGPUMaterialTextureManager(
    private val deviceProvider: () -> GpuDevice?,
    private val statsTracker: RenderStatsTracker? = null
) {

    private data class CachedTexture(
        val gpuTexture: GpuTexture,
        val view: GpuTextureView,
        var version: Int,
        var width: Int,
        var height: Int,
        var depth: Int,
        var trackedBytes: Long
    )

    private data class LayoutKey(
        val useAlbedo: Boolean,
        val useNormal: Boolean,
        val useVolume: Boolean
    )

    private data class BindGroupKey(
        val layoutKey: LayoutKey,
        val albedoId: Int?,
        val albedoVersion: Int,
        val normalId: Int?,
        val normalVersion: Int,
        val volumeId: Int?,
        val volumeVersion: Int
    )

    private data class TextureUpload(
        val width: Int,
        val height: Int,
        val depth: Int,
        val format: String,
        val dimension: GpuTextureDimension,
        val viewDimension: GpuTextureViewDimension,
        val bytesPerTexel: Int,
        val trackedBytes: Long,
        val data: dynamic
    )

    private var currentDevice: GpuDevice? = null
    private var defaultSampler: GpuSampler? = null

    private val layoutCache = mutableMapOf<LayoutKey, GpuBindGroupLayout>()
    private val bindGroupCache = mutableMapOf<BindGroupKey, MaterialTextureBinding>()
    private val textureCache = mutableMapOf<Int, CachedTexture>()

    private var fallbackAlbedo: CachedTexture? = null
    private var fallbackNormal: CachedTexture? = null
    private var fallbackVolume: CachedTexture? = null

    fun onDeviceReady(device: GpuDevice) {
        if (currentDevice === device) return
        dispose()
        currentDevice = device
        defaultSampler = device.createSampler(
            GpuSamplerDescriptor(
                label = "Material Texture Sampler"
            )
        )
        fallbackAlbedo = createFallbackTexture(device, byteArrayOf(-1, -1, -1, -1))
        fallbackNormal =
            createFallbackTexture(device, byteArrayOf(127, 127, 255.toByte(), 255.toByte()))
        fallbackVolume = createFallbackTexture(
            device = device,
            data = byteArrayOf(-1, -1, -1, -1),
            depth = 1,
            dimension = GpuTextureDimension.D3,
            viewDimension = GpuTextureViewDimension.D3,
            label = "MaterialVolumeFallback"
        )
    }

    fun prepare(
        descriptor: MaterialDescriptor,
        material: EngineMaterial?,
        useAlbedo: Boolean,
        useNormal: Boolean,
        useVolume: Boolean
    ): MaterialTextureBinding? {
        if (!useAlbedo && !useNormal && !useVolume) return null

        val device = currentDevice ?: deviceProvider()?.also(::onDeviceReady) ?: return null
        val sampler = defaultSampler ?: return null

        val layoutKey = LayoutKey(useAlbedo, useNormal, useVolume)
        val layout = layoutCache.getOrPut(layoutKey) {
            createLayout(descriptor, layoutKey, device) ?: return null
        }

        val albedoTexture = if (useAlbedo) {
            acquireTexture(device, albedoSource(material)) ?: fallbackAlbedo
        } else fallbackAlbedo

        val normalTexture = if (useNormal) {
            acquireTexture(device, normalSource(material)) ?: fallbackNormal
        } else fallbackNormal

        val volumeTexture = if (useVolume) {
            acquireTexture(device, volumeSource(material)) ?: fallbackVolume
        } else fallbackVolume

        val albedoKey = albedoTexture?.let { it.gpuTexture.hashCode() }
        val normalKey = normalTexture?.let { it.gpuTexture.hashCode() }
        val volumeKey = volumeTexture?.let { it.gpuTexture.hashCode() }
        val albedoVersion = albedoTexture?.version ?: -1
        val normalVersion = normalTexture?.version ?: -1
        val volumeVersion = volumeTexture?.version ?: -1

        val cacheKey = BindGroupKey(
            layoutKey,
            albedoKey,
            albedoVersion,
            normalKey,
            normalVersion,
            volumeKey,
            volumeVersion
        )
        bindGroupCache[cacheKey]?.let { return it }

        val entries = mutableListOf<GpuBindGroupEntry>()

        if (useAlbedo) {
            val textureBinding = descriptor.bindingFor(
                MaterialBindingSource.ALBEDO_MAP,
                MaterialBindingType.TEXTURE_2D
            ) ?: return null
            val samplerBinding =
                descriptor.bindingFor(MaterialBindingSource.ALBEDO_MAP, MaterialBindingType.SAMPLER)
                    ?: return null
            val textureView = (albedoTexture ?: fallbackAlbedo)?.view ?: return null
            entries += GpuBindGroupEntry(
                binding = textureBinding.binding,
                resource = GpuBindingResource.Texture(textureView)
            )
            entries += GpuBindGroupEntry(
                binding = samplerBinding.binding,
                resource = GpuBindingResource.Sampler(sampler)
            )
        }

        if (useNormal) {
            val textureBinding = descriptor.bindingFor(
                MaterialBindingSource.NORMAL_MAP,
                MaterialBindingType.TEXTURE_2D
            ) ?: return null
            val samplerBinding =
                descriptor.bindingFor(MaterialBindingSource.NORMAL_MAP, MaterialBindingType.SAMPLER)
                    ?: return null
            val textureView = (normalTexture ?: fallbackNormal)?.view ?: return null
            entries += GpuBindGroupEntry(
                binding = textureBinding.binding,
                resource = GpuBindingResource.Texture(textureView)
            )
            entries += GpuBindGroupEntry(
                binding = samplerBinding.binding,
                resource = GpuBindingResource.Sampler(sampler)
            )
        }

        if (useVolume) {
            val textureBinding = descriptor.bindingFor(
                MaterialBindingSource.VOLUME_TEXTURE,
                MaterialBindingType.TEXTURE_3D
            ) ?: return null
            val samplerBinding =
                descriptor.bindingFor(MaterialBindingSource.VOLUME_TEXTURE, MaterialBindingType.SAMPLER)
                    ?: return null
            val textureView = (volumeTexture ?: fallbackVolume)?.view ?: return null
            entries += GpuBindGroupEntry(
                binding = textureBinding.binding,
                resource = GpuBindingResource.Texture(textureView)
            )
            entries += GpuBindGroupEntry(
                binding = samplerBinding.binding,
                resource = GpuBindingResource.Sampler(sampler)
            )
        }

        if (entries.isEmpty()) return null

        val bindGroup = device.createBindGroup(
            GpuBindGroupDescriptor(
                layout = layout,
                entries = entries.sortedBy { it.binding },
                label = "Material Texture BindGroup"
            )
        )
        val binding = MaterialTextureBinding(bindGroup, layout)
        bindGroupCache[cacheKey] = binding
        return binding
    }

    fun dispose() {
        bindGroupCache.clear()
        textureCache.values.forEach { cached ->
            statsTracker?.recordTextureDisposed(cached.trackedBytes)
            runCatching { cached.gpuTexture.destroy() }
        }
        textureCache.clear()
        fallbackAlbedo?.let {
            statsTracker?.recordTextureDisposed(it.trackedBytes)
            runCatching { it.gpuTexture.destroy() }
        }
        fallbackNormal?.let {
            statsTracker?.recordTextureDisposed(it.trackedBytes)
            runCatching { it.gpuTexture.destroy() }
        }
        fallbackVolume?.let {
            statsTracker?.recordTextureDisposed(it.trackedBytes)
            runCatching { it.gpuTexture.destroy() }
        }
        fallbackAlbedo = null
        fallbackNormal = null
        fallbackVolume = null
        defaultSampler = null
        layoutCache.clear()
        bindGroupCache.clear()
        currentDevice = null
    }

    private fun albedoSource(material: EngineMaterial?): Texture2D? = when (material) {
        is MeshBasicMaterial -> material.map as? Texture2D
        is MeshStandardMaterial -> material.map
        else -> null
    }

    private fun normalSource(material: EngineMaterial?): Texture2D? = when (material) {
        is MeshStandardMaterial -> material.normalMap
        else -> null
    }

    private fun volumeSource(material: EngineMaterial?): Data3DTexture? = when (material) {
        is MeshBasicMaterial -> material.map as? Data3DTexture
        else -> null
    }

    private fun createLayout(
        descriptor: MaterialDescriptor,
        key: LayoutKey,
        device: GpuDevice
    ): GpuBindGroupLayout? {
        val entries = mutableListOf<GpuBindGroupLayoutEntry>()

        if (key.useAlbedo) {
            val textureBinding = descriptor.bindingFor(
                MaterialBindingSource.ALBEDO_MAP,
                MaterialBindingType.TEXTURE_2D
            ) ?: return null
            val samplerBinding =
                descriptor.bindingFor(MaterialBindingSource.ALBEDO_MAP, MaterialBindingType.SAMPLER)
                    ?: return null
            entries += textureLayoutEntry(textureBinding.binding)
            entries += samplerLayoutEntry(samplerBinding.binding)
        }
        if (key.useNormal) {
            val textureBinding = descriptor.bindingFor(
                MaterialBindingSource.NORMAL_MAP,
                MaterialBindingType.TEXTURE_2D
            ) ?: return null
            val samplerBinding =
                descriptor.bindingFor(MaterialBindingSource.NORMAL_MAP, MaterialBindingType.SAMPLER)
                    ?: return null
            entries += textureLayoutEntry(textureBinding.binding)
            entries += samplerLayoutEntry(samplerBinding.binding)
        }
        if (key.useVolume) {
            val textureBinding = descriptor.bindingFor(
                MaterialBindingSource.VOLUME_TEXTURE,
                MaterialBindingType.TEXTURE_3D
            ) ?: return null
            val samplerBinding =
                descriptor.bindingFor(MaterialBindingSource.VOLUME_TEXTURE, MaterialBindingType.SAMPLER)
                    ?: return null
            entries += textureLayoutEntry(
                binding = textureBinding.binding,
                dimension = GpuTextureViewDimension.D3
            )
            entries += samplerLayoutEntry(samplerBinding.binding)
        }
        if (entries.isEmpty()) return null

        return device.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                entries = entries.sortedBy { it.binding },
                label = "Material Texture Layout (${key.useAlbedo}, ${key.useNormal}, ${key.useVolume})"
            )
        )
    }

    private fun textureLayoutEntry(
        binding: Int,
        dimension: GpuTextureViewDimension = GpuTextureViewDimension.D2
    ): GpuBindGroupLayoutEntry =
        GpuBindGroupLayoutEntry(
            binding = binding,
            visibility = GpuShaderStage.FRAGMENT.bits,
            texture = GpuTextureBindingLayout(
                sampleType = GpuTextureSampleType.FLOAT,
                viewDimension = dimension,
                multisampled = false
            )
        )

    private fun samplerLayoutEntry(binding: Int): GpuBindGroupLayoutEntry =
        GpuBindGroupLayoutEntry(
            binding = binding,
            visibility = GpuShaderStage.FRAGMENT.bits,
            sampler = GpuSamplerBindingLayout(GpuSamplerBindingType.FILTERING)
        )

    private fun MaterialDescriptor.bindingFor(
        source: MaterialBindingSource,
        type: MaterialBindingType
    ) = bindings.firstOrNull { it.source == source && it.type == type }

    private fun acquireTexture(device: GpuDevice, texture: Texture?): CachedTexture? {
        texture ?: return null
        val upload = buildTextureUpload(texture) ?: return null

        val cached = textureCache[texture.id]
        if (cached != null &&
            cached.version == texture.version &&
            cached.width == upload.width &&
            cached.height == upload.height &&
            cached.depth == upload.depth
        ) {
            return cached
        }

        cached?.let { previous ->
            statsTracker?.recordTextureDisposed(previous.trackedBytes)
            runCatching { previous.gpuTexture.destroy() }
        }

        val gpuTexture = device.createTexture(
            GpuTextureDescriptor(
                width = upload.width,
                height = upload.height,
                depthOrArrayLayers = upload.depth,
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = upload.dimension,
                format = upload.format,
                usage = GpuTextureUsage.TEXTURE_BINDING.bits or GpuTextureUsage.COPY_DST.bits,
                label = texture.name.ifEmpty { "MaterialTexture${texture.id}" }
            )
        )
        writeTextureData(device, gpuTexture, upload)
        val view = gpuTexture.createView(
            GpuTextureViewDescriptor(dimension = upload.viewDimension)
        )

        val cachedTexture = CachedTexture(
            gpuTexture = gpuTexture,
            view = view,
            version = texture.version,
            width = upload.width,
            height = upload.height,
            depth = upload.depth,
            trackedBytes = upload.trackedBytes
        )
        textureCache[texture.id] = cachedTexture
        statsTracker?.recordTextureCreated(upload.trackedBytes)
        texture.needsUpdate = false
        return cachedTexture
    }

    private fun buildTextureUpload(texture: Texture): TextureUpload? = when (texture) {
        is Texture2D -> buildTexture2DUpload(texture)
        is Data3DTexture -> buildTexture3DUpload(texture)
        else -> null
    }

    private fun buildTexture2DUpload(texture: Texture2D): TextureUpload? {
        val upload = textureDataFor(
            texture.format,
            texture.getData(),
            texture.getFloatData(),
            null,
            texture.width,
            texture.height,
            1
        ) ?: return null
        return upload.copy(
            dimension = GpuTextureDimension.D2,
            viewDimension = GpuTextureViewDimension.D2
        )
    }

    private fun buildTexture3DUpload(texture: Data3DTexture): TextureUpload? {
        val upload = textureDataFor(
            texture.format,
            texture.getData().takeIf { it.isNotEmpty() },
            texture.getFloatData(),
            texture.getIntData(),
            texture.width,
            texture.height,
            texture.depth
        ) ?: return null
        return upload.copy(
            dimension = GpuTextureDimension.D3,
            viewDimension = GpuTextureViewDimension.D3
        )
    }

    private fun textureDataFor(
        format: TextureFormat,
        byteData: ByteArray?,
        floatData: FloatArray?,
        intData: IntArray?,
        width: Int,
        height: Int,
        depth: Int
    ): TextureUpload? {
        if (width <= 0 || height <= 0 || depth <= 0) return null

        return when {
            floatData != null && format == TextureFormat.RGBA32F -> {
                val typed = Float32Array(floatData.size)
                val dyn = typed.asDynamic()
                for (i in floatData.indices) {
                    dyn[i] = floatData[i]
                }
                TextureUpload(
                    width = width,
                    height = height,
                    depth = depth,
                    format = "rgba32float",
                    dimension = GpuTextureDimension.D2,
                    viewDimension = GpuTextureViewDimension.D2,
                    bytesPerTexel = 16,
                    trackedBytes = floatData.size.toLong() * 4L,
                    data = typed
                )
            }

            byteData != null -> {
                val typed = Uint8Array(byteData.size)
                val dyn = typed.asDynamic()
                for (i in byteData.indices) {
                    dyn[i] = byteData[i].toInt() and 0xFF
                }
                TextureUpload(
                    width = width,
                    height = height,
                    depth = depth,
                    format = when (format) {
                        TextureFormat.SRGB8_ALPHA8 -> "rgba8unorm-srgb"
                        else -> "rgba8unorm"
                    },
                    dimension = GpuTextureDimension.D2,
                    viewDimension = GpuTextureViewDimension.D2,
                    bytesPerTexel = 4,
                    trackedBytes = byteData.size.toLong(),
                    data = typed
                )
            }

            intData != null -> {
                val typed = Uint8Array(intData.size)
                val dyn = typed.asDynamic()
                for (i in intData.indices) {
                    dyn[i] = intData[i].coerceIn(0, 255)
                }
                TextureUpload(
                    width = width,
                    height = height,
                    depth = depth,
                    format = "rgba8unorm",
                    dimension = GpuTextureDimension.D2,
                    viewDimension = GpuTextureViewDimension.D2,
                    bytesPerTexel = 4,
                    trackedBytes = intData.size.toLong(),
                    data = typed
                )
            }

            floatData != null -> {
                val bytes = ByteArray(floatData.size) { index ->
                    (floatData[index].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255).toByte()
                }
                val typed = Uint8Array(bytes.size)
                val dyn = typed.asDynamic()
                for (i in bytes.indices) {
                    dyn[i] = bytes[i].toInt() and 0xFF
                }
                TextureUpload(
                    width = width,
                    height = height,
                    depth = depth,
                    format = "rgba8unorm",
                    dimension = GpuTextureDimension.D2,
                    viewDimension = GpuTextureViewDimension.D2,
                    bytesPerTexel = 4,
                    trackedBytes = bytes.size.toLong(),
                    data = typed
                )
            }

            else -> null
        }
    }

    private fun createFallbackTexture(
        device: GpuDevice,
        data: ByteArray,
        depth: Int = 1,
        dimension: GpuTextureDimension = GpuTextureDimension.D2,
        viewDimension: GpuTextureViewDimension = GpuTextureViewDimension.D2,
        label: String = "MaterialTextureFallback"
    ): CachedTexture? {
        val gpuTexture = device.createTexture(
            GpuTextureDescriptor(
                width = 1,
                height = 1,
                depthOrArrayLayers = depth,
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = dimension,
                format = "rgba8unorm",
                usage = GpuTextureUsage.TEXTURE_BINDING.bits or GpuTextureUsage.COPY_DST.bits,
                label = label
            )
        )
        val typed = Uint8Array(data.size)
        val dyn = typed.asDynamic()
        for (i in data.indices) {
            dyn[i] = data[i].toInt() and 0xFF
        }
        writeTextureData(
            device,
            gpuTexture,
            TextureUpload(
                width = 1,
                height = 1,
                depth = depth,
                format = "rgba8unorm",
                dimension = dimension,
                viewDimension = viewDimension,
                bytesPerTexel = 4,
                trackedBytes = data.size.toLong(),
                data = typed
            )
        )
        val view = gpuTexture.createView(GpuTextureViewDescriptor(dimension = viewDimension))
        val trackedBytes = data.size.toLong() * depth
        statsTracker?.recordTextureCreated(trackedBytes)
        return CachedTexture(
            gpuTexture,
            view,
            version = 0,
            width = 1,
            height = 1,
            depth = depth,
            trackedBytes = trackedBytes
        )
    }

    private fun writeTextureData(
        device: GpuDevice,
        texture: GpuTexture,
        upload: TextureUpload
    ) {
        val rawDevice = device.unwrapHandle() as? GPUDevice ?: return
        val rawTexture = texture.unwrapHandle() as? GPUTexture ?: return

        val destination = js("({})")
        destination.texture = rawTexture
        destination.mipLevel = 0
        val origin = js("({})")
        origin.x = 0
        origin.y = 0
        origin.z = 0
        destination.origin = origin

        val layout = js("({})")
        layout.offset = 0
        layout.bytesPerRow = upload.width * upload.bytesPerTexel
        layout.rowsPerImage = upload.height

        val size = js("({})")
        size.width = upload.width
        size.height = upload.height
        size.depthOrArrayLayers = upload.depth

        rawDevice.queue.writeTexture(destination, upload.data, layout, size)
    }
}

