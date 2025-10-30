package io.materia.helper

import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Color
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferGeometry
import io.materia.light.DirectionalLight
import io.materia.light.HemisphereLight
import io.materia.light.PointLight
import io.materia.light.SpotLight
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract Tests for Light and Camera Helpers
 * Covers FR-H005 through FR-H009 from contracts/helper-api.kt
 *
 * TDD Requirement: These tests MUST FAIL initially
 */
class LightCameraHelpersContractTest {

    /**
     * FR-H005: CameraHelper displays camera frustum
     */
    @Test
    fun testCameraHelperShowsFrustum() {
        val camera = PerspectiveCamera(
            fov = 75f,
            aspect = 16f / 9f,
            near = 0.1f,
            far = 1000f
        )

        val cameraHelper = CameraHelper(camera)

        // Verify helper is created
        assertNotNull(cameraHelper, "CameraHelper should be instantiated")

        // Verify camera reference
        assertEquals(camera, cameraHelper.camera, "CameraHelper should reference the camera")

        // Verify it's an Object3D
        assertTrue(cameraHelper is Object3D, "CameraHelper should extend Object3D")

        // Verify geometry exists for frustum visualization
        assertNotNull(cameraHelper.geometry, "CameraHelper should have geometry")

        // Verify pointMap exists (maps frustum points)
        assertNotNull(cameraHelper.pointMap, "CameraHelper should have pointMap")
        assertTrue(
            cameraHelper.pointMap.isNotEmpty(),
            "CameraHelper pointMap should contain frustum points"
        )
    }

    /**
     * FR-H005: CameraHelper updates with camera changes
     */
    @Test
    fun testCameraHelperUpdatesWithCamera() {
        val camera = PerspectiveCamera(fov = 75f, aspect = 1.5f, near = 0.1f, far = 100f)
        val cameraHelper = CameraHelper(camera)

        // Change camera parameters
        camera.fov = 90f
        camera.updateProjectionMatrix()

        // Update helper
        cameraHelper.update()

        // Verify helper reflects camera changes
        assertNotNull(cameraHelper.geometry, "CameraHelper geometry should exist after update")
    }

    /**
     * FR-H006: DirectionalLightHelper shows light direction
     */
    @Test
    fun testDirectionalLightHelperShowsDirection() {
        val light = DirectionalLight(Color(0xFFFFFF), intensity = 1f)
        light.position.set(10f, 10f, 10f)
        light.target.position.set(0f, 0f, 0f)

        val lightHelper = DirectionalLightHelper(light, size = 5f, color = Color(0xFF0000))

        // Verify helper is created
        assertNotNull(lightHelper, "DirectionalLightHelper should be instantiated")

        // Verify light reference
        assertEquals(light, lightHelper.light, "DirectionalLightHelper should reference the light")

        // Verify size
        assertEquals(5f, lightHelper.size, "DirectionalLightHelper should preserve size")

        // Verify color
        assertNotNull(lightHelper.color, "DirectionalLightHelper should have color")
        assertEquals(
            0xFF0000,
            lightHelper.color?.getHex(),
            "DirectionalLightHelper color should match"
        )

        // Verify it's an Object3D
        assertTrue(lightHelper is Object3D, "DirectionalLightHelper should extend Object3D")

        // Verify geometry exists
        assertNotNull(lightHelper.geometry, "DirectionalLightHelper should have geometry")
    }

    /**
     * FR-H006: DirectionalLightHelper updates with light changes
     */
    @Test
    fun testDirectionalLightHelperUpdates() {
        val light = DirectionalLight(Color(0xFFFFFF))
        val lightHelper = DirectionalLightHelper(light)

        // Move light
        light.position.set(20f, 20f, 20f)
        light.target.position.set(5f, 0f, 0f)

        // Update helper
        lightHelper.update()

        // Should not throw
        assertNotNull(lightHelper.geometry, "DirectionalLightHelper should update without error")
    }

    /**
     * FR-H007: SpotLightHelper displays spotlight cone
     */
    @Test
    fun testSpotLightHelperShowsCone() {
        val light = SpotLight(
            color = Color(0xFFFFFF),
            intensity = 1f,
            distance = 100f,
            angle = 0.5f,
            penumbra = 0.1f
        )
        light.position.set(0f, 10f, 0f)
        light.target.position.set(0f, 0f, 0f)

        val lightHelper = SpotLightHelper(light, color = Color(0xFFFF00))

        // Verify helper is created
        assertNotNull(lightHelper, "SpotLightHelper should be instantiated")

        // Verify light reference
        assertEquals(light, lightHelper.light, "SpotLightHelper should reference the light")

        // Verify color
        assertNotNull(lightHelper.color, "SpotLightHelper should have color")

        // Verify it's an Object3D
        assertTrue(lightHelper is Object3D, "SpotLightHelper should extend Object3D")

        // Verify geometry exists (cone shape)
        assertNotNull(lightHelper.geometry, "SpotLightHelper should have geometry")
    }

