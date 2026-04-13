package io.materia.engine.render

import android.content.res.AssetManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera.Fov
import com.google.android.filament.Colors.RgbaType
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndexBuffer.Builder.IndexType
import com.google.android.filament.LightManager
import com.google.android.filament.Material as FilamentMaterial
import com.google.android.filament.Material.CullingMode as FilamentCullingMode
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.Renderer
import com.google.android.filament.Scene as FilamentScene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TransformManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import io.materia.core.Result
import io.materia.engine.camera.PerspectiveCamera
import io.materia.engine.geometry.AttributeSemantic
import io.materia.engine.geometry.Geometry
import io.materia.engine.material.BlendMode as EngineBlendMode
import io.materia.engine.material.CullMode
import io.materia.engine.material.Material as EngineMaterial
import io.materia.engine.material.RenderState
import io.materia.engine.material.UnlitColorMaterial
import io.materia.engine.material.UnlitLineMaterial
import io.materia.engine.material.UnlitPointsMaterial
import io.materia.engine.math.Color
import io.materia.engine.scene.InstancedPoints
import io.materia.engine.scene.Mesh
import io.materia.engine.scene.Node
import io.materia.engine.scene.Scene
import io.materia.renderer.BackendType
import io.materia.renderer.RenderSurface
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererInitializationException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal actual fun createPlatformEngineRendererOrNull(
    surface: RenderSurface,
    config: RendererConfig,
    options: EngineRendererOptions
): EngineRenderer? = AndroidFilamentEngineRenderer(surface, config, options)

