/**
 * T025 - WebGL Buffer Manager Module
 *
 * Manages WebGL buffer objects (VBOs, IBOs) with efficient creation, binding, and cleanup.
 * Supports vertex data, color data, index buffers, and advanced buffer management features.
 *
 * This module extracts and improves the buffer management functionality from
 * BasicSceneExample.js.kt (lines 561-583) into a reusable, production-ready component.
 */

package io.materia.renderer.webgl

/**
 * Buffer types supported by the manager
 */
enum class BufferType(val glType: Int) {
    ARRAY_BUFFER(34962), // WebGLRenderingContext.ARRAY_BUFFER
    ELEMENT_ARRAY_BUFFER(34963) // WebGLRenderingContext.ELEMENT_ARRAY_BUFFER
}

/**
 * Buffer usage patterns for optimization
 */
enum class BufferUsage(val glUsage: Int) {
    STATIC_DRAW(35044), // WebGLRenderingContext.STATIC_DRAW
    DYNAMIC_DRAW(35048), // WebGLRenderingContext.DYNAMIC_DRAW
    STREAM_DRAW(35040) // WebGLRenderingContext.STREAM_DRAW
}

/**
 * Data types for vertex attributes
 */
enum class AttributeType(val glType: Int, val size: Int) {
    FLOAT(5126, 4), // WebGLRenderingContext.FLOAT
    UNSIGNED_BYTE(5121, 1), // WebGLRenderingContext.UNSIGNED_BYTE
    UNSIGNED_SHORT(5123, 2), // WebGLRenderingContext.UNSIGNED_SHORT
    BYTE(5120, 1), // WebGLRenderingContext.BYTE
    SHORT(5122, 2) // WebGLRenderingContext.SHORT
}

/**
 * Result type for buffer operations
 */
sealed class BufferResult<out T> {
    data class Success<T>(val value: T) : BufferResult<T>()
    data class Error(val message: String) : BufferResult<Nothing>()
}

/**
 * Represents a WebGL buffer with metadata
 */
data class ManagedBuffer(
    val buffer: dynamic, // WebGLBuffer
    val type: BufferType,
    val usage: BufferUsage,
    val size: Int,
    val elementCount: Int
) {
    /**
     * Bind this buffer for use
     */
    fun bind(gl: dynamic) {
        gl.bindBuffer(type.glType, buffer)
    }

    /**
     * Unbind this buffer type
     */
    fun unbind(gl: dynamic) {
        gl.bindBuffer(type.glType, null)
    }

    /**
     * Clean up the buffer
     */
    fun dispose(gl: dynamic) {
        gl.deleteBuffer(buffer)
    }
}

/**
 * Vertex attribute configuration
 */
data class VertexAttribute(
    val location: Int,
    val size: Int,           // Number of components (1-4)
    val type: AttributeType,
    val normalized: Boolean = false,
    val stride: Int = 0,     // Bytes between consecutive attributes
    val offset: Int = 0      // Byte offset of the first component
) {
    /**
     * Setup this vertex attribute pointer
     */
    fun setup(gl: dynamic) {
        gl.vertexAttribPointer(
            location,
            size,
            type.glType,
            normalized,
            stride,
            offset
        )
        gl.enableVertexAttribArray(location)
    }

    /**
     * Disable this vertex attribute
     */
    fun disable(gl: dynamic) {
        gl.disableVertexAttribArray(location)
    }
}

/**
 * WebGL Buffer Manager with comprehensive buffer management capabilities
 */
class BufferManager(private val gl: dynamic) {
    private val managedBuffers = mutableMapOf<String, ManagedBuffer>()
    private var activeArrayBuffer: ManagedBuffer? = null
    private var activeElementBuffer: ManagedBuffer? = null

    /**
     * Create a vertex buffer from float array data
     */
    fun createVertexBuffer(
        name: String,
        data: FloatArray,
        usage: BufferUsage = BufferUsage.STATIC_DRAW
    ): BufferResult<ManagedBuffer> {
        return createBuffer(
            name = name,
            type = BufferType.ARRAY_BUFFER,
            data = data.toTypedArray(),
            usage = usage,
            elementCount = data.size
        )
    }

    /**
     * Create an index buffer from short array data
     */
    fun createIndexBuffer(
        name: String,
        data: ShortArray,
        usage: BufferUsage = BufferUsage.STATIC_DRAW
    ): BufferResult<ManagedBuffer> {
        return createBuffer(
            name = name,
            type = BufferType.ELEMENT_ARRAY_BUFFER,
            data = data.toTypedArray(),
            usage = usage,
            elementCount = data.size
        )
    }

    /**
     * Create an index buffer from int array data (for WebGL2 or OES_element_index_uint)
     */
    fun createIndexBufferInt(
        name: String,
        data: IntArray,
        usage: BufferUsage = BufferUsage.STATIC_DRAW
    ): BufferResult<ManagedBuffer> {
        return createBuffer(
            name = name,
            type = BufferType.ELEMENT_ARRAY_BUFFER,
            data = data.toTypedArray(),
            usage = usage,
            elementCount = data.size
        )
    }

    /**
     * Create a buffer from byte array data
     */
    fun createByteBuffer(
        name: String,
        data: ByteArray,
        type: BufferType = BufferType.ARRAY_BUFFER,
        usage: BufferUsage = BufferUsage.STATIC_DRAW
    ): BufferResult<ManagedBuffer> {
        return createBuffer(
            name = name,
            type = type,
            data = data.toTypedArray(),
            usage = usage,
            elementCount = data.size
        )
    }

    /**
     * Generic buffer creation method
     */
    private fun createBuffer(
        name: String,
        type: BufferType,
        data: Array<*>,
        usage: BufferUsage,
        elementCount: Int
    ): BufferResult<ManagedBuffer> {
        // Check if buffer with this name already exists
        managedBuffers[name]?.let { existingBuffer ->
            existingBuffer.dispose(gl)
        }

        val buffer = gl.createBuffer() ?: return BufferResult.Error(
            "Failed to create WebGL buffer '$name'"
        )

        return try {
            // Bind and upload data
            gl.bindBuffer(type.glType, buffer)
            gl.bufferData(type.glType, data, usage.glUsage)

            // Calculate size
            val elementSize = when (data.firstOrNull()) {
                is Float -> 4
                is Short -> 2
                is Int -> 4
                is Byte -> 1
                else -> 1
            }
            val totalSize = elementCount * elementSize

            val managedBuffer = ManagedBuffer(
                buffer = buffer,
                type = type,
                usage = usage,
                size = totalSize,
                elementCount = elementCount
            )

            // Store in managed buffers
            managedBuffers[name] = managedBuffer

            // Update active buffer tracking
            when (type) {
                BufferType.ARRAY_BUFFER -> activeArrayBuffer = managedBuffer
                BufferType.ELEMENT_ARRAY_BUFFER -> activeElementBuffer = managedBuffer
            }

            BufferResult.Success(managedBuffer)
        } catch (e: Exception) {
            gl.deleteBuffer(buffer)
            BufferResult.Error("Failed to create buffer '$name': ${e.message}")
        }
    }

    /**
     * Update buffer data (for dynamic buffers)
     */
    fun updateBuffer(
        name: String,
        data: FloatArray,
        offset: Int = 0
    ): BufferResult<Unit> {
        val buffer = managedBuffers[name] ?: return BufferResult.Error(
            "Buffer '$name' not found"
        )

        return try {
            buffer.bind(gl)
            gl.bufferSubData(buffer.type.glType, offset, data.toTypedArray())
            BufferResult.Success(Unit)
        } catch (e: Exception) {
            BufferResult.Error("Failed to update buffer '$name': ${e.message}")
        }
    }

    /**
     * Bind a managed buffer by name
     */
    fun bindBuffer(name: String): BufferResult<ManagedBuffer> {
        val buffer = managedBuffers[name] ?: return BufferResult.Error(
            "Buffer '$name' not found"
        )

        return try {
            buffer.bind(gl)

            // Update active buffer tracking
            when (buffer.type) {
                BufferType.ARRAY_BUFFER -> activeArrayBuffer = buffer
                BufferType.ELEMENT_ARRAY_BUFFER -> activeElementBuffer = buffer
            }

            BufferResult.Success(buffer)
        } catch (e: Exception) {
            BufferResult.Error("Failed to bind buffer '$name': ${e.message}")
        }
    }

