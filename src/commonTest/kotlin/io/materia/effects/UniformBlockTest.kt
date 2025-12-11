package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

/**
 * TDD Tests for UniformBlock builder - Type-safe uniform buffer layout management.
 * 
 * UniformBlock handles:
 * - WebGPU alignment requirements (16-byte for vec4, etc.)
 * - Automatic padding calculations
 * - WGSL struct generation
 * - Type-safe uniform value updates
 */
class UniformBlockTest {

    // ============ Basic Field Tests ============

    @Test
    fun emptyUniformBlock_hasZeroSize() {
        val block = UniformBlock.empty()
        assertEquals(0, block.size)
        assertTrue(block.layout.isEmpty())
    }

    @Test
    fun singleFloat_correctSizeAndOffset() {
        val block = uniformBlock {
            float("time")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("time", block.layout[0].name)
        assertEquals(UniformType.FLOAT, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        assertEquals(4, block.layout[0].size)
        assertEquals(4, block.size)
    }

    @Test
    fun singleInt_correctSizeAndOffset() {
        val block = uniformBlock {
            int("frameCount")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("frameCount", block.layout[0].name)
        assertEquals(UniformType.INT, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        assertEquals(4, block.layout[0].size)
        assertEquals(4, block.size)
    }

    @Test
    fun singleVec2_correctSizeAndOffset() {
        val block = uniformBlock {
            vec2("resolution")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("resolution", block.layout[0].name)
        assertEquals(UniformType.VEC2, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        assertEquals(8, block.layout[0].size)
        assertEquals(8, block.size)
    }

    @Test
    fun singleVec3_correctSizeAndOffset() {
        val block = uniformBlock {
            vec3("lightDir")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("lightDir", block.layout[0].name)
        assertEquals(UniformType.VEC3, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        assertEquals(12, block.layout[0].size)
        assertEquals(12, block.size)
    }

    @Test
    fun singleVec4_correctSizeAndOffset() {
        val block = uniformBlock {
            vec4("color")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("color", block.layout[0].name)
        assertEquals(UniformType.VEC4, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        assertEquals(16, block.layout[0].size)
        assertEquals(16, block.size)
    }

    @Test
    fun singleMat3_correctSizeAndOffset() {
        val block = uniformBlock {
            mat3("rotation")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("rotation", block.layout[0].name)
        assertEquals(UniformType.MAT3, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        // mat3 in WGSL is actually 3 vec4s (48 bytes) due to alignment
        assertEquals(48, block.layout[0].size)
        assertEquals(48, block.size)
    }

    @Test
    fun singleMat4_correctSizeAndOffset() {
        val block = uniformBlock {
            mat4("transform")
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("transform", block.layout[0].name)
        assertEquals(UniformType.MAT4, block.layout[0].type)
        assertEquals(0, block.layout[0].offset)
        assertEquals(64, block.layout[0].size)
        assertEquals(64, block.size)
    }

    // ============ Alignment Tests ============

    @Test
    fun vec2_requiresAlignment8() {
        // float (4 bytes) + vec2 (8 bytes, aligned to 8)
        // = float at 0, padding at 4, vec2 at 8
        val block = uniformBlock {
            float("time")
            vec2("resolution")
        }
        
        assertEquals(2, block.layout.size)
        assertEquals(0, block.layout[0].offset)  // time at 0
        assertEquals(8, block.layout[1].offset)  // resolution aligned to 8
        assertEquals(16, block.size)
    }

    @Test
    fun vec3_requiresAlignment16() {
        // vec3 requires 16-byte alignment in WGSL
        val block = uniformBlock {
            float("time")
            vec3("position")
        }
        
        assertEquals(2, block.layout.size)
        assertEquals(0, block.layout[0].offset)   // time at 0
        assertEquals(16, block.layout[1].offset)  // vec3 aligned to 16
        assertEquals(28, block.size)  // 16 + 12
    }

    @Test
    fun vec4_requiresAlignment16() {
        // vec4 requires 16-byte alignment
        val block = uniformBlock {
            float("time")
            vec4("color")
        }
        
        assertEquals(2, block.layout.size)
        assertEquals(0, block.layout[0].offset)   // time at 0
        assertEquals(16, block.layout[1].offset)  // vec4 aligned to 16
        assertEquals(32, block.size)  // 16 + 16
    }

    @Test
    fun mat4_requiresAlignment16() {
        // mat4 requires 16-byte alignment
        val block = uniformBlock {
            float("time")
            mat4("transform")
        }
        
        assertEquals(2, block.layout.size)
        assertEquals(0, block.layout[0].offset)   // time at 0
        assertEquals(16, block.layout[1].offset)  // mat4 aligned to 16
        assertEquals(80, block.size)  // 16 + 64
    }

    @Test
    fun multipleFloats_packTogether() {
        // Floats should pack without extra padding
        val block = uniformBlock {
            float("time")
            float("speed")
            float("scale")
        }
        
        assertEquals(3, block.layout.size)
        assertEquals(0, block.layout[0].offset)   // time at 0
        assertEquals(4, block.layout[1].offset)   // speed at 4
        assertEquals(8, block.layout[2].offset)   // scale at 8
        assertEquals(12, block.size)
    }

    @Test
    fun twoFloatsThenVec2_packsOptimally() {
        // Two floats (8 bytes total) before vec2 - vec2 aligned to 8 anyway
        val block = uniformBlock {
            float("time")
            float("speed")
            vec2("resolution")
        }
        
        assertEquals(3, block.layout.size)
        assertEquals(0, block.layout[0].offset)   // time at 0
        assertEquals(4, block.layout[1].offset)   // speed at 4
        assertEquals(8, block.layout[2].offset)   // resolution at 8 (aligned)
        assertEquals(16, block.size)
    }

    // ============ Complex Layout Tests ============

    @Test
    fun auroraUniformLayout_correctSizeAndOffsets() {
        // Based on the aurora example from recommendations doc
        val block = uniformBlock {
            float("time")           // offset 0, size 4
            vec2("resolution")      // offset 8, size 8 (aligned to 8)
            vec2("mouse")           // offset 16, size 8
            vec4("paletteA")        // offset 32, size 16 (aligned to 16)
            vec4("paletteB")        // offset 48, size 16
            vec4("paletteC")        // offset 64, size 16
            vec4("paletteD")        // offset 80, size 16
        }
        
        assertEquals(7, block.layout.size)
        
        // Verify offsets
        assertEquals(0, block.layout[0].offset)   // time
        assertEquals(8, block.layout[1].offset)   // resolution
        assertEquals(16, block.layout[2].offset)  // mouse
        assertEquals(32, block.layout[3].offset)  // paletteA
        assertEquals(48, block.layout[4].offset)  // paletteB
        assertEquals(64, block.layout[5].offset)  // paletteC
        assertEquals(80, block.layout[6].offset)  // paletteD
        
        assertEquals(96, block.size)
    }

    @Test
    fun fieldByName_returnsCorrectField() {
        val block = uniformBlock {
            float("time")
            vec2("resolution")
            vec4("color")
        }
        
        val timeField = block.field("time")
        assertEquals("time", timeField?.name)
        assertEquals(UniformType.FLOAT, timeField?.type)
        assertEquals(0, timeField?.offset)
        
        val resField = block.field("resolution")
        assertEquals("resolution", resField?.name)
        assertEquals(UniformType.VEC2, resField?.type)
        
        val colorField = block.field("color")
        assertEquals("color", colorField?.name)
        assertEquals(UniformType.VEC4, colorField?.type)
    }

    @Test
    fun fieldByName_returnsNullForUnknownField() {
        val block = uniformBlock {
            float("time")
        }
        
        assertEquals(null, block.field("unknown"))
    }

    // ============ WGSL Generation Tests ============

    @Test
    fun toWGSL_generatesCorrectStruct() {
        val block = uniformBlock {
            float("time")
            vec2("resolution")
        }
        
        val wgsl = block.toWGSL("Uniforms")
        
        assertContains(wgsl, "struct Uniforms {")
        assertContains(wgsl, "time: f32")
        assertContains(wgsl, "resolution: vec2<f32>")
        assertContains(wgsl, "}")
    }

    @Test
    fun toWGSL_includesPaddingFields() {
        val block = uniformBlock {
            float("time")
            vec4("color")
        }
        
        val wgsl = block.toWGSL("Uniforms")
        
        assertContains(wgsl, "struct Uniforms {")
        assertContains(wgsl, "time: f32")
        // Should include padding between float and vec4
        assertContains(wgsl, "_pad")
        assertContains(wgsl, "color: vec4<f32>")
    }

    @Test
    fun toWGSL_allTypes_generatesCorrectTypeNames() {
        val block = uniformBlock {
            float("f")
            int("i")
            vec2("v2")
            vec3("v3")
            vec4("v4")
            mat3("m3")
            mat4("m4")
        }
        
        val wgsl = block.toWGSL("AllTypes")
        
        assertContains(wgsl, "f: f32")
        assertContains(wgsl, "i: i32")
        assertContains(wgsl, "v2: vec2<f32>")
        assertContains(wgsl, "v3: vec3<f32>")
        assertContains(wgsl, "v4: vec4<f32>")
        assertContains(wgsl, "m3: mat3x3<f32>")
        assertContains(wgsl, "m4: mat4x4<f32>")
    }

    // ============ Array Tests ============

    @Test
    fun floatArray_correctSizeAndAlignment() {
        val block = uniformBlock {
            array("values", UniformType.FLOAT, 4)
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("values", block.layout[0].name)
        assertEquals(UniformType.FLOAT_ARRAY, block.layout[0].type)
        assertEquals(4, block.layout[0].arraySize)
        // Arrays of scalars have each element aligned to 16 bytes in WGSL
        assertEquals(64, block.layout[0].size)  // 4 elements * 16 bytes alignment
        assertEquals(64, block.size)
    }

    @Test
    fun vec4Array_correctSizeAndAlignment() {
        val block = uniformBlock {
            array("lights", UniformType.VEC4, 4)
        }
        
        assertEquals(1, block.layout.size)
        assertEquals("lights", block.layout[0].name)
        assertEquals(UniformType.VEC4_ARRAY, block.layout[0].type)
        assertEquals(4, block.layout[0].arraySize)
        assertEquals(64, block.layout[0].size)  // 4 * 16 bytes
        assertEquals(64, block.size)
    }

    // ============ Buffer Creation Tests ============

    @Test
    fun createBuffer_correctSize() {
        val block = uniformBlock {
            float("time")
            vec4("color")
        }
        
        val buffer = block.createBuffer()
        
        assertEquals(block.size, buffer.size * 4)  // FloatArray size is in floats, not bytes
    }

    @Test
    fun uniformUpdater_setFloat() {
        val block = uniformBlock {
            float("time")
            float("speed")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        updater.set("time", 1.5f)
        updater.set("speed", 2.0f)
        
        assertEquals(1.5f, buffer[0], 0.0001f)
        assertEquals(2.0f, buffer[1], 0.0001f)
    }

    @Test
    fun uniformUpdater_setVec2() {
        val block = uniformBlock {
            vec2("resolution")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        updater.set("resolution", 1920f, 1080f)
        
        assertEquals(1920f, buffer[0], 0.0001f)
        assertEquals(1080f, buffer[1], 0.0001f)
    }

    @Test
    fun uniformUpdater_setVec3() {
        val block = uniformBlock {
            vec3("position")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        updater.set("position", 1.0f, 2.0f, 3.0f)
        
        assertEquals(1.0f, buffer[0], 0.0001f)
        assertEquals(2.0f, buffer[1], 0.0001f)
        assertEquals(3.0f, buffer[2], 0.0001f)
    }

    @Test
    fun uniformUpdater_setVec4() {
        val block = uniformBlock {
            vec4("color")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        updater.set("color", 1.0f, 0.5f, 0.25f, 1.0f)
        
        assertEquals(1.0f, buffer[0], 0.0001f)
        assertEquals(0.5f, buffer[1], 0.0001f)
        assertEquals(0.25f, buffer[2], 0.0001f)
        assertEquals(1.0f, buffer[3], 0.0001f)
    }

    @Test
    fun uniformUpdater_setInt() {
        val block = uniformBlock {
            int("frameCount")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        updater.setInt("frameCount", 42)
        
        // Integer is stored as bits in the float buffer
        // Float.fromBits(value) stores int bits, so we retrieve with toRawBits
        assertEquals(42, buffer[0].toRawBits())
    }

    @Test
    fun uniformUpdater_withOffset() {
        val block = uniformBlock {
            float("time")
            vec2("resolution")  // offset 8
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        updater.set("time", 1.0f)
        updater.set("resolution", 800f, 600f)
        
        assertEquals(1.0f, buffer[0], 0.0001f)
        // buffer[1] is padding
        assertEquals(800f, buffer[2], 0.0001f)  // resolution.x at offset 8 (index 2)
        assertEquals(600f, buffer[3], 0.0001f)  // resolution.y at offset 12 (index 3)
    }

    // ============ Matrix Updater Tests ============

    @Test
    fun uniformUpdater_setMat3_fromArray() {
        val block = uniformBlock {
            mat3("rotation")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        // Identity matrix in column-major order
        val identity = floatArrayOf(
            1f, 0f, 0f,  // column 0
            0f, 1f, 0f,  // column 1
            0f, 0f, 1f   // column 2
        )
        updater.setMat3("rotation", identity)
        
        // mat3 is stored as 3 vec4s (12 floats) with padding
        // Column 0
        assertEquals(1f, buffer[0], 0.0001f)
        assertEquals(0f, buffer[1], 0.0001f)
        assertEquals(0f, buffer[2], 0.0001f)
        assertEquals(0f, buffer[3], 0.0001f)  // padding
        // Column 1
        assertEquals(0f, buffer[4], 0.0001f)
        assertEquals(1f, buffer[5], 0.0001f)
        assertEquals(0f, buffer[6], 0.0001f)
        assertEquals(0f, buffer[7], 0.0001f)  // padding
        // Column 2
        assertEquals(0f, buffer[8], 0.0001f)
        assertEquals(0f, buffer[9], 0.0001f)
        assertEquals(1f, buffer[10], 0.0001f)
        assertEquals(0f, buffer[11], 0.0001f)  // padding
    }

    @Test
    fun uniformUpdater_setMat3_fromComponents() {
        val block = uniformBlock {
            mat3("transform")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        // Set with individual components (column-major)
        updater.setMat3(
            "transform",
            2f, 0f, 0f,   // column 0
            0f, 3f, 0f,   // column 1
            0f, 0f, 4f    // column 2
        )
        
        // Verify diagonal values
        assertEquals(2f, buffer[0], 0.0001f)  // m00
        assertEquals(3f, buffer[5], 0.0001f)  // m11
        assertEquals(4f, buffer[10], 0.0001f) // m22
    }

    @Test
    fun uniformUpdater_setMat4_fromArray() {
        val block = uniformBlock {
            mat4("modelView")
        }
        
        val buffer = block.createBuffer()
        val updater = block.createUpdater(buffer)
        
        // Identity matrix in column-major order
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,  // column 0
            0f, 1f, 0f, 0f,  // column 1
            0f, 0f, 1f, 0f,  // column 2
            0f, 0f, 0f, 1f   // column 3
        )
        updater.setMat4("modelView", identity)
        
        // Verify diagonal
        assertEquals(1f, buffer[0], 0.0001f)   // m00
        assertEquals(1f, buffer[5], 0.0001f)   // m11
        assertEquals(1f, buffer[10], 0.0001f)  // m22
        assertEquals(1f, buffer[15], 0.0001f)  // m33
    }

    // ============ DSL Builder Tests ============

    @Test
    fun dslBuilder_chainingFields() {
        val block = uniformBlock {
            float("a")
            float("b")
            float("c")
        }
        
        assertEquals(3, block.layout.size)
        assertEquals(listOf("a", "b", "c"), block.layout.map { it.name })
    }

    @Test
    fun uniformBlock_immutable() {
        val block = uniformBlock {
            float("time")
        }
        
        // Layout should be immutable
        assertTrue(block.layout is List<UniformField>)
    }
}
