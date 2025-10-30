package io.materia.renderer.webgpu

import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Vector3
import io.materia.core.scene.Mesh
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.MeshBasicMaterial
import kotlin.test.Test
import kotlin.test.assertTrue

class FrustumCullerTest {
    @Test
    fun cullerUsesGeometryBoundingSphereForVisibility() {
        val camera = PerspectiveCamera(fov = 60f, aspect = 1f, near = 0.1f, far = 100f).apply {
            position.set(0f, 0f, 5f)
            lookAt(Vector3(0f, 0f, -1f))
            updateMatrixWorld(true)
        }

        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        -20f, -1f, 0f,
                        -20f, 1f, 0f,
                        -21f, 0f, 0f
                    ),
                    3
                )
            )
            computeBoundingSphere()
        }

        val mesh = Mesh(geometry, MeshBasicMaterial()).apply {
            position.set(20f, 0f, -10f)
            updateMatrix()
            updateMatrixWorld()
        }

        val culler = FrustumCuller().apply {
            extractPlanesFromCamera(camera)
        }

        assertTrue(
            culler.isObjectVisible(mesh),
            "Mesh should remain visible because its geometry's bounding sphere intersects the frustum"
        )
    }
}