private class AndroidFilamentEngineRenderer(
    private val surface: RenderSurface,
    private val config: RendererConfig,
    private val options: EngineRendererOptions
) : EngineRenderer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val entityManager by lazy {
        Filament.init()
        EntityManager.get()
    }
    private val renderables = mutableMapOf<Any, RenderableHandle>()
    private val visitedKeys = mutableSetOf<Any>()

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var swapChain: SwapChain? = null
    private var view: View? = null
    private var filamentScene: FilamentScene? = null
    private var skybox: Skybox? = null
    private var lightEntity: Int = 0
    private var cameraEntity: Int = 0
    private var filamentCamera: com.google.android.filament.Camera? = null
    private var displayHelper: DisplayHelper? = null
    private var uiHelper: UiHelper? = null
    private var backendType: BackendType = BackendType.VULKAN
    private var deviceNameLabel: String = Build.MODEL ?: "Android Device"
    private var driverVersionLabel: String = "Filament VULKAN"
    private var surfaceWidth: Int = max(surface.width, 1)
    private var surfaceHeight: Int = max(surface.height, 1)
    private var initialized = false
    private var lastBeginFrameState: Boolean? = null
    private val assetManager by lazy(LazyThreadSafetyMode.NONE) {
        resolveAssetManager(surface.getHandle())
    }
    private var loggedCameraState = false
    private var loggedRenderableState = false
    private var loggedCenterPixel = false
    private var loggedCenterPick = false
    private var loggedProjectedMesh = false

    override val backend: BackendType
        get() = backendType

    override val deviceName: String
        get() = deviceNameLabel

    override val driverVersion: String
        get() = driverVersionLabel

    override var fxaaEnabled: Boolean = options.enableFxaa
        set(value) {
            field = value
            runOnRenderThread {
                view?.setAntiAliasing(
                    if (value) View.AntiAliasing.FXAA else View.AntiAliasing.NONE
                )
            }
        }

    override suspend fun initialize(): Result<Unit> {
        if (initialized) {
            return Result.Success(Unit)
        }

        return try {
            runOnRenderThread {
                if (initialized) {
                    return@runOnRenderThread
                }

                val nativeHandle = surface.getHandle()
                val nativeSurface = resolveNativeSurface(nativeHandle)
                val androidAssets = assetManager
                Filament.init()

                val createdEngine = Engine.create(Engine.Backend.VULKAN)
                val createdRenderer = createdEngine.createRenderer()
                if (nativeHandle is SurfaceView) {
                    displayHelper = DisplayHelper(nativeHandle.context)
                }
                val createdScene = createdEngine.createScene()
                val createdLightEntity = entityManager.create()
                LightManager.Builder(LightManager.Type.DIRECTIONAL)
                    .castShadows(false)
                    .direction(0f, -1f, -1f)
                    .color(1f, 1f, 1f)
                    .intensity(100_000f)
                    .build(createdEngine, createdLightEntity)
                createdScene.addEntity(createdLightEntity)

                val createdView = createdEngine.createView().apply {
                    setScene(createdScene)
                    setAntiAliasing(
                        if (fxaaEnabled) View.AntiAliasing.FXAA else View.AntiAliasing.NONE
                    )
                    setViewport(Viewport(0, 0, surfaceWidth, surfaceHeight))
                }

                val createdCameraEntity = entityManager.create()
                val createdCamera = createdEngine.createCamera(createdCameraEntity).apply {
                    setExposure(1.0f)
                }
                createdView.setCamera(createdCamera)

                engine = createdEngine
                renderer = createdRenderer
                filamentScene = createdScene
                skybox = null
                view = createdView
                lightEntity = createdLightEntity
                cameraEntity = createdCameraEntity
                filamentCamera = createdCamera

                if (nativeHandle is SurfaceView) {
                    val helper = UiHelper()
                    helper.setOpaque(true)
                    helper.setRenderCallback(object : UiHelper.RendererCallback {
                        override fun onNativeWindowChanged(surface: Surface) {
                            swapChain?.let(createdEngine::destroySwapChain)
                            swapChain = createdEngine.createSwapChain(surface, helper.swapChainFlags)
                            nativeHandle.display?.let { display ->
                                displayHelper?.attach(createdRenderer, display)
                            }
                        }

                        override fun onDetachedFromSurface() {
                            displayHelper?.detach()
                            swapChain?.let(createdEngine::destroySwapChain)
                            swapChain = null
                        }

                        override fun onResized(width: Int, height: Int) {
                            surfaceWidth = max(width, 1)
                            surfaceHeight = max(height, 1)
                            createdView.setViewport(Viewport(0, 0, surfaceWidth, surfaceHeight))
                        }
                    })
                    uiHelper = helper
                    helper.attachTo(nativeHandle)
                    if (swapChain == null) {
                        swapChain = createdEngine.createSwapChain(nativeSurface, helper.swapChainFlags)
                        nativeHandle.display?.let { display ->
                            displayHelper?.attach(createdRenderer, display)
                        }
                    }
                } else {
                    swapChain = createdEngine.createSwapChain(nativeSurface)
                }

                backendType = createdEngine.backend.toBackendType()
                deviceNameLabel = Build.MODEL ?: "Android Device"
                driverVersionLabel = "Filament ${createdEngine.backend.name}"
                initialized = true
            }
            Result.Success(Unit)
        } catch (error: Throwable) {
            destroyResources()
            val wrapped = error as? RendererInitializationException
                ?: RendererInitializationException.DeviceCreationFailedException(
                    backend = BackendType.VULKAN,
                    adapterInfo = Build.MODEL ?: "Android Device",
                    reason = error.message ?: error::class.simpleName ?: "Unknown Filament error"
                )
            Result.Error(wrapped.message ?: "Failed to initialize Android Filament renderer", wrapped)
        }
    }

    override fun render(scene: Scene, camera: PerspectiveCamera) {
        runOnRenderThread {
            check(initialized) { "EngineRenderer not initialized. Call initialize() first." }

            val currentEngine = engine ?: return@runOnRenderThread
            val currentRenderer = renderer ?: return@runOnRenderThread
            val currentSwapChain = swapChain ?: return@runOnRenderThread
            val currentView = view ?: return@runOnRenderThread
            val currentScene = filamentScene ?: return@runOnRenderThread
            val currentCamera = filamentCamera ?: return@runOnRenderThread
            val helper = uiHelper
            if (helper != null && !helper.isReadyToRender) {
                return@runOnRenderThread
            }
            val frameClearColor = if (scene.backgroundColor.size >= 4) {
                scene.backgroundColor
            } else {
                options.clearColor
            }

            currentView.setViewport(Viewport(0, 0, max(surfaceWidth, 1), max(surfaceHeight, 1)))
            currentRenderer.getClearOptions().apply {
                clear = true
                discard = false
                this.clearColor = frameClearColor.copyOf()
            }.also(currentRenderer::setClearOptions)

            scene.updateWorldMatrix(force = true)
            camera.updateProjection()
            camera.updateWorldMatrix(force = true)
            syncCamera(currentCamera, camera)
            syncSkybox(scene)

            val cameraDebugState = CameraDebugState(
                projection = currentCamera.getProjectionMatrix(DoubleArray(16)).map { it.toFloat() }.toFloatArray(),
                view = currentCamera.getViewMatrix(DoubleArray(16)).map { it.toFloat() }.toFloatArray(),
                viewportWidth = surfaceWidth,
                viewportHeight = surfaceHeight
            )

            visitedKeys.clear()
            val cameraVectors = CameraVectors.from(camera)
            scene.traverse { node ->
                when (node) {
                    is Mesh -> syncMeshNode(currentEngine, currentScene, node, cameraVectors, cameraDebugState)
                    is InstancedPoints -> syncInstancedPointsNode(currentEngine, currentScene, node, cameraVectors)
                }
            }
            pruneUnusedRenderables(currentEngine, currentScene)

            val beganFrame = currentRenderer.beginFrame(currentSwapChain, System.nanoTime())
            if (lastBeginFrameState != beganFrame) {
                Log.i(
                    TAG,
                    "beginFrame=$beganFrame viewport=${surfaceWidth}x${surfaceHeight} renderables=${renderables.size}"
                )
                lastBeginFrameState = beganFrame
            }

            if (beganFrame) {
                currentRenderer.render(currentView)
                if (!loggedCenterPixel) {
                    loggedCenterPixel = true
                    val pixelBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                    val pixelDescriptor = Texture.PixelBufferDescriptor(
                        pixelBuffer,
                        Texture.Format.RGBA,
                        Texture.Type.UBYTE
                    ).apply {
                        setCallback(mainHandler) {
                            pixelBuffer.rewind()
                            val red = pixelBuffer.get().toInt() and 0xFF
                            val green = pixelBuffer.get().toInt() and 0xFF
                            val blue = pixelBuffer.get().toInt() and 0xFF
                            val alpha = pixelBuffer.get().toInt() and 0xFF
                            Log.i(TAG, "center pixel rgba=($red, $green, $blue, $alpha)")
                        }
                    }
                    currentRenderer.readPixels(
                        surfaceWidth / 2,
                        surfaceHeight / 2,
                        1,
                        1,
                        pixelDescriptor
                    )
                }
                if (!loggedCenterPick) {
                    loggedCenterPick = true
                    val sampleXs = intArrayOf(surfaceWidth / 4, surfaceWidth / 2, (surfaceWidth * 3) / 4)
                    val sampleYs = intArrayOf(surfaceHeight / 4, surfaceHeight / 2, (surfaceHeight * 3) / 4)
                    sampleYs.forEach { sampleY ->
                        sampleXs.forEach { sampleX ->
                            currentView.pick(
                                sampleX,
                                sampleY,
                                mainHandler,
                                object : View.OnPickCallback {
                                    override fun onPick(result: View.PickingQueryResult) {
                                        Log.i(
                                            TAG,
                                            "pick[$sampleX,$sampleY] renderable=${result.renderable} depth=${result.depth} frag=(${result.fragCoords?.joinToString() ?: "n/a"})"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                currentRenderer.endFrame()
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        surfaceWidth = max(width, 1)
        surfaceHeight = max(height, 1)
        runOnRenderThread {
            view?.setViewport(Viewport(0, 0, surfaceWidth, surfaceHeight))
            val helper = uiHelper
            if (helper != null) {
                helper.setDesiredSize(surfaceWidth, surfaceHeight)
            } else {
                engine?.let { recreateSwapChain(it) }
            }
        }
    }

    override fun dispose() {
        destroyResources()
    }

    private fun syncCamera(
        target: com.google.android.filament.Camera,
        source: PerspectiveCamera
    ) {
        target.setProjection(
            source.fovDegrees.toDouble(),
            source.aspect.toDouble(),
            source.near.toDouble(),
            source.far.toDouble(),
            Fov.VERTICAL
        )

        val eye = source.transform.position
        val viewMatrix = source.viewMatrix().toFloatArray()
        val forwardX = -viewMatrix[2]
        val forwardY = -viewMatrix[6]
        val forwardZ = -viewMatrix[10]
        val upX = viewMatrix[1]
        val upY = viewMatrix[5]
        val upZ = viewMatrix[9]
        target.lookAt(
            eye.x.toDouble(),
            eye.y.toDouble(),
            eye.z.toDouble(),
            (eye.x + forwardX).toDouble(),
            (eye.y + forwardY).toDouble(),
            (eye.z + forwardZ).toDouble(),
            upX.toDouble(),
            upY.toDouble(),
            upZ.toDouble()
        )
        if (!loggedCameraState) {
            val position = target.getPosition(FloatArray(3))
            val forward = target.getForwardVector(FloatArray(3))
            Log.i(
                TAG,
                "camera position=(${position[0]}, ${position[1]}, ${position[2]}) forward=(${forward[0]}, ${forward[1]}, ${forward[2]})"
            )
            loggedCameraState = true
        }
    }

    private fun syncSkybox(scene: Scene) {
        val currentSkybox = skybox ?: return
        val clearColor = if (scene.backgroundColor.size >= 4) {
            scene.backgroundColor
        } else {
            options.clearColor
        }
        currentSkybox.setColor(
            clearColor[0],
            clearColor[1],
            clearColor[2],
            clearColor[3]
        )
    }

    private fun syncMeshNode(
        currentEngine: Engine,
        currentScene: FilamentScene,
        mesh: Mesh,
        cameraVectors: CameraVectors,
        cameraDebugState: CameraDebugState
    ) {
        val materialState = MaterialState.from(mesh.material)
        if (mesh.material is UnlitPointsMaterial) {
            syncPointMesh(currentEngine, currentScene, mesh, materialState, cameraVectors)
            return
        }

        val accessor = GeometryAccessor(mesh.geometry) ?: run {
            releaseRenderable(currentEngine, currentScene, mesh)
            return
        }
        val sourceIndices = accessor.sourceIndices
        val vertexCount = when (materialState.primitiveType) {
            PrimitiveType.LINES -> sourceIndices.size - (sourceIndices.size % 2)
            PrimitiveType.TRIANGLES -> sourceIndices.size - (sourceIndices.size % 3)
            else -> sourceIndices.size
        }
        if (vertexCount <= 0) {
            releaseRenderable(currentEngine, currentScene, mesh)
            return
        }

        val handle = obtainRenderable(
            currentEngine = currentEngine,
            currentScene = currentScene,
            key = mesh,
            blendMode = materialState.renderState.blendMode,
            primitiveType = materialState.primitiveType,
            vertexCount = vertexCount
        )

        handle.vertexBytes.clear()
        repeat(vertexCount) { outputIndex ->
            val sourceIndex = sourceIndices[outputIndex]
            val base = sourceIndex * accessor.strideFloats
            val positionBase = base + accessor.positionOffsetFloats
            handle.vertexBytes.putFloat(accessor.vertices[positionBase])
            handle.vertexBytes.putFloat(accessor.vertices[positionBase + 1])
            handle.vertexBytes.putFloat(accessor.vertices[positionBase + 2])
            writePremultipliedVertexColor(
                handle.vertexBytes,
                materialState.color.r,
                materialState.color.g,
                materialState.color.b,
                materialState.color.a
            )
        }
        uploadVertexData(currentEngine, handle, vertexCount)
        applyRenderableState(currentEngine, handle, materialState)
        applyTransform(currentEngine, handle, mesh.getWorldMatrix().toFloatArray())
        logProjectedMeshVertices(mesh, accessor, cameraDebugState)
        visitedKeys += mesh
    }

    private fun syncPointMesh(
        currentEngine: Engine,
        currentScene: FilamentScene,
        mesh: Mesh,
        materialState: MaterialState,
        cameraVectors: CameraVectors
    ) {
        val strideFloats = (mesh.geometry.vertexBuffer.strideBytes / Float.SIZE_BYTES).coerceAtLeast(1)
        val data = mesh.geometry.vertexBuffer.data
        val pointCount = data.size / strideFloats
        val vertexCount = pointCount * BILLBOARD_VERTEX_COUNT
        if (vertexCount <= 0) {
            releaseRenderable(currentEngine, currentScene, mesh)
            return
        }

        val worldMatrix = mesh.getWorldMatrix().toFloatArray()
        val pointScale = extractUniformScale(worldMatrix)
        val handle = obtainRenderable(
            currentEngine = currentEngine,
            currentScene = currentScene,
            key = mesh,
            blendMode = materialState.renderState.blendMode,
            primitiveType = PrimitiveType.TRIANGLES,
            vertexCount = vertexCount
        )

        handle.vertexBytes.clear()
        repeat(pointCount) { pointIndex ->
            val base = pointIndex * strideFloats
            val worldX = transformX(worldMatrix, data[base], data[base + 1], data[base + 2])
            val worldY = transformY(worldMatrix, data[base], data[base + 1], data[base + 2])
            val worldZ = transformZ(worldMatrix, data[base], data[base + 1], data[base + 2])
            val red = if (strideFloats >= 6) data[base + 3] else 1f
            val green = if (strideFloats >= 6) data[base + 4] else 1f
            val blue = if (strideFloats >= 6) data[base + 5] else 1f
            val size = if (strideFloats >= 7) data[base + 6] else materialState.pointSize
            writeBillboard(
                buffer = handle.vertexBytes,
                cameraVectors = cameraVectors,
                centerX = worldX,
                centerY = worldY,
                centerZ = worldZ,
                size = max(size * pointScale, MIN_POINT_SIZE_WORLD),
                red = red,
                green = green,
                blue = blue,
                alpha = 1f
            )
        }

        uploadVertexData(currentEngine, handle, vertexCount)
        applyRenderableState(currentEngine, handle, materialState)
        applyTransform(currentEngine, handle, IDENTITY_MATRIX)
        visitedKeys += mesh
    }

    private fun syncInstancedPointsNode(
        currentEngine: Engine,
        currentScene: FilamentScene,
        points: InstancedPoints,
        cameraVectors: CameraVectors
    ) {
        val pointCount = points.instanceCount()
        val vertexCount = pointCount * BILLBOARD_VERTEX_COUNT
        if (vertexCount <= 0) {
            releaseRenderable(currentEngine, currentScene, points)
            return
        }

        val materialState = MaterialState.from(points.material)
        val worldMatrix = points.getWorldMatrix().toFloatArray()
        val pointScale = extractUniformScale(worldMatrix)
        val handle = obtainRenderable(
            currentEngine = currentEngine,
            currentScene = currentScene,
            key = points,
            blendMode = materialState.renderState.blendMode,
            primitiveType = PrimitiveType.TRIANGLES,
            vertexCount = vertexCount
        )

        handle.vertexBytes.clear()
        repeat(pointCount) { pointIndex ->
            val base = pointIndex * points.componentsPerInstance
            val worldX = transformX(
                worldMatrix,
                points.instanceData[base],
                points.instanceData[base + 1],
                points.instanceData[base + 2]
            )
            val worldY = transformY(
                worldMatrix,
                points.instanceData[base],
                points.instanceData[base + 1],
                points.instanceData[base + 2]
            )
            val worldZ = transformZ(
                worldMatrix,
                points.instanceData[base],
                points.instanceData[base + 1],
                points.instanceData[base + 2]
            )
            val size = points.instanceData[base + 6]
            writeBillboard(
                buffer = handle.vertexBytes,
                cameraVectors = cameraVectors,
                centerX = worldX,
                centerY = worldY,
                centerZ = worldZ,
                size = max(size * pointScale, MIN_POINT_SIZE_WORLD),
                red = points.instanceData[base + 3],
                green = points.instanceData[base + 4],
                blue = points.instanceData[base + 5],
                alpha = 1f
            )
        }

        uploadVertexData(currentEngine, handle, vertexCount)
        applyRenderableState(currentEngine, handle, materialState)
        applyTransform(currentEngine, handle, IDENTITY_MATRIX)
        visitedKeys += points
    }

    private fun obtainRenderable(
        currentEngine: Engine,
        currentScene: FilamentScene,
        key: Any,
        blendMode: EngineBlendMode,
        primitiveType: PrimitiveType,
        vertexCount: Int
    ): RenderableHandle {
        val requestedIndexType = indexTypeFor(vertexCount)
        val existing = renderables[key]
        if (existing == null) {
            return createRenderable(currentEngine, currentScene, key, blendMode, primitiveType, vertexCount, requestedIndexType)
        }

        if (
            existing.primitiveType != primitiveType ||
            existing.vertexCapacity != vertexCount ||
            existing.indexCapacity != vertexCount ||
            existing.indexType != requestedIndexType
        ) {
            destroyRenderable(currentEngine, currentScene, existing)
            renderables.remove(key)
            return createRenderable(currentEngine, currentScene, key, blendMode, primitiveType, vertexCount, requestedIndexType)
        }

        val renderableManager = currentEngine.renderableManager
        val renderableInstance = renderableManager.getInstance(existing.entity)

        if (existing.blendMode != blendMode) {
            val replacement = createMaterialInstance(currentEngine, blendMode)
            renderableManager.setMaterialInstanceAt(renderableInstance, 0, replacement)
            currentEngine.destroyMaterialInstance(existing.materialInstance)
            existing.materialInstance = replacement
            existing.blendMode = blendMode
        }
        return existing
    }

    private fun createRenderable(
        currentEngine: Engine,
        currentScene: FilamentScene,
        key: Any,
        blendMode: EngineBlendMode,
        primitiveType: PrimitiveType,
        vertexCount: Int,
        indexType: IndexType
    ): RenderableHandle {
        val vertexBuffer = createVertexBuffer(currentEngine, vertexCount)
        val indexBuffer = createIndexBuffer(currentEngine, vertexCount, indexType)
        val materialInstance = createMaterialInstance(currentEngine, blendMode)
        val entity = entityManager.create()
        RenderableManager.Builder(1)
            .boundingBox(LARGE_BOUNDING_BOX)
            .geometry(0, primitiveType, vertexBuffer, indexBuffer)
            .material(0, materialInstance)
            .build(currentEngine, entity)
        currentScene.addEntity(entity)
        if (!loggedRenderableState) {
            val transformInstance = currentEngine.transformManager.getInstance(entity)
            val renderableInstance = currentEngine.renderableManager.getInstance(entity)
            Log.i(
                TAG,
                "renderable entity=$entity transformInstance=$transformInstance renderableInstance=$renderableInstance primitiveCount=${currentEngine.renderableManager.getPrimitiveCount(renderableInstance)}"
            )
            loggedRenderableState = true
        }

        return RenderableHandle(
            entity = entity,
            primitiveType = primitiveType,
            blendMode = blendMode,
            vertexCapacity = vertexCount,
            indexCapacity = vertexCount,
            indexType = indexType,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            materialInstance = materialInstance,
            vertexBytes = allocateDirect(vertexCount * VERTEX_STRIDE_BYTES),
            indexBytes = allocateDirect(vertexCount * indexSizeBytes(indexType))
        ).also { renderables[key] = it }
    }

    private fun uploadVertexData(
        currentEngine: Engine,
        handle: RenderableHandle,
        vertexCount: Int
    ) {
        val byteCount = vertexCount * VERTEX_STRIDE_BYTES
        handle.vertexBytes.limit(byteCount)
        handle.vertexBytes.position(0)
        val vertexUpload = handle.vertexBytes.duplicate().apply {
            position(0)
            limit(byteCount)
        }.slice().order(ByteOrder.nativeOrder())
        handle.vertexBuffer.setBufferAt(currentEngine, 0, vertexUpload, 0, byteCount)

        if (handle.uploadedIndexCount != vertexCount) {
            handle.indexBytes.clear()
            when (handle.indexType) {
                IndexType.USHORT -> repeat(vertexCount) { handle.indexBytes.putShort(it.toShort()) }
                IndexType.UINT -> repeat(vertexCount) { handle.indexBytes.putInt(it) }
            }
            val indexByteCount = vertexCount * indexSizeBytes(handle.indexType)
            handle.indexBytes.limit(indexByteCount)
            handle.indexBytes.position(0)
            val indexUpload = handle.indexBytes.duplicate().apply {
                position(0)
                limit(indexByteCount)
            }.slice().order(ByteOrder.nativeOrder())
            handle.indexBuffer.setBuffer(currentEngine, indexUpload, 0, indexByteCount)
            handle.uploadedIndexCount = vertexCount
        }

        currentEngine.flushAndWait()
    }

    private fun applyRenderableState(
        currentEngine: Engine,
        handle: RenderableHandle,
        state: MaterialState
    ) {
        val materialInstance = handle.materialInstance
        materialInstance.setCullingMode(state.renderState.cullMode.toFilamentCulling())
        materialInstance.setDoubleSided(state.renderState.cullMode == CullMode.NONE)
        materialInstance.setDepthWrite(state.renderState.depthWrite)
        materialInstance.setDepthCulling(state.renderState.depthTest)

        val renderableInstance = currentEngine.renderableManager.getInstance(handle.entity)
        currentEngine.renderableManager.setPriority(
            renderableInstance,
            if (state.renderState.blendMode == EngineBlendMode.Opaque) OPAQUE_PRIORITY else BLENDED_PRIORITY
        )
    }

    private fun applyTransform(
        currentEngine: Engine,
        handle: RenderableHandle,
        transform: FloatArray
    ) {
        val transformManager = currentEngine.transformManager
        val transformInstance = transformManager.getInstance(handle.entity)
        transformManager.setTransform(transformInstance, transform)
    }

    private fun logProjectedMeshVertices(
        mesh: Mesh,
        accessor: GeometryAccessor,
        cameraDebugState: CameraDebugState
    ) {
        if (loggedProjectedMesh || mesh.name != "triangle") {
            return
        }
        val worldMatrix = mesh.getWorldMatrix().toFloatArray()
        val indicesToLog = accessor.sourceIndices.take(3)
        val projected = indicesToLog.joinToString(separator = " | ") { sourceIndex ->
            val base = sourceIndex * accessor.strideFloats + accessor.positionOffsetFloats
            val worldX = transformX(worldMatrix, accessor.vertices[base], accessor.vertices[base + 1], accessor.vertices[base + 2])
            val worldY = transformY(worldMatrix, accessor.vertices[base], accessor.vertices[base + 1], accessor.vertices[base + 2])
            val worldZ = transformZ(worldMatrix, accessor.vertices[base], accessor.vertices[base + 1], accessor.vertices[base + 2])
            projectPoint(cameraDebugState, worldX, worldY, worldZ)
        }
        Log.i(TAG, "triangle projected vertices: $projected")
        loggedProjectedMesh = true
    }

    private fun pruneUnusedRenderables(
        currentEngine: Engine,
        currentScene: FilamentScene
    ) {
        val iterator = renderables.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in visitedKeys) {
                destroyRenderable(currentEngine, currentScene, entry.value)
                iterator.remove()
            }
        }
    }

    private fun releaseRenderable(
        currentEngine: Engine,
        currentScene: FilamentScene,
        key: Any
    ) {
        val handle = renderables.remove(key) ?: return
        destroyRenderable(currentEngine, currentScene, handle)
    }

    private fun destroyRenderable(
        currentEngine: Engine,
        currentScene: FilamentScene,
        handle: RenderableHandle
    ) {
        currentScene.removeEntity(handle.entity)
        currentEngine.destroyEntity(handle.entity)
        currentEngine.destroyVertexBuffer(handle.vertexBuffer)
        currentEngine.destroyIndexBuffer(handle.indexBuffer)
        currentEngine.destroyMaterialInstance(handle.materialInstance)
    }

    private fun createVertexBuffer(currentEngine: Engine, vertexCount: Int): VertexBuffer {
        return VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, VERTEX_STRIDE_BYTES)
            .attribute(VertexAttribute.COLOR, 0, AttributeType.UBYTE4, POSITION_COMPONENTS * FLOAT_BYTES, VERTEX_STRIDE_BYTES)
            .normalized(VertexAttribute.COLOR)
            .build(currentEngine)
    }

    private fun createIndexBuffer(
        currentEngine: Engine,
        indexCount: Int,
        indexType: IndexType
    ): IndexBuffer {
        return IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(indexType)
            .build(currentEngine)
    }

    private fun createMaterialInstance(
        currentEngine: Engine,
        blendMode: EngineBlendMode
    ): MaterialInstance {
        val material = materialFor(currentEngine, blendMode)
        return material.createInstance()
    }

    private fun materialFor(
        currentEngine: Engine,
        blendMode: EngineBlendMode
    ): FilamentMaterial {
        return AndroidFilamentMaterialAssets.materials.getOrPut(blendMode) {
            val payload = AndroidFilamentMaterialAssets.loadPayload(assetManager, blendMode)
            FilamentMaterial.Builder()
                .payload(payload, payload.remaining())
                .build(currentEngine)
                .also { material ->
                    material.compile(
                        FilamentMaterial.CompilerPriorityQueue.HIGH,
                        FilamentMaterial.UserVariantFilterBit.ALL,
                        mainHandler
                    ) {
                        Log.i(TAG, "material compiled: ${material.name}")
                    }
                    currentEngine.flush()
                }
        }
    }

    private fun destroyResources() {
        runOnRenderThread {
            val currentEngine = engine
            val currentScene = filamentScene
            if (currentEngine != null && currentScene != null) {
                renderables.values.forEach { destroyRenderable(currentEngine, currentScene, it) }
            }
            renderables.clear()
            visitedKeys.clear()

            if (currentEngine != null) {
                AndroidFilamentMaterialAssets.materials.values.forEach { currentEngine.destroyMaterial(it) }
                AndroidFilamentMaterialAssets.materials.clear()

                uiHelper?.detach()
                uiHelper = null
                displayHelper?.detach()
                displayHelper = null
                if (lightEntity != 0) {
                    currentEngine.destroyEntity(lightEntity)
                }
                skybox?.let { currentEngine.destroySkybox(it) }
                view?.let { currentEngine.destroyView(it) }
                filamentScene?.let { currentEngine.destroyScene(it) }
                renderer?.let { currentEngine.destroyRenderer(it) }
                swapChain?.let { currentEngine.destroySwapChain(it) }
                if (cameraEntity != 0) {
                    currentEngine.destroyEntity(cameraEntity)
                }
                currentEngine.flushAndWait()
                currentEngine.destroy()
            }

            engine = null
            renderer = null
            swapChain = null
            view = null
            filamentScene = null
            skybox = null
            lightEntity = 0
            cameraEntity = 0
            filamentCamera = null
            initialized = false
        }
    }

    private fun recreateSwapChain(currentEngine: Engine) {
        val nativeSurface = resolveNativeSurface(surface.getHandle())
        swapChain?.let { currentEngine.destroySwapChain(it) }
        swapChain = uiHelper?.let { helper ->
            currentEngine.createSwapChain(nativeSurface, helper.swapChainFlags)
        } ?: currentEngine.createSwapChain(nativeSurface)
    }

    private fun resolveNativeSurface(handle: Any = surface.getHandle()): Surface {
        val nativeSurface = when (handle) {
            is SurfaceView -> handle.holder.surface
            is SurfaceHolder -> handle.surface
            is Surface -> handle
            else -> throw RendererInitializationException.SurfaceCreationFailedException(
                backend = BackendType.VULKAN,
                surfaceType = handle::class.simpleName ?: "Unknown Android surface"
            )
        }

        if (!nativeSurface.isValid) {
            throw RendererInitializationException.SurfaceCreationFailedException(
                backend = BackendType.VULKAN,
                surfaceType = nativeSurface::class.simpleName ?: "Android Surface"
            )
        }

        return nativeSurface
    }

    private fun resolveAssetManager(handle: Any): AssetManager = when (handle) {
        is SurfaceView -> handle.context.assets
        else -> throw IllegalStateException(
            "Android Filament renderer requires a SurfaceView-backed RenderSurface to load precompiled material assets."
        )
    }

    private fun <T> runOnRenderThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }

        var result: Any? = null
        var failure: Throwable? = null
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                result = block()
            } catch (error: Throwable) {
                failure = error
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        failure?.let { throw it }

        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private data class MaterialState(
        val color: Color,
        val renderState: RenderState,
        val primitiveType: PrimitiveType,
        val pointSize: Float = 1f
    ) {
        companion object {
            fun from(material: EngineMaterial): MaterialState = when (material) {
                is UnlitColorMaterial -> MaterialState(
                    color = material.color,
                    renderState = material.renderState,
                    primitiveType = PrimitiveType.TRIANGLES
                )

                is UnlitLineMaterial -> MaterialState(
                    color = material.color,
                    renderState = material.renderState,
                    primitiveType = PrimitiveType.LINES
                )

                is UnlitPointsMaterial -> MaterialState(
                    color = material.baseColor,
                    renderState = material.renderState,
                    primitiveType = PrimitiveType.TRIANGLES,
                    pointSize = material.size
                )
            }
        }
    }

    private data class RenderableHandle(
        val entity: Int,
        var primitiveType: PrimitiveType,
        var blendMode: EngineBlendMode,
        var vertexCapacity: Int,
        var indexCapacity: Int,
        var indexType: IndexType,
        var vertexBuffer: VertexBuffer,
        var indexBuffer: IndexBuffer,
        var materialInstance: MaterialInstance,
        var vertexBytes: ByteBuffer,
        var indexBytes: ByteBuffer,
        var uploadedIndexCount: Int = -1
    )

    private data class VertexColor(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float
    )

    private class GeometryAccessor private constructor(
        val geometry: Geometry,
        val vertices: FloatArray,
        val strideFloats: Int,
        val positionOffsetFloats: Int,
        private val colorOffsetFloats: Int,
        private val colorComponents: Int,
        val sourceIndices: IntArray
    ) {
        fun readColor(index: Int): VertexColor {
            if (colorOffsetFloats < 0 || colorComponents < 3) {
                return WHITE_VERTEX_COLOR
            }
            val base = index * strideFloats + colorOffsetFloats
            val alpha = if (colorComponents >= 4) {
                vertices[base + 3].coerceIn(0f, 1f)
            } else {
                1f
            }
            return VertexColor(
                red = vertices[base].coerceIn(0f, 1f),
                green = vertices[base + 1].coerceIn(0f, 1f),
                blue = vertices[base + 2].coerceIn(0f, 1f),
                alpha = alpha
            )
        }

        companion object {
            operator fun invoke(geometry: Geometry): GeometryAccessor? {
                val positionAttribute = geometry.layout.attributes[AttributeSemantic.POSITION] ?: return null
                val strideFloats = (geometry.vertexBuffer.strideBytes / Float.SIZE_BYTES).coerceAtLeast(1)
                val sourceIndices = geometry.indexBuffer?.let { indices ->
                    IntArray(indices.size) { index -> indices[index].toInt() and 0xFFFF }
                } ?: IntArray(geometry.vertexBuffer.data.size / strideFloats) { it }

                val colorAttribute = geometry.layout.attributes[AttributeSemantic.COLOR]
                return GeometryAccessor(
                    geometry = geometry,
                    vertices = geometry.vertexBuffer.data,
                    strideFloats = strideFloats,
                    positionOffsetFloats = positionAttribute.offset / Float.SIZE_BYTES,
                    colorOffsetFloats = colorAttribute?.offset?.div(Float.SIZE_BYTES) ?: -1,
                    colorComponents = colorAttribute?.components ?: 0,
                    sourceIndices = sourceIndices
                )
            }
        }
    }

    private companion object {
        private const val TAG = "AndroidFilamentRenderer"
        private const val POSITION_COMPONENTS = 3
        private const val FLOAT_BYTES = Float.SIZE_BYTES
        private const val COLOR_BYTES = Int.SIZE_BYTES
        private const val VERTEX_STRIDE_BYTES = (POSITION_COMPONENTS * FLOAT_BYTES) + COLOR_BYTES
        private const val BILLBOARD_VERTEX_COUNT = 6
        private const val MIN_POINT_SIZE_WORLD = 0.02f
        private const val OPAQUE_PRIORITY = 4
        private const val BLENDED_PRIORITY = 7

        private val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        private val LARGE_BOUNDING_BOX = Box(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1_000f, 1_000f, 1_000f)
        )

        private val WHITE_VERTEX_COLOR = VertexColor(1f, 1f, 1f, 1f)
    }

}
private object AndroidFilamentMaterialAssets {
    private const val MATERIAL_ASSET_ROOT = "filament/materials"
    val materials = mutableMapOf<EngineBlendMode, FilamentMaterial>()

    fun loadPayload(assetManager: AssetManager, blendMode: EngineBlendMode): ByteBuffer {
        val assetPath = when (blendMode) {
            EngineBlendMode.Opaque -> "$MATERIAL_ASSET_ROOT/materia_opaque_color.filamat"
            EngineBlendMode.Alpha -> "$MATERIAL_ASSET_ROOT/materia_alpha_color.filamat"
            EngineBlendMode.Additive -> "$MATERIAL_ASSET_ROOT/materia_additive_color.filamat"
        }
        val bytes = assetManager.open(assetPath).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size)
            .put(bytes)
            .apply { flip() }
    }
}

private data class CameraVectors(
    val rightX: Float,
    val rightY: Float,
    val rightZ: Float,
    val upX: Float,
    val upY: Float,
    val upZ: Float
) {
    companion object {
        fun from(camera: PerspectiveCamera): CameraVectors {
            val viewMatrix = camera.viewMatrix().toFloatArray()
            return CameraVectors(
                rightX = viewMatrix[0],
                rightY = viewMatrix[4],
                rightZ = viewMatrix[8],
                upX = viewMatrix[1],
                upY = viewMatrix[5],
                upZ = viewMatrix[9]
            )
        }
    }
}

private fun writeBillboard(
    buffer: ByteBuffer,
    cameraVectors: CameraVectors,
    centerX: Float,
    centerY: Float,
    centerZ: Float,
    size: Float,
    red: Float,
    green: Float,
    blue: Float,
    alpha: Float
) {
    val halfSize = size * 0.5f
    val rightX = cameraVectors.rightX * halfSize
    val rightY = cameraVectors.rightY * halfSize
    val rightZ = cameraVectors.rightZ * halfSize
    val upX = cameraVectors.upX * halfSize
    val upY = cameraVectors.upY * halfSize
    val upZ = cameraVectors.upZ * halfSize

    writeVertex(
        buffer,
        centerX - rightX - upX,
        centerY - rightY - upY,
        centerZ - rightZ - upZ,
        red,
        green,
        blue,
        alpha
    )
    writeVertex(
        buffer,
        centerX + rightX - upX,
        centerY + rightY - upY,
        centerZ + rightZ - upZ,
        red,
        green,
        blue,
        alpha
    )
    writeVertex(
        buffer,
        centerX + rightX + upX,
        centerY + rightY + upY,
        centerZ + rightZ + upZ,
        red,
        green,
        blue,
        alpha
    )
    writeVertex(
        buffer,
        centerX - rightX - upX,
        centerY - rightY - upY,
        centerZ - rightZ - upZ,
        red,
        green,
        blue,
        alpha
    )
    writeVertex(
        buffer,
        centerX + rightX + upX,
        centerY + rightY + upY,
        centerZ + rightZ + upZ,
        red,
        green,
        blue,
        alpha
    )
    writeVertex(
        buffer,
        centerX - rightX + upX,
        centerY - rightY + upY,
        centerZ - rightZ + upZ,
        red,
        green,
        blue,
        alpha
    )
}

private fun writeVertex(
    buffer: ByteBuffer,
    x: Float,
    y: Float,
    z: Float,
    red: Float,
    green: Float,
    blue: Float,
    alpha: Float
) {
    buffer.putFloat(x)
    buffer.putFloat(y)
    buffer.putFloat(z)
}

private fun writePremultipliedVertexColor(
    buffer: ByteBuffer,
    red: Float,
    green: Float,
    blue: Float,
    alpha: Float
) {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    buffer.put(((red.coerceIn(0f, 1f) * clampedAlpha) * 255f).roundToInt().coerceIn(0, 255).toByte())
    buffer.put(((green.coerceIn(0f, 1f) * clampedAlpha) * 255f).roundToInt().coerceIn(0, 255).toByte())
    buffer.put(((blue.coerceIn(0f, 1f) * clampedAlpha) * 255f).roundToInt().coerceIn(0, 255).toByte())
    buffer.put((clampedAlpha * 255f).roundToInt().coerceIn(0, 255).toByte())
}

private fun allocateDirect(size: Int): ByteBuffer = ByteBuffer
    .allocate(size)
    .order(ByteOrder.nativeOrder())

private fun indexTypeFor(vertexCount: Int): IndexType =
    if (vertexCount > 0xFFFF) IndexType.UINT else IndexType.USHORT

private fun indexSizeBytes(indexType: IndexType): Int = when (indexType) {
    IndexType.USHORT -> Short.SIZE_BYTES
    IndexType.UINT -> Int.SIZE_BYTES
}

private data class CameraDebugState(
    val projection: FloatArray,
    val view: FloatArray,
    val viewportWidth: Int,
    val viewportHeight: Int
)

private fun projectPoint(
    cameraDebugState: CameraDebugState,
    x: Float,
    y: Float,
    z: Float
): String {
    val view = cameraDebugState.view
    val projection = cameraDebugState.projection
    val viewX = view[0] * x + view[4] * y + view[8] * z + view[12]
    val viewY = view[1] * x + view[5] * y + view[9] * z + view[13]
    val viewZ = view[2] * x + view[6] * y + view[10] * z + view[14]
    val viewW = view[3] * x + view[7] * y + view[11] * z + view[15]

    val clipX = projection[0] * viewX + projection[4] * viewY + projection[8] * viewZ + projection[12] * viewW
    val clipY = projection[1] * viewX + projection[5] * viewY + projection[9] * viewZ + projection[13] * viewW
    val clipZ = projection[2] * viewX + projection[6] * viewY + projection[10] * viewZ + projection[14] * viewW
    val clipW = projection[3] * viewX + projection[7] * viewY + projection[11] * viewZ + projection[15] * viewW

    if (kotlin.math.abs(clipW) < 0.000001f) {
        return "world=($x,$y,$z) clipW=$clipW"
    }

    val ndcX = clipX / clipW
    val ndcY = clipY / clipW
    val ndcZ = clipZ / clipW
    val screenX = ((ndcX * 0.5f) + 0.5f) * cameraDebugState.viewportWidth
    val screenY = (1f - ((ndcY * 0.5f) + 0.5f)) * cameraDebugState.viewportHeight
    return "world=($x,$y,$z) ndc=($ndcX,$ndcY,$ndcZ) screen=($screenX,$screenY)"
}

private fun extractUniformScale(matrix: FloatArray): Float {
    val scaleX = sqrt(matrix[0] * matrix[0] + matrix[1] * matrix[1] + matrix[2] * matrix[2])
    val scaleY = sqrt(matrix[4] * matrix[4] + matrix[5] * matrix[5] + matrix[6] * matrix[6])
    val scaleZ = sqrt(matrix[8] * matrix[8] + matrix[9] * matrix[9] + matrix[10] * matrix[10])
    return (scaleX + scaleY + scaleZ) / 3f
}

private fun transformX(matrix: FloatArray, x: Float, y: Float, z: Float): Float =
    matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]

private fun transformY(matrix: FloatArray, x: Float, y: Float, z: Float): Float =
    matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]

private fun transformZ(matrix: FloatArray, x: Float, y: Float, z: Float): Float =
    matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]

private fun Engine.Backend.toBackendType(): BackendType = when (this) {
    Engine.Backend.WEBGPU -> BackendType.WEBGPU
    Engine.Backend.OPENGL -> BackendType.WEBGL
    Engine.Backend.VULKAN -> BackendType.VULKAN
    else -> BackendType.VULKAN
}

private fun CullMode.toFilamentCulling(): FilamentCullingMode = when (this) {
    CullMode.NONE -> FilamentCullingMode.NONE
    CullMode.FRONT -> FilamentCullingMode.FRONT
    CullMode.BACK -> FilamentCullingMode.BACK
}