/**
 * UniformBlock - Type-safe uniform buffer layout management for WebGPU/WGSL
 * 
 * This module provides automatic alignment handling, WGSL struct generation,
 * and type-safe uniform value updates.
 * 
 * WebGPU/WGSL Alignment Rules:
 * - f32, i32, u32: 4-byte alignment
 * - vec2<f32>: 8-byte alignment
 * - vec3<f32>, vec4<f32>: 16-byte alignment
 * - mat3x3<f32>: 48 bytes (3 x vec4 due to alignment)
 * - mat4x4<f32>: 64 bytes, 16-byte alignment
 * - Arrays: each element aligned to 16 bytes for scalars
 */
package io.materia.effects

import kotlin.math.ceil

/**
 * Uniform data types supported by WebGPU/WGSL
 */
enum class UniformType(
    /** Size in bytes */
    val byteSize: Int,
    /** Alignment requirement in bytes */
    val alignment: Int,
    /** WGSL type name */
    val wgslType: String
) {
    FLOAT(4, 4, "f32"),
    INT(4, 4, "i32"),
    UINT(4, 4, "u32"),
    VEC2(8, 8, "vec2<f32>"),
    VEC3(12, 16, "vec3<f32>"),  // vec3 aligns to 16 in WGSL
    VEC4(16, 16, "vec4<f32>"),
    MAT3(48, 16, "mat3x3<f32>"),  // 3 x vec4 (padded)
    MAT4(64, 16, "mat4x4<f32>"),
    
    // Array types - element alignment is 16 for scalars
    FLOAT_ARRAY(0, 16, "array<f32>"),
    INT_ARRAY(0, 16, "array<i32>"),
    VEC2_ARRAY(0, 16, "array<vec2<f32>>"),
    VEC3_ARRAY(0, 16, "array<vec3<f32>>"),
    VEC4_ARRAY(0, 16, "array<vec4<f32>>"),
    MAT4_ARRAY(0, 16, "array<mat4x4<f32>>");
    
    companion object {
        fun arrayTypeFor(elementType: UniformType): UniformType = when (elementType) {
            FLOAT -> FLOAT_ARRAY
            INT -> INT_ARRAY
            VEC2 -> VEC2_ARRAY
            VEC3 -> VEC3_ARRAY
            VEC4 -> VEC4_ARRAY
            MAT4 -> MAT4_ARRAY
            else -> throw IllegalArgumentException("Arrays of $elementType not supported")
        }
        
        fun elementSizeFor(elementType: UniformType, count: Int): Int = when (elementType) {
            FLOAT, INT, UINT -> count * 16  // Each scalar padded to 16 bytes
            VEC2 -> count * 16  // vec2 padded to 16 bytes in array
            VEC3 -> count * 16  // vec3 already 16 bytes aligned
            VEC4 -> count * 16  // vec4 is 16 bytes
            MAT4 -> count * 64  // mat4 is 64 bytes
            else -> throw IllegalArgumentException("Arrays of $elementType not supported")
        }
    }
}

/**
 * Represents a single field in the uniform block
 */
data class UniformField(
    /** Field name */
    val name: String,
    /** Uniform type */
    val type: UniformType,
    /** Byte offset from start of block */
    val offset: Int,
    /** Size in bytes */
    val size: Int,
    /** For array types, the number of elements */
    val arraySize: Int = 0
) {
    /** WGSL type declaration */
    val wgslType: String
        get() = if (arraySize > 0) {
            "array<${type.wgslType.removeSurrounding("array<", ">")}, $arraySize>"
        } else {
            type.wgslType
        }
}

/**
 * Represents a padding field for WGSL generation
 */
data class PaddingField(val name: String, val offset: Int, val size: Int)

/**
 * Immutable uniform block layout with automatic alignment handling
 */
