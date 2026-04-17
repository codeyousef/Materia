package io.materia.examples.triangle.android

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors.RgbaType
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

internal class DirectFilamentTriangleRuntime(
    private val surfaceView: SurfaceView
) {

    private companion object {
        private const val TAG = "DirectFilamentTriangle"
        private const val MATERIAL_ASSET = "filament/materials/materia_flat_color_opaque.filamat"
    }

    private val displayHelper = DisplayHelper(surfaceView.context)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private lateinit var material: Material
    private lateinit var vertexBuffer: VertexBuffer
    private lateinit var indexBuffer: IndexBuffer

    private var swapChain: SwapChain? = null
    private var renderable: Int = 0
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1
    private var initialized = false

    private val backend = Engine.Backend.OPENGL

    val backendName: String
        get() = backend.name

    val deviceName: String
        get() = Build.MODEL ?: "Android Device"

    val driverVersion: String
        get() = "Filament ${backend.name}"

    val isReadyToRender: Boolean
        get() = initialized && uiHelper.isReadyToRender && swapChain != null

    fun initialize() {
        if (initialized) return

        Filament.init()
        setupFilament()
        setupScene()
        setupSurfaceHelper()
        if (surfaceView.width > 0 && surfaceView.height > 0) {
            updateViewport(surfaceView.width, surfaceView.height)
        }
        initialized = true
    }

    fun renderFrame(frameTimeNanos: Long) {
        if (!initialized || !uiHelper.isReadyToRender) {
            return
        }

        val currentSwapChain = swapChain ?: return
        if (renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun resize(width: Int, height: Int) {
        updateViewport(width, height)
        uiHelper.setDesiredSize(max(width, 1), max(height, 1))
    }

    fun buildOverlayText(frameTimeMs: Double = 0.0): String = buildString {
        appendLine("🎯 Triangle MVP bootstrap complete")
        appendLine("  Backend : $backendName")
        appendLine("  Device  : $deviceName")
        appendLine("  Driver  : $driverVersion")
        appendLine("  Meshes  : 1")
        appendLine("  Frame   : ${"%.2f".format(frameTimeMs)} ms")
        appendLine("  Camera  : (0.0, 0.0, 4.0)")
    }.trim()

    fun dispose() {
        if (!initialized) return

        uiHelper.detach()
        displayHelper.detach()
        swapChain?.let {
            engine.destroySwapChain(it)
            engine.flushAndWait()
            swapChain = null
        }

        if (renderable != 0) {
            scene.removeEntity(renderable)
            engine.destroyEntity(renderable)
            EntityManager.get().destroy(renderable)
            renderable = 0
        }

        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterial(material)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        EntityManager.get().destroy(camera.entity)
        engine.destroy()
        initialized = false
    }

    private fun setupSurfaceHelper() {
        uiHelper.setOpaque(true)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let(engine::destroySwapChain)
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                surfaceView.display?.let { display ->
                    displayHelper.attach(renderer, display)
                }
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                updateViewport(width, height)
                FilamentHelper.synchronizePendingFrames(engine)
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    private fun setupFilament() {
        engine = Engine.Builder()
            .backend(backend)
            .build()
        renderer = engine.createRenderer().apply {
            clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = floatArrayOf(0.12f, 0.18f, 0.32f, 1f)
            }
        }
        scene = engine.createScene()
        view = engine.createView().apply {
            isPostProcessingEnabled = false
            scene = this@DirectFilamentTriangleRuntime.scene
        }
        camera = engine.createCamera(engine.entityManager.create())
        view.camera = camera
    }

    private fun setupScene() {
        material = loadMaterial(MATERIAL_ASSET).also { loadedMaterial ->
            loadedMaterial.defaultInstance.setDoubleSided(true)
            loadedMaterial.defaultInstance.setCullingMode(Material.CullingMode.NONE)
            loadedMaterial.defaultInstance.setColorWrite(true)
            loadedMaterial.defaultInstance.setDepthWrite(false)
            loadedMaterial.defaultInstance.setDepthCulling(false)
            loadedMaterial.defaultInstance.setParameter("triangleColor", RgbaType.LINEAR, 1.0f, 0.56f, 0.24f, 1.0f)
        }
        createMesh()
        renderable = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(-2f, -2f, -1f, 2f, 2f, 1f))
            .culling(false)
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 3)
            .material(0, material.defaultInstance)
            .build(engine, renderable)
        scene.addEntity(renderable)
    }

    private fun createMesh() {
        val floatSize = Float.SIZE_BYTES
        val shortSize = Short.SIZE_BYTES
        val vertexSize = 3 * floatSize

        val vertexData = allocateBuffer(3 * vertexSize)
            .putFloat(0.0f)
            .putFloat(1.15f)
            .putFloat(0.0f)
            .putFloat(-1.15f)
            .putFloat(-0.95f)
            .putFloat(0.0f)
            .putFloat(1.15f)
            .putFloat(-0.95f)
            .putFloat(0.0f)
            .flip() as ByteBuffer

        vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(3)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, vertexSize)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, vertexData)

        val indexData = allocateBuffer(3 * shortSize)
            .putShort(0)
            .putShort(1)
            .putShort(2)
            .flip() as ByteBuffer

        indexBuffer = IndexBuffer.Builder()
            .indexCount(3)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexData)
    }

    private fun loadMaterial(assetPath: String): Material {
        val payload = surfaceView.context.assets.open(assetPath).use { input ->
            val bytes = input.readBytes()
            allocateBuffer(bytes.size).put(bytes).flip() as ByteBuffer
        }
        return Material.Builder()
            .payload(payload, payload.remaining())
            .build(engine)
            .also { loadedMaterial ->
                loadedMaterial.compile(
                    Material.CompilerPriorityQueue.HIGH,
                    Material.UserVariantFilterBit.ALL,
                    mainHandler,
                    Runnable {}
                )
                engine.flush()
            }
    }

    private fun updateViewport(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        val aspect = viewportWidth.toDouble() / viewportHeight.toDouble()
        val zoom = 1.35
        camera.setProjection(
            Camera.Projection.ORTHO,
            -aspect * zoom,
            aspect * zoom,
            -zoom,
            zoom,
            0.1,
            10.0
        )
        camera.lookAt(0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
    }

    private fun allocateBuffer(size: Int): ByteBuffer =
        ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
}
