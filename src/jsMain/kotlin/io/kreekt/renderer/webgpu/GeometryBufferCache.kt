package io.kreekt.renderer.webgpu

import io.kreekt.geometry.BufferGeometry

internal class GeometryBufferCache(
    private val deviceProvider: () -> GPUDevice?
) {
    private val buffersByGeometry = mutableMapOf<String, GeometryBuffers>()

    fun getOrCreate(geometry: BufferGeometry, frameCount: Int): GeometryBuffers? {
        val uuid = geometry.uuid
        buffersByGeometry[uuid]?.let { return it }

        val device = deviceProvider() ?: run {
            console.error("WebGPU device unavailable when creating geometry buffers")
            return null
        }

        return try {
            val positionAttr = geometry.getAttribute("position") ?: run {
                console.error("Position attribute is null for geometry $uuid")
                return null
            }
            val normalAttr = geometry.getAttribute("normal")
            val colorAttr = geometry.getAttribute("color")
            val indexAttr = geometry.index

            val vertexCount = positionAttr.count as Int
            val vertexData = FloatArray(vertexCount * 9)

            for (i in 0 until vertexCount) {
                val offset = i * 9

                vertexData[offset + 0] = positionAttr.getX(i)
                vertexData[offset + 1] = positionAttr.getY(i)
                vertexData[offset + 2] = positionAttr.getZ(i)

                if (normalAttr != null) {
                    vertexData[offset + 3] = normalAttr.getX(i)
                    vertexData[offset + 4] = normalAttr.getY(i)
                    vertexData[offset + 5] = normalAttr.getZ(i)
                } else {
                    vertexData[offset + 3] = 0f
                    vertexData[offset + 4] = 1f
                    vertexData[offset + 5] = 0f
                }

                if (colorAttr != null) {
                    vertexData[offset + 6] = colorAttr.getX(i)
                    vertexData[offset + 7] = colorAttr.getY(i)
                    vertexData[offset + 8] = colorAttr.getZ(i)

                    if (i == 0 && frameCount < 3) {
                        console.log(
                            "T021 First vertex: pos=(${vertexData[offset]}, ${vertexData[offset + 1]}, ${vertexData[offset + 2]}), " +
                                "color=(${vertexData[offset + 6]}, ${vertexData[offset + 7]}, ${vertexData[offset + 8]})"
                        )
                    }
                } else {
                    vertexData[offset + 6] = 1f
                    vertexData[offset + 7] = 1f
                    vertexData[offset + 8] = 1f
                    console.warn("T021 No color attribute - using white default")
                }
            }

            val vertexBuffer = WebGPUBuffer(
                device,
                BufferDescriptor(
                    size = vertexData.size * 4,
                    usage = GPUBufferUsage.VERTEX or GPUBufferUsage.COPY_DST,
                    label = "Vertex Buffer $uuid"
                )
            )
            vertexBuffer.create()
            vertexBuffer.upload(vertexData)

            var indexBuffer: GPUBuffer? = null
            var indexCount = 0
            var indexFormat = "uint32"

            if (indexAttr != null) {
                indexCount = indexAttr.count as Int
                val indexData = IntArray(indexCount) { indexAttr.getX(it).toInt() }

                val buffer = WebGPUBuffer(
                    device,
                    BufferDescriptor(
                        size = indexData.size * 4,
                        usage = GPUBufferUsage.INDEX or GPUBufferUsage.COPY_DST,
                        label = "Index Buffer $uuid"
                    )
                )
                buffer.create()
                buffer.uploadIndices(indexData)
                indexBuffer = buffer.getBuffer()
            }

            GeometryBuffers(
                vertexBuffer = vertexBuffer.getBuffer()!!,
                indexBuffer = indexBuffer,
                vertexCount = vertexCount,
                indexCount = indexCount,
                indexFormat = indexFormat
            ).also { buffersByGeometry[uuid] = it }
        } catch (e: Exception) {
            console.error("Failed to create geometry buffers: ${e.message}")
            null
        }
    }

    fun clear() {
        buffersByGeometry.clear()
    }
}

internal data class GeometryBuffers(
    val vertexBuffer: GPUBuffer,
    val indexBuffer: GPUBuffer?,
    val vertexCount: Int,
    val indexCount: Int,
    val indexFormat: String
)
