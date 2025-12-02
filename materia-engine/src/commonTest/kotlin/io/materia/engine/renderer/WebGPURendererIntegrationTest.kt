package io.materia.engine.renderer

import io.materia.core.math.Color
import io.materia.engine.camera.PerspectiveCamera
import io.materia.engine.material.BasicMaterial
import io.materia.engine.material.StandardMaterial
import io.materia.engine.material.Side
import io.materia.engine.math.Vec3
import io.materia.engine.scene.EngineMesh
import io.materia.engine.shader.ShaderFeature
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.gpu.GpuPowerPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the WebGPURenderer and related components.
 * 
 * These tests verify the integration between:
 * - Scene graph (EngineMesh)
 * - Materials (BasicMaterial, StandardMaterial)
 * - Renderer configuration
 * 
 * Note: Actual GPU rendering tests require platform-specific test runners
 * with GPU access. These tests verify the API contracts and state management.
 */
class WebGPURendererIntegrationTest {

    /**
     * Creates a simple triangle geometry for testing.
     */
    private fun createTestGeometry(): BufferGeometry {
        val geometry = BufferGeometry()
        val positions = floatArrayOf(
            0f, 1f, 0f,
            -1f, -1f, 0f,
            1f, -1f, 0f
        )
        geometry.setAttribute("position", BufferAttribute(positions, 3))
        return geometry
    }

    @Test
    fun renderer_configurationDefaults() {
        val config = WebGPURendererConfig()
        
        assertTrue(config.depthTest)
        assertEquals(0f, config.clearColor.r)
        assertEquals(0f, config.clearColor.g)
        assertEquals(0f, config.clearColor.b)
        assertEquals(1f, config.clearAlpha)
        assertEquals(GpuPowerPreference.HIGH_PERFORMANCE, config.powerPreference)
        assertTrue(config.autoResize)
        assertEquals(1, config.antialias)
        assertFalse(config.debug)
    }

    @Test
    fun renderer_customConfiguration() {
        val config = WebGPURendererConfig(
            depthTest = false,
            clearColor = Color(0.2f, 0.3f, 0.4f),
            clearAlpha = 0.5f,
            powerPreference = GpuPowerPreference.LOW_POWER,
            autoResize = false,
            antialias = 4,
            debug = true
        )
        
        assertFalse(config.depthTest)
        assertEquals(0.2f, config.clearColor.r, 0.001f)
        assertEquals(0.3f, config.clearColor.g, 0.001f)
        assertEquals(0.4f, config.clearColor.b, 0.001f)
        assertEquals(0.5f, config.clearAlpha)
        assertEquals(GpuPowerPreference.LOW_POWER, config.powerPreference)
        assertFalse(config.autoResize)
        assertEquals(4, config.antialias)
        assertTrue(config.debug)
    }

    @Test
    fun renderStats_initialState() {
        val stats = WebGPURenderStats()
        
        assertEquals(0L, stats.frameCount)
        assertEquals(0, stats.drawCalls)
        assertEquals(0, stats.triangles)
        assertEquals(0, stats.textureBinds)
        assertEquals(0, stats.pipelineSwitches)
        assertEquals(0f, stats.frameTime)
    }

    @Test
    fun renderStats_canBeUpdated() {
        val stats = WebGPURenderStats()
        
        stats.frameCount = 100
        stats.drawCalls = 50
        stats.triangles = 10000
        stats.textureBinds = 25
        stats.pipelineSwitches = 10
        stats.frameTime = 16.67f
        
        assertEquals(100L, stats.frameCount)
        assertEquals(50, stats.drawCalls)
        assertEquals(10000, stats.triangles)
        assertEquals(25, stats.textureBinds)
        assertEquals(10, stats.pipelineSwitches)
        assertEquals(16.67f, stats.frameTime, 0.01f)
    }

    @Test
    fun meshWithMaterial_canBeConstructed() {
        val geometry = createTestGeometry()
        
        val mesh1 = EngineMesh(geometry, BasicMaterial(color = Color(1f, 0f, 0f)))
        val mesh2 = EngineMesh(geometry, StandardMaterial(
            baseColor = Color(0f, 1f, 0f),
            metallic = 0.5f,
            roughness = 0.3f
        ))
        
        assertNotNull(mesh1.engineMaterial)
        assertNotNull(mesh2.engineMaterial)
    }

