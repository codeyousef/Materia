package io.kreekt.renderer.webgpu

import io.kreekt.renderer.CubeTexture
import io.kreekt.renderer.CubeTextureImpl
import io.kreekt.renderer.CubeFace
import io.kreekt.renderer.Texture2D
import io.kreekt.renderer.TextureFilter
import io.kreekt.renderer.TextureFormat
import io.kreekt.renderer.gpu.GpuBindGroup
import io.kreekt.renderer.gpu.GpuBindGroupDescriptor
import io.kreekt.renderer.gpu.GpuBindGroupEntry
import io.kreekt.renderer.gpu.GpuBindGroupLayout
import io.kreekt.renderer.gpu.GpuBindGroupLayoutDescriptor
import io.kreekt.renderer.gpu.GpuBindGroupLayoutEntry
import io.kreekt.renderer.gpu.GpuBindingResource
import io.kreekt.renderer.gpu.GpuDevice
import io.kreekt.renderer.gpu.GpuResourceRegistry
import io.kreekt.renderer.gpu.GpuSampler
import io.kreekt.renderer.gpu.GpuSamplerBindingLayout
import io.kreekt.renderer.gpu.GpuSamplerBindingType
import io.kreekt.renderer.gpu.GpuSamplerDescriptor
import io.kreekt.renderer.gpu.GpuSamplerFilter
import io.kreekt.renderer.gpu.GpuShaderStage
import io.kreekt.renderer.gpu.GpuTexture
import io.kreekt.renderer.gpu.GpuTextureBindingLayout
import io.kreekt.renderer.gpu.GpuTextureDescriptor
import io.kreekt.renderer.gpu.GpuTextureDimension
import io.kreekt.renderer.gpu.GpuTextureSampleType
import io.kreekt.renderer.gpu.GpuTextureViewDescriptor
import io.kreekt.renderer.gpu.GpuTextureView
import io.kreekt.renderer.gpu.GpuTextureViewDimension
import io.kreekt.renderer.gpu.unwrapHandle
import io.kreekt.renderer.webgpu.GPUTextureUsage
import io.kreekt.renderer.webgpu.GPUDevice
import io.kreekt.renderer.webgpu.GPUTexture
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import kotlin.math.abs
import kotlin.math.max

internal data class EnvironmentBinding(
    val bindGroup: GpuBindGroup,
    val layout: GpuBindGroupLayout,
    val mipCount: Int,
    val usingFallbackEnvironment: Boolean = false,
    val usingFallbackBrdf: Boolean = false
)

/**
 * Handles uploading prefiltered environment cubemaps to GPU memory and
 * prepares sampler/bind group objects for the WebGPU renderer.
 */