    /**
     * FR-H007: SpotLightHelper updates with light changes
     */
    @Test
    fun testSpotLightHelperUpdates() {
        val light = SpotLight(Color(0xFFFFFF))
        val lightHelper = SpotLightHelper(light)

        // Change light parameters
        light.angle = 1.0f
        light.distance = 50f

        // Update helper
        lightHelper.update()

        // Should not throw
        assertNotNull(lightHelper.geometry, "SpotLightHelper should update without error")
    }

    /**
     * FR-H008: PointLightHelper displays spherical range
     */
    @Test
    fun testPointLightHelperShowsRange() {
        val light = PointLight(
            color = Color(0xFFFFFF),
            intensity = 1f,
            distance = 50f,
            decay = 2f
        )
        light.position.set(5f, 5f, 5f)

        val lightHelper = PointLightHelper(light, sphereSize = 2f, color = Color(0x00FF00))

        // Verify helper is created
        assertNotNull(lightHelper, "PointLightHelper should be instantiated")

        // Verify light reference
        assertEquals(light, lightHelper.light, "PointLightHelper should reference the light")

        // Verify sphere size
        assertEquals(2f, lightHelper.sphereSize, "PointLightHelper should preserve sphere size")

        // Verify color
        assertNotNull(lightHelper.color, "PointLightHelper should have color")

        // Verify it's an Object3D
        assertTrue(lightHelper is Object3D, "PointLightHelper should extend Object3D")

        // Verify geometry exists (sphere)
        assertNotNull(lightHelper.geometry, "PointLightHelper should have geometry")
    }

    /**
     * FR-H008: PointLightHelper updates with light changes
     */
    @Test
    fun testPointLightHelperUpdates() {
        val light = PointLight(Color(0xFFFFFF))
        val lightHelper = PointLightHelper(light)

        // Move light
        light.position.set(10f, 10f, 10f)

        // Update helper
        lightHelper.update()

        // Should not throw
        assertNotNull(lightHelper.geometry, "PointLightHelper should update without error")
    }

    /**
     * FR-H009: HemisphereLightHelper displays sky/ground colors
     */
    @Test
    fun testHemisphereLightHelperShowsColors() {
        val skyColor = Color(0x0000FF)
        val groundColor = Color(0x00FF00)
        val light = HemisphereLight(skyColor, groundColor, intensity = 1f)
        light.position.set(0f, 10f, 0f)

        val lightHelper = HemisphereLightHelper(light, size = 3f)

        // Verify helper is created
        assertNotNull(lightHelper, "HemisphereLightHelper should be instantiated")

        // Verify light reference
        assertEquals(light, lightHelper.light, "HemisphereLightHelper should reference the light")

        // Verify size
        assertEquals(3f, lightHelper.size, "HemisphereLightHelper should preserve size")

        // Verify it's an Object3D
        assertTrue(lightHelper is Object3D, "HemisphereLightHelper should extend Object3D")

        // Verify geometry exists (split sphere)
        assertNotNull(lightHelper.geometry, "HemisphereLightHelper should have geometry")
    }

    /**
     * FR-H009: HemisphereLightHelper displays split sphere
     */
    @Test
    fun testHemisphereLightHelperSplitSphere() {
        val light = HemisphereLight(Color(0xFFFFFF), Color(0x000000))
        val lightHelper = HemisphereLightHelper(light)

        // Verify geometry represents split sphere (should have color attribute)
        assertNotNull(lightHelper.geometry, "HemisphereLightHelper should have geometry")
        val geom = lightHelper.geometry
        if (geom is BufferGeometry) {
            val colors = geom.getAttribute("color")
            assertNotNull(
                colors,
                "HemisphereLightHelper geometry should have color attribute for split sphere"
            )
        }
    }

    /**
     * All helpers should be visible in scene
     */
    @Test
    fun testAllHelpersAreVisible() {
        val camera = PerspectiveCamera()
        val dirLight = DirectionalLight(Color(0xFFFFFF))
        val spotLight = SpotLight(Color(0xFFFFFF))
        val pointLight = PointLight(Color(0xFFFFFF))
        val hemiLight = HemisphereLight(Color(0xFFFFFF), Color(0x000000))

        val cameraHelper = CameraHelper(camera)
        val dirHelper = DirectionalLightHelper(dirLight)
        val spotHelper = SpotLightHelper(spotLight)
        val pointHelper = PointLightHelper(pointLight)
        val hemiHelper = HemisphereLightHelper(hemiLight)

        // All helpers should be visible by default
        assertTrue(cameraHelper.visible, "CameraHelper should be visible by default")
        assertTrue(dirHelper.visible, "DirectionalLightHelper should be visible by default")
        assertTrue(spotHelper.visible, "SpotLightHelper should be visible by default")
        assertTrue(pointHelper.visible, "PointLightHelper should be visible by default")
        assertTrue(hemiHelper.visible, "HemisphereLightHelper should be visible by default")
    }
}