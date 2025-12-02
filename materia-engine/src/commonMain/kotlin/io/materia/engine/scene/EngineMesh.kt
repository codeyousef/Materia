/**
 * EngineMesh - GPU-Backed Mesh for Scene Graph
 *
 * Extends the existing Mesh class with GPU buffer management for the
 * unified renderer architecture.
 */
package io.materia.engine.scene

import io.materia.core.math.Matrix4
import io.materia.core.scene.Mesh
import io.materia.engine.core.Disposable
import io.materia.engine.core.DisposableContainer
import io.materia.engine.material.EngineMaterial
import io.materia.geometry.BufferGeometry
import io.materia.gpu.*

/**
 * GPU-backed mesh that manages its own vertex and index buffers.
 *
 * This class extends the base Mesh to add:
 * - Automatic GPU buffer creation from geometry
 * - Buffer update when geometry changes
 * - Proper resource cleanup via Disposable interface
 *
 * ## Usage
 *
 * ```kotlin
 * val geometry = BoxGeometry(1f, 1f, 1f)
 * val material = StandardMaterial(baseColor = Color.RED)
 * val mesh = EngineMesh(geometry, material)
 *
 * // Add to scene
 * scene.add(mesh)
 *
 * // When done
 * mesh.dispose()
 * ```
 *
 * ## Buffer Management
 *
 * Buffers are created lazily on first access and cached. They are automatically
 * recreated when:
 * - Geometry's `needsUpdate` flag is true
 * - The mesh is re-added to a scene
 *
 * ## Thread Safety
 *
 * Buffer operations must be performed on the render thread.
 */
