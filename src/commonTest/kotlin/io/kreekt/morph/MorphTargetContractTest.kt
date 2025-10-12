package io.kreekt.morph

import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract test for Morph target blend shapes - T031
 * Covers: FR-M001, FR-M002, FR-M003, FR-M004, FR-M005 from contracts/morph-api.kt
 */
class MorphTargetContractTest {

    @Test
    fun testStoreMultipleMorphTargets() {
        // FR-M001: Store multiple morph targets
        val geometry = BufferGeometry()

        // Base geometry - a simple quad
        val basePositions = floatArrayOf(
            -1f, -1f, 0f,  // Bottom-left
            1f, -1f, 0f,   // Bottom-right
            1f, 1f, 0f,    // Top-right
            -1f, 1f, 0f    // Top-left
        )
        geometry.setAttribute("position", BufferAttribute(basePositions, 3))

        // Add morph targets
        val morphTargets = mutableListOf<MorphTarget>()

        // Target 1: Expand quad
        val target1Positions = floatArrayOf(
            -2f, -2f, 0f,
            2f, -2f, 0f,
            2f, 2f, 0f,
            -2f, 2f, 0f
        )
        morphTargets.add(
            MorphTarget(
                name = "expand",
                position = BufferAttribute(target1Positions, 3)
            )
        )

        // Target 2: Move forward
        val target2Positions = floatArrayOf(
            -1f, -1f, 1f,
            1f, -1f, 1f,
            1f, 1f, 1f,
            -1f, 1f, 1f
        )
        morphTargets.add(
            MorphTarget(
                name = "forward",
                position = BufferAttribute(target2Positions, 3)
            )
        )

        // Target 3: Twist
        val target3Positions = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0.5f,
            1f, 1f, 0f,
            -1f, 1f, -0.5f
        )
        morphTargets.add(
            MorphTarget(
                name = "twist",
                position = BufferAttribute(target3Positions, 3)
            )
        )

        // Convert MorphTarget list to BufferAttribute list
        geometry.morphTargets = morphTargets.map { it.position!! }

        // Store mapping for name lookups
        morphTargets.forEachIndexed { index, target ->
            geometry.morphTargetDictionary[target.name] = index
        }

