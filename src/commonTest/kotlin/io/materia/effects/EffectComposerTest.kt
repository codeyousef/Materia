package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * TDD Tests for EffectComposer - Manages a chain of post-processing passes.
 *
 * EffectComposer provides:
 * - Pass chain management (add, remove, reorder)
 * - Ping-pong buffer management for multi-pass rendering
 * - Automatic size propagation to passes
 * - Simple render() API for executing the chain
 */
class EffectComposerTest {

    // ============ Creation Tests ============

    @Test
    fun create_withDefaultConfiguration() {
        val composer = EffectComposer()

        assertNotNull(composer)
        assertTrue(composer.passes.isEmpty())
        assertEquals(0, composer.passCount)
    }

    @Test
    fun create_withInitialSize() {
        val composer = EffectComposer(width = 1920, height = 1080)

        assertEquals(1920, composer.width)
        assertEquals(1080, composer.height)
    }

    // ============ Pass Management Tests ============

    @Test
    fun addPass_addsToChain() {
        val composer = EffectComposer()
        val pass = createTestPass()

        composer.addPass(pass)

        assertEquals(1, composer.passCount)
        assertTrue(pass in composer.passes)
    }

    @Test
    fun addPass_maintainsOrder() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")
        val pass3 = createTestPass("pass3")

        composer.addPass(pass1)
        composer.addPass(pass2)
        composer.addPass(pass3)

        assertEquals(3, composer.passCount)
        assertEquals(pass1, composer.passes[0])
        assertEquals(pass2, composer.passes[1])
        assertEquals(pass3, composer.passes[2])
    }

    @Test
    fun insertPass_atIndex() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")
        val pass3 = createTestPass("pass3")

        composer.addPass(pass1)
        composer.addPass(pass3)
        composer.insertPass(pass2, index = 1)

        assertEquals(pass1, composer.passes[0])
        assertEquals(pass2, composer.passes[1])
        assertEquals(pass3, composer.passes[2])
    }

    @Test
    fun removePass_byReference() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")

        composer.addPass(pass1)
        composer.addPass(pass2)

        val removed = composer.removePass(pass1)

        assertTrue(removed)
        assertEquals(1, composer.passCount)
        assertFalse(pass1 in composer.passes)
        assertTrue(pass2 in composer.passes)
    }

    @Test
    fun removePass_notFound_returnsFalse() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")

        composer.addPass(pass1)

        val removed = composer.removePass(pass2)

        assertFalse(removed)
        assertEquals(1, composer.passCount)
    }

    @Test
    fun removePassAt_byIndex() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")
        val pass3 = createTestPass("pass3")

        composer.addPass(pass1)
        composer.addPass(pass2)
        composer.addPass(pass3)

        val removed = composer.removePassAt(1)

        assertEquals(pass2, removed)
        assertEquals(2, composer.passCount)
        assertEquals(pass1, composer.passes[0])
        assertEquals(pass3, composer.passes[1])
    }

    @Test
    fun clearPasses_removesAll() {
        val composer = EffectComposer()
        composer.addPass(createTestPass("pass1"))
        composer.addPass(createTestPass("pass2"))
        composer.addPass(createTestPass("pass3"))

        composer.clearPasses()

        assertEquals(0, composer.passCount)
        assertTrue(composer.passes.isEmpty())
    }

    // ============ Size Propagation Tests ============

    @Test
    fun setSize_propagatesToPasses() {
        val composer = EffectComposer(width = 800, height = 600)
        val pass = createTestPass()

        composer.addPass(pass)
        composer.setSize(1920, 1080)

        assertEquals(1920, composer.width)
        assertEquals(1080, composer.height)
        assertEquals(1920, pass.width)
        assertEquals(1080, pass.height)
    }

    @Test
    fun addPass_inheritsCurrentSize() {
        val composer = EffectComposer(width = 1920, height = 1080)
        val pass = createTestPass()

        composer.addPass(pass)

        assertEquals(1920, pass.width)
        assertEquals(1080, pass.height)
    }

    // ============ Pass Ordering Tests ============

    @Test
    fun swapPasses_exchangesPositions() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")
        val pass3 = createTestPass("pass3")

        composer.addPass(pass1)
        composer.addPass(pass2)
        composer.addPass(pass3)

        composer.swapPasses(0, 2)

        assertEquals(pass3, composer.passes[0])
        assertEquals(pass2, composer.passes[1])
        assertEquals(pass1, composer.passes[2])
    }

    @Test
    fun movePass_toNewIndex() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")
        val pass3 = createTestPass("pass3")

        composer.addPass(pass1)
        composer.addPass(pass2)
        composer.addPass(pass3)

        composer.movePass(0, 2)

        assertEquals(pass2, composer.passes[0])
        assertEquals(pass3, composer.passes[1])
        assertEquals(pass1, composer.passes[2])
    }

    // ============ Enable/Disable Tests ============

    @Test
    fun disabledPasses_areSkipped() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")
        pass2.enabled = false

        composer.addPass(pass1)
        composer.addPass(pass2)

        val enabledPasses = composer.getEnabledPasses()

        assertEquals(1, enabledPasses.size)
        assertEquals(pass1, enabledPasses[0])
    }

    // ============ Disposal Tests ============

    @Test
    fun dispose_disposesAllPasses() {
        val composer = EffectComposer()
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")

        composer.addPass(pass1)
        composer.addPass(pass2)

        composer.dispose()

        assertTrue(pass1.isDisposed)
        assertTrue(pass2.isDisposed)
        assertTrue(composer.isDisposed)
    }

    @Test
    fun dispose_isIdempotent() {
        val composer = EffectComposer()
        composer.addPass(createTestPass())

        composer.dispose()
        composer.dispose() // Should not throw
        composer.dispose()

        assertTrue(composer.isDisposed)
    }

    @Test
    fun addPass_afterDispose_throws() {
        val composer = EffectComposer()
        composer.dispose()

        assertFailsWith<IllegalStateException> {
            composer.addPass(createTestPass())
        }
    }

    // ============ Fluent API Tests ============

    @Test
    fun fluentApi_chainsOperations() {
        val pass1 = createTestPass("pass1")
        val pass2 = createTestPass("pass2")

        val composer = EffectComposer()
            .also { it.addPass(pass1) }
            .also { it.addPass(pass2) }
            .also { it.setSize(1920, 1080) }

        assertEquals(2, composer.passCount)
        assertEquals(1920, composer.width)
    }

    // ============ Helper Functions ============

    private var passCounter = 0

    private fun createTestPass(name: String = "test-pass-${++passCounter}"): FullScreenEffectPass {
        return FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = """
                    @fragment
                    fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                        return vec4<f32>(uv, 0.0, 1.0);
                    }
                """.trimIndent()
            }
        )
    }
}
