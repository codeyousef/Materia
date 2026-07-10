package io.materia.controls

import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrbitControlsStabilityTest {

    @Test
    fun guidedPoseIsIndependentOfFrameRate() {
        val sixtyFps = createControls()
        val thirtyFps = createControls()
        val endPosition = Vector3(4f, 6f, 8f)
        val endTarget = Vector3(1f, 2f, 0f)

        sixtyFps.moveTo(endPosition, endTarget, duration = 0.6f)
        thirtyFps.moveTo(endPosition, endTarget, duration = 0.6f)

        repeat(36) { sixtyFps.update(1f / 60f) }
        repeat(18) { thirtyFps.update(1f / 30f) }

        assertVectorEquals(endPosition, sixtyFps.camera.position)
        assertVectorEquals(endPosition, thirtyFps.camera.position)
        assertVectorEquals(endTarget, sixtyFps.target)
        assertVectorEquals(endTarget, thirtyFps.target)
        assertTrue(sixtyFps.isSettled())
        assertTrue(thirtyFps.isSettled())
    }

    @Test
    fun releasedOrbitSettlesWithinConfiguredWindow() {
        val controls = createControls(
            ControlsConfig(dampingTime = 0.04f, settleEpsilon = 0.0001f)
        )

        controls.onPointerDown(0f, 0f, PointerButton.PRIMARY)
        controls.onPointerMove(160f, 40f, PointerButton.PRIMARY)
        controls.onPointerUp(160f, 40f, PointerButton.PRIMARY)

        assertFalse(controls.isSettled())
        repeat(8) { controls.update(0.05f) }

        assertTrue(controls.isSettled(), "Orbit motion should settle within 350-400 ms")
        val settledPosition = controls.camera.position.clone()
        repeat(4) { controls.update(0.05f) }
        assertVectorEquals(settledPosition, controls.camera.position)
    }

    @Test
    fun pointerInputInterruptsGuidedPoseWithoutJumping() {
        val controls = createControls()
        controls.moveTo(Vector3(8f, 8f, 8f), Vector3(2f, 1f, 0f), duration = 1f)
        controls.update(0.2f)
        val interruptedPosition = controls.camera.position.clone()

        controls.onPointerDown(20f, 20f, PointerButton.PRIMARY)
        controls.update(0.05f)

        assertVectorEquals(interruptedPosition, controls.camera.position)
        assertTrue(controls.isSettled())
    }

    @Test
    fun largeFrameDeltaDoesNotCompleteTransition() {
        val controls = createControls(ControlsConfig(maxDeltaTime = 0.05f))
        controls.moveTo(Vector3(10f, 5f, 10f), Vector3(), duration = 1f)

        controls.update(5f)

        assertTrue(controls.camera.position.x < 1f)
        assertFalse(controls.isSettled())
    }

    private fun createControls(config: ControlsConfig = ControlsConfig()): OrbitControls {
        val camera = PerspectiveCamera(50f, 16f / 9f, 0.1f, 100f)
        camera.position.set(0f, 5f, 10f)
        camera.lookAt(Vector3())
        return OrbitControls(camera, config)
    }

    private fun assertVectorEquals(expected: Vector3, actual: Vector3) {
        assertEquals(expected.x, actual.x, 0.0005f)
        assertEquals(expected.y, actual.y, 0.0005f)
        assertEquals(expected.z, actual.z, 0.0005f)
    }
}
