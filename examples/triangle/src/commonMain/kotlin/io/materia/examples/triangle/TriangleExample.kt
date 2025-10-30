package io.materia.examples.triangle

import io.materia.engine.camera.PerspectiveCamera
import io.materia.engine.geometry.AttributeSemantic
import io.materia.engine.geometry.AttributeType
import io.materia.engine.geometry.Geometry
import io.materia.engine.geometry.GeometryAttribute
import io.materia.engine.geometry.GeometryLayout
import io.materia.engine.material.BlendMode
import io.materia.engine.material.RenderState
import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.material.UnlitPointsMaterial
import io.materia.engine.math.Color
import io.materia.engine.math.Vec3
import io.materia.engine.render.EngineRenderer
import io.materia.engine.render.EngineRendererOptions
import io.materia.engine.render.createEngineRenderer
import io.materia.engine.scene.Mesh
import io.materia.engine.scene.Scene
import io.materia.engine.scene.VertexBuffer
import io.materia.gpu.GpuBackend
import io.materia.gpu.GpuPowerPreference
import io.materia.renderer.BackendType
import io.materia.renderer.PowerPreference
import io.materia.renderer.RenderSurface
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.TimeSource

data class TriangleBootLog(
    val backend: BackendType,
    val deviceName: String,
    val driverVersion: String,
    val meshCount: Int,
    val cameraPosition: Vec3,
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
    private val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    private val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE
) {

    suspend fun boot(
        renderSurface: RenderSurface? = null,
        widthOverride: Int? = null,
        heightOverride: Int? = null
    ): TriangleBootResult {
        val targetWidth = widthOverride ?: renderSurface?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val targetHeight =
            heightOverride ?: renderSurface?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val aspect = targetWidth.toFloat() / max(1, targetHeight).toFloat()

        val (scene, camera, meshes) = buildScene(aspect)

        if (renderSurface == null) {
            val log = TriangleBootLog(
                backend = preferredBackends.firstOrNull()?.asBackendType() ?: BackendType.WEBGPU,
                deviceName = "Stub Device",
                driverVersion = "n/a",
                meshCount = meshes.size,
                cameraPosition = camera.transform.position.copy(),
                frameTimeMs = 0.0
            )
            return TriangleBootResult(log, null, scene, camera)
        }

        val rendererConfig = RendererConfig(
            preferredBackend = preferredBackends.firstOrNull()?.asBackendType(),
            powerPreference = powerPreference.toRendererPowerPreference()
        )
        val rendererOptions = EngineRendererOptions(
            preferredBackends = preferredBackends,
            powerPreference = powerPreference,
            clearColor = floatArrayOf(0.05f, 0.05f, 0.1f, 1f)
        )

        val engineRenderer = RendererFactory.createEngineRenderer(
            surface = renderSurface,
            config = rendererConfig,
            options = rendererOptions
        ).getOrThrow()

        engineRenderer.resize(targetWidth, targetHeight)

        val mark = TimeSource.Monotonic.markNow()
        engineRenderer.render(scene, camera)
        val frameTimeMs = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0

        val log = TriangleBootLog(
            backend = engineRenderer.backend,
            deviceName = engineRenderer.deviceName,
            driverVersion = engineRenderer.driverVersion,
            meshCount = meshes.size,
            cameraPosition = camera.transform.position.copy(),
            frameTimeMs = frameTimeMs
        )

        return TriangleBootResult(log, engineRenderer, scene, camera)
    }

    private fun buildScene(aspect: Float): Triple<Scene, PerspectiveCamera, List<Mesh>> {
        val triangleGeometry = Geometry(
            vertexBuffer = VertexBuffer(
                data = GPU_TRIANGLE_VERTEX_DATA,
                strideBytes = Float.SIZE_BYTES * GPU_TRIANGLE_COMPONENTS
            ),
            layout = GeometryLayout(
                stride = Float.SIZE_BYTES * GPU_TRIANGLE_COMPONENTS,
                attributes = mapOf(
                    AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32),
                    AttributeSemantic.COLOR to GeometryAttribute(
                        Float.SIZE_BYTES * 3,
                        3,
                        AttributeType.FLOAT32
                    )
                )
            )
        )

        val triangleMaterial = UnlitColorMaterial(
            label = "triangle",
            color = Color.fromFloats(1f, 0.4f, 0.2f),
            renderState = RenderState(depthTest = false, depthWrite = false)
        )

        val triangleMesh = Mesh(
            name = "triangle",
            geometry = triangleGeometry,
            material = triangleMaterial
        )

        val pointsGeometry = Geometry(
            vertexBuffer = VertexBuffer(
                data = GPU_POINTS_VERTEX_DATA,
                strideBytes = Float.SIZE_BYTES * GPU_POINTS_COMPONENTS
            ),
            layout = GeometryLayout(
                stride = Float.SIZE_BYTES * GPU_POINTS_COMPONENTS,
                attributes = mapOf(
                    AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32)
                )
            )
        )

        val pointsMaterial = UnlitPointsMaterial(
            label = "triangle-points",
            renderState = RenderState(
                depthTest = false,
                depthWrite = false,
                blendMode = BlendMode.Additive
            )
        )

        val pointsMesh = Mesh(
            name = "triangle-stars",
            geometry = pointsGeometry,
            material = pointsMaterial
        )

        val scene = Scene("triangle-scene").apply {
            add(triangleMesh)
            add(pointsMesh)
        }

        val camera = PerspectiveCamera(fovDegrees = 60f, aspect = aspect, near = 0.1f, far = 100f)
        camera.transform.setPosition(0f, 0f, 2.5f)
        camera.lookAt(Vec3.Zero)

        return Triple(scene, camera, listOf(triangleMesh, pointsMesh))
    }

    companion object {
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720

        private const val GPU_TRIANGLE_COMPONENTS = 6
        private const val GPU_POINTS_COMPONENTS = 11

        private val GPU_TRIANGLE_VERTEX_DATA = floatArrayOf(
            0f, 0.5f, 0f, 1f, 0.4f, 0.2f,
            -0.5f, -0.5f, 0f, 1f, 0.4f, 0.2f,
            0.5f, -0.5f, 0f, 1f, 0.4f, 0.2f
        )

        private val GPU_POINTS_VERTEX_DATA = floatArrayOf(
            -0.75f, 0.6f, 0f, 0.3f, 0.9f, 0.5f, 1f, 0f, 0f, 0f, 0f,
            0.0f, 0.85f, 0f, 0.6f, 0.9f, 0.2f, 1.2f, 0.1f, 0f, 0f, 0f,
            0.65f, 0.55f, 0f, 0.2f, 0.8f, 1.0f, 0.9f, 0.2f, 0f, 0f, 0f,
            -0.6f, -0.65f, 0f, 0.95f, 0.4f, 0.2f, 1.1f, 0.3f, 0f, 0f, 0f,
            0.7f, -0.7f, 0f, 0.8f, 0.3f, 0.9f, 1.4f, 0.4f, 0f, 0f, 0f
        )
    }
}

class TriangleBootResult(
    val log: TriangleBootLog,
    val renderer: EngineRenderer?,
    val scene: Scene,
    val camera: PerspectiveCamera
) {
    fun renderFrame() {
        renderer?.render(scene, camera)
    }

    fun resize(width: Int, height: Int) {
        renderer?.resize(width, height)
    }

    fun dispose() {
        renderer?.dispose()
    }
}

private fun GpuBackend.asBackendType(): BackendType = when (this) {
    GpuBackend.WEBGPU -> BackendType.WEBGPU
    GpuBackend.VULKAN, GpuBackend.MOLTENVK -> BackendType.VULKAN
}

private fun GpuPowerPreference.toRendererPowerPreference(): PowerPreference = when (this) {
    GpuPowerPreference.LOW_POWER -> PowerPreference.LOW_POWER
    GpuPowerPreference.HIGH_PERFORMANCE -> PowerPreference.HIGH_PERFORMANCE
}
