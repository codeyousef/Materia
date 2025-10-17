package io.kreekt.renderer.geometry

import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.webgpu.VertexAttribute
import io.kreekt.renderer.webgpu.VertexBufferLayout
import io.kreekt.renderer.webgpu.VertexFormat
import io.kreekt.renderer.webgpu.VertexStepMode

enum class GeometryAttribute {
    POSITION,
    NORMAL,
    COLOR,
    UV0,
    UV1,
    TANGENT,
    MORPH_POSITION,
    MORPH_NORMAL,
    INSTANCE_MATRIX
}

data class GeometryAttributeBinding(
    val attribute: GeometryAttribute,
    val location: Int,
    val stepMode: VertexStepMode
)

data class GeometryMetadata(
    val bindings: List<GeometryAttributeBinding>,
    val hasMorphTargets: Boolean,
    val morphTargetCount: Int,
    val isInstanced: Boolean
) {
    fun has(attribute: GeometryAttribute): Boolean = bindings.any { it.attribute == attribute }

    fun bindingFor(attribute: GeometryAttribute): GeometryAttributeBinding? =
        bindings.firstOrNull { it.attribute == attribute }
}

data class VertexStream(
    val data: FloatArray,
    val layout: VertexBufferLayout
)

data class GeometryBuffer(
    val streams: List<VertexStream>,
    val indexData: IntArray?,
    val vertexCount: Int,
    val instanceCount: Int,
    val metadata: GeometryMetadata
) {
    val indexCount: Int get() = indexData?.size ?: 0
}

data class GeometryBuildOptions(
    val includeNormals: Boolean = true,
    val includeColors: Boolean = true,
    val includeUVs: Boolean = true,
    val includeSecondaryUVs: Boolean = true,
    val includeTangents: Boolean = true,
    val includeMorphTargets: Boolean = true,
    val includeInstancing: Boolean = true
)

object GeometryBuilder {

    private const val POSITION_ATTR = "position"
    private const val NORMAL_ATTR = "normal"
    private const val COLOR_ATTR = "color"
    private const val UV_ATTR = "uv"
    private const val UV2_ATTR = "uv2"
    private const val TANGENT_ATTR = "tangent"

    fun build(
        geometry: BufferGeometry,
        options: GeometryBuildOptions = GeometryBuildOptions()
    ): GeometryBuffer {
        val vertexStream = buildVertexStream(geometry, options)
        val instanceStream = if (options.includeInstancing) {
            buildInstanceStream(geometry, vertexStream.nextShaderLocation)
        } else null

        val streams = mutableListOf(vertexStream.stream)
        val instanceCount = instanceStream?.instanceCount ?: geometry.instanceCount
        instanceStream?.stream?.let(streams::add)

        val indexAttribute = geometry.index
        val indexData = indexAttribute?.let { attr ->
            IntArray(attr.count) { attr.getX(it).toInt() }
        }

        val metadata = GeometryMetadata(
            bindings = vertexStream.bindings + (instanceStream?.bindings ?: emptyList()),
            hasMorphTargets = geometry.morphAttributes.isNotEmpty(),
            morphTargetCount = geometry.morphAttributes.values.maxOfOrNull { it.size } ?: 0,
            isInstanced = instanceStream != null
        )

        return GeometryBuffer(
            streams = streams,
            indexData = indexData,
            vertexCount = vertexStream.vertexCount,
            instanceCount = instanceCount,
            metadata = metadata
        )
    }

    private data class PackedAttribute(
        val attribute: BufferAttribute?,
        val componentCount: Int,
        val defaultValue: FloatArray?,
        val format: VertexFormat,
        val includeWhenMissing: Boolean,
        val attributeType: GeometryAttribute?
    )

    private data class VertexStreamResult(
        val stream: VertexStream,
        val vertexCount: Int,
        val nextShaderLocation: Int,
        val bindings: List<GeometryAttributeBinding>
    )