    /**
     * Setup vertex attributes for the currently bound array buffer
     */
    fun setupVertexAttributes(attributes: List<VertexAttribute>): BufferResult<Unit> {
        return try {
            for (attribute in attributes) {
                attribute.setup(gl)
            }
            BufferResult.Success(Unit)
        } catch (e: Exception) {
            BufferResult.Error("Failed to setup vertex attributes: ${e.message}")
        }
    }

    /**
     * Create a simple vertex buffer with position and color data (original demo functionality)
     */
    fun createSimpleVertexBuffer(
        name: String,
        positions: FloatArray,
        colors: FloatArray
    ): BufferResult<Pair<ManagedBuffer, ManagedBuffer>> {
        val positionBuffer = createVertexBuffer("${name}_position", positions)
        val colorBuffer = createVertexBuffer("${name}_color", colors)

        return when {
            positionBuffer is BufferResult.Success && colorBuffer is BufferResult.Success -> {
                BufferResult.Success(Pair(positionBuffer.value, colorBuffer.value))
            }

            positionBuffer is BufferResult.Error -> positionBuffer
            colorBuffer is BufferResult.Error -> colorBuffer
            else -> BufferResult.Error("Unknown error creating vertex buffers")
        }
    }

    /**
     * Create a cube geometry buffer set (enhanced version of original functionality)
     */
    fun createCubeBuffers(name: String): BufferResult<Triple<ManagedBuffer, ManagedBuffer, ManagedBuffer>> {
        // Cube vertices (8 vertices, 3 components each)
        val vertices = floatArrayOf(
            // Front face
            -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f,
            // Back face
            -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f, -1f,
            // Top face
            -1f, 1f, -1f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f,
            // Bottom face
            -1f, -1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, -1f, -1f, 1f,
            // Right face
            1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f,
            // Left face
            -1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f
        )

        // Colors for each face
        val colors = floatArrayOf(
            // Front face (red)
            1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f,
            // Back face (green)
            0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
            // Top face (blue)
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
            // Bottom face (yellow)
            1f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f,
            // Right face (magenta)
            1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 1f,
            // Left face (cyan)
            0f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 1f
        )

        // Indices for triangles (2 triangles per face, 6 faces)
        val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3,    // Front
            4, 5, 6, 4, 6, 7,    // Back
            8, 9, 10, 8, 10, 11,  // Top
            12, 13, 14, 12, 14, 15, // Bottom
            16, 17, 18, 16, 18, 19, // Right
            20, 21, 22, 20, 22, 23  // Left
        )

        val vertexBuffer = createVertexBuffer("${name}_vertices", vertices)
        val colorBuffer = createVertexBuffer("${name}_colors", colors)
        val indexBuffer = createIndexBuffer("${name}_indices", indices)

