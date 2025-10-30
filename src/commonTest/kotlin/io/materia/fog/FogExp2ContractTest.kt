package io.materia.fog

import io.materia.core.math.Color
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract Tests for Exponential Squared Fog
 * Covers FR-F004, FR-F005, FR-F006 from contracts/fog-api.kt
 *
 * TDD Requirement: These tests MUST FAIL initially
 */
class FogExp2ContractTest {

    /**
     * FR-F004: FogExp2 is instantiated with color and density parameters
     */
    @Test
    fun testFogExp2Creation() {
        val fog = FogExp2(
            color = Color(0xCCCCCC),
            density = 0.001f
        )

        // Verify fog is created
        assertNotNull(fog, "FogExp2 should be instantiated")

        // Verify parameters
        assertEquals(
            0xCCCCCC,
            fog.color.getHex(),
            "FogExp2 color should match constructor parameter"
        )
        assertEquals(0.001f, fog.density, "FogExp2 density should match constructor parameter")
    }

    /**
     * FR-F004: FogExp2 defaults to white color with reasonable density
     */
    @Test
    fun testFogExp2Defaults() {
        val fog = FogExp2()

        // Verify default values
        assertEquals(0xFFFFFF, fog.color.getHex(), "FogExp2 should default to white")
        assertEquals(0.00025f, fog.density, "FogExp2 density should default to 0.00025")
    }

    /**
     * FR-F005: Exponential squared falloff
     * Formula: factor = exp(-(density * depth)^2)
     */
    @Test
    fun testFogExp2ExponentialFalloff() {
        val fog = FogExp2(
            color = Color(0xFFFFFF),
            density = 0.01f
        )

        // Test at origin (depth = 0, should be 1.0 = no fog)
        val factorAtOrigin = fog.getFogFactor(0f)
        assertEquals(1.0f, factorAtOrigin, 0.01f, "FogExp2 factor should be 1.0 at origin")

        // Test at various distances and verify exponential squared formula
        // factor = exp(-(density * depth)^2)
        val depth = 50f
        val expectedFactor = exp(-((fog.density * depth) * (fog.density * depth)))
        val actualFactor = fog.getFogFactor(depth)
        assertEquals(
            expectedFactor, actualFactor, 0.01f,
            "FogExp2 should follow exponential squared formula"
        )

        // Test that fog increases with distance (factor decreases)
        val factor10 = fog.getFogFactor(10f)
        val factor20 = fog.getFogFactor(20f)
        val factor30 = fog.getFogFactor(30f)

        assertTrue(factor10 > factor20, "Fog factor should decrease with distance")
        assertTrue(factor20 > factor30, "Fog factor should continue decreasing with distance")
    }

    /**
     * FR-F005: Higher density = more fog at same distance
     */
    @Test
    fun testFogExp2DensityEffect() {
        val lowDensityFog = FogExp2(density = 0.001f)
        val highDensityFog = FogExp2(density = 0.01f)

        val depth = 50f

        val lowFactor = lowDensityFog.getFogFactor(depth)
        val highFactor = highDensityFog.getFogFactor(depth)

        // Higher density should produce lower factor (more fog) at same distance
        assertTrue(
            highFactor < lowFactor,
            "Higher density should produce more fog (lower factor) at same distance"
        )
    }

    /**
     * FR-F005: Fog factor is always clamped to [0, 1]
     */
    @Test
    fun testFogExp2FactorClamping() {
        val fog = FogExp2(density = 0.01f)

        // Test various depths
        for (depth in listOf(0f, 10f, 100f, 1000f, 10000f)) {
            val factor = fog.getFogFactor(depth)
            assertTrue(
                factor >= 0f && factor <= 1f,
                "FogExp2 factor should be clamped to [0, 1] at depth $depth (got $factor)"
            )
        }
    }

    /**
     * FR-F006: FogExp2 generates shader code for WGSL/GLSL integration
     */
    @Test
    fun testFogExp2GeneratesShaderCode() {
        val fog = FogExp2(
            color = Color(0x888888),
            density = 0.001f
        )

        val shaderCode = fog.generateShaderCode()

        // Verify shader code is generated
        assertNotNull(shaderCode, "FogExp2 should generate shader code")
        assertTrue(shaderCode.isNotEmpty(), "FogExp2 shader code should not be empty")

        // Shader code should reference fog parameters
        assertTrue(
            shaderCode.contains("density") || shaderCode.contains("fogDensity"),
            "Shader code should reference density parameter"
        )
        assertTrue(
            shaderCode.contains("color") || shaderCode.contains("fogColor"),
            "Shader code should reference fog color"
        )

        // Should contain exponential function
        assertTrue(
            shaderCode.contains("exp") || shaderCode.contains("pow"),
            "Shader code should contain exponential function"
        )
    }