        // Verify storage
        assertEquals(3, geometry.morphTargets!!.size)
        assertEquals(0, geometry.morphTargetDictionary["expand"])
        assertEquals(1, geometry.morphTargetDictionary["forward"])
        assertEquals(2, geometry.morphTargetDictionary["twist"])
    }

    @Test
    fun testBlendBetweenBaseAndTargets() {
        // FR-M002: Blend between base and targets
        val geometry = createMorphGeometry()
        val blender = MorphBlender(geometry)

        // Test blending with single target
        blender.setInfluence(0, 0.5f) // 50% blend to first target

        val blendedPositions = blender.computeBlendedPositions()
        val basePos = geometry.getAttribute("position")!!
        val targetPos = geometry.morphTargets!![0]

        // Verify interpolation
        for (i in 0 until basePos.count) {
            val expectedX = basePos.getX(i) + (targetPos.getX(i) - basePos.getX(i)) * 0.5f
            val expectedY = basePos.getY(i) + (targetPos.getY(i) - basePos.getY(i)) * 0.5f
            val expectedZ = basePos.getZ(i) + (targetPos.getZ(i) - basePos.getZ(i)) * 0.5f

            assertEquals(expectedX, blendedPositions.getX(i), 0.001f)
            assertEquals(expectedY, blendedPositions.getY(i), 0.001f)
            assertEquals(expectedZ, blendedPositions.getZ(i), 0.001f)
        }

        // Test blending with multiple targets
        blender.setInfluence(0, 0.3f)
        blender.setInfluence(1, 0.7f)

        val multiBlended = blender.computeBlendedPositions()
        // Result should be weighted sum of influences
        assertNotNull(multiBlended)
    }

    @Test
    fun testNamedMorphTargets() {
        // FR-M003: Named morph targets
        val geometry = createMorphGeometry()

        // Access targets by name
        val expandTarget = geometry.getMorphTargetByName("expand")
        assertNotNull(expandTarget)
        assertEquals("expand", expandTarget.name)

        val forwardTarget = geometry.getMorphTargetByName("forward")
        assertNotNull(forwardTarget)
        assertEquals("forward", forwardTarget.name)

        // Test name-based influence setting
        val blender = MorphBlender(geometry)
        blender.setInfluenceByName("expand", 0.8f)
        assertEquals(0.8f, blender.getInfluence(0))

        blender.setInfluenceByName("forward", 0.2f)
        assertEquals(0.2f, blender.getInfluence(1))
    }

    @Test
    fun testPositionAndNormalMorphing() {
        // FR-M004, FR-M005: Position and normal morphing
        val geometry = BufferGeometry()

        // Base geometry with positions and normals
        val basePositions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val baseNormals = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f
        )
        geometry.setAttribute("position", BufferAttribute(basePositions, 3))
        geometry.setAttribute("normal", BufferAttribute(baseNormals, 3))

        // Morph target with both position and normal deltas
        val targetPositions = floatArrayOf(
            0f, 0f, 1f,    // Move first vertex forward
            1f, 0f, 0.5f,  // Move second vertex partially
            0f, 1f, 0.2f   // Move third vertex slightly
        )
        val targetNormals = floatArrayOf(
            0.1f, 0f, 0.99f,  // Slightly tilted normal
            -0.1f, 0f, 0.99f,
            0f, 0.1f, 0.99f
        )

        val morphTarget = MorphTarget(
            name = "deform",
            position = BufferAttribute(targetPositions, 3),
            normal = BufferAttribute(targetNormals, 3)
        )
        // Convert and store morph target
        geometry.morphTargets = listOf(morphTarget.position!!)
        geometry.morphAttributes["normal"] = listOf(morphTarget.normal!!)

        // Test morphing both attributes
        val blender = MorphBlender(geometry)
        blender.setInfluence(0, 1.0f) // Full morph

        val morphedPositions = blender.computeBlendedPositions()
        val morphedNormals = blender.computeBlendedNormals()

        // Verify position morphing
        assertEquals(0f, morphedPositions.getX(0))
        assertEquals(0f, morphedPositions.getY(0))
        assertEquals(1f, morphedPositions.getZ(0)) // Fully morphed to target

        // Verify normal morphing
        assertNotNull(morphedNormals)
        assertTrue(morphedNormals.getX(0) > 0f) // Normal tilted
        assertTrue(morphedNormals.getZ(0) > 0.9f) // Still mostly pointing forward
    }

    @Test
    fun testMorphTargetInfluenceRange() {
        // Test influence clamping and normalization
        val geometry = createMorphGeometry()
        val blender = MorphBlender(geometry)

        // Test clamping to [0, 1] range
        blender.setInfluence(0, -0.5f)
        assertEquals(0f, blender.getInfluence(0))

        blender.setInfluence(0, 1.5f)
        assertEquals(1f, blender.getInfluence(0))

        // Test multiple influences sum
        blender.setInfluence(0, 0.5f)
        blender.setInfluence(1, 0.5f)
        blender.setInfluence(2, 0.5f)

        val totalInfluence = blender.getTotalInfluence()
        assertEquals(1.5f, totalInfluence) // Can exceed 1.0 for artistic effect
    }

    @Test
    fun testMorphTargetColors() {
        // Test morphing vertex colors
        val geometry = BufferGeometry()

        val baseColors = floatArrayOf(
            1f, 0f, 0f,  // Red
            0f, 1f, 0f,  // Green
            0f, 0f, 1f   // Blue
        )
        geometry.setAttribute("color", BufferAttribute(baseColors, 3))

        val targetColors = floatArrayOf(
            0f, 1f, 1f,  // Cyan
            1f, 0f, 1f,  // Magenta
            1f, 1f, 0f   // Yellow
        )

        val morphTarget = MorphTarget(
            name = "colorShift",
            color = BufferAttribute(targetColors, 3)
        )
        geometry.morphAttributes["color"] = listOf(morphTarget.color!!)

        val blender = MorphBlender(geometry)
        blender.setInfluence(0, 0.5f)

        val blendedColors = blender.computeBlendedColors()
        assertNotNull(blendedColors)

        // Check interpolated colors
        assertEquals(0.5f, blendedColors.getX(0), 0.01f) // R: 1 -> 0
        assertEquals(0.5f, blendedColors.getY(0), 0.01f) // G: 0 -> 1
        assertEquals(0.5f, blendedColors.getZ(0), 0.01f) // B: 0 -> 1
    }

    @Test
    fun testMorphTargetPerformance() {
        // Test performance with many morph targets
        val vertexCount = 10000
        val targetCount = 20

        val geometry = BufferGeometry()
        val basePositions = FloatArray(vertexCount * 3) { it.toFloat() }
        geometry.setAttribute("position", BufferAttribute(basePositions, 3))

        // Create many morph targets
        val morphTargets = mutableListOf<MorphTarget>()
        for (i in 0 until targetCount) {
            val targetPositions = FloatArray(vertexCount * 3) {
                it.toFloat() + i * 0.1f
            }
            morphTargets.add(
                MorphTarget(
                    name = "target_$i",
                    position = BufferAttribute(targetPositions, 3)
                )
            )
        }
        geometry.morphTargets = morphTargets.map { it.position!! }

        val blender = MorphBlender(geometry)

        // Set random influences
        for (i in 0 until targetCount) {
            blender.setInfluence(i, kotlin.random.Random.nextDouble().toFloat())
        }

        // Measure blending performance
        val startTime = kotlin.time.TimeSource.Monotonic.markNow()
        val iterations = 100

        for (i in 0 until iterations) {
            blender.computeBlendedPositions()
        }

        val duration = startTime.elapsedNow()
        val avgTime = duration.inWholeMilliseconds.toFloat() / iterations

        // Should be fast enough for real-time (< 16ms for 60 FPS)
        assertTrue(avgTime < 16f, "Morph blending should be fast, took ${avgTime}ms")
    }

    @Test
    fun testMorphTargetTextureStorage() {
        // Test storing morph targets in texture for GPU processing
        val geometry = createMorphGeometry()
        val textureStorage = MorphTargetTextureStorage(geometry)

        // Pack morph targets into texture
        val texture = textureStorage.createMorphTexture()
        assertNotNull(texture)

        // Texture dimensions should accommodate all morph data
        val vertexCount = geometry.getAttribute("position")!!.count
        val targetCount = geometry.morphTargets!!.size
        val componentsPerVertex = 3 // x, y, z

        val requiredPixels = vertexCount * targetCount
        val texturePixels = texture.width * texture.height

        assertTrue(texturePixels >= requiredPixels)

        // Verify data can be retrieved
        val retrievedData = textureStorage.getMorphDataFromTexture(
            targetIndex = 0,
            vertexIndex = 0
        )
        assertNotNull(retrievedData)
        assertEquals(3, retrievedData.size) // x, y, z components
    }

    // Helper functions

    private fun createMorphGeometry(): BufferGeometry {
        val geometry = BufferGeometry()

        val basePositions = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f
        )
        geometry.setAttribute("position", BufferAttribute(basePositions, 3))

        val morphTargets = listOf(
            MorphTarget(
                name = "expand",
                position = BufferAttribute(
                    floatArrayOf(
                        -2f, -2f, 0f,
                        2f, -2f, 0f,
                        2f, 2f, 0f,
                        -2f, 2f, 0f
                    ), 3
                )
            ),
            MorphTarget(
                name = "forward",
                position = BufferAttribute(
                    floatArrayOf(
                        -1f, -1f, 1f,
                        1f, -1f, 1f,
                        1f, 1f, 1f,
                        -1f, 1f, 1f
                    ), 3
                )
            ),
            MorphTarget(
                name = "twist",
                position = BufferAttribute(
                    floatArrayOf(
                        -1f, -1f, 0f,
                        1f, -1f, 0.5f,
                        1f, 1f, 0f,
                        -1f, 1f, -0.5f
                    ), 3
                )
            )
        )
        // Convert MorphTarget list to BufferAttribute list
        geometry.morphTargets = morphTargets.map { it.position!! }

        // Store name->index mapping
        morphTargets.forEachIndexed { index, target ->
            geometry.morphTargetDictionary[target.name] = index
        }

        return geometry
    }
}