        return when {
            vertexBuffer is BufferResult.Success &&
                    colorBuffer is BufferResult.Success &&
                    indexBuffer is BufferResult.Success -> {
                BufferResult.Success(
                    Triple(
                        vertexBuffer.value,
                        colorBuffer.value,
                        indexBuffer.value
                    )
                )
            }

            vertexBuffer is BufferResult.Error -> vertexBuffer
            colorBuffer is BufferResult.Error -> colorBuffer
            indexBuffer is BufferResult.Error -> indexBuffer
            else -> BufferResult.Error("Unknown error creating cube buffers")
        }
    }

    /**
     * Draw elements using the current index buffer
     */
    fun drawElements(
        mode: Int = 4, // WebGLRenderingContext.TRIANGLES
        count: Int? = null,
        type: Int = 5123, // WebGLRenderingContext.UNSIGNED_SHORT
        offset: Int = 0
    ): BufferResult<Unit> {
        val elementBuffer = activeElementBuffer ?: return BufferResult.Error(
            "No active element buffer for drawing"
        )

        return try {
            val drawCount = count ?: elementBuffer.elementCount
            gl.drawElements(mode, drawCount, type, offset)
            BufferResult.Success(Unit)
        } catch (e: Exception) {
            BufferResult.Error("Failed to draw elements: ${e.message}")
        }
    }

    /**
     * Draw arrays using the current vertex buffer
     */
    fun drawArrays(
        mode: Int = 4, // WebGLRenderingContext.TRIANGLES
        first: Int = 0,
        count: Int? = null
    ): BufferResult<Unit> {
        val arrayBuffer = activeArrayBuffer ?: return BufferResult.Error(
            "No active array buffer for drawing"
        )

        return try {
            val drawCount =
                count ?: (arrayBuffer.elementCount / 3) // Assume 3 components per vertex
            gl.drawArrays(mode, first, drawCount)
            BufferResult.Success(Unit)
        } catch (e: Exception) {
            BufferResult.Error("Failed to draw arrays: ${e.message}")
        }
    }

    /**
     * Get buffer by name
     */
    fun getBuffer(name: String): ManagedBuffer? = managedBuffers[name]

    /**
     * Get all managed buffer names
     */
    fun getBufferNames(): Set<String> = managedBuffers.keys.toSet()

    /**
     * Get buffer statistics
     */
    fun getBufferStats(): Map<String, Any> {
        val totalBuffers = managedBuffers.size
        val totalMemory = managedBuffers.values.sumOf { it.size }
        val arrayBuffers = managedBuffers.values.count { it.type == BufferType.ARRAY_BUFFER }
        val elementBuffers =
            managedBuffers.values.count { it.type == BufferType.ELEMENT_ARRAY_BUFFER }

        return mapOf(
            "totalBuffers" to totalBuffers,
            "totalMemoryBytes" to totalMemory,
            "arrayBuffers" to arrayBuffers,
            "elementBuffers" to elementBuffers,
            "activeArrayBuffer" to (activeArrayBuffer != null),
            "activeElementBuffer" to (activeElementBuffer != null)
        )
    }

    /**
     * Remove and dispose a buffer
     */
    fun removeBuffer(name: String): BufferResult<Unit> {
        val buffer = managedBuffers[name] ?: return BufferResult.Error(
            "Buffer '$name' not found"
        )

        return try {
            buffer.dispose(gl)
            managedBuffers.remove(name)

            // Clear active buffer references
            if (activeArrayBuffer == buffer) activeArrayBuffer = null
            if (activeElementBuffer == buffer) activeElementBuffer = null

            BufferResult.Success(Unit)
        } catch (e: Exception) {
            BufferResult.Error("Failed to remove buffer '$name': ${e.message}")
        }
    }

    /**
     * Dispose all managed buffers
     */
    fun disposeAll(): BufferResult<Unit> {
        return try {
            for (buffer in managedBuffers.values) {
                buffer.dispose(gl)
            }
            managedBuffers.clear()
            activeArrayBuffer = null
            activeElementBuffer = null
            BufferResult.Success(Unit)
        } catch (e: Exception) {
            BufferResult.Error("Failed to dispose all buffers: ${e.message}")
        }
    }

    /**
     * Unbind all buffers
     */
    fun unbindAll() {
        gl.bindBuffer(BufferType.ARRAY_BUFFER.glType, null)
        gl.bindBuffer(BufferType.ELEMENT_ARRAY_BUFFER.glType, null)
        activeArrayBuffer = null
        activeElementBuffer = null
    }
}

/**
 * Extension functions for easier buffer management
 */

/**
 * Create vertex attributes for common 3D rendering (position + color)
 */
fun createPositionColorAttributes(
    positionLocation: Int,
    colorLocation: Int
): List<VertexAttribute> {
    return listOf(
        VertexAttribute(
            location = positionLocation,
            size = 3,
            type = AttributeType.FLOAT
        ),
        VertexAttribute(
            location = colorLocation,
            size = 3,
            type = AttributeType.FLOAT
        )
    )
}

/**
 * Create vertex attributes for advanced 3D rendering (position + normal + texture coordinates + color)
 */
fun createAdvanced3DAttributes(
    positionLocation: Int,
    normalLocation: Int,
    texCoordLocation: Int,
    colorLocation: Int
): List<VertexAttribute> {
    return listOf(
        VertexAttribute(
            location = positionLocation,
            size = 3,
            type = AttributeType.FLOAT
        ),
        VertexAttribute(
            location = normalLocation,
            size = 3,
            type = AttributeType.FLOAT
        ),
        VertexAttribute(
            location = texCoordLocation,
            size = 2,
            type = AttributeType.FLOAT
        ),
        VertexAttribute(
            location = colorLocation,
            size = 3,
            type = AttributeType.FLOAT
        )
    )
}