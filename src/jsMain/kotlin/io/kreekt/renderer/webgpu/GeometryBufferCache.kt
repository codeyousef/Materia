package io.kreekt.renderer.webgpu

import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.gpu.GpuDevice
import io.kreekt.renderer.geometry.GeometryBuilder
import io.kreekt.renderer.geometry.GeometryBuildOptions
import io.kreekt.renderer.geometry.GeometryMetadata
import io.kreekt.renderer.webgpu.VertexBufferLayout

internal class GeometryBufferCache(
    private val deviceProvider: () -> GpuDevice?,
    private val statsTracker: RenderStatsTracker? = null
) {
    private data class CacheKey(val geometryId: String, val options: GeometryBuildOptions)

    private val buffersByGeometry = mutableMapOf<CacheKey, GeometryBuffers>()

    fun getOrCreate(
        geometry: BufferGeometry,
        frameCount: Int,
        options: GeometryBuildOptions
    ): GeometryBuffers? {
        val key = CacheKey(geometry.uuid, options)
        buffersByGeometry[key]?.let { return it }

        val gpuDevice = deviceProvider() ?: run {
            console.error("WebGPU device unavailable when creating geometry buffers")
            return null
        }

        return try {
            val geometryBuffer = GeometryBuilder.build(geometry, options)

            val vertexStreams = geometryBuffer.streams.mapIndexed { index, stream ->
                val vertexBuffer = WebGPUBuffer(
                    gpuDevice,
                    BufferDescriptor(
                        size = stream.data.size * 4,
                        usage = GPUBufferUsage.VERTEX or GPUBufferUsage.COPY_DST,
                        label = "Vertex Stream $index for ${geometry.uuid}"
                    )
                )
                vertexBuffer.create()
                vertexBuffer.upload(stream.data)

                statsTracker?.recordBufferAllocated((stream.data.size * 4).toLong())

                StreamBuffer(
                    buffer = vertexBuffer.getBuffer()!!,
                    sizeBytes = stream.data.size * 4,
                    layout = stream.layout
                )
            }

            var indexBuffer: GPUBuffer? = null
            var indexSizeBytes = 0
            val indexData = geometryBuffer.indexData
            if (indexData != null) {
                val buffer = WebGPUBuffer(
                    gpuDevice,
                    BufferDescriptor(
                        size = indexData.size * 4,
                        usage = GPUBufferUsage.INDEX or GPUBufferUsage.COPY_DST,
                        label = "Index Buffer ${geometry.uuid}"
                    )
                )
                buffer.create()
                buffer.uploadIndices(indexData)
                indexBuffer = buffer.getBuffer()
                indexSizeBytes = indexData.size * 4
                statsTracker?.recordBufferAllocated(indexSizeBytes.toLong())
            }

            val buffers = GeometryBuffers(
                vertexStreams = vertexStreams,
                indexBuffer = indexBuffer,
                indexBufferSize = indexSizeBytes,
                vertexCount = geometryBuffer.vertexCount,
                indexCount = geometryBuffer.indexCount,
                indexFormat = "uint32",
                instanceCount = geometryBuffer.instanceCount,
                metadata = geometryBuffer.metadata
            )

            buffersByGeometry[key] = buffers
            buffers
        } catch (e: Exception) {
            console.error("Failed to create geometry buffers: ${e.message}")
            null
        }
    }

    fun clear() {
        buffersByGeometry.values.forEach { buffers ->
            buffers.vertexStreams.forEach { stream ->
                try {
                    statsTracker?.recordBufferDeallocated(stream.sizeBytes.toLong())
                    stream.buffer.destroy()
                } catch (_: Throwable) {
                    // ignored
                }
            }

            try {
                buffers.indexBuffer?.let { indexBuffer ->
                    statsTracker?.recordBufferDeallocated(buffers.indexBufferSize.toLong())
                    indexBuffer.destroy()
                }
            } catch (_: Throwable) {
                // ignored
            }
        }
        buffersByGeometry.clear()
    }
}

internal data class GeometryBuffers(
    val vertexStreams: List<StreamBuffer>,
    val indexBuffer: GPUBuffer?,
    val indexBufferSize: Int,
    val vertexCount: Int,
    val indexCount: Int,
    val indexFormat: String,
    val instanceCount: Int,
    val metadata: GeometryMetadata
)

internal data class StreamBuffer(
    val buffer: GPUBuffer,
    val sizeBytes: Int,
    val layout: VertexBufferLayout
)