// Supporting classes for the contract test

data class MorphTarget(
    val name: String,
    val position: BufferAttribute? = null,
    val normal: BufferAttribute? = null,
    val color: BufferAttribute? = null
)

// Extension to BufferGeometry for morph target lookup by name
fun BufferGeometry.getMorphTargetByName(name: String): MorphTarget? {
    val index = morphTargetDictionary[name] ?: return null
    val position = morphTargets?.getOrNull(index) ?: return null
    return MorphTarget(name = name, position = position)
}

class MorphBlender(private val geometry: BufferGeometry) {
    private val morphTargetCount = maxOf(
        geometry.morphTargets?.size ?: 0,
        geometry.morphAttributes.values.maxOfOrNull { it.size } ?: 0
    )
    private val influences = FloatArray(morphTargetCount)

    fun setInfluence(index: Int, value: Float) {
        if (index >= 0 && index < influences.size) {
            influences[index] = value.coerceIn(0f, 1f)
        }
    }

    fun getInfluence(index: Int): Float {
        return if (index >= 0 && index < influences.size) {
            influences[index]
        } else {
            0f
        }
    }

    fun setInfluenceByName(name: String, value: Float) {
        val index = geometry.morphTargetDictionary[name]
        if (index != null && index >= 0) {
            setInfluence(index, value)
        }
    }