    private data class InstanceStreamResult(
        val stream: VertexStream,
        val instanceCount: Int,
        val bindings: List<GeometryAttributeBinding>
    )

    private fun buildVertexStream(
        geometry: BufferGeometry,
        options: GeometryBuildOptions
    ): VertexStreamResult {
        val positions = geometry.getAttribute(POSITION_ATTR)
            ?: error("BufferGeometry is missing required '$POSITION_ATTR' attribute")

        require(positions.itemSize >= 3) {
            "Expected position attribute with itemSize >= 3 (received ${positions.itemSize})"
        }

        val vertexCount = positions.count
        val normals = if (options.includeNormals) geometry.getAttribute(NORMAL_ATTR) else null
        val tangents = if (options.includeTangents) geometry.getAttribute(TANGENT_ATTR) else null
        val colors = if (options.includeColors) geometry.getAttribute(COLOR_ATTR) else null
        val uv = if (options.includeUVs) geometry.getAttribute(UV_ATTR) else null
        val uv2 = if (options.includeSecondaryUVs) geometry.getAttribute(UV2_ATTR) else null

        val packedAttributes = mutableListOf<PackedAttribute>()
        packedAttributes += PackedAttribute(
            attribute = positions,
            componentCount = 3,
            defaultValue = null,
            format = VertexFormat.FLOAT32X3,
            includeWhenMissing = true,
            attributeType = GeometryAttribute.POSITION
        )
        packedAttributes += PackedAttribute(
            attribute = normals,
            componentCount = 3,
            defaultValue = DEFAULT_NORMAL,
            format = VertexFormat.FLOAT32X3,
            includeWhenMissing = options.includeNormals,
            attributeType = GeometryAttribute.NORMAL
        )
        packedAttributes += PackedAttribute(
            attribute = colors,
            componentCount = 3,
            defaultValue = DEFAULT_COLOR,
            format = VertexFormat.FLOAT32X3,
            includeWhenMissing = options.includeColors,
            attributeType = GeometryAttribute.COLOR
        )
        packedAttributes += PackedAttribute(
            attribute = tangents,
            componentCount = 4,
            defaultValue = DEFAULT_TANGENT,
            format = VertexFormat.FLOAT32X4,
            includeWhenMissing = options.includeTangents,
            attributeType = GeometryAttribute.TANGENT
        )
        packedAttributes += PackedAttribute(
            attribute = uv,
            componentCount = 2,
            defaultValue = DEFAULT_UV,
            format = VertexFormat.FLOAT32X2,
            includeWhenMissing = options.includeUVs,
            attributeType = GeometryAttribute.UV0
        )
        packedAttributes += PackedAttribute(
            attribute = uv2,
            componentCount = 2,
            defaultValue = DEFAULT_UV,
            format = VertexFormat.FLOAT32X2,
            includeWhenMissing = options.includeSecondaryUVs,
            attributeType = GeometryAttribute.UV1
        )

        if (options.includeMorphTargets) {
            geometry.morphAttributes[POSITION_ATTR]?.forEach { attr ->
                require(attr.itemSize >= 3) {
                    "Morph position attribute requires itemSize >= 3 (received ${attr.itemSize})"
                }
                packedAttributes += PackedAttribute(
                    attribute = attr,
                    componentCount = 3,
                    defaultValue = ZERO3,
                    format = VertexFormat.FLOAT32X3,
                    includeWhenMissing = true,
                    attributeType = GeometryAttribute.MORPH_POSITION
                )
            }
            geometry.morphAttributes[NORMAL_ATTR]?.forEach { attr ->
                require(attr.itemSize >= 3) {
                    "Morph normal attribute requires itemSize >= 3 (received ${attr.itemSize})"
                }
                packedAttributes += PackedAttribute(
                    attribute = attr,
                    componentCount = 3,
                    defaultValue = ZERO3,
                    format = VertexFormat.FLOAT32X3,
                    includeWhenMissing = true,
                    attributeType = GeometryAttribute.MORPH_NORMAL
                )
            }
        }

        val attributes = packedAttributes.filter { it.includeWhenMissing || it.attribute != null }
        val componentsPerVertex = attributes.sumOf { it.componentCount }
        val vertexData = FloatArray(vertexCount * componentsPerVertex)

        var writeOffset = 0
        for (index in 0 until vertexCount) {
            attributes.forEach { attr ->
                if (attr.attribute != null) {
                    writeComponents(attr.attribute, index, attr.componentCount, vertexData, writeOffset)
                } else {
                    attr.defaultValue?.copyInto(vertexData, writeOffset, 0, attr.componentCount)
                }
                writeOffset += attr.componentCount
            }
        }

        val layoutAttributes = mutableListOf<VertexAttribute>()
        val bindings = mutableMapOf<GeometryAttribute, GeometryAttributeBinding>()
        var shaderLocation = 0
        var byteOffset = 0
        attributes.forEach { attr ->
            layoutAttributes += VertexAttribute(
                format = attr.format,
                offset = byteOffset,
                shaderLocation = shaderLocation
            )
            attr.attributeType?.let { type ->
                bindings.putIfAbsent(
                    type,
                    GeometryAttributeBinding(
                        attribute = type,
                        location = shaderLocation,
                        stepMode = VertexStepMode.VERTEX
                    )
                )
            }
            shaderLocation += 1
            byteOffset += attr.componentCount * FLOAT_BYTES
        }

        val layout = VertexBufferLayout(
            arrayStride = byteOffset,
            stepMode = VertexStepMode.VERTEX,
            attributes = layoutAttributes
        )

        return VertexStreamResult(
            stream = VertexStream(vertexData, layout),
            vertexCount = vertexCount,
            nextShaderLocation = shaderLocation,
            bindings = bindings.values.toList()
        )
    }

