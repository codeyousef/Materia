package io.kreekt.helpers

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.helper.CameraHelper
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