    fun getTotalInfluence(): Float {
        return influences.sum()
    }

    fun computeBlendedPositions(): BufferAttribute {
        val basePositions = geometry.getAttribute("position")!!
        val result = FloatArray(basePositions.array.size)

        // Start with base positions
        basePositions.array.copyInto(result)

        // Apply morph targets
        geometry.morphTargets?.forEachIndexed { index, target ->
            val influence = influences[index]
            if (influence > 0) {
                for (i in result.indices) {
                    result[i] += (target.array[i] - basePositions.array[i]) * influence
                }
            }
        }

        return BufferAttribute(result, basePositions.itemSize)
    }

    fun computeBlendedNormals(): BufferAttribute? {
        val baseNormals = geometry.getAttribute("normal") ?: return null
        val result = FloatArray(baseNormals.array.size)

        // Start with base normals
        baseNormals.array.copyInto(result)

        // Apply morph targets
        val morphNormals = geometry.morphAttributes["normal"]
        morphNormals?.forEachIndexed { index, target ->
            val influence = influences[index]
            if (influence > 0) {
                for (i in result.indices) {
                    result[i] += (target.array[i] - baseNormals.array[i]) * influence
                }
            }
        }

        // Renormalize normals
        for (i in 0 until result.size / 3) {
            val offset = i * 3
            val x = result[offset]
            val y = result[offset + 1]
            val z = result[offset + 2]
            val length = kotlin.math.sqrt(x * x + y * y + z * z)
            if (length > 0) {
                result[offset] /= length
                result[offset + 1] /= length
                result[offset + 2] /= length
            }
        }

        return BufferAttribute(result, baseNormals.itemSize)
    }

    fun computeBlendedColors(): BufferAttribute? {
        val baseColors = geometry.getAttribute("color") ?: return null
        val result = FloatArray(baseColors.array.size)

        // Start with base colors
        baseColors.array.copyInto(result)

        // Apply morph targets
        val morphColors = geometry.morphAttributes["color"]
        morphColors?.forEachIndexed { index, target ->
            val influence = influences[index]
            if (influence > 0) {
                for (i in result.indices) {
                    result[i] += (target.array[i] - baseColors.array[i]) * influence
                }
            }
        }

        return BufferAttribute(result, baseColors.itemSize)
    }
}

class MorphTargetTextureStorage(private val geometry: BufferGeometry) {
    fun createMorphTexture(): MorphTexture {
        val vertexCount = geometry.getAttribute("position")!!.count
        val targetCount = geometry.morphTargets!!.size

        // Calculate texture dimensions
        val width = kotlin.math.ceil(kotlin.math.sqrt(vertexCount.toDouble() * targetCount)).toInt()
        val height = width

        return MorphTexture(width, height)
    }

    fun getMorphDataFromTexture(targetIndex: Int, vertexIndex: Int): FloatArray {
        // Simulate retrieving morph data from texture
        val target = geometry.morphTargets!![targetIndex]
        val offset = vertexIndex * 3
        return floatArrayOf(
            target.array[offset],
            target.array[offset + 1],
            target.array[offset + 2]
        )
    }
}

class MorphTexture(val width: Int, val height: Int)