package io.materia.renderer.webgpu

import org.khronos.webgl.Uint8Array

/**
 * Texture descriptor for creation.
 */
data class TextureDescriptor(
    val label: String? = null,
    val width: Int,
    val height: Int,
    val depth: Int = 1,
    val format: TextureFormat = TextureFormat.RGBA8_UNORM,
    val usage: Int = GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.COPY_DST,
    val mipLevelCount: Int = 1,
    val sampleCount: Int = 1
)

/**
 * WebGPU texture implementation.
 * T031: 2D/3D texture handling and sampling.
 */
class WebGPUTexture(
    private val device: GPUDevice,
    private val descriptor: TextureDescriptor
) {
    private var texture: GPUTexture? = null
    private var view: GPUTextureView? = null

    /**
     * Creates the GPU texture.
     */
    fun create(): io.materia.core.Result<Unit> {
        return try {
            val textureDescriptor = js("({})").unsafeCast<dynamic>()
            descriptor.label?.let { textureDescriptor.label = it }

            val size = js("({})").unsafeCast<dynamic>()
            size.width = descriptor.width
            size.height = descriptor.height
            size.depthOrArrayLayers = descriptor.depth
            textureDescriptor.size = size

            textureDescriptor.mipLevelCount = descriptor.mipLevelCount
            textureDescriptor.sampleCount = descriptor.sampleCount
            textureDescriptor.dimension = "2d"
            textureDescriptor.format = when (descriptor.format) {
                TextureFormat.RGBA8_UNORM -> "rgba8unorm"
                TextureFormat.RGBA8_SRGB -> "rgba8unorm-srgb"
                TextureFormat.BGRA8_UNORM -> "bgra8unorm"
                TextureFormat.BGRA8_SRGB -> "bgra8unorm-srgb"
                TextureFormat.DEPTH24_PLUS -> "depth24plus"
                TextureFormat.DEPTH32_FLOAT -> "depth32float"
            }
            textureDescriptor.usage = descriptor.usage

            texture = device.createTexture(textureDescriptor.unsafeCast<GPUTextureDescriptor>())

            view = texture?.createView()

            io.materia.core.Result.Success(Unit)
        } catch (e: Exception) {
            io.materia.core.Result.Error("Texture creation failed: ${e.message}", e)
        }
    }

    /**
     * Uploads image data to the texture.
     * @param data Image data
     * @param width Width in pixels
     * @param height Height in pixels
     */
    fun upload(data: ByteArray, width: Int, height: Int): io.materia.core.Result<Unit> {
        return try {
            texture?.let { tex ->
                val destination = js("({})").unsafeCast<dynamic>()
                destination.texture = tex
                destination.mipLevel = 0
                val origin = js("({})").unsafeCast<dynamic>()
                origin.x = 0
                origin.y = 0
                origin.z = 0
                destination.origin = origin

                val dataLayout = js("({})").unsafeCast<dynamic>()
                dataLayout.offset = 0
                dataLayout.bytesPerRow = width * 4
                dataLayout.rowsPerImage = height

                val writeSize = js("({})").unsafeCast<dynamic>()
                writeSize.width = width
                writeSize.height = height
                writeSize.depthOrArrayLayers = 1

                val uint8Array = Uint8Array(data.size)
                val uint8ArrayDynamic = uint8Array.asDynamic()
                for (i in data.indices) {
                    uint8ArrayDynamic[i] = data[i].toInt() and 0xFF
                }

                device.queue.writeTexture(destination, uint8Array, dataLayout, writeSize)
                io.materia.core.Result.Success(Unit)
            } ?: io.materia.core.Result.Error(
                "Texture not created",
                RuntimeException("Texture not created")
            )
        } catch (e: Exception) {
            io.materia.core.Result.Error("Texture upload failed: ${e.message}", e)
        }
    }

    /**
     * Gets the GPU texture handle.
     */
    fun getTexture(): GPUTexture? = texture

    /**
     * Gets the texture view for sampling.
     */
    fun getView(): GPUTextureView? = view

    /**
     * Creates a custom texture view.
     */
    fun createView(viewDescriptor: GPUTextureViewDescriptor? = null): GPUTextureView? {
        return if (viewDescriptor != null) {
            texture?.createView(viewDescriptor)
        } else {
            texture?.createView()
        }
    }

    /**
     * Gets texture dimensions.
     */
    fun getDimensions(): Triple<Int, Int, Int> {
        return Triple(descriptor.width, descriptor.height, descriptor.depth)
    }

    /**
     * Gets texture format.
     */
    fun getFormat(): TextureFormat = descriptor.format

    /**
     * Disposes the texture and releases GPU memory.
     */
    fun dispose() {
        texture?.destroy()
        texture = null
        view = null
    }
}
