package io.materia.renderer.webgpu

import io.materia.renderer.webgpu.RenderStatsTracker
import io.materia.material.MeshBasicMaterial
import io.materia.material.MeshStandardMaterial
import io.materia.material.Material as EngineMaterial
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
import io.materia.texture.Texture
import io.materia.texture.Texture2D
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
        var trackedBytes: Long
    )

    private data class LayoutKey(
        val useAlbedo: Boolean,
        val useNormal: Boolean
    )

    private data class BindGroupKey(
        val layoutKey: LayoutKey,
        val albedoId: Int?,
        val albedoVersion: Int,
        val normalId: Int?,
        val normalVersion: Int
    )

    private var currentDevice: GpuDevice? = null
    private var defaultSampler: GpuSampler? = null

    private val layoutCache = mutableMapOf<LayoutKey, GpuBindGroupLayout>()
    private val bindGroupCache = mutableMapOf<BindGroupKey, MaterialTextureBinding>()
    private val textureCache = mutableMapOf<Int, CachedTexture>()

    private var fallbackAlbedo: CachedTexture? = null
    private var fallbackNormal: CachedTexture? = null

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
    }

    fun prepare(
        descriptor: MaterialDescriptor,
        material: EngineMaterial?,
        useAlbedo: Boolean,
        useNormal: Boolean
    ): MaterialTextureBinding? {
        if (!useAlbedo && !useNormal) return null

        val device = currentDevice ?: deviceProvider()?.also(::onDeviceReady) ?: return null
        val sampler = defaultSampler ?: return null

        val layoutKey = LayoutKey(useAlbedo, useNormal)
        val layout = layoutCache.getOrPut(layoutKey) {
            createLayout(descriptor, layoutKey, device) ?: return null
        }

        val albedoTexture = if (useAlbedo) {
            acquireTexture(device, albedoSource(material)) ?: fallbackAlbedo
        } else fallbackAlbedo

        val normalTexture = if (useNormal) {
            acquireTexture(device, normalSource(material)) ?: fallbackNormal
        } else fallbackNormal

        val albedoKey = albedoTexture?.let { it.gpuTexture.hashCode() }
        val normalKey = normalTexture?.let { it.gpuTexture.hashCode() }
        val albedoVersion = albedoTexture?.version ?: -1
        val normalVersion = normalTexture?.version ?: -1

        val cacheKey = BindGroupKey(layoutKey, albedoKey, albedoVersion, normalKey, normalVersion)
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
        fallbackAlbedo = null
        fallbackNormal = null
        defaultSampler = null
        layoutCache.clear()
        bindGroupCache.clear()
        currentDevice = null
    }

    private fun albedoSource(material: EngineMaterial?): Texture2D? = when (material) {
        is MeshBasicMaterial -> material.map as? Texture2D
        is MeshStandardMaterial -> material.map as? Texture2D
        else -> null
    }

    private fun normalSource(material: EngineMaterial?): Texture2D? = when (material) {
        is MeshStandardMaterial -> material.normalMap as? Texture2D
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
        if (entries.isEmpty()) return null

        return device.createBindGroupLayout(
            GpuBindGroupLayoutDescriptor(
                entries = entries.sortedBy { it.binding },
                label = "Material Texture Layout (${key.useAlbedo}, ${key.useNormal})"
            )
        )
    }

    private fun textureLayoutEntry(binding: Int): GpuBindGroupLayoutEntry =
        GpuBindGroupLayoutEntry(
            binding = binding,
            visibility = GpuShaderStage.FRAGMENT.bits,
            texture = GpuTextureBindingLayout(
                sampleType = GpuTextureSampleType.FLOAT,
                viewDimension = GpuTextureViewDimension.D2,
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

    private fun acquireTexture(device: GpuDevice, texture: Texture2D?): CachedTexture? {
        texture ?: return null
        val width = texture.width
        val height = texture.height
        if (width <= 0 || height <= 0) return null

        val data = texture.getData() ?: texture.getFloatData()?.let { floats ->
            ByteArray(floats.size) { idx ->
                (floats[idx].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255).toByte()
            }
        } ?: return null

        val bytesPerTexel = 4
        val totalBytes = width.toLong() * height * bytesPerTexel

        val cached = textureCache[texture.id]
        if (cached != null && cached.version == texture.version && cached.width == width && cached.height == height) {
            return cached
        }

        cached?.let { previous ->
            statsTracker?.recordTextureDisposed(previous.trackedBytes)
            runCatching { previous.gpuTexture.destroy() }
        }

        val gpuTexture = device.createTexture(
            GpuTextureDescriptor(
                width = width,
                height = height,
                depthOrArrayLayers = 1,
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = "rgba8unorm",
                usage = GpuTextureUsage.TEXTURE_BINDING.bits or GpuTextureUsage.COPY_DST.bits,
                label = texture.name.ifEmpty { "MaterialTexture${texture.id}" }
            )
        )
        writeTextureBytes(device, gpuTexture, width, height, data)
        val view =
            gpuTexture.createView(GpuTextureViewDescriptor(dimension = GpuTextureViewDimension.D2))

        val cachedTexture = CachedTexture(
            gpuTexture = gpuTexture,
            view = view,
            version = texture.version,
            width = width,
            height = height,
            trackedBytes = totalBytes
        )
        textureCache[texture.id] = cachedTexture
        statsTracker?.recordTextureCreated(totalBytes)
        texture.needsUpdate = false
        return cachedTexture
    }

    private fun createFallbackTexture(device: GpuDevice, data: ByteArray): CachedTexture? {
        val gpuTexture = device.createTexture(
            GpuTextureDescriptor(
                width = 1,
                height = 1,
                depthOrArrayLayers = 1,
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = "rgba8unorm",
                usage = GpuTextureUsage.TEXTURE_BINDING.bits or GpuTextureUsage.COPY_DST.bits,
                label = "MaterialTextureFallback"
            )
        )
        writeTextureBytes(device, gpuTexture, 1, 1, data)
        val view =
            gpuTexture.createView(GpuTextureViewDescriptor(dimension = GpuTextureViewDimension.D2))
        val trackedBytes = data.size.toLong()
        statsTracker?.recordTextureCreated(trackedBytes)
        return CachedTexture(
            gpuTexture,
            view,
            version = 0,
            width = 1,
            height = 1,
            trackedBytes = trackedBytes
        )
    }

    private fun writeTextureBytes(
        device: GpuDevice,
        texture: GpuTexture,
        width: Int,
        height: Int,
        data: ByteArray
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
        layout.bytesPerRow = width * 4
        layout.rowsPerImage = height

        val size = js("({})")
        size.width = width
        size.height = height
        size.depthOrArrayLayers = 1

        val dataArray = Uint8Array(data.size)
        val dyn = dataArray.asDynamic()
        for (i in data.indices) {
            dyn[i] = data[i].toInt() and 0xFF
        }
        rawDevice.queue.writeTexture(destination, dataArray, layout, size)
    }
}