    @Test
    fun cameraSetup_perspectiveCamera() {
        val camera = PerspectiveCamera(
            fovDegrees = 75f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000f
        )
        
        camera.transform.setPosition(0f, 5f, 10f)
        camera.lookAt(Vec3.Zero)
        
        // Verify camera is positioned correctly
        assertEquals(0f, camera.transform.position.x, 0.001f)
        assertEquals(5f, camera.transform.position.y, 0.001f)
        assertEquals(10f, camera.transform.position.z, 0.001f)
        
        // Projection matrix should be computed
        assertNotNull(camera.projectionMatrix())
    }

    @Test
    fun materialFeatureFlags_basicMaterial() {
        val material = BasicMaterial(
            color = Color(1f, 0f, 0f)
        )
        
        val features = material.getRequiredFeatures()
        
        // BasicMaterial should not require any special features
        assertTrue(features.isEmpty())
    }

    @Test
    fun materialFeatureFlags_standardMaterial() {
        val material = StandardMaterial(
            baseColor = Color(1f, 1f, 1f),
            metallic = 0.5f,
            roughness = 0.3f
        )
        
        val features = material.getRequiredFeatures()
        
        // StandardMaterial always requires directional light
        assertTrue(ShaderFeature.USE_DIRECTIONAL_LIGHT in features)
    }

    @Test
    fun materialFeatureFlags_standardMaterialWithTextures() {
        val material = StandardMaterial(
            baseColor = Color(1f, 1f, 1f)
        )
        
        // Simulate having textures
        material.baseColorMap = "dummy-texture-reference"
        material.normalMap = "dummy-normal-reference"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_DIRECTIONAL_LIGHT in features)
        assertTrue(ShaderFeature.USE_TEXTURE in features)
        assertTrue(ShaderFeature.USE_NORMAL_MAP in features)
    }

    @Test
    fun materialFeatureFlags_alphaCutoff() {
        val material = StandardMaterial(
            alphaCutoff = 0.5f
        )
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_ALPHA_CUTOFF in features)
    }

    @Test
    fun materialSide_affectsCulling() {
        val frontOnly = BasicMaterial().apply { side = Side.FRONT }
        val backOnly = BasicMaterial().apply { side = Side.BACK }
        val doubleSided = BasicMaterial().apply { side = Side.DOUBLE }
        
        assertEquals(Side.FRONT, frontOnly.side)
        assertEquals(Side.BACK, backOnly.side)
        assertEquals(Side.DOUBLE, doubleSided.side)
    }

    @Test
    fun materialTransparency_affectsBlending() {
        val opaque = BasicMaterial(transparent = false)
        val transparent = BasicMaterial(transparent = true, opacity = 0.5f)
        
        assertFalse(opaque.transparent)
        assertTrue(transparent.transparent)
        assertEquals(0.5f, transparent.opacity)
    }

    @Test
    fun meshHierarchy_complexStructure() {
        val geometry = createTestGeometry()
        
        // Create a robot-like hierarchy
        val robot = EngineMesh(geometry)
        robot.name = "robot"
        
        val body = EngineMesh(geometry)
        body.name = "body"
        body.position.set(0f, 1f, 0f)
        
        val leftArm = EngineMesh(geometry)
        leftArm.name = "leftArm"
        leftArm.position.set(-0.5f, 0f, 0f)
        
        val rightArm = EngineMesh(geometry)
        rightArm.name = "rightArm"
        rightArm.position.set(0.5f, 0f, 0f)
        
        val head = EngineMesh(geometry)
        head.name = "head"
        head.position.set(0f, 0.75f, 0f)
        
        body.add(leftArm)
        body.add(rightArm)
        body.add(head)
        robot.add(body)
        
        // Verify hierarchy
        assertEquals(1, robot.children.size)
        assertEquals(3, body.children.size)
        
        // Update world matrices
        robot.updateMatrixWorld(true)
        
        // Verify head world position: robot(0,0,0) + body(0,1,0) + head(0,0.75,0) = (0, 1.75, 0)
        val headWorldPos = head.getWorldPosition()
        assertEquals(0f, headWorldPos.x, 0.001f)
        assertEquals(1.75f, headWorldPos.y, 0.001f)
        assertEquals(0f, headWorldPos.z, 0.001f)
    }

    @Test
    fun renderer_initialStateBeforeInit() {
        val renderer = WebGPURenderer()
        
        assertFalse(renderer.isDisposed)
        assertEquals(0L, renderer.stats.frameCount)
    }

    @Test
    fun renderer_disposeIsIdempotent() {
        val renderer = WebGPURenderer()
        
        renderer.dispose()
        assertTrue(renderer.isDisposed)
        
        // Should not throw
        renderer.dispose()
        assertTrue(renderer.isDisposed)
    }
}
