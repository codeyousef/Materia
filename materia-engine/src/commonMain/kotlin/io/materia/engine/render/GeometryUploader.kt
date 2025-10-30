package io.materia.engine.render

import io.materia.engine.geometry.AttributeSemantic
import io.materia.engine.geometry.Geometry
import io.materia.gpu.GpuBuffer
import io.materia.gpu.GpuBufferDescriptor
import io.materia.gpu.GpuBufferUsage
import io.materia.gpu.GpuDevice
import io.materia.gpu.GpuIndexFormat
import io.materia.gpu.gpuBufferUsage

internal class GeometryUploader(
    private val device: GpuDevice
) {
    fun upload(geometry: Geometry, label: String? = null): UploadedGeometry {
        val vertexData = geometry.vertexBuffer.data
        val strideBytes = resolveStrideBytes(geometry)
        val vertexCount = if (strideBytes > 0) {
            (vertexData.size * Float.SIZE_BYTES) / strideBytes
        } else {
            vertexData.size / componentsFor(geometry)
        }

        val vertexBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = label ?: "geometry-vertex-buffer",
                size = vertexData.size * Float.SIZE_BYTES.toLong(),
                usage = gpuBufferUsage(GpuBufferUsage.VERTEX, GpuBufferUsage.COPY_DST)
            )
        )
        vertexBuffer.writeFloats(vertexData)

        var indexFormat: GpuIndexFormat? = null
        val indexBuffer = geometry.indexBuffer?.let { indices ->
            val buffer = device.createBuffer(
                GpuBufferDescriptor(
                    label = (label ?: "geometry") + "-index-buffer",
                    size = indices.size * Short.SIZE_BYTES.toLong(),
                    usage = gpuBufferUsage(GpuBufferUsage.INDEX, GpuBufferUsage.COPY_DST)
                )
            )
            buffer.write(indices.toLittleEndianBytes())
            indexFormat = GpuIndexFormat.UINT16
            buffer
        }

        return UploadedGeometry(
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            vertexCount = vertexCount,
            indexCount = geometry.indexBuffer?.size,
            indexFormat = indexFormat
        )
    }

    private fun resolveStrideBytes(geometry: Geometry): Int =
        when {
            geometry.vertexBuffer.strideBytes > 0 -> geometry.vertexBuffer.strideBytes
            geometry.layout.stride > 0 -> geometry.layout.stride
            else -> componentsFor(geometry) * Float.SIZE_BYTES
        }

    private fun componentsFor(geometry: Geometry): Int {
        val position = geometry.layout.attributes[AttributeSemantic.POSITION]
        return position?.components ?: 1
    }
}

internal data class UploadedGeometry(
    val vertexBuffer: GpuBuffer,
    val indexBuffer: GpuBuffer?,
    val vertexCount: Int,
    val indexCount: Int?,
    val indexFormat: GpuIndexFormat?
) {
    fun destroy() {
        vertexBuffer.destroy()
        indexBuffer?.destroy()
    }
}

private fun ShortArray.toLittleEndianBytes(): ByteArray {
    val result = ByteArray(size * Short.SIZE_BYTES)
    var i = 0
    for (value in this) {
        result[i++] = (value.toInt() and 0xFF).toByte()
        result[i++] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
    return result
}