class UniformBlock internal constructor(
    /** Ordered list of uniform fields */
    val layout: List<UniformField>,
    /** Total size of the block in bytes */
    val size: Int,
    /** Internal padding fields for WGSL generation */
    internal val paddingFields: List<PaddingField>
) {
    
    /**
     * Get a field by name
     */
    fun field(name: String): UniformField? = layout.find { it.name == name }
    
    /**
     * Create a float buffer to hold uniform data
     * @return FloatArray sized to hold all uniform data
     */
    fun createBuffer(): FloatArray = FloatArray(size / 4)
    
    /**
     * Create an updater for the given buffer
     */
    fun createUpdater(buffer: FloatArray): UniformUpdater = UniformUpdater(this, buffer)
    
    /**
     * Generate WGSL struct definition
     * @param structName Name for the WGSL struct
     * @return Complete WGSL struct declaration
     */
    fun toWGSL(structName: String): String = buildString {
        appendLine("struct $structName {")
        
        // Combine fields and padding, sort by offset
        val allFields = mutableListOf<Triple<Int, String, String>>()  // offset, name, type
        
        layout.forEach { field ->
            allFields.add(Triple(field.offset, field.name, field.wgslType))
        }
        
        paddingFields.forEach { padding ->
            val paddingType = when (padding.size) {
                4 -> "f32"
                8 -> "vec2<f32>"
                12 -> "vec3<f32>"
                else -> "f32" // fallback
            }
            allFields.add(Triple(padding.offset, padding.name, paddingType))
        }
        
        // Sort by offset and output
        allFields.sortedBy { it.first }.forEach { (_, name, type) ->
            appendLine("    $name: $type,")
        }
        
        appendLine("}")
    }
    
    companion object {
        /**
         * Create an empty uniform block
         */
        fun empty(): UniformBlock = UniformBlock(emptyList(), 0, emptyList())
        
        /**
         * Build a uniform block using the DSL builder
         */
        fun build(block: UniformBlockBuilder.() -> Unit): UniformBlock {
            val builder = UniformBlockBuilder()
            builder.block()
            return builder.build()
        }
    }
}

/**
 * DSL builder for UniformBlock
 */
class UniformBlockBuilder {
    private val fields = mutableListOf<PendingField>()
    
    private data class PendingField(
        val name: String,
        val type: UniformType,
        val arraySize: Int = 0
    )
    
    fun float(name: String) {
        fields.add(PendingField(name, UniformType.FLOAT))
    }
    
    fun int(name: String) {
        fields.add(PendingField(name, UniformType.INT))
    }
    
    fun uint(name: String) {
        fields.add(PendingField(name, UniformType.UINT))
    }
    
    fun vec2(name: String) {
        fields.add(PendingField(name, UniformType.VEC2))
    }
    
    fun vec3(name: String) {
        fields.add(PendingField(name, UniformType.VEC3))
    }
    
    fun vec4(name: String) {
        fields.add(PendingField(name, UniformType.VEC4))
    }
    
    fun mat3(name: String) {
        fields.add(PendingField(name, UniformType.MAT3))
    }
    
    fun mat4(name: String) {
        fields.add(PendingField(name, UniformType.MAT4))
    }
    
    fun array(name: String, elementType: UniformType, count: Int) {
        val arrayType = UniformType.arrayTypeFor(elementType)
        fields.add(PendingField(name, arrayType, count))
    }
    
    internal fun build(): UniformBlock {
        if (fields.isEmpty()) {
            return UniformBlock.empty()
        }
        
        val layoutFields = mutableListOf<UniformField>()
        val paddingFieldsList = mutableListOf<PaddingField>()
        var currentOffset = 0
        var paddingIndex = 0
        
        for (pending in fields) {
            val alignment = pending.type.alignment
            val size = if (pending.arraySize > 0) {
                // For arrays, calculate based on element type
                val baseType = when (pending.type) {
                    UniformType.FLOAT_ARRAY -> UniformType.FLOAT
                    UniformType.INT_ARRAY -> UniformType.INT
                    UniformType.VEC2_ARRAY -> UniformType.VEC2
                    UniformType.VEC3_ARRAY -> UniformType.VEC3
                    UniformType.VEC4_ARRAY -> UniformType.VEC4
                    UniformType.MAT4_ARRAY -> UniformType.MAT4
                    else -> pending.type
                }
                UniformType.elementSizeFor(baseType, pending.arraySize)
            } else {
                pending.type.byteSize
            }
            
            // Calculate aligned offset
            val alignedOffset = alignTo(currentOffset, alignment)
            
            // Add padding if needed
            if (alignedOffset > currentOffset) {
                val paddingSize = alignedOffset - currentOffset
                paddingFieldsList.add(PaddingField("_pad$paddingIndex", currentOffset, paddingSize))
                paddingIndex++
            }
            
            layoutFields.add(
                UniformField(
                    name = pending.name,
                    type = pending.type,
                    offset = alignedOffset,
                    size = size,
                    arraySize = pending.arraySize
                )
            )
            
            currentOffset = alignedOffset + size
        }
        
        return UniformBlock(
            layout = layoutFields.toList(),
            size = currentOffset,
            paddingFields = paddingFieldsList.toList()
        )
    }
    