    private fun buildInstanceStream(
        geometry: BufferGeometry,
        startShaderLocation: Int
    ): InstanceStreamResult? {
        val instancedAttributes = geometry.instancedAttributes
        if (instancedAttributes.isEmpty()) {
            return null
        }

        val instanceCount = when {
            geometry.instanceCount > 0 -> geometry.instanceCount
            instancedAttributes.isNotEmpty() -> instancedAttributes.values.first().count
            else -> 0
        }

        if (instanceCount == 0) {
            return null
        }

        val sorted = instancedAttributes.entries.sortedBy { it.key }
        val attributes = mutableListOf<PackedAttribute>()
        sorted.forEach { (_, attribute) ->
            when {
                attribute.itemSize <= 4 -> attributes += PackedAttribute(
                    attribute = attribute,
                    componentCount = attribute.itemSize,
                    defaultValue = null,
                    format = formatForSize(attribute.itemSize),
                    includeWhenMissing = true,
                    attributeType = GeometryAttribute.INSTANCE_MATRIX
                )
                attribute.itemSize % 4 == 0 -> {
                    val chunkCount = attribute.itemSize / 4
                    repeat(chunkCount) { chunk ->
                        attributes += PackedAttribute(
                            attribute = ChunkedBufferAttribute(attribute, chunk * 4, 4),
                            componentCount = 4,
                            defaultValue = null,
                            format = VertexFormat.FLOAT32X4,
                            includeWhenMissing = true,
                            attributeType = if (chunk == 0) GeometryAttribute.INSTANCE_MATRIX else null
                        )
                    }
                }
                else -> error("Unsupported instanced attribute itemSize=${attribute.itemSize}")
            }
        }

        if (attributes.isEmpty()) {
            return null
        }

        val componentsPerInstance = attributes.sumOf { it.componentCount }
        val instanceData = FloatArray(instanceCount * componentsPerInstance)

        var writeOffset = 0
        for (instanceIndex in 0 until instanceCount) {
            attributes.forEach { attr ->
                writeComponents(attr.attribute!!, instanceIndex, attr.componentCount, instanceData, writeOffset)
                writeOffset += attr.componentCount
            }
        }

        val layoutAttributes = mutableListOf<VertexAttribute>()
        val bindings = mutableMapOf<GeometryAttribute, GeometryAttributeBinding>()
        var shaderLocation = startShaderLocation
        var byteOffset = 0
        attributes.forEach { attr ->
            layoutAttributes += VertexAttribute(
                format = attr.format,
                offset = byteOffset,
                shaderLocation = shaderLocation
            )
            attr.attributeType?.let { type ->
                bindings.putIfAbsent(
                    type,
                    GeometryAttributeBinding(
                        attribute = type,
                        location = shaderLocation,
                        stepMode = VertexStepMode.INSTANCE
                    )
                )
            }
            shaderLocation += 1
            byteOffset += attr.componentCount * FLOAT_BYTES
        }

        val layout = VertexBufferLayout(
            arrayStride = byteOffset,
            stepMode = VertexStepMode.INSTANCE,
            attributes = layoutAttributes
        )

        return InstanceStreamResult(
            stream = VertexStream(instanceData, layout),
            instanceCount = instanceCount,
            bindings = bindings.values.toList()
        )
    }

