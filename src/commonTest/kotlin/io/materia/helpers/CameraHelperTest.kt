package io.materia.helpers

import io.materia.camera.PerspectiveCamera
import io.materia.helper.CameraHelper
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class CameraHelperTest {
    @Test
    fun testCameraHelperCreation() {
        val camera = PerspectiveCamera()
        val helper = CameraHelper(camera)
        assertNotNull(helper)
    }
}
