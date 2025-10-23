package io.kreekt.examples.triangle

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.Color
import io.kreekt.core.math.Vector3
import io.kreekt.core.scene.Background
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.PowerPreference
import io.kreekt.renderer.RenderSurface
import io.kreekt.renderer.Renderer
import io.kreekt.renderer.RendererConfig
import io.kreekt.renderer.RendererFactory
import kotlin.math.max
import kotlin.math.roundToInt

data class TriangleBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val meshCount: Int,
    val cameraPosition: Vector3,
    val frameTimeMs: Double
) {
    fun pretty(): String = buildString {
        appendLine("🎯 Triangle MVP bootstrap complete")
        appendLine("  Backend : $backend")
        appendLine("  Device  : $deviceName")
        appendLine("  Driver  : $driverVersion")
        appendLine("  Meshes  : $meshCount")
        appendLine("  Frame   : ${frameTimeMs.asMsString()} ms")
        appendLine(
            "  Camera  : (${cameraPosition.x.format2f()}, " +
                "${cameraPosition.y.format2f()}, ${cameraPosition.z.format2f()})"
        )
    }

    private fun Double.asMsString(): String = ((this * 100).roundToInt() / 100.0).toString()
    private fun Float.format2f(): String = ((this * 100f).roundToInt() / 100f).toString()
}

class TriangleExample(
    private val preferredBackends: List<BackendType> = listOf(BackendType.WEBGPU),
    private val powerPreference: PowerPreference = PowerPreference.HIGH_PERFORMANCE
) {
    private var renderer: Renderer? = null
    private var activeScene: Scene? = null
    private var activeCamera: PerspectiveCamera? = null
    private var triangleMesh: Mesh? = null

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): TriangleBootLog {
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val targetHeight = heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val aspect = targetWidth.toFloat() / max(1, targetHeight).toFloat()

        val (scene, camera, mesh) = buildScene(aspect)
        val meshCount = scene.children.count { it is Mesh }

        triangleMesh = mesh
        activeScene = scene
        activeCamera = camera

        if (renderSurface == null) {
            return TriangleBootLog(
                backend = preferredBackends.firstOrNull() ?: BackendType.WEBGPU,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                meshCount = meshCount,
                cameraPosition = camera.position.clone(),
                frameTimeMs = 0.0
            )
        }

        renderer?.dispose()
        val config = RendererConfig(
            preferredBackend = preferredBackends.firstOrNull(),
            powerPreference = powerPreference
        )

        val resolvedRenderer = RendererFactory.create(renderSurface, config).getOrThrow()
        resolvedRenderer.initialize(config).getOrThrow()
        if (targetHeight > 0) {
            camera.aspect = targetWidth.toFloat() / targetHeight.toFloat()
            camera.updateProjectionMatrix()
        }
        resolvedRenderer.resize(targetWidth, targetHeight)

        scene.updateMatrixWorld(true)
        resolvedRenderer.render(scene, camera)

        renderer = resolvedRenderer

        val capabilities = resolvedRenderer.capabilities
        val stats = resolvedRenderer.stats

        return TriangleBootLog(
            backend = capabilities.backend,
            deviceName = capabilities.deviceName,
            driverVersion = capabilities.driverVersion,
            meshCount = meshCount,
            cameraPosition = camera.position.clone(),
            frameTimeMs = stats.frameTime
        )
    }

    fun renderFrame(deltaSeconds: Float = 0f) {
        val renderer = renderer ?: return
        val scene = activeScene ?: return
        val camera = activeCamera ?: return

        triangleMesh?.let { mesh ->
            if (deltaSeconds > 0f) {
                mesh.rotation.y += deltaSeconds * ROTATION_SPEED
            }
        }

        scene.updateMatrixWorld(true)
        renderer.render(scene, camera)
    }

    fun resize(width: Int, height: Int) {
        val renderer = renderer ?: return
        val camera = activeCamera ?: return
        if (width <= 0 || height <= 0) return

        camera.aspect = width.toFloat() / height.toFloat()
        camera.updateProjectionMatrix()
        renderer.resize(width, height)
    }

    fun shutdown() {
        renderer?.dispose()
        renderer = null
        activeScene = null
        activeCamera = null
        triangleMesh = null
    }

    private fun buildScene(aspect: Float): Triple<Scene, PerspectiveCamera, Mesh> {
        val geometry = BufferGeometry().apply {
            setAttribute("position", BufferAttribute(TRIANGLE_VERTICES.copyOf(), TRIANGLE_COMPONENTS))
        }

        val material = MeshBasicMaterial().apply {
            color = Color(1f, 0.4f, 0.2f)
            toneMapped = false
        }

        val mesh = Mesh(geometry, material).apply {
            name = "TriangleMesh"
        }

        val scene = Scene().apply {
            name = "TriangleScene"
            background = Background.Color(Color(0.05f, 0.05f, 0.1f, 1f))
            add(mesh)
        }

        val camera = PerspectiveCamera(fov = 60f, aspect = aspect).apply {
            position.set(0f, 0f, 2f)
            lookAt(Vector3(0f, 0f, 0f))
        }
        scene.add(camera)
        scene.updateMatrixWorld(true)
        camera.updateMatrixWorld(true)

        return Triple(scene, camera, mesh)
    }

    companion object {
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
        private const val TRIANGLE_COMPONENTS = 3
        private const val ROTATION_SPEED = 0.6f

        private val TRIANGLE_VERTICES = floatArrayOf(
            0f, 0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f
        )
    }
}
