package io.materia.renderer.webgpu

import io.materia.renderer.gpu.GpuBuffer
import io.materia.renderer.gpu.GpuBufferDescriptor
import io.materia.renderer.gpu.GpuDevice
import io.materia.renderer.gpu.unwrapHandle
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.Uint32Array

/**
 * WebGPU buffer implementation.
 * T030: GPU buffer management for vertices, indices, and uniforms.
 */
class WebGPUBuffer(
    private val device: GpuDevice,
    private val descriptor: BufferDescriptor
) {
    private var gpuBuffer: GpuBuffer? = null
    private var buffer: GPUBuffer? = null

    /**
     * Creates the GPU buffer.
     */
    fun create(): io.materia.core.Result<Unit> {
        return try {
            val gpuDescriptor = GpuBufferDescriptor(
                label = descriptor.label,
                size = descriptor.size.toLong(),
                usage = descriptor.usage,
                mappedAtCreation = descriptor.mappedAtCreation
            )
            gpuBuffer = device.createBuffer(gpuDescriptor, null)
            buffer = gpuBuffer?.unwrapHandle() as? GPUBuffer
            if (buffer == null) {
                io.materia.core.Result.Error(
                    "Buffer creation failed",
                    IllegalStateException("GPU buffer handle null")
                )
            } else {
                io.materia.core.Result.Success(Unit)
            }
        } catch (e: Exception) {
            io.materia.core.Result.Error("Buffer creation failed", e)
        }
    }

    /**
     * Uploads data to the buffer.
     * @param data Data to upload (FloatArray, IntArray, etc.)
     * @param offset Offset in bytes
     */
    fun upload(data: FloatArray, offset: Int = 0): io.materia.core.Result<Unit> {
        return try {
            buffer?.let { buf ->
                val float32Array = Float32Array(data.size)
                for (i in data.indices) {
                    float32Array.asDynamic()[i] = data[i]
                }
                val rawDevice = device.unwrapHandle() as? GPUDevice
                    ?: return io.materia.core.Result.Error(
                        "Device unavailable",
                        IllegalStateException("GPU device missing")
                    )
                rawDevice.queue.writeBuffer(buf, offset, float32Array, 0, data.size)
                io.materia.core.Result.Success(Unit)
            } ?: io.materia.core.Result.Error(
                "Buffer not created",
                IllegalStateException("Buffer not created")
            )
        } catch (e: Exception) {
            io.materia.core.Result.Error("Buffer upload failed", e)
        }
    }

    /**
     * Uploads index data to the buffer.
     */
    fun uploadIndices(data: IntArray, offset: Int = 0): io.materia.core.Result<Unit> {
        return try {
            buffer?.let { buf ->
                val uint32Array = Uint32Array(data.size)
                for (i in data.indices) {
                    uint32Array.asDynamic()[i] = data[i]
                }
                val rawDevice = device.unwrapHandle() as? GPUDevice
                    ?: return io.materia.core.Result.Error(
                        "Device unavailable",
                        IllegalStateException("GPU device missing")
                    )
                rawDevice.queue.writeBuffer(buf, offset, uint32Array, 0, data.size)
                io.materia.core.Result.Success(Unit)
            } ?: io.materia.core.Result.Error(
                "Buffer not created",
                IllegalStateException("Buffer not created")
            )
        } catch (e: Exception) {
            io.materia.core.Result.Error("Index upload failed", e)
        }
    }

    /**
     * Uploads index data as Uint16.
     */
    fun uploadIndices16(data: ShortArray, offset: Int = 0): io.materia.core.Result<Unit> {
        return try {
            buffer?.let { buf ->
                val uint16Array = Uint16Array(data.size)
                for (i in data.indices) {
                    uint16Array.asDynamic()[i] = data[i]
                }
                val rawDevice = device.unwrapHandle() as? GPUDevice
                    ?: return io.materia.core.Result.Error(
                        "Device unavailable",
                        IllegalStateException("GPU device missing")
                    )
                rawDevice.queue.writeBuffer(buf, offset, uint16Array, 0, data.size)
                io.materia.core.Result.Success(Unit)
            } ?: io.materia.core.Result.Error(
                "Buffer not created",
                IllegalStateException("Buffer not created")
            )
        } catch (e: Exception) {
            io.materia.core.Result.Error("Index upload failed", e)
        }
    }

    /**
     * Gets the GPU buffer handle.
     */
    fun getBuffer(): GPUBuffer? = buffer

    /**
     * Returns the abstraction wrapper for this buffer if it was created.
     */
    fun gpuBuffer(): GpuBuffer? = gpuBuffer

    /**
     * Gets buffer size in bytes.
     */
    fun getSize(): Int = descriptor.size

    /**
     * Gets buffer usage flags.
     */
    fun getUsage(): Int = descriptor.usage

    /**
     * Binds the buffer for rendering.
     * @param slot Binding slot
     * @param renderPass Render pass encoder
     */
    fun bind(slot: Int, renderPass: GPURenderPassEncoder) {
        buffer?.let {
            if ((descriptor.usage and GPUBufferUsage.VERTEX) != 0) {
                renderPass.setVertexBuffer(slot, it)
            } else if ((descriptor.usage and GPUBufferUsage.INDEX) != 0) {
                renderPass.setIndexBuffer(it, "uint32")
            }
        }
    }

    /**
     * Disposes the buffer and releases GPU memory.
     */
    fun dispose() {
        buffer?.destroy()
        buffer = null
        gpuBuffer = null
    }
}
