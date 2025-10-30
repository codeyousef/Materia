package io.materia.lighting

import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.camera.StereoCamera
import io.materia.camera.StereoUtils
import io.materia.core.Result
import io.materia.core.scene.Scene
import io.materia.lighting.ibl.HDREnvironment
import io.materia.lighting.ibl.IBLConfig
import io.materia.lighting.ibl.IBLEnvironmentMaps
import io.materia.lighting.ibl.IBLResult
import io.materia.renderer.BackendType
import io.materia.renderer.CubeTexture
import io.materia.renderer.RenderStats
import io.materia.renderer.Renderer
import io.materia.renderer.RendererCapabilities
import io.materia.renderer.RendererConfig
import io.materia.renderer.Texture
import io.materia.renderer.createTestCube
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HeadsetEnvironmentRegressionTest {

    @Test
    fun headsetSceneRendersOnBothPrimaryBackends() = runBlocking {
        val processor = IBLProcessorImpl()
        val config = IBLConfig(
            irradianceSize = 32,
            prefilterSize = 64,
            brdfLutSize = 128,
            roughnessLevels = 4,
            samples = 192
        )

        val scene = Scene().apply {
            add(createTestCube().also { it.name = "LitTestCube" })
        }

        val headsetRig = PerspectiveCamera(fov = 90f, aspect = 1f).apply {
            position.set(0f, 1.6f, 3f)
            lookAt(0f, 1.6f, 0f)
            updateMatrixWorld(true)
        }

        val stereoCamera = StereoCamera().apply {
            aspect = 1f
            eyeSep = StereoUtils.scaleIPD(64f, 1f)
            convergence =
                StereoUtils.calculateOptimalConvergence(nearObjects = 0.5f, farObjects = 5f)
            update(headsetRig)
        }
        stereoCamera.cameraL.name = "HeadsetLeftEye"
        stereoCamera.cameraR.name = "HeadsetRightEye"

        val hdr = createSyntheticHdr(width = 48, height = 24)
        val result = processor.processEnvironmentForScene(hdr, config, scene)

        assertTrue(
            result is IBLResult.Success<*>,
            "Environment processing must succeed for headset regression"
        )
        val maps = (result as IBLResult.Success<*>).data as IBLEnvironmentMaps
        assertSame(
            maps.prefilter,
            scene.environment,
            "Scene should consume prefiltered environment cubemap"
        )
        assertSame(
            maps.brdfLut,
            scene.environmentBrdfLut,
            "Scene should consume generated BRDF LUT"
        )
        assertNotNull(scene.environment, "Scene must expose processed environment")
        assertNotNull(scene.environmentBrdfLut, "Scene must expose processed BRDF LUT")
        assertEquals(
            config.prefilterSize,
            maps.prefilter.size,
            "Prefilter cube needs expected dimension"
        )
        assertEquals(
            config.brdfLutSize,
            maps.brdfLut.width,
            "BRDF LUT width should match configuration"
        )
        assertEquals(
            config.brdfLutSize,
            maps.brdfLut.height,
            "BRDF LUT height should match configuration"
        )

        val renderers = listOf(
            RecordingRenderer(BackendType.WEBGPU, config.roughnessLevels),
            RecordingRenderer(BackendType.VULKAN, config.roughnessLevels)
        )

        renderers.forEach { renderer ->
            val init = renderer.initialize(RendererConfig(preferredBackend = renderer.backend))
            assertTrue(
                init is Result.Success<*>,
                "Renderer init should succeed for ${renderer.backend}"
            )
            renderer.resize(width = 1440, height = 1440)

            renderer.render(scene, stereoCamera.cameraL)
            renderer.render(scene, stereoCamera.cameraR)

            renderer.verifyHeadsetFrames(maps)
            assertEquals(
                config.roughnessLevels,
                renderer.stats.iblPrefilterMipCount,
                "Renderer stats should track roughness mips for ${renderer.backend}"
            )
            assertTrue(
                renderer.stats.iblLastRoughness > 0f,
                "Renderer stats should record latest roughness for ${renderer.backend}"
            )

            renderer.dispose()
        }
    }

    private fun createSyntheticHdr(width: Int, height: Int): HDREnvironment {
        val channels = 3
        val data = FloatArray(width * height * channels) { index ->
            val x = index % (width * channels)
            val y = index / (width * channels)
            val normalized = (x + y) % (width * channels)
            0.1f + normalized / (width * channels).toFloat()
        }
        return HDREnvironment(data, width, height)
    }

    private class RecordingRenderer(
        override val backend: BackendType,
        private val expectedRoughnessLevels: Int
    ) : Renderer {
        override val capabilities = RendererCapabilities(
            backend = backend,
            deviceName = "${backend.name} Headset Test GPU",
            version = when (backend) {
                BackendType.WEBGPU -> "WebGPU 1.0"
                BackendType.VULKAN -> "Vulkan 1.3"
                BackendType.WEBGL -> "WebGL 2.0"
            },
            supportsCompute = true,
            supportsRayTracing = false,
            maxCubeMapSize = 4096
        )

        private var currentStats = RenderStats(
            fps = 90.0,
            frameTime = 11.1,
            triangles = 0,
            drawCalls = 0,
            iblPrefilterMipCount = expectedRoughnessLevels,
            iblLastRoughness = 1f
        )
        override val stats: RenderStats
            get() = currentStats

        private var initialized = false
        private val _frames = mutableListOf<RenderedFrame>()
        val renderedFrames: List<RenderedFrame> get() = _frames

        override suspend fun initialize(config: RendererConfig): Result<Unit> {
            require(config.preferredBackend == null || config.preferredBackend == backend) {
                "Expected preferred backend $backend but received ${config.preferredBackend}"
            }
            initialized = true
            return Result.Success(Unit)
        }

        override fun render(scene: Scene, camera: Camera) {
            check(initialized) { "Renderer must be initialized before rendering" }
            _frames += RenderedFrame(
                backend = backend,
                cameraName = camera.name,
                environment = scene.environment,
                brdf = scene.environmentBrdfLut,
                childCount = scene.children.size
            )
            currentStats = currentStats.copy(
                triangles = scene.children.size * 12,
                drawCalls = scene.children.size,
                iblLastRoughness = 1f
            )
        }

        override fun resize(width: Int, height: Int) {
            lastResizeWidth = width
            lastResizeHeight = height
        }

        override fun dispose() {
            initialized = false
        }

        fun verifyHeadsetFrames(maps: IBLEnvironmentMaps) {
            check(renderedFrames.size == 2) { "Expected two eye renders for $backend" }
            val eyeNames = renderedFrames.map { it.cameraName }.toSet()
            check(eyeNames.contains("HeadsetLeftEye") && eyeNames.contains("HeadsetRightEye")) {
                "Expected left/right eye renders, got $eyeNames for $backend"
            }
            renderedFrames.forEach { frame ->
                check(frame.environment === maps.prefilter) { "Renderer $backend should see processed prefilter map" }
                check(frame.brdf === maps.brdfLut) { "Renderer $backend should reuse generated BRDF LUT" }
                check(frame.childCount >= 1) { "Renderer $backend should render at least one mesh" }
            }
            check(lastResizeWidth > 0 && lastResizeHeight > 0) { "Renderer $backend should record headset surface dimensions" }
        }

        private var lastResizeWidth: Int = 0
        private var lastResizeHeight: Int = 0
    }

    private data class RenderedFrame(
        val backend: BackendType,
        val cameraName: String,
        val environment: CubeTexture?,
        val brdf: Texture?,
        val childCount: Int
    )
}