    /**
     * FR-F006: FogExp2 is cloneable
     */
    @Test
    fun testFogExp2Clone() {
        val original = FogExp2(
            color = Color(0xFF00FF),
            density = 0.002f
        )

        val cloned = original.clone()

        // Verify clone is created
        assertNotNull(cloned, "FogExp2 clone should be created")

        // Verify clone has same parameters
        assertEquals(
            original.color.getHex(),
            cloned.color.getHex(),
            "Cloned fog should have same color"
        )
        assertEquals(original.density, cloned.density, "Cloned fog should have same density")

        // Verify independence (modifying clone doesn't affect original)
        cloned.density = 0.005f
        assertEquals(
            0.002f,
            original.density,
            "Original fog should not be affected by clone modifications"
        )
    }

    /**
     * FR-F004: FogExp2 parameters are mutable
     */
    @Test
    fun testFogExp2ParametersMutable() {
        val fog = FogExp2()

        // Modify parameters
        fog.color = Color(0x00FF00)
        fog.density = 0.01f

        // Verify changes
        assertEquals(0x00FF00, fog.color.getHex(), "FogExp2 color should be mutable")
        assertEquals(0.01f, fog.density, "FogExp2 density should be mutable")
    }

    /**
     * FR-F006: FogExp2 serialization to JSON
     */
    @Test
    fun testFogExp2Serialization() {
        val fog = FogExp2(
            color = Color(0xAAAAAA),
            density = 0.003f
        )

        val json = fog.toJSON()

        // Verify JSON structure
        assertNotNull(json, "FogExp2 should serialize to JSON")
        assertTrue(json.containsKey("type"), "FogExp2 JSON should contain type")
        assertTrue(json.containsKey("color"), "FogExp2 JSON should contain color")
        assertTrue(json.containsKey("density"), "FogExp2 JSON should contain density")

        assertEquals("FogExp2", json["type"], "FogExp2 type should be 'FogExp2'")
        assertEquals(0xAAAAAA, json["color"], "FogExp2 JSON color should match")
        assertEquals(0.003f, json["density"], "FogExp2 JSON density should match")
    }

    /**
     * FR-F004: FogExp2 has isFogExp2 property for type checking
     */
    @Test
    fun testFogExp2TypeProperty() {
        val fog = FogExp2()

        assertTrue(fog.isFogExp2, "FogExp2 should have isFogExp2 property set to true")
    }

    /**
     * FR-F005: Exponential squared provides smooth, realistic fog
     */
    @Test
    fun testFogExp2SmoothTransition() {
        val fog = FogExp2(density = 0.01f)

        // Verify smooth falloff by checking intermediate values
        val depths = listOf(0f, 10f, 20f, 30f, 40f, 50f)
        val factors = depths.map { fog.getFogFactor(it) }

        // Verify monotonic decrease
        for (i in 0 until factors.size - 1) {
            assertTrue(
                factors[i] >= factors[i + 1],
                "Fog factors should decrease monotonically with depth"
            )
        }

        // Verify smooth curve (no sharp transitions)
        for (i in 1 until factors.size - 1) {
            val delta1 = factors[i - 1] - factors[i]
            val delta2 = factors[i] - factors[i + 1]

            // Exponential squared should have accelerating falloff
            // (each step should have larger delta as we go further)
            assertTrue(
                delta2 >= delta1,
                "Exponential squared should have accelerating falloff"
            )
        }
    }

    /**
     * FR-F005: Very small density produces minimal fog
     */
    @Test
    fun testFogExp2MinimalDensity() {
        val fog = FogExp2(density = 0.0001f)

        // Even at reasonable distances, fog should be minimal
        val factorAt100 = fog.getFogFactor(100f)
        assertTrue(
            factorAt100 > 0.9f,
            "Minimal density should produce minimal fog at moderate distances"
        )
    }

    /**
     * FR-F005: Very high density produces thick fog quickly
     */
    @Test
    fun testFogExp2HighDensity() {
        val fog = FogExp2(density = 0.1f)

        // At even short distances, fog should be significant
        val factorAt10 = fog.getFogFactor(10f)
        assertTrue(factorAt10 < 0.5f, "High density should produce thick fog at short distances")
    }
}