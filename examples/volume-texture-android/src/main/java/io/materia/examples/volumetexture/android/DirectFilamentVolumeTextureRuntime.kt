package io.materia.examples.volumetexture.android

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera as FilamentCamera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material as FilamentMaterial
import com.google.android.filament.Material.CullingMode
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.Renderer
import com.google.android.filament.Scene as FilamentScene
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Color
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.core.scene.Background
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.material.MeshBasicMaterial
import io.materia.material.Side
import io.materia.texture.Data3DTexture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

internal class DirectFilamentVolumeTextureRuntime(
    private val surfaceView: SurfaceView,
    private val sourceScene: Scene,
    private val sourceCamera: PerspectiveCamera
) {

    private companion object {
        private const val MATERIAL_ASSET = "filament/materials/materia_opaque_color.filamat"
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_BYTES = Int.SIZE_BYTES
        private const val VERTEX_STRIDE_BYTES = POSITION_COMPONENTS * Float.SIZE_BYTES + COLOR_BYTES
        private const val MAX_UNSIGNED_SHORT = 65_535
    }

    private val displayHelper = DisplayHelper(surfaceView.context)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val meshHandles = mutableListOf<MeshHandle>()

    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var filamentScene: FilamentScene
    private lateinit var view: View
    private lateinit var filamentCamera: FilamentCamera
    private lateinit var material: FilamentMaterial

    private var swapChain: SwapChain? = null
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

    fun initialize() {
        if (initialized) return

        Filament.init()
        sourceScene.updateMatrixWorld(force = true)
        sourceCamera.updateProjectionMatrix()
        sourceCamera.updateMatrixWorld(force = true)

        setupFilament()
        material = loadMaterial(MATERIAL_ASSET)
        buildScene()
        syncSceneState()
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
        syncSceneState()

        if (renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun resize(width: Int, height: Int) {
        updateViewport(width, height)
        uiHelper.setDesiredSize(max(width, 1), max(height, 1))
    }

    fun buildOverlayText(textureResolution: Int, meshCount: Int): String = buildString {
        appendLine("Volume Texture Android ready")
        appendLine("Backend : $backendName")
        appendLine("Device  : $deviceName")
        appendLine("Driver  : $driverVersion")
        appendLine("3D Tex  : ${textureResolution}^3")
        appendLine("Meshes  : $meshCount")
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

        meshHandles.forEach { handle ->
            filamentScene.removeEntity(handle.entity)
            engine.destroyEntity(handle.entity)
            EntityManager.get().destroy(handle.entity)
            engine.destroyVertexBuffer(handle.vertexBuffer)
            engine.destroyIndexBuffer(handle.indexBuffer)
            engine.destroyMaterialInstance(handle.materialInstance)
        }
        meshHandles.clear()

        engine.destroyMaterial(material)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(filamentScene)
        engine.destroyCameraComponent(filamentCamera.entity)
        EntityManager.get().destroy(filamentCamera.entity)
        engine.destroy()
        initialized = false
    }

    private fun setupFilament() {
        engine = Engine.Builder()
            .backend(backend)
            .build()
        renderer = engine.createRenderer()
        filamentScene = engine.createScene()
        view = engine.createView().apply {
            isPostProcessingEnabled = false
            scene = filamentScene
        }
        filamentCamera = engine.createCamera(engine.entityManager.create())
        view.camera = filamentCamera
    }

    private fun buildScene() {
        sourceScene.traverseVisible { node ->
            if (node is Mesh) {
                createMeshHandle(node)?.let(meshHandles::add)
            }
        }
    }

    private fun createMeshHandle(mesh: Mesh): MeshHandle? {
        val meshData = buildMeshData(mesh) ?: return null
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(meshData.vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, VERTEX_STRIDE_BYTES)
            .attribute(
                VertexAttribute.COLOR,
                0,
                AttributeType.UBYTE4,
                POSITION_COMPONENTS * Float.SIZE_BYTES,
                VERTEX_STRIDE_BYTES
            )
            .normalized(VertexAttribute.COLOR)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, meshData.vertexData)

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(meshData.indexCount)
            .bufferType(meshData.indexType)
            .build(engine)
        indexBuffer.setBuffer(engine, meshData.indexData)

        val materialInstance = material.createInstance().apply {
            setDoubleSided(meshData.side == Side.DoubleSide)
            setCullingMode(meshData.side.toFilamentCulling())
            setColorWrite(true)
            setDepthWrite(true)
            setDepthCulling(true)
        }

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(meshData.boundingBox)
            .culling(false)
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, meshData.indexCount)
            .material(0, materialInstance)
            .build(engine, entity)
        filamentScene.addEntity(entity)

        return MeshHandle(
            sourceMesh = mesh,
            entity = entity,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            materialInstance = materialInstance
        )
    }

    private fun buildMeshData(mesh: Mesh): MeshData? {
        val material = mesh.material as? MeshBasicMaterial ?: return null
        val geometry = mesh.geometry
        val positions = geometry.getAttribute("position") ?: return null
        if (positions.itemSize < 3 || positions.count == 0) {
            return null
        }

        val volumeTexture = material.map as? Data3DTexture
        val volumeSampler = volumeTexture?.let(VolumeSampler::from)
        val colorAttribute = geometry.getAttribute("color")
        val baseAlpha = material.opacity.coerceIn(0f, 1f)

        val vertexData = allocateBuffer(positions.count * VERTEX_STRIDE_BYTES)
        for (index in 0 until positions.count) {
            val x = positions.getX(index)
            val y = positions.getY(index)
            val z = positions.getZ(index)
            vertexData.putFloat(x)
            vertexData.putFloat(y)
            vertexData.putFloat(z)

            var red = material.color.r
            var green = material.color.g
            var blue = material.color.b
            var alpha = baseAlpha

            if (colorAttribute != null && colorAttribute.itemSize >= 3) {
                red *= colorAttribute.getX(index)
                green *= colorAttribute.getY(index)
                blue *= colorAttribute.getZ(index)
                if (colorAttribute.itemSize >= 4) {
                    alpha *= colorAttribute.getW(index)
                }
            }

            if (volumeSampler != null) {
                val sample = volumeSampler.sampleLocalPosition(x, y, z)
                red *= sample.r
                green *= sample.g
                blue *= sample.b
                alpha *= sample.a
            }

            writeColor(vertexData, red, green, blue, alpha)
        }
        vertexData.flip()

        val sourceIndex = geometry.index
        val generatedIndices = buildIndexValues(sourceIndex, positions)
        if (generatedIndices.isEmpty()) {
            return null
        }
        val useUint32 = generatedIndices.maxOrNull()?.let { it > MAX_UNSIGNED_SHORT } ?: false
        val indexData = allocateBuffer(generatedIndices.size * if (useUint32) Int.SIZE_BYTES else Short.SIZE_BYTES)
        generatedIndices.forEach { value ->
            if (useUint32) {
                indexData.putInt(value)
            } else {
                indexData.putShort(value.toShort())
            }
        }
        indexData.flip()

        return MeshData(
            vertexData = vertexData,
            vertexCount = positions.count,
            indexData = indexData,
            indexCount = generatedIndices.size,
            indexType = if (useUint32) IndexBuffer.Builder.IndexType.UINT else IndexBuffer.Builder.IndexType.USHORT,
            boundingBox = geometry.computeBoundingBox().toFilamentBox(),
            side = material.side
        )
    }

    private fun buildIndexValues(indexAttribute: BufferAttribute?, positions: BufferAttribute): IntArray {
        if (indexAttribute != null && indexAttribute.count >= 3) {
            return IntArray(indexAttribute.count) { index -> indexAttribute.getX(index).roundToInt() }
        }
        return IntArray(positions.count) { it }
    }

    private fun syncSceneState() {
        sourceScene.updateMatrixWorld(force = true)
        sourceCamera.updateProjectionMatrix()
        sourceCamera.updateMatrixWorld(force = true)
        updateClearColor()
        syncCamera()
        val transformManager = engine.transformManager

        meshHandles.forEach { handle ->
            val transformInstance = transformManager.getInstance(handle.entity)
            transformManager.setTransform(transformInstance, handle.sourceMesh.matrixWorld.toArray())
        }
    }

    private fun syncCamera() {
        filamentCamera.setProjection(
            sourceCamera.fov.toDouble(),
            sourceCamera.aspect.toDouble(),
            sourceCamera.near.toDouble(),
            sourceCamera.far.toDouble(),
            FilamentCamera.Fov.VERTICAL
        )

        val eye = sourceCamera.getWorldPosition(Vector3())
        val forward = sourceCamera.getWorldDirection(Vector3())
        val up = Vector3(0f, 1f, 0f)
            .applyQuaternion(sourceCamera.getWorldQuaternion(Quaternion()))
            .normalize()

        filamentCamera.lookAt(
            eye.x.toDouble(),
            eye.y.toDouble(),
            eye.z.toDouble(),
            (eye.x + forward.x).toDouble(),
            (eye.y + forward.y).toDouble(),
            (eye.z + forward.z).toDouble(),
            up.x.toDouble(),
            up.y.toDouble(),
            up.z.toDouble()
        )
    }

    private fun updateClearColor() {
        val color = when (val background = sourceScene.background) {
            is Background.Color -> background.color
            is Background.Gradient -> Color(
                (background.top.r + background.bottom.r) * 0.5f,
                (background.top.g + background.bottom.g) * 0.5f,
                (background.top.b + background.bottom.b) * 0.5f,
                1f
            )

            else -> Color(0x07131A)
        }

        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(color.r, color.g, color.b, color.a)
        }
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

    private fun loadMaterial(assetPath: String): FilamentMaterial {
        val payload = surfaceView.context.assets.open(assetPath).use { input ->
            val bytes = input.readBytes()
            allocateBuffer(bytes.size).put(bytes).flip() as ByteBuffer
        }
        return FilamentMaterial.Builder()
            .payload(payload, payload.remaining())
            .build(engine)
            .also { loadedMaterial ->
                loadedMaterial.compile(
                    FilamentMaterial.CompilerPriorityQueue.HIGH,
                    FilamentMaterial.UserVariantFilterBit.ALL,
                    mainHandler,
                    Runnable {}
                )
                engine.flush()
            }
    }

    private fun updateViewport(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
    }

    private fun allocateBuffer(size: Int): ByteBuffer =
        ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

    private fun writeColor(buffer: ByteBuffer, red: Float, green: Float, blue: Float, alpha: Float) {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        buffer.put(((red.coerceIn(0f, 1f) * clampedAlpha) * 255f).roundToInt().coerceIn(0, 255).toByte())
        buffer.put(((green.coerceIn(0f, 1f) * clampedAlpha) * 255f).roundToInt().coerceIn(0, 255).toByte())
        buffer.put(((blue.coerceIn(0f, 1f) * clampedAlpha) * 255f).roundToInt().coerceIn(0, 255).toByte())
        buffer.put((clampedAlpha * 255f).roundToInt().coerceIn(0, 255).toByte())
    }

    private fun Side.toFilamentCulling(): CullingMode = when (this) {
        Side.FrontSide -> CullingMode.BACK
        Side.BackSide -> CullingMode.FRONT
        Side.DoubleSide -> CullingMode.NONE
    }

    private fun io.materia.core.math.Box3.toFilamentBox(): Box {
        val centerX = (min.x + max.x) * 0.5f
        val centerY = (min.y + max.y) * 0.5f
        val centerZ = (min.z + max.z) * 0.5f
        val halfX = max((max.x - min.x) * 0.5f, 0.001f)
        val halfY = max((max.y - min.y) * 0.5f, 0.001f)
        val halfZ = max((max.z - min.z) * 0.5f, 0.001f)
        return Box(
            floatArrayOf(centerX, centerY, centerZ),
            floatArrayOf(halfX, halfY, halfZ)
        )
    }

    private data class MeshHandle(
        val sourceMesh: Mesh,
        val entity: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
        val materialInstance: MaterialInstance
    )

    private data class MeshData(
        val vertexData: ByteBuffer,
        val vertexCount: Int,
        val indexData: ByteBuffer,
        val indexCount: Int,
        val indexType: IndexBuffer.Builder.IndexType,
        val boundingBox: Box,
        val side: Side
    )

    private data class VolumeSampler(
        val width: Int,
        val height: Int,
        val depth: Int,
        val byteData: ByteArray?,
        val floatData: FloatArray?,
        val intData: IntArray?
    ) {
        fun sampleLocalPosition(x: Float, y: Float, z: Float): Color = sampleNormalized(
            x = x * 0.5f + 0.5f,
            y = y * 0.5f + 0.5f,
            z = z * 0.5f + 0.5f
        )

        private fun sampleNormalized(x: Float, y: Float, z: Float): Color {
            val sampleX = resolveCoordinate(x, width)
            val sampleY = resolveCoordinate(y, height)
            val sampleZ = resolveCoordinate(z, depth)
            val index = (((sampleZ * height) + sampleY) * width + sampleX) * 4

            floatData?.let { data ->
                if (index + 3 < data.size) {
                    return Color(
                        data[index].coerceIn(0f, 1f),
                        data[index + 1].coerceIn(0f, 1f),
                        data[index + 2].coerceIn(0f, 1f),
                        data[index + 3].coerceIn(0f, 1f)
                    )
                }
            }

            intData?.let { data ->
                if (index + 3 < data.size) {
                    return Color(
                        data[index].coerceIn(0, 255) / 255f,
                        data[index + 1].coerceIn(0, 255) / 255f,
                        data[index + 2].coerceIn(0, 255) / 255f,
                        data[index + 3].coerceIn(0, 255) / 255f
                    )
                }
            }

            byteData?.let { data ->
                if (index + 3 < data.size) {
                    return Color(
                        data[index].toUByte().toFloat() / 255f,
                        data[index + 1].toUByte().toFloat() / 255f,
                        data[index + 2].toUByte().toFloat() / 255f,
                        data[index + 3].toUByte().toFloat() / 255f
                    )
                }
            }

            return Color.WHITE
        }

        private fun resolveCoordinate(value: Float, size: Int): Int {
            if (size <= 1) return 0
            return (value.coerceIn(0f, 1f) * (size - 1).toFloat()).roundToInt().coerceIn(0, size - 1)
        }

        companion object {
            fun from(texture: Data3DTexture): VolumeSampler = VolumeSampler(
                width = texture.width,
                height = texture.height,
                depth = texture.depth,
                byteData = texture.getData().takeIf { it.isNotEmpty() },
                floatData = texture.getFloatData(),
                intData = texture.getIntData()
            )
        }
    }
}