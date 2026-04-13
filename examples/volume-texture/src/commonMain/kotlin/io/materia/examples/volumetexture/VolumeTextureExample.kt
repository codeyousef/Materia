package io.materia.examples.volumetexture

import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.core.scene.Background
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.primitives.BoxGeometry
import io.materia.geometry.primitives.SphereGeometry
import io.materia.material.MeshBasicMaterial
import io.materia.renderer.BackendType
import io.materia.renderer.PowerPreference
import io.materia.renderer.RenderSurface
import io.materia.renderer.Renderer
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureWrap
import io.materia.texture.Data3DTexture
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.TimeSource

data class VolumeTextureBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val textureResolution: Int,
    val meshCount: Int,
    val triangles: Int,
    val frameTimeMs: Double
) {
    fun pretty(): String = buildString {
        appendLine("🧊 Volume Texture scene ready")
        appendLine("  Backend : $backend")
        appendLine("  Device  : $deviceName")
        appendLine("  Driver  : $driverVersion")
        appendLine("  3D Tex  : ${textureResolution}³")
        appendLine("  Meshes  : $meshCount")
        appendLine("  Tris    : $triangles")
        appendLine("  Frame   : ${frameTimeMs.formatMs()} ms")
    }

    private fun Double.formatMs(): String = ((this * 100.0).roundToInt() / 100.0).toString()
}

data class VolumeTextureBootResult(
    val log: VolumeTextureBootLog,
    val runtime: VolumeTextureRuntime
)

class VolumeTextureExample(
    private val preferredBackend: BackendType? = null,
    private val textureResolution: Int = 24
) {

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): VolumeTextureBootResult {
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val targetHeight =
            heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val sceneBundle = buildScene(targetWidth.toFloat() / max(1, targetHeight).toFloat())

        if (renderSurface == null) {
            val fallbackBackend = preferredBackend ?: BackendType.WEBGPU
            val log = VolumeTextureBootLog(
                backend = fallbackBackend,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                textureResolution = textureResolution,
                meshCount = 2,
                triangles = 12 + 24 * 18 * 2,
                frameTimeMs = 0.0
            )
            return VolumeTextureBootResult(
                log = log,
                runtime = VolumeTextureRuntime(
                    renderer = null,
                    scene = sceneBundle.scene,
                    camera = sceneBundle.camera,
                    primaryMesh = sceneBundle.primaryMesh,
                    accentMesh = sceneBundle.accentMesh
                )
            )
        }

        val renderer = RendererFactory.create(
            surface = renderSurface,
            config = RendererConfig(
                preferredBackend = preferredBackend,
                enableValidation = true,
                vsync = true,
                msaaSamples = 1,
                powerPreference = PowerPreference.HIGH_PERFORMANCE
            )
        ).getOrThrow()

        renderer.resize(targetWidth, targetHeight)
        sceneBundle.camera.aspect = targetWidth.toFloat() / max(1, targetHeight).toFloat()
        sceneBundle.camera.updateProjectionMatrix()

        val mark = TimeSource.Monotonic.markNow()
        renderer.render(sceneBundle.scene, sceneBundle.camera)
        val frameTimeMs = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
        val stats = renderer.stats
        val capabilities = renderer.capabilities

        val log = VolumeTextureBootLog(
            backend = renderer.backend,
            deviceName = capabilities.deviceName,
            driverVersion = capabilities.driverVersion,
            textureResolution = textureResolution,
            meshCount = 2,
            triangles = stats.triangles,
            frameTimeMs = frameTimeMs
        )

        return VolumeTextureBootResult(
            log = log,
            runtime = VolumeTextureRuntime(
                renderer = renderer,
                scene = sceneBundle.scene,
                camera = sceneBundle.camera,
                primaryMesh = sceneBundle.primaryMesh,
                accentMesh = sceneBundle.accentMesh
            )
        )
    }

    private fun buildScene(aspect: Float): SceneBundle {
        val volumeTexture = createVolumeTexture(textureResolution)

        val primaryMaterial = MeshBasicMaterial().apply {
            color = Color(0xFFFFFF)
            map = volumeTexture
        }
        val accentMaterial = MeshBasicMaterial().apply {
            color = Color(0xFFFFFF)
            map = volumeTexture
        }

        val primaryMesh = Mesh(BoxGeometry(2f, 2f, 2f), primaryMaterial).apply {
            name = "volume-cube"
            position.set(-1.35f, 0f, 0f)
        }
        val accentMesh = Mesh(SphereGeometry(0.95f, 24, 18), accentMaterial).apply {
            name = "volume-sphere"
            position.set(1.55f, 0.15f, 0f)
        }

        val scene = Scene().apply {
            background = Background.Color(Color(0x07131A))
            add(primaryMesh)
            add(accentMesh)
        }

        val camera = PerspectiveCamera(
            fov = 55f,
            aspect = aspect,
            near = 0.1f,
            far = 100f
        ).apply {
            position.set(3.9f, 2.35f, 5.8f)
            lookAt(Vector3(0f, 0.15f, 0f))
        }

        return SceneBundle(scene, camera, primaryMesh, accentMesh)
    }

    private fun createVolumeTexture(size: Int): Data3DTexture {
        val data = ByteArray(size * size * size * 4)
        val maxIndex = (size - 1).coerceAtLeast(1).toFloat()
        var offset = 0

        for (z in 0 until size) {
            val nz = z.toFloat() / maxIndex
            for (y in 0 until size) {
                val ny = y.toFloat() / maxIndex
                for (x in 0 until size) {
                    val nx = x.toFloat() / maxIndex
                    val checker = if (((x / 4) + (y / 4) + (z / 4)) % 2 == 0) 0.16f else -0.08f
                    val radial = radialFalloff(nx, ny, nz)

                    val red = (0.12f + nx * 0.82f + checker).coerceIn(0f, 1f)
                    val green = (0.08f + ny * 0.72f + radial * 0.18f).coerceIn(0f, 1f)
                    val blue = (0.18f + nz * 0.62f + checker * 0.4f + radial * 0.24f)
                        .coerceIn(0f, 1f)

                    data[offset++] = channel(red)
                    data[offset++] = channel(green)
                    data[offset++] = channel(blue)
                    data[offset++] = 255.toByte()
                }
            }
        }

        return Data3DTexture(
            data = data,
            width = size,
            height = size,
            depth = size,
            textureName = "VolumeTextureExample"
        ).apply {
            magFilter = TextureFilter.LINEAR
            minFilter = TextureFilter.LINEAR
            wrapS = TextureWrap.CLAMP_TO_EDGE
            wrapT = TextureWrap.CLAMP_TO_EDGE
            wrapR = TextureWrap.CLAMP_TO_EDGE
        }
    }

    private fun radialFalloff(nx: Float, ny: Float, nz: Float): Float {
        val dx = nx - 0.5f
        val dy = ny - 0.5f
        val dz = nz - 0.5f
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        return (1f - distance / 0.87f).coerceIn(0f, 1f)
    }

    private fun channel(value: Float): Byte = (value * 255f).roundToInt().coerceIn(0, 255).toByte()

    private data class SceneBundle(
        val scene: Scene,
        val camera: PerspectiveCamera,
        val primaryMesh: Mesh,
        val accentMesh: Mesh
    )

    private companion object {
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
    }
}

class VolumeTextureRuntime(
    private val renderer: Renderer?,
    val scene: Scene,
    val camera: PerspectiveCamera,
    private val primaryMesh: Mesh,
    private val accentMesh: Mesh
) {
    private var elapsedSeconds = 0f

    fun frame(deltaSeconds: Float) {
        elapsedSeconds += deltaSeconds

        primaryMesh.rotation.y = elapsedSeconds * 0.75f
        primaryMesh.rotation.x = 0.2f + sin(elapsedSeconds * 0.6f) * 0.18f

        accentMesh.rotation.x = -elapsedSeconds * 0.55f
        accentMesh.rotation.y = elapsedSeconds * 0.32f
        accentMesh.position.y = 0.15f + sin(elapsedSeconds * 1.35f) * 0.35f

        camera.position.x = 3.9f + sin(elapsedSeconds * 0.2f) * 0.55f
        camera.lookAt(Vector3(0f, 0.15f, 0f))

        renderer?.render(scene, camera)
    }

    fun resize(width: Int, height: Int) {
        camera.aspect = width.toFloat() / max(1, height).toFloat()
        camera.updateProjectionMatrix()
        renderer?.resize(width, height)
    }

    fun dispose() {
        renderer?.dispose()
    }
}