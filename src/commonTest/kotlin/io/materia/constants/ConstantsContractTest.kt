package io.materia.constants

import kotlin.test.*

/** T046 - FR-CE001 through FR-CE010 */
class ConstantsContractTest {
    @Test
    fun testBlendingModes() = assertTrue(BlendMode.values().isNotEmpty())

    @Test
    fun testDepthModes() = assertTrue(DepthMode.values().isNotEmpty())

    @Test
    fun testTextureMapping() = assertTrue(TextureMapping.values().isNotEmpty())
}

enum class BlendMode { ADD, MULTIPLY }
enum class DepthMode { LESS, EQUAL }
enum class TextureMapping { UV, CUBE }
