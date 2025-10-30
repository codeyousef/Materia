package io.materia.instancing

import io.materia.geometry.BufferAttribute
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract test for InstancedBufferAttribute - T028
 * Covers: FR-I007, FR-I008, FR-I009, FR-I010 from contracts/instancing-api.kt
 */
class InstancedBufferAttributeContractTest {

    @Test
    fun testStorePerInstanceData() {
        // FR-I007: Store per-instance data
        val data = floatArrayOf(
            1f, 0f, 0f, 1f,  // Instance 1 color (red)
            0f, 1f, 0f, 1f,  // Instance 2 color (green)
            0f, 0f, 1f, 1f,  // Instance 3 color (blue)
            1f, 1f, 0f, 1f   // Instance 4 color (yellow)
        )

        val attribute = InstancedBufferAttribute(data, itemSize = 4, normalized = false)

        assertEquals(4, attribute.itemSize)
        assertEquals(4, attribute.count)
        assertEquals(16, attribute.array.size)

        // Verify data integrity
        assertEquals(1f, attribute.getX(0)) // First instance red channel
        assertEquals(0f, attribute.getY(0))
        assertEquals(0f, attribute.getZ(0))
        assertEquals(1f, attribute.getW(0))

        assertEquals(0f, attribute.getX(1)) // Second instance green channel
        assertEquals(1f, attribute.getY(1))
    }

    @Test
    fun testEfficientGPUUploads() {
        // FR-I008: Efficient GPU uploads
        val largeData = FloatArray(10000 * 4) { it.toFloat() } // 10k instances, 4 components each
        val attribute = InstancedBufferAttribute(largeData, itemSize = 4)

        // Test that attribute is marked for GPU upload
        assertTrue(attribute.needsUpdate)

        // Modify data
        attribute.setXYZW(100, 1f, 2f, 3f, 4f)
        assertTrue(attribute.needsUpdate) // Should be marked dirty
    }

    @Test
    fun testPartialBufferUpdates() {
        // FR-I009: Partial buffer updates
        val data = FloatArray(1000 * 3) { 0f } // 1000 instances, 3 components each
        val attribute = InstancedBufferAttribute(data, itemSize = 3)

        // Update a range of instances
        val updateStart = 100
        val updateCount = 50

        for (i in updateStart until updateStart + updateCount) {
            attribute.setXYZ(i, i.toFloat(), i.toFloat() * 2, i.toFloat() * 3)
        }

        // Verify update range is tracked
        assertTrue(attribute.updateRange != IntRange.EMPTY)
    }

    @Test
    fun testMeshPerAttributeParameter() {
        // FR-I010: meshPerAttribute parameter for instancing divisor
        val matrices = FloatArray(100 * 16) { 0f } // 100 4x4 matrices
        val attribute = InstancedBufferAttribute(matrices, itemSize = 16)

        // Default: one instance per attribute value
        assertEquals(1, attribute.meshPerAttribute)

        // Test custom divisor (e.g., 2 meshes share same instance data)
        attribute.meshPerAttribute = 2
        assertEquals(2, attribute.meshPerAttribute)

        // This affects how many times geometry is drawn per instance
        val instanceCount = 200
        val effectiveInstances = instanceCount / attribute.meshPerAttribute
        assertEquals(100, effectiveInstances) // 200 meshes / 2 per attribute = 100 unique instances
    }

    @Test
    fun testInstanceMatrixAttribute() {
        // Special case: 4x4 matrix as instance attribute
        val instanceMatrices = FloatArray(50 * 16) // 50 4x4 matrices

        // Initialize with identity matrices
        for (i in 0 until 50) {
            val offset = i * 16
            // Identity matrix
            instanceMatrices[offset + 0] = 1f  // m11
            instanceMatrices[offset + 5] = 1f  // m22
            instanceMatrices[offset + 10] = 1f // m33
            instanceMatrices[offset + 15] = 1f // m44
        }

        val matrixAttribute = InstancedBufferAttribute(instanceMatrices, itemSize = 16)

        // Verify matrix data
        assertEquals(16, matrixAttribute.itemSize)
        assertEquals(50, matrixAttribute.count)

        // Verify identity matrix diagonal
        assertEquals(1f, matrixAttribute.array[0])
        assertEquals(1f, matrixAttribute.array[5])
        assertEquals(1f, matrixAttribute.array[10])
        assertEquals(1f, matrixAttribute.array[15])
    }

    @Test
    fun testMemoryEfficiency() {
        // Verify memory-efficient storage
        val instanceCount = 10000
        val componentsPerInstance = 7 // position (3) + quaternion (4)

        val data = FloatArray(instanceCount * componentsPerInstance)
        val attribute = InstancedBufferAttribute(data, itemSize = componentsPerInstance)

        // Test copy-on-write optimization
        val attribute2 = attribute.clone()
        assertTrue(attribute2.array !== attribute.array) // Should be different arrays
        assertEquals(attribute.count, attribute2.count)
    }
}
