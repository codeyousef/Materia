package io.materia.renderer

import io.materia.camera.PerspectiveCamera
import io.materia.core.scene.Scene
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private data class SimulatedBackend(
    val type: BackendType,
    val capabilities: RendererCapabilities
)

private class SimulatedRenderer(
    override val backend: BackendType,
    override val capabilities: RendererCapabilities
) : Renderer {

    private var disposed = false
    private var renderCount = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    override val stats: RenderStats
        get() = RenderStats(
            fps = 120.0,
            frameTime = 8.3,
            triangles = renderCount * 1_000,
            drawCalls = renderCount,
            textureMemory = 32L * 1024 * 1024,
            bufferMemory = 16L * 1024 * 1024,
            timestamp = 1_000L * renderCount
        )

    override suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit> {
        return io.materia.core.Result.Success(Unit)
    }

    override fun render(scene: Scene, camera: io.materia.camera.Camera) {
        check(!disposed) { "Renderer has been disposed" }
        renderCount += 1
    }

    override fun resize(width: Int, height: Int) {
        check(!disposed) { "Renderer has been disposed" }
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun dispose() {
        disposed = true
    }

    fun surfaceDimensions(): Pair<Int, Int> = surfaceWidth to surfaceHeight
}

private object SimulatedRendererFactory {
    fun detectAvailableBackends(backends: List<SimulatedBackend>): List<BackendType> =
        backends.map { it.type }

    fun create(
        backends: List<SimulatedBackend>,
        preferred: BackendType? = null
    ): io.materia.core.Result<Renderer> {
        val selected = when {
            preferred != null -> backends.firstOrNull { it.type == preferred }
            else -> backends.firstOrNull()
        } ?: return io.materia.core.Result.Error(
            "No graphics backend available",
            RendererInitializationException.NoGraphicsSupportException(
                platform = "Simulated",
                availableBackends = emptyList(),
                requiredFeatures = listOf("WebGPU", "Vulkan")
            )
        )

        return io.materia.core.Result.Success(
            SimulatedRenderer(
                selected.type,
                selected.capabilities
            )
        )
    }
}

class RendererContractSuiteTest {

    private lateinit var availableBackends: List<SimulatedBackend>
    private lateinit var scene: Scene
    private lateinit var camera: PerspectiveCamera

    @BeforeTest
    fun setUp() {
        availableBackends = listOf(
            SimulatedBackend(
                type = BackendType.WEBGPU,
                capabilities = RendererCapabilities(
                    backend = BackendType.WEBGPU,
                    deviceName = "Simulated GPU",
                    driverVersion = "1.0",
                    supportsCompute = true,
                    supportsRayTracing = false,
                    supportsMultisampling = true,
                    maxTextureSize = 8192,
                    maxVertexAttributes = 24
                )
            ),
            SimulatedBackend(
                type = BackendType.WEBGL,
                capabilities = RendererCapabilities(
                    backend = BackendType.WEBGL,
                    deviceName = "Simulated WebGL",
                    driverVersion = "1.0",
                    supportsCompute = false,
                    supportsRayTracing = false,
                    supportsMultisampling = false,
                    maxTextureSize = 4096,
                    maxVertexAttributes = 16
                )
            )
        )
        scene = Scene()
        camera = PerspectiveCamera(fov = 60f, aspect = 16f / 9f, near = 0.1f, far = 1000f)
    }

    @Test
    fun detectAvailableBackendsReturnsConfiguredList() {
        val detected = SimulatedRendererFactory.detectAvailableBackends(availableBackends)

        assertEquals(2, detected.size)
        assertTrue(detected.contains(BackendType.WEBGPU))
        assertTrue(detected.contains(BackendType.WEBGL))
    }

    @Test
    fun rendererCapabilitiesMeetMinimumThresholds() {
        val result = SimulatedRendererFactory.create(availableBackends)
        val renderer = result.getOrThrow()
        val caps = renderer.capabilities

        assertTrue(caps.maxTextureSize >= 2048)
        assertTrue(caps.maxVertexAttributes >= 16)
        assertTrue(caps.deviceName.isNotBlank())
        assertEquals(renderer.backend, caps.backend)
    }

    @Test
    fun rendererLifecycleUpdatesState() {
        val renderer =
            SimulatedRendererFactory.create(availableBackends).getOrThrow() as SimulatedRenderer

        renderer.resize(1280, 720)
        assertEquals(1280 to 720, renderer.surfaceDimensions())

        renderer.render(scene, camera)
        renderer.render(scene, camera)

        val stats = renderer.stats
        assertTrue(stats.fps >= 0)
        assertTrue(stats.drawCalls >= 0)

        renderer.dispose()
        assertFailsWith<IllegalStateException> {
            renderer.render(scene, camera)
        }
    }

    @Test
    fun rendererRespectsPreferredBackend() {
        val renderer = SimulatedRendererFactory.create(
            availableBackends,
            preferred = BackendType.WEBGL
        ).getOrThrow()

        assertEquals(BackendType.WEBGL, renderer.backend)
    }

    @Test
    fun rendererFactoryFailsWhenNoBackendAvailable() {
        val result = SimulatedRendererFactory.create(emptyList())

        assertTrue(result is io.materia.core.Result.Error)
        val exception = (result as io.materia.core.Result.Error).exception
        assertTrue(exception is RendererInitializationException.NoGraphicsSupportException)
    }

    @Test
    fun statsProvidePerformanceSummary() {
        val renderer = SimulatedRendererFactory.create(availableBackends).getOrThrow()
        renderer.render(scene, camera)

        val stats = renderer.stats
        val summary = stats.toDetailedString()

        assertTrue(summary.contains("Performance Stats"))
        assertTrue(summary.contains("FPS"))
        assertTrue(summary.contains("Triangles"))
    }

    @Test
    fun initializationExceptionsCarryTroubleshootingInfo() {
        val exception = RendererInitializationException.DeviceCreationFailedException(
            backend = BackendType.WEBGPU,
            adapterInfo = "Simulated GPU",
            reason = "Driver update required"
        )

        assertTrue(exception.message?.contains("Driver") == true)
        assertEquals(BackendType.WEBGPU, exception.backend)
    }
}