    private fun writeComponents(
        attribute: BufferAttribute,
        vertexIndex: Int,
        componentCount: Int,
        target: FloatArray,
        targetOffset: Int
    ) {
        when (componentCount) {
            1 -> target[targetOffset] = attribute.getX(vertexIndex)
            2 -> {
                target[targetOffset] = attribute.getX(vertexIndex)
                target[targetOffset + 1] = attribute.getY(vertexIndex)
            }
            3 -> {
                target[targetOffset] = attribute.getX(vertexIndex)
                target[targetOffset + 1] = attribute.getY(vertexIndex)
                target[targetOffset + 2] = attribute.getZ(vertexIndex)
            }
            4 -> {
                target[targetOffset] = attribute.getX(vertexIndex)
                target[targetOffset + 1] = attribute.getY(vertexIndex)
                target[targetOffset + 2] = attribute.getZ(vertexIndex)
                target[targetOffset + 3] = attribute.getW(vertexIndex)
            }
            else -> {
                val base = vertexIndex * attribute.itemSize
                val source = attribute.array
                for (component in 0 until componentCount) {
                    target[targetOffset + component] = source[base + component]
                }
            }
        }
    }

    private fun formatForSize(components: Int): VertexFormat = when (components) {
        1 -> VertexFormat.FLOAT32
        2 -> VertexFormat.FLOAT32X2
        3 -> VertexFormat.FLOAT32X3
        4 -> VertexFormat.FLOAT32X4
        else -> error("Unsupported component count $components")
    }

    private class ChunkedBufferAttribute(
        private val delegate: BufferAttribute,
        private val start: Int,
        private val length: Int
    ) : BufferAttribute(delegate.array, length, delegate.normalized) {
        override val count: Int get() = delegate.count
        override val itemSize: Int get() = length

        override fun getX(index: Int): Float = value(index, 0)
        override fun getY(index: Int): Float = value(index, 1)
        override fun getZ(index: Int): Float = value(index, 2)
        override fun getW(index: Int): Float = value(index, 3)

        private fun value(vertexIndex: Int, component: Int): Float {
            if (component >= length) return 0f
            val base = vertexIndex * delegate.itemSize + start + component
            return delegate.array[base]
        }
    }

    private const val FLOAT_BYTES = 4
    private val DEFAULT_NORMAL = floatArrayOf(0f, 1f, 0f)
    private val DEFAULT_COLOR = floatArrayOf(1f, 1f, 1f)
    private val DEFAULT_TANGENT = floatArrayOf(1f, 0f, 0f, 1f)
    private val DEFAULT_UV = floatArrayOf(0f, 0f)
    private val ZERO3 = floatArrayOf(0f, 0f, 0f)
}
