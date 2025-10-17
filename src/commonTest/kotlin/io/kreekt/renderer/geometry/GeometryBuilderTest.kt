package io.kreekt.renderer.geometry

import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.geometry.GeometryAttribute
import io.kreekt.renderer.webgpu.VertexStepMode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryBuilderTest {

    @Test
    fun buildInterleavedVertexData() {
        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f, 0f,
                        1f, 0f, 0f,
                        0f, 1f, 0f
                    ),
                    itemSize = 3
                )
            )
            setAttribute(
                "normal",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f, 1f,
                        0f, 0f, 1f,
                        0f, 0f, 1f
                    ),
                    itemSize = 3
                )
            )
            setAttribute(
                "color",
                BufferAttribute(
                    floatArrayOf(
                        1f, 0f, 0f,
                        0f, 1f, 0f,
                        0f, 0f, 1f
                    ),
                    itemSize = 3
                )
            )
        }

        val buffer = GeometryBuilder.build(
            geometry,
            GeometryBuildOptions(
                includeNormals = true,
                includeColors = true,
                includeUVs = false,
                includeSecondaryUVs = false,
                includeTangents = false,
                includeMorphTargets = false,
                includeInstancing = false
            )
        )
        val stream = buffer.streams.first()

        assertEquals(3, buffer.vertexCount)
        assertEquals(0, buffer.indexCount)
        assertTrue(buffer.indexData == null)
        assertEquals(36, stream.layout.arrayStride)
        assertEquals(3, stream.layout.attributes.size)
        assertTrue(buffer.metadata.has(GeometryAttribute.NORMAL))
        assertTrue(buffer.metadata.has(GeometryAttribute.COLOR))

        val expected = floatArrayOf(
            // v0
            0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f,
            // v1
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f,
            // v2
            0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 1f
        )
        assertContentEquals(expected, stream.data)
    }

    @Test
    fun buildWithIndices() {
        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f, 0f,
                        1f, 0f, 0f,
                        0f, 1f, 0f,
                        1f, 1f, 0f
                    ),
                    itemSize = 3
                )
            )
            setIndex(
                BufferAttribute(
                    floatArrayOf(
                        0f, 1f, 2f,
                        1f, 3f, 2f
                    ),
                    itemSize = 1
                )
            )
        }

        val buffer = GeometryBuilder.build(
            geometry,
            GeometryBuildOptions(
                includeNormals = false,
                includeColors = false,
                includeUVs = false,
                includeSecondaryUVs = false,
                includeTangents = false,
                includeMorphTargets = false,
                includeInstancing = false
            )
        )
        val stream = buffer.streams.first()

        assertEquals(4, buffer.vertexCount)
        assertEquals(6, buffer.indexCount)
        assertContentEquals(intArrayOf(0, 1, 2, 1, 3, 2), buffer.indexData)
        assertEquals(12, stream.layout.arrayStride)
        assertEquals(1, stream.layout.attributes.size)
    }

    @Test
    fun buildInstancedStream() {
        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f, 0f,
                        1f, 0f, 0f,
                        0f, 1f, 0f
                    ),
                    itemSize = 3
                )
            )
            val instanceOffsets = BufferAttribute(
                floatArrayOf(
                    0f, 0f, 0f,
                    2f, 0f, 0f
                ),
                itemSize = 3
            )
            setInstancedAttribute("instanceOffset", instanceOffsets)
            instanceCount = 2
        }

        val buffer = GeometryBuilder.build(geometry)
        assertEquals(2, buffer.streams.size)
        val instanceStream = buffer.streams[1]
        assertEquals(VertexStepMode.INSTANCE, instanceStream.layout.stepMode)
        assertEquals(2, buffer.instanceCount)
        assertContentEquals(
            floatArrayOf(
                0f, 0f, 0f,
                2f, 0f, 0f
            ),
            instanceStream.data
        )
    }
}