internal class WebGPUEnvironmentManager(
    private val deviceProvider: () -> GpuDevice?,
    private val statsTracker: RenderStatsTracker? = null
) {
    private val resourceRegistry = GpuResourceRegistry()
    private var cubeTexture: GpuTexture? = null
    private var cubeView: GpuTextureView? = null
    private var sampler: GpuSampler? = null
    private var brdfTexture: GpuTexture? = null
    private var brdfView: GpuTextureView? = null
    private var brdfSampler: GpuSampler? = null
    private var bindGroupLayout: GpuBindGroupLayout? = null
    private var bindGroup: GpuBindGroup? = null
    private var lastTextureId: Int = -1
    private var mipCount: Int = 1
    private var trackedBytes: Long = 0
    private var lastBrdfId: Int = -1
    private var lastBrdfVersion: Int = -1
    private var trackedBrdfBytes: Long = 0
    private var lastTextureVersion: Int = -1
    private var lastUsingFallbackEnvironment: Boolean = false
    private var lastUsingFallbackBrdf: Boolean = false
    private var fallbackCubeTexture: CubeTextureImpl? = null
    private var fallbackBrdfTexture: Texture2D? = null

    fun prepare(cube: CubeTexture?, brdf: Texture2D?): EnvironmentBinding? {
        val device = deviceProvider() ?: return null

        val usingFallbackEnvironment = cube == null
        val effectiveCube: CubeTexture = cube ?: ensureFallbackCube()
        val sourceId = cube?.id ?: FALLBACK_ENVIRONMENT_ID
        val currentVersion = when {
            usingFallbackEnvironment -> FALLBACK_VERSION
            cube is CubeTextureImpl -> cube.version
            else -> if (cube?.needsUpdate == true) lastTextureVersion + 1 else lastTextureVersion
        }

        val environmentChanged = bindGroup == null ||
            cubeTexture == null ||
            sourceId != lastTextureId ||
            currentVersion != lastTextureVersion ||
            cube?.needsUpdate == true ||
            usingFallbackEnvironment != lastUsingFallbackEnvironment

        val usingFallbackBrdf = brdf == null
        val brdfSourceId = brdf?.id ?: FALLBACK_BRDF_ID
        val brdfSourceVersion = brdf?.version ?: FALLBACK_VERSION
        var brdfChanged = brdfView == null ||
            brdfSourceId != lastBrdfId ||
            brdfSourceVersion != lastBrdfVersion ||
            brdf?.needsUpdate == true ||
            usingFallbackBrdf != lastUsingFallbackBrdf

        if (environmentChanged) {
            uploadEnvironment(device, effectiveCube)
            createSampler(device)
            brdfChanged = true
        }

        if (brdfChanged) {
            ensureBrdfResources(device, brdf)
        }

        if (environmentChanged || brdfChanged || bindGroup == null) {
            createBindGroup(device)
        }

        val layout = bindGroupLayout ?: return null
        val group = bindGroup ?: return null

        cube?.needsUpdate = false

        lastTextureId = sourceId
        lastTextureVersion = currentVersion
        lastUsingFallbackEnvironment = usingFallbackEnvironment
        lastUsingFallbackBrdf = usingFallbackBrdf

        return EnvironmentBinding(
            bindGroup = group,
            layout = layout,
            mipCount = mipCount,
            usingFallbackEnvironment = usingFallbackEnvironment,
            usingFallbackBrdf = usingFallbackBrdf
        )
    }

    fun dispose() {
        if (trackedBytes > 0) {
            statsTracker?.recordTextureDisposed(trackedBytes)
            trackedBytes = 0
        }
        if (trackedBrdfBytes > 0) {
            statsTracker?.recordTextureDisposed(trackedBrdfBytes)
            trackedBrdfBytes = 0
        }
        resourceRegistry.disposeAll()
        resourceRegistry.reset()
        cubeTexture = null
        cubeView = null
        sampler = null
        brdfTexture = null
        brdfView = null
        brdfSampler = null
        bindGroup = null
        bindGroupLayout = null
        lastTextureId = -1
        lastTextureVersion = -1
        lastBrdfId = -1
        lastBrdfVersion = -1
        lastUsingFallbackEnvironment = false
        lastUsingFallbackBrdf = false
    }

    private fun uploadEnvironment(device: GpuDevice, cube: CubeTexture) {
        val size = cube.size

        val (mipLevels, faceMipData, byteSize) = collectMipChain(cube)
        mipCount = mipLevels

        if (trackedBytes > 0) {
            statsTracker?.recordTextureDisposed(trackedBytes)
            trackedBytes = 0
        }
        if (trackedBrdfBytes > 0) {
            statsTracker?.recordTextureDisposed(trackedBrdfBytes)
            trackedBrdfBytes = 0
        }
        resourceRegistry.disposeAll()
        resourceRegistry.reset()
        cubeTexture = null
        cubeView = null
        brdfTexture = null
        brdfView = null
        brdfSampler = null
        bindGroup = null
        lastBrdfId = -1
        lastBrdfVersion = -1

        val descriptor = GpuTextureDescriptor(
            width = size,
            height = size,
            depthOrArrayLayers = 6,
            mipLevelCount = mipCount,
            sampleCount = 1,
            dimension = GpuTextureDimension.D2,
            format = "rgba16float",
            usage = GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.COPY_DST,
            label = "IBL Prefilter Cubemap"
        )

        val texture = device.createTexture(descriptor)
        resourceRegistry.trackTexture(texture)
        cubeTexture = texture
        cubeView = texture.createView(
            GpuTextureViewDescriptor(
                label = "IBL Prefilter Cube View",
                dimension = GpuTextureViewDimension.CUBE,
                mipLevelCount = mipCount
            )
        )
        trackedBytes = byteSize
        statsTracker?.recordTextureCreated(byteSize)

        val rawDevice = device.unwrapHandle() as? GPUDevice ?: return
        val rawTexture = texture.unwrapHandle() as GPUTexture

        for (level in 0 until mipCount) {
            val mipSize = max(1, size shr level)
            val bytesPerPixel = HALF_BYTE_STRIDE
            val rowBytes = mipSize * bytesPerPixel
            val alignedRowBytes = alignRowPitch(rowBytes)

            for (face in 0 until 6) {
                val dataKey = face to level
                val rawData = faceMipData[dataKey] ?: continue
                val uploadBytes =
                    if (alignedRowBytes == rowBytes) rawData else padRows(rawData, rowBytes, alignedRowBytes, mipSize)

                val destination = js("({})")
                destination.texture = rawTexture
                destination.mipLevel = level
                val origin = js("({})")
                origin.x = 0
                origin.y = 0
                origin.z = face
                destination.origin = origin

                val dataLayout = js("({})")
                dataLayout.offset = 0
                dataLayout.bytesPerRow = alignedRowBytes
                dataLayout.rowsPerImage = mipSize

                val sizeDesc = js("({})")
                sizeDesc.width = mipSize
                sizeDesc.height = mipSize
                sizeDesc.depthOrArrayLayers = 1

                val uint8 = Uint8Array(uploadBytes.size)
                val uint8Dynamic = uint8.asDynamic()
                for (i in uploadBytes.indices) {
                    uint8Dynamic[i] = uploadBytes[i].toInt() and 0xFF
                }

                rawDevice.queue.writeTexture(destination, uint8, dataLayout, sizeDesc)
            }
        }
    }

    private fun ensureBrdfResources(device: GpuDevice, brdf: Texture2D?) {
        val textureSource = brdf ?: ensureFallbackBrdfTexture()
        val sourceId = brdf?.id ?: FALLBACK_BRDF_ID
        val sourceVersion = brdf?.version ?: FALLBACK_VERSION

        if (brdfView != null && sourceId == lastBrdfId && sourceVersion == lastBrdfVersion) {
            brdf?.needsUpdate = false
            return
        }

        brdfTexture = null
        brdfView = null
        brdfSampler = null

        val width = textureSource.width
        val height = textureSource.height
        val floatData = textureSource.getData() ?: fallbackBrdfData(width, height)

        val descriptor = GpuTextureDescriptor(
            width = width,
            height = height,
            depthOrArrayLayers = 1,
            mipLevelCount = 1,
            sampleCount = 1,
            dimension = GpuTextureDimension.D2,
            format = "rg32float",
            usage = GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.COPY_DST,
            label = "IBL BRDF LUT"
        )

        val texture = device.createTexture(descriptor)
        resourceRegistry.trackTexture(texture)
        brdfTexture = texture
        val view = texture.createView(
            GpuTextureViewDescriptor(
                label = "IBL BRDF View",
                dimension = GpuTextureViewDimension.D2
            )
        )
        brdfView = view

        val samplerDescriptor = GpuSamplerDescriptor(
            magFilter = GpuSamplerFilter.LINEAR,
            minFilter = GpuSamplerFilter.LINEAR,
            mipmapFilter = GpuSamplerFilter.LINEAR,
            lodMinClamp = 0f,
            lodMaxClamp = 0f,
            label = "IBL BRDF Sampler"
        )
        val samplerHandle = device.createSampler(samplerDescriptor)
        brdfSampler = samplerHandle

        uploadBrdfData(device, texture, floatData, width, height)

        trackedBrdfBytes = width.toLong() * height.toLong() * BRDF_BYTES_PER_PIXEL
        statsTracker?.recordTextureCreated(trackedBrdfBytes)

        lastBrdfId = sourceId
        lastBrdfVersion = sourceVersion
        brdf?.needsUpdate = false
    }

    private fun createSampler(device: GpuDevice) {
        sampler = device.createSampler(
            GpuSamplerDescriptor(
                magFilter = GpuSamplerFilter.LINEAR,
                minFilter = GpuSamplerFilter.LINEAR,
                mipmapFilter = GpuSamplerFilter.LINEAR,
                lodMinClamp = 0f,
                lodMaxClamp = max(0, mipCount - 1).toFloat(),
                label = "IBL Prefilter Sampler"
            )
        )
    }

    private fun createBindGroup(device: GpuDevice) {
        val layout = bindGroupLayout ?: createBindGroupLayout(device)
        val view = cubeView ?: return
        val samplerHandle = sampler ?: return
        val brdfViewHandle = brdfView ?: return
        val brdfSamplerHandle = brdfSampler ?: return

        val descriptor = GpuBindGroupDescriptor(
            layout = layout,
            entries = listOf(
                GpuBindGroupEntry(
                    binding = 0,
                    resource = GpuBindingResource.Texture(view)
                ),
                GpuBindGroupEntry(
                    binding = 1,
                    resource = GpuBindingResource.Sampler(samplerHandle)
                ),
                GpuBindGroupEntry(
                    binding = 2,
                    resource = GpuBindingResource.Texture(brdfViewHandle)
                ),
                GpuBindGroupEntry(
                    binding = 3,
                    resource = GpuBindingResource.Sampler(brdfSamplerHandle)
                )
            ),
            label = "IBL Prefilter Bind Group"
        )
        bindGroup = device.createBindGroup(descriptor)
    }

    private fun createBindGroupLayout(device: GpuDevice): GpuBindGroupLayout {
        val descriptor = GpuBindGroupLayoutDescriptor(
            entries = listOf(
                GpuBindGroupLayoutEntry(
                    binding = 0,
                    visibility = GpuShaderStage.FRAGMENT.bits,
                    texture = GpuTextureBindingLayout(
                        sampleType = GpuTextureSampleType.FLOAT,
                        viewDimension = GpuTextureViewDimension.CUBE,
                        multisampled = false
                    )
                ),
                GpuBindGroupLayoutEntry(
                    binding = 1,
                    visibility = GpuShaderStage.FRAGMENT.bits,
                    sampler = GpuSamplerBindingLayout(GpuSamplerBindingType.FILTERING)
                ),
                GpuBindGroupLayoutEntry(
                    binding = 2,
                    visibility = GpuShaderStage.FRAGMENT.bits,
                    texture = GpuTextureBindingLayout(
                        sampleType = GpuTextureSampleType.FLOAT,
                        viewDimension = GpuTextureViewDimension.D2,
                        multisampled = false
                    )
                ),
                GpuBindGroupLayoutEntry(
                    binding = 3,
                    visibility = GpuShaderStage.FRAGMENT.bits,
                    sampler = GpuSamplerBindingLayout(GpuSamplerBindingType.FILTERING)
                )
            ),
            label = "IBL Prefilter Bind Group Layout"
        )
        val layout = device.createBindGroupLayout(descriptor)
        bindGroupLayout = layout
        return layout
    }

    private fun ensureFallbackCube(): CubeTextureImpl {
        fallbackCubeTexture?.let { return it }

        val cube = CubeTextureImpl(
            size = 1,
            format = TextureFormat.RGBA8,
            filter = TextureFilter.LINEAR,
            generateMipmaps = false,
            textureName = "FallbackEnvironment"
        )
        val faceData = FloatArray(4).apply { this[3] = 1f }
        CubeFace.values().forEach { face ->
            cube.setFaceData(face, faceData.copyOf())
        }
        cube.needsUpdate = false
        cube.version = FALLBACK_VERSION
        fallbackCubeTexture = cube
        return cube
    }

    private fun ensureFallbackBrdfTexture(): Texture2D {
        fallbackBrdfTexture?.let { return it }
        val width = FALLBACK_BRDF_SIZE
        val height = FALLBACK_BRDF_SIZE
        val texture = Texture2D(
            width = width,
            height = height,
            format = TextureFormat.RG32F,
            filter = TextureFilter.LINEAR,
            generateMipmaps = false,
            textureName = "FallbackBrdfLut"
        )
        texture.setData(fallbackBrdfData(width, height))
        texture.needsUpdate = false
        texture.version = FALLBACK_VERSION
        fallbackBrdfTexture = texture
        return texture
    }

    private fun collectMipChain(cube: CubeTexture): Triple<Int, Map<Pair<Int, Int>, ByteArray>, Long> {
        val mipCount = when (cube) {
            is CubeTextureImpl -> cube.maxMipLevel() + 1
            else -> 1
        }.coerceAtLeast(1)

        val faceMipData = mutableMapOf<Pair<Int, Int>, ByteArray>()
        var totalBytes = 0L

        val baseSize = cube.size
        for (level in 0 until mipCount) {
            val mipSize = max(1, baseSize shr level)
            val floatCountPerFace = mipSize * mipSize * 4
            val bytesPerFace = HALF_BYTE_STRIDE * mipSize * mipSize
            totalBytes += bytesPerFace.toLong() * 6

            for (face in 0 until 6) {
                val floatData = when (cube) {
                    is CubeTextureImpl -> cube.getFaceData(CubeFace.values()[face], level)
                    else -> null
                } ?: when (cube) {
                    is io.kreekt.texture.CubeTexture -> cube.getFaceFloatData(face)
                    else -> null
                } ?: FloatArray(floatCountPerFace) { 0f }

                val halfBytes = floatArrayToHalfBytes(floatData)
                faceMipData[face to level] = halfBytes
            }
        }

        return Triple(mipCount, faceMipData, totalBytes)
    }

    private fun io.kreekt.texture.CubeTexture.getFaceFloatData(face: Int): FloatArray? {
        val raw = getFaceFloat(face) ?: return null
        return raw
    }

    private fun io.kreekt.texture.CubeTexture.getFaceFloat(face: Int): FloatArray? {
        val float = this.getFaceFloatData(io.kreekt.texture.CubeFace.values()[face])
        if (float != null) return float
        val bytes = this.getFaceData(io.kreekt.texture.CubeFace.values()[face]) ?: return null
        val data = FloatArray(bytes.size)
        for (i in bytes.indices) {
            data[i] = (bytes[i].toInt() and 0xFF) / 255f
        }
        return data
    }

    private fun floatArrayToHalfBytes(source: FloatArray): ByteArray {
        val result = ByteArray(source.size * 2)
        var outIndex = 0
        for (value in source) {
            val half = floatToHalf(value)
            result[outIndex++] = (half and 0xFF).toByte()
            result[outIndex++] = ((half shr 8) and 0xFF).toByte()
        }
        return result
    }

    private fun floatToHalf(value: Float): Int {
        if (value.isNaN()) return 0x7E00
        if (value == Float.POSITIVE_INFINITY) return 0x7C00
        if (value == Float.NEGATIVE_INFINITY) return 0xFC00
        val bits = abs(value).toBits()
        val sign = if (value < 0f) 0x8000 else 0
        var exponent = ((bits shr 23) and 0xFF) - 127 + 15
        var mantissa = bits and 0x7FFFFF

        return when {
            exponent <= 0 -> {
                if (exponent < -10) {
                    sign
                } else {
                    mantissa = mantissa or 0x800000
                    val shift = 14 - exponent
                    val halfMantissa = mantissa shr shift
                    sign or (halfMantissa + ((mantissa shr (shift - 1)) and 1))
                }
            }

            exponent >= 0x1F -> sign or 0x7C00
            else -> {
                val halfMantissa = mantissa shr 13
                val half = sign or (exponent shl 10) or halfMantissa
                half + ((mantissa shr 12) and 1)
            }
        }
    }

    private fun alignRowPitch(rowBytes: Int): Int {
        val alignment = 256
        return if (rowBytes % alignment == 0) rowBytes else ((rowBytes / alignment) + 1) * alignment
    }

    private fun padRows(data: ByteArray, rowBytes: Int, alignedRowBytes: Int, rows: Int): ByteArray {
        val padded = ByteArray(alignedRowBytes * rows)
        for (y in 0 until rows) {
            data.copyInto(
                destination = padded,
                destinationOffset = y * alignedRowBytes,
                startIndex = y * rowBytes,
                endIndex = y * rowBytes + rowBytes
            )
        }
        return padded
    }

    private fun uploadBrdfData(device: GpuDevice, texture: GpuTexture, data: FloatArray, width: Int, height: Int) {
        val floatArray = Float32Array(data.size)
        val floatDynamic = floatArray.asDynamic()
        for (i in data.indices) {
            floatDynamic[i] = data[i]
        }
        val rawBytes = Uint8Array(floatArray.buffer)

        val rawDevice = device.unwrapHandle() as? GPUDevice ?: return
        val rawTexture = texture.unwrapHandle() as GPUTexture

        val destination = js("({})")
        destination.texture = rawTexture
        destination.mipLevel = 0
        val origin = js("({})")
        origin.x = 0
        origin.y = 0
        origin.z = 0
        destination.origin = origin

        val bytesPerRow = width * BRDF_BYTES_PER_PIXEL
        val dataLayout = js("({})")
        dataLayout.offset = 0
        dataLayout.bytesPerRow = bytesPerRow
        dataLayout.rowsPerImage = height

        val sizeDesc = js("({})")
        sizeDesc.width = width
        sizeDesc.height = height
        sizeDesc.depthOrArrayLayers = 1

        rawDevice.queue.writeTexture(destination, rawBytes, dataLayout, sizeDesc)
    }

    private fun fallbackBrdfData(width: Int, height: Int): FloatArray {
        val data = FloatArray(width * height * 2)
        for (i in 0 until width * height) {
            data[i * 2] = 0f
            data[i * 2 + 1] = 1f
        }
        return data
    }

    companion object {
        private const val HALF_BYTE_STRIDE = 8 // rgba16f = 4 * 2 bytes
        private const val BRDF_BYTES_PER_PIXEL = 8
        private const val FALLBACK_BRDF_ID = -3
        private const val FALLBACK_VERSION = -1
        private const val FALLBACK_BRDF_SIZE = 32
    }
}