class EngineMesh(
    geometry: BufferGeometry,
    var engineMaterial: EngineMaterial? = null
) : Mesh(geometry, null), Disposable {

    private val resources = DisposableContainer()
    private var _disposed = false

    // GPU buffers (created lazily)
    private var _vertexBuffer: GpuBuffer? = null
    private var _indexBuffer: GpuBuffer? = null
    private var _uniformBuffer: GpuBuffer? = null
    private var _bindGroup: GpuBindGroup? = null

    // Track which device these resources belong to
    private var boundDevice: GpuDevice? = null

    // Model matrix uniform data
    private val modelUniformData = FloatArray(32) // model matrix + normal matrix

    override val isDisposed: Boolean get() = _disposed

    /**
     * Gets the vertex buffer, creating it if necessary.
     *
     * @param device The GPU device to create the buffer on
     * @return The vertex buffer containing position, normal, uv, etc.
     */
    fun getVertexBuffer(device: GpuDevice): GpuBuffer {
        ensureBuffers(device)
        return _vertexBuffer ?: throw IllegalStateException("Vertex buffer not created")
    }

    /**
     * Gets the index buffer if the geometry is indexed.
     *
     * @param device The GPU device to create the buffer on
     * @return The index buffer, or null if geometry is non-indexed
     */
    fun getIndexBuffer(device: GpuDevice): GpuBuffer? {
        ensureBuffers(device)
        return _indexBuffer
    }

    /**
     * Gets the uniform buffer containing model transform.
     */
    fun getUniformBuffer(device: GpuDevice): GpuBuffer {
        ensureBuffers(device)
        return _uniformBuffer ?: throw IllegalStateException("Uniform buffer not created")
    }

    /**
     * Gets bind group for model uniforms.
     */
    fun getBindGroup(device: GpuDevice, layout: GpuBindGroupLayout): GpuBindGroup {
        if (_bindGroup == null || boundDevice != device) {
            createBindGroup(device, layout)
        }
        return _bindGroup!!
    }

    /**
     * Updates the model matrix uniform.
     */
    fun updateModelUniform() {
        val buffer = _uniformBuffer ?: return

        // Copy model matrix elements
        matrixWorld.elements.copyInto(modelUniformData, 0, 0, 16)

        // Calculate and copy normal matrix (inverse transpose of model matrix 3x3)
        val normalMatrix = Matrix4().copy(matrixWorld).invert().transpose()
        normalMatrix.elements.copyInto(modelUniformData, 16, 0, 16)

        buffer.writeFloats(modelUniformData)
    }

    /**
     * Ensures GPU buffers are created and up-to-date.
     */
    private fun ensureBuffers(device: GpuDevice) {
        if (boundDevice != device) {
            // Device changed, recreate all buffers
            disposeBuffers()
            boundDevice = device
        }

        val positionAttr = geometry.getAttribute("position")
        val normalAttr = geometry.getAttribute("normal")
        val uvAttr = geometry.getAttribute("uv")
        val colorAttr = geometry.getAttribute("color")
        val indexAttr = geometry.index

        // Check if buffers need update
        val needsVertexUpdate = _vertexBuffer == null ||
                positionAttr?.needsUpdate == true ||
                normalAttr?.needsUpdate == true ||
                uvAttr?.needsUpdate == true ||
                colorAttr?.needsUpdate == true

        val needsIndexUpdate = indexAttr != null &&
                (_indexBuffer == null || indexAttr.needsUpdate)

        if (needsVertexUpdate) {
            createVertexBuffer(device, positionAttr, normalAttr, uvAttr, colorAttr)
        }

        if (needsIndexUpdate) {
            createIndexBuffer(device, indexAttr!!)
        }

        if (_uniformBuffer == null) {
            createUniformBuffer(device)
        }
    }

    private fun createVertexBuffer(
        device: GpuDevice,
        position: io.materia.geometry.BufferAttribute?,
        normal: io.materia.geometry.BufferAttribute?,
        uv: io.materia.geometry.BufferAttribute?,
        color: io.materia.geometry.BufferAttribute?
    ) {
        _vertexBuffer?.destroy()

        if (position == null) {
            throw IllegalStateException("Geometry must have position attribute")
        }

        val vertexCount = position.count

        // Calculate stride based on available attributes
        var stride = 3 // position
        if (normal != null) stride += 3
        if (uv != null) stride += 2
        if (color != null) stride += 3

        // Interleave vertex data
        val vertexData = FloatArray(vertexCount * stride)
        var offset = 0

        for (i in 0 until vertexCount) {
            // Position (required)
            vertexData[offset++] = position.getX(i)
            vertexData[offset++] = position.getY(i)
            vertexData[offset++] = position.getZ(i)

            // Normal (optional)
            if (normal != null) {
                vertexData[offset++] = normal.getX(i)
                vertexData[offset++] = normal.getY(i)
                vertexData[offset++] = normal.getZ(i)
            }

            // UV (optional)
            if (uv != null) {
                vertexData[offset++] = uv.getX(i)
                vertexData[offset++] = uv.getY(i)
            }

            // Color (optional)
            if (color != null) {
                vertexData[offset++] = color.getX(i)
                vertexData[offset++] = color.getY(i)
                vertexData[offset++] = color.getZ(i)
            }
        }

        val bufferSize = (vertexData.size * Float.SIZE_BYTES).toLong()
        _vertexBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "$name-vertex-buffer",
                size = bufferSize,
                usage = gpuBufferUsage(GpuBufferUsage.VERTEX, GpuBufferUsage.COPY_DST)
            )
        )
        _vertexBuffer?.writeFloats(vertexData)

        // Mark attributes as updated
        position.needsUpdate = false
        normal?.let { it.needsUpdate = false }
        uv?.let { it.needsUpdate = false }
        color?.let { it.needsUpdate = false }
    }

    private fun createIndexBuffer(
        device: GpuDevice,
        index: io.materia.geometry.BufferAttribute
    ) {
        _indexBuffer?.destroy()

        // Convert float indices to integers
        val indexCount = index.count
        val indexData = IntArray(indexCount) { i ->
            index.getX(i).toInt()
        }

        // Convert to bytes (uint16 or uint32)
        val useUint32 = indexData.any { it > 65535 }
        val byteSize = if (useUint32) indexCount * 4 else indexCount * 2

        val bytes = ByteArray(byteSize)
        if (useUint32) {
            for (i in indexData.indices) {
                val value = indexData[i]
                bytes[i * 4 + 0] = (value and 0xFF).toByte()
                bytes[i * 4 + 1] = ((value shr 8) and 0xFF).toByte()
                bytes[i * 4 + 2] = ((value shr 16) and 0xFF).toByte()
                bytes[i * 4 + 3] = ((value shr 24) and 0xFF).toByte()
            }
        } else {
            for (i in indexData.indices) {
                val value = indexData[i]
                bytes[i * 2 + 0] = (value and 0xFF).toByte()
                bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
        }

        _indexBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "$name-index-buffer",
                size = byteSize.toLong(),
                usage = gpuBufferUsage(GpuBufferUsage.INDEX, GpuBufferUsage.COPY_DST)
            )
        )
        _indexBuffer?.write(bytes)

        index.needsUpdate = false
    }

    private fun createUniformBuffer(device: GpuDevice) {
        _uniformBuffer?.destroy()

        // Model matrix (64 bytes) + Normal matrix (64 bytes)
        _uniformBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "$name-uniform-buffer",
                size = 128,
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )
    }

    private fun createBindGroup(device: GpuDevice, layout: GpuBindGroupLayout) {
        val uniformBuffer = getUniformBuffer(device)

        _bindGroup = device.createBindGroup(
            GpuBindGroupDescriptor(
                label = "$name-bind-group",
                layout = layout,
                entries = listOf(
                    GpuBindGroupEntry(
                        binding = 0,
                        resource = GpuBindingResource.Buffer(uniformBuffer)
                    )
                )
            )
        )
    }

    private fun disposeBuffers() {
        _vertexBuffer?.destroy()
        _vertexBuffer = null

        _indexBuffer?.destroy()
        _indexBuffer = null

        _uniformBuffer?.destroy()
        _uniformBuffer = null

        _bindGroup = null
        boundDevice = null
    }

    override fun dispose() {
        if (_disposed) return
        _disposed = true

        disposeBuffers()
        resources.dispose()
        engineMaterial?.dispose()
    }

    /**
     * Gets the vertex count for this mesh.
     */
    val vertexCount: Int
        get() = geometry.getAttribute("position")?.count ?: 0

    /**
     * Gets the index count for this mesh.
     */
    val indexCount: Int
        get() = geometry.index?.count ?: 0

    /**
     * Whether this mesh uses indexed drawing.
     */
    val isIndexed: Boolean
        get() = geometry.index != null

    /**
     * Gets the index format for this mesh.
     */
    val indexFormat: GpuIndexFormat
        get() {
            val index = geometry.index ?: return GpuIndexFormat.UINT16
            return if (index.count > 65535) GpuIndexFormat.UINT32 else GpuIndexFormat.UINT16
        }
}