    private fun alignTo(offset: Int, alignment: Int): Int {
        val remainder = offset % alignment
        return if (remainder == 0) offset else offset + (alignment - remainder)
    }
}

/**
 * Helper class for updating uniform values in a buffer
 */
class UniformUpdater(
    private val block: UniformBlock,
    private val buffer: FloatArray
) {
    /**
     * Set a float uniform value
     */
    fun set(name: String, value: Float) {
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        buffer[index] = value
    }
    
    /**
     * Set a vec2 uniform value
     */
    fun set(name: String, x: Float, y: Float) {
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        buffer[index] = x
        buffer[index + 1] = y
    }
    
    /**
     * Set a vec3 uniform value
     */
    fun set(name: String, x: Float, y: Float, z: Float) {
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        buffer[index] = x
        buffer[index + 1] = y
        buffer[index + 2] = z
    }
    
    /**
     * Set a vec4 uniform value
     */
    fun set(name: String, x: Float, y: Float, z: Float, w: Float) {
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        buffer[index] = x
        buffer[index + 1] = y
        buffer[index + 2] = z
        buffer[index + 3] = w
    }
    
    /**
     * Set an integer uniform value
     */
    fun setInt(name: String, value: Int) {
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        buffer[index] = Float.fromBits(value)
    }
    
    /**
     * Set a mat3 uniform value from a float array.
     * The 9 values are laid out in column-major order and expanded to
     * 3 vec4s (12 floats) in the buffer due to WGSL std140 alignment.
     */
    fun setMat3(name: String, matrix: FloatArray) {
        require(matrix.size >= 9) { "Mat3 requires 9 float values" }
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        // Column 0
        buffer[index + 0] = matrix[0]
        buffer[index + 1] = matrix[1]
        buffer[index + 2] = matrix[2]
        buffer[index + 3] = 0f  // padding
        // Column 1
        buffer[index + 4] = matrix[3]
        buffer[index + 5] = matrix[4]
        buffer[index + 6] = matrix[5]
        buffer[index + 7] = 0f  // padding
        // Column 2
        buffer[index + 8] = matrix[6]
        buffer[index + 9] = matrix[7]
        buffer[index + 10] = matrix[8]
        buffer[index + 11] = 0f  // padding
    }

    /**
     * Set a mat3 uniform value from individual components (column-major order).
     */
    fun setMat3(
        name: String,
        m00: Float, m01: Float, m02: Float,
        m10: Float, m11: Float, m12: Float,
        m20: Float, m21: Float, m22: Float
    ) {
        setMat3(name, floatArrayOf(m00, m01, m02, m10, m11, m12, m20, m21, m22))
    }

    /**
     * Set a mat4 uniform value from a float array
     */
    fun setMat4(name: String, matrix: FloatArray) {
        require(matrix.size >= 16) { "Mat4 requires 16 float values" }
        val field = block.field(name) ?: throw IllegalArgumentException("Unknown uniform: $name")
        val index = field.offset / 4
        for (i in 0 until 16) {
            buffer[index + i] = matrix[i]
        }
    }
}

/**
 * DSL function to create a uniform block
 */
fun uniformBlock(block: UniformBlockBuilder.() -> Unit): UniformBlock {
    return UniformBlock.build(block)
}
