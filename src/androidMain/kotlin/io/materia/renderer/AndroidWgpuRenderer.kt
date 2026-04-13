package io.materia.renderer

import android.os.Build
import io.materia.camera.Camera
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.scene.Background
import io.materia.core.scene.DrawMode
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.geometry.InstancedPointsGeometry
import io.materia.material.Blending
import io.materia.material.Material as CommonMaterial
import io.materia.material.MaterialSide
import io.materia.material.MeshBasicMaterial
import io.materia.material.MeshStandardMaterial
import io.materia.material.Side
import io.materia.points.Points
import io.materia.points.PointsMaterial
import io.materia.texture.Data3DTexture
import io.materia.texture.VolumeTextureSampler
import io.ygdrasil.webgpu.*
import io.ygdrasil.webgpu.Color as GpuColor
import kotlin.math.max
import kotlin.math.roundToInt

internal class AndroidWgpuRenderer(
    private val surface: AndroidRenderSurface
) : Renderer {

    override val backend: BackendType = BackendType.VULKAN

    override val capabilities: RendererCapabilities
        get() = rendererCapabilities

    override val stats: RenderStats
        get() = renderStats

    private var rendererCapabilities = RendererCapabilities(
        backend = backend,
        deviceName = Build.MODEL ?: "Android Device",
        driverVersion = "Vulkan via wgpu4k",
        supportsCompute = true,
        supportsRayTracing = false,
        supportsMultisampling = false,
        maxTextureSize3D = 2048,
        maxTextureArrayLayers = 256,
        maxSamples = 1,
        textureFormats = setOf(
            TextureFormat.RGBA8,
            TextureFormat.RGB8,
            TextureFormat.RGBA16F,
            TextureFormat.RGBA32F
        ),
        depthFormats = setOf(DepthFormat.DEPTH24_STENCIL8),
        vendor = Build.MANUFACTURER ?: "Unknown",
        renderer = Build.HARDWARE ?: "Unknown",
        version = "Vulkan via wgpu4k",
        shadingLanguageVersion = "WGSL",
        instancedRendering = true,
        multipleRenderTargets = true,
        depthTextures = true,
        floatTextures = true,
        halfFloatTextures = true,
        floatTextureLinear = true,
        standardDerivatives = true,
        vertexArrayObjects = true,
        computeShaders = true,
        shadowMaps = true,
        shadowMapComparison = true,
        shadowMapPCF = true,
        asyncOperations = true
    )

    private var renderStats = RenderStats(
        fps = 0.0,
        frameTime = 0.0,
        triangles = 0,
        drawCalls = 0
    )

    private var androidContext: AndroidContext? = null
    private var wgpuContext: WGPUContext? = null
    private var device: GPUDevice? = null
    private var uniformLayout: GPUBindGroupLayout? = null
    private var depthTexture: GPUTexture? = null
    private var depthTextureView: GPUTextureView? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var initialised = false

    private val pipelineCache = mutableMapOf<PipelineKey, PipelineResources>()
    private val renderables = mutableMapOf<Int, CachedRenderable>()
    private val visitedIds = mutableSetOf<Int>()

    private val viewProjectionMatrix = Matrix4()
    private val modelViewProjectionMatrix = Matrix4()
    private var clearColor = DEFAULT_CLEAR_COLOR.copyOf()

    override suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit> {
        if (initialised) {
            return io.materia.core.Result.Success(Unit)
        }

        if (!surface.holder.surface.isValid) {
            return io.materia.core.Result.Error(
                "Android render surface is not ready.",
                RendererInitializationException.SurfaceCreationFailedException(
                    backend = backend,
                    surfaceType = surface::class.simpleName ?: "AndroidRenderSurface"
                )
            )
        }

        return try {
            ensureAndroidWgpuRuntimeCompatible()

            val initialWidth = max(1, surface.width.takeIf { it > 0 } ?: 640)
            val initialHeight = max(1, surface.height.takeIf { it > 0 } ?: 480)

            val context = androidContextRenderer(
                surfaceHolder = surface.holder,
                width = initialWidth,
                height = initialHeight
            )
            val gpuContext = context.wgpuContext

            androidContext = context
            wgpuContext = gpuContext
            device = gpuContext.device
            uniformLayout = createUniformLayout(gpuContext.device)

            configureSurface(initialWidth, initialHeight)
            rendererCapabilities = queryCapabilities(gpuContext)
            initialised = true

            io.materia.core.Result.Success(Unit)
        } catch (exception: Throwable) {
            dispose()
            val initError = when {
                exception is RendererInitializationException -> exception
                exception.isAndroidWgpuCompatibilityFailure() -> androidWgpuCompatibilityException(exception)
                else -> RendererInitializationException.DeviceCreationFailedException(
                    backend = backend,
                    adapterInfo = Build.MODEL ?: "Android Device",
                    reason = exception.message ?: exception::class.simpleName ?: "Unknown error"
                )
            }
            io.materia.core.Result.Error(
                initError.message ?: "Failed to initialise Android renderer",
                initError
            )
        }
    }

    override fun render(scene: Scene, camera: Camera) {
        ensureInitialised()

        val context = wgpuContext ?: return
        val currentDevice = device ?: return
        val width = max(1, surface.width.takeIf { it > 0 } ?: surfaceWidth)
        val height = max(1, surface.height.takeIf { it > 0 } ?: surfaceHeight)
        if (width != surfaceWidth || height != surfaceHeight) {
            resize(width, height)
        }

        updateClearColor(scene)
        scene.updateMatrixWorld(true)
        camera.updateMatrixWorld(false)
        camera.updateProjectionMatrix()
        viewProjectionMatrix.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)

        val frameStart = System.nanoTime()
        val currentTexture = context.renderingContext.getCurrentTexture()
        val colorView = currentTexture.createView(TextureViewDescriptor(label = "android-frame-view"))
        val commandEncoder = currentDevice.createCommandEncoder(
            CommandEncoderDescriptor(label = "android-render-pass")
        )

        val colorAttachment = RenderPassColorAttachment(
            view = colorView,
            loadOp = GPULoadOp.Clear,
            storeOp = GPUStoreOp.Store,
            clearValue = GpuColor(
                clearColor[0].toDouble(),
                clearColor[1].toDouble(),
                clearColor[2].toDouble(),
                clearColor[3].toDouble()
            )
        )
        val depthAttachment = depthTextureView?.let {
            RenderPassDepthStencilAttachment(
                view = it,
                depthLoadOp = GPULoadOp.Clear,
                depthStoreOp = GPUStoreOp.Store,
                depthClearValue = 1f,
                depthReadOnly = false
            )
        }

        val pass = commandEncoder.beginRenderPass(
            RenderPassDescriptor(
                label = "android-main-pass",
                colorAttachments = listOf(colorAttachment),
                depthStencilAttachment = depthAttachment
            )
        )

        visitedIds.clear()
        var drawCalls = 0
        var triangles = 0

        scene.traverseVisible { node ->
            when {
                node is Mesh && node.visible -> {
                    val renderable = prepareMesh(node) ?: return@traverseVisible
                    visitedIds.add(node.id)
                    triangles += drawMesh(pass, node, renderable)
                    drawCalls++
                }

                node is Points && node.visible -> {
                    val renderable = preparePoints(node) ?: return@traverseVisible
                    visitedIds.add(node.id)
                    triangles += drawPoints(pass, node, renderable)
                    drawCalls++
                }
            }
        }

        pass.end()
        currentDevice.queue.submit(listOf(commandEncoder.finish()))
        context.surface.present()

        releaseUnusedRenderables()
        updateStats(drawCalls, triangles, frameStart)
    }

    override fun resize(width: Int, height: Int) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        if (!initialised) {
            return
        }

        configureSurface(surfaceWidth, surfaceHeight)
    }

    override fun dispose() {
        renderables.values.forEach { it.dispose() }
        renderables.clear()
        pipelineCache.clear()
        visitedIds.clear()

        depthTexture?.close()
        depthTexture = null
        depthTextureView = null
        uniformLayout = null

        androidContext?.close()
        androidContext = null
        wgpuContext = null
        device = null
        initialised = false
    }

    private fun prepareMesh(mesh: Mesh): CachedMesh? {
        val materialState = resolveMaterialState(mesh.material)
        if (!materialState.visible) {
            return null
        }

        val payload = buildMeshPayload(mesh, materialState) ?: return null
        val pipelineKey = PipelineKey(
            isPoints = false,
            topology = payload.topology,
            blendMode = materialState.blendMode,
            cullMode = materialState.cullMode,
            depthWrite = materialState.depthWrite,
            depthTest = materialState.depthTest
        )

        val existing = renderables[mesh.id] as? CachedMesh
        val renderable = if (existing == null || existing.vertexByteSize != payload.vertexBytes.size || existing.indexByteSize != (payload.indexBytes?.size ?: 0)) {
            existing?.dispose()
            val uniformBuffer = createUniformBuffer(mesh.id)
            CachedMesh(
                vertexBuffer = createBuffer(
                    payload.vertexBytes.size,
                    setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
                    "mesh-${mesh.id}-vertex"
                ),
                vertexByteSize = payload.vertexBytes.size,
                indexBuffer = payload.indexBytes?.let {
                    createBuffer(
                        it.size,
                        setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
                        "mesh-${mesh.id}-index"
                    )
                },
                indexByteSize = payload.indexBytes?.size ?: 0,
                uniformBuffer = uniformBuffer,
                bindGroup = createUniformBindGroup(mesh.id, uniformBuffer),
                pipelineKey = pipelineKey,
                vertexCount = payload.vertexCount,
                indexCount = payload.indexCount,
                indexFormat = payload.indexFormat,
                triangles = payload.triangleCount
            )
        } else {
            existing
        }

        val currentDevice = device ?: return null
        currentDevice.queue.writeBuffer(renderable.vertexBuffer, 0uL, payload.vertexBytes)
        val indexBuffer = renderable.indexBuffer
        if (payload.indexBytes != null && indexBuffer != null) {
            currentDevice.queue.writeBuffer(indexBuffer, 0uL, payload.indexBytes)
        }

        renderable.pipelineKey = pipelineKey
        renderable.vertexCount = payload.vertexCount
        renderable.indexCount = payload.indexCount
        renderable.indexFormat = payload.indexFormat
        renderable.triangles = payload.triangleCount
        renderables[mesh.id] = renderable
        return renderable
    }

    private fun preparePoints(points: Points): CachedPoints? {
        val materialState = resolveMaterialState(points.material)
        if (!materialState.visible) {
            return null
        }

        val payload = buildPointsPayload(points, materialState) ?: return null
        val pipelineKey = PipelineKey(
            isPoints = true,
            topology = GPUPrimitiveTopology.TriangleList,
            blendMode = materialState.blendMode,
            cullMode = GPUCullMode.None,
            depthWrite = materialState.depthWrite,
            depthTest = materialState.depthTest
        )

        val existing = renderables[points.id] as? CachedPoints
        val renderable = if (existing == null || existing.instanceByteSize != payload.instanceBytes.size) {
            existing?.dispose()
            val uniformBuffer = createUniformBuffer(points.id)
            CachedPoints(
                instanceBuffer = createBuffer(
                    payload.instanceBytes.size,
                    setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
                    "points-${points.id}-instance"
                ),
                instanceByteSize = payload.instanceBytes.size,
                uniformBuffer = uniformBuffer,
                bindGroup = createUniformBindGroup(points.id, uniformBuffer),
                pipelineKey = pipelineKey,
                instanceCount = payload.instanceCount,
                triangles = payload.triangleCount
            )
        } else {
            existing
        }

        val currentDevice = device ?: return null
        currentDevice.queue.writeBuffer(renderable.instanceBuffer, 0uL, payload.instanceBytes)

        renderable.pipelineKey = pipelineKey
        renderable.instanceCount = payload.instanceCount
        renderable.triangles = payload.triangleCount
        renderables[points.id] = renderable
        return renderable
    }

    private fun drawMesh(pass: GPURenderPassEncoder, mesh: Mesh, renderable: CachedMesh): Int {
        val pipeline = pipelineCache.getOrPut(renderable.pipelineKey) { createPipeline(renderable.pipelineKey) }
        val currentDevice = device ?: return 0

        modelViewProjectionMatrix.multiplyMatrices(viewProjectionMatrix, mesh.matrixWorld)
        currentDevice.queue.writeBuffer(
            renderable.uniformBuffer,
            0uL,
            modelViewProjectionMatrix.elements.toByteArray()
        )

        pass.setPipeline(pipeline.pipeline)
        pass.setBindGroup(0u, renderable.bindGroup)
        pass.setVertexBuffer(0u, renderable.vertexBuffer)

        mesh.onBeforeRender?.invoke(mesh)
        val indexBuffer = renderable.indexBuffer
        val indexFormat = renderable.indexFormat
        if (indexBuffer != null && renderable.indexCount > 0 && indexFormat != null) {
            pass.setIndexBuffer(indexBuffer, indexFormat, 0uL)
            pass.drawIndexed(renderable.indexCount.toUInt(), 1u, 0u, 0, 0u)
        } else {
            pass.draw(renderable.vertexCount.toUInt(), 1u, 0u, 0u)
        }
        mesh.onAfterRender?.invoke(mesh)

        return renderable.triangles
    }

    private fun drawPoints(pass: GPURenderPassEncoder, points: Points, renderable: CachedPoints): Int {
        val pipeline = pipelineCache.getOrPut(renderable.pipelineKey) { createPipeline(renderable.pipelineKey) }
        val currentDevice = device ?: return 0

        modelViewProjectionMatrix.multiplyMatrices(viewProjectionMatrix, points.matrixWorld)
        currentDevice.queue.writeBuffer(
            renderable.uniformBuffer,
            0uL,
            modelViewProjectionMatrix.elements.toByteArray()
        )

        pass.setPipeline(pipeline.pipeline)
        pass.setBindGroup(0u, renderable.bindGroup)
        pass.setVertexBuffer(0u, renderable.instanceBuffer)

        points.onBeforeRender?.invoke(points)
        pass.draw(POINT_QUAD_VERTICES.toUInt(), renderable.instanceCount.toUInt(), 0u, 0u)
        points.onAfterRender?.invoke(points)

        return renderable.triangles
    }

    private fun configureSurface(width: Int, height: Int) {
        val context = wgpuContext ?: return
        val currentDevice = device ?: return
        context.surface.configure(
            SurfaceConfiguration(
                device = currentDevice,
                format = SURFACE_FORMAT,
                usage = setOf(GPUTextureUsage.RenderAttachment),
                alphaMode = CompositeAlphaMode.Opaque
            )
        )

        surfaceWidth = width
        surfaceHeight = height
        ensureDepthTexture(width, height)
    }

    private fun ensureDepthTexture(width: Int, height: Int) {
        val currentDevice = device ?: return
        if (depthTexture != null && depthTextureWidth == width && depthTextureHeight == height) {
            return
        }

        depthTexture?.close()
        depthTexture = currentDevice.createTexture(
            TextureDescriptor(
                label = "android-depth-texture",
                size = Extent3D(width.toUInt(), height.toUInt(), 1u),
                mipLevelCount = 1u,
                sampleCount = 1u,
                dimension = GPUTextureDimension.TwoD,
                format = GPUTextureFormat.Depth24Plus,
                usage = setOf(GPUTextureUsage.RenderAttachment)
            )
        )
        depthTextureView = depthTexture?.createView(TextureViewDescriptor(label = "android-depth-view"))
        depthTextureWidth = width
        depthTextureHeight = height
    }

    private fun createPipeline(key: PipelineKey): PipelineResources {
        val currentDevice = device ?: error("Android renderer device is not available.")
        val layout = uniformLayout ?: error("Android renderer uniform layout is not available.")
        val pipelineLayout = currentDevice.createPipelineLayout(
            PipelineLayoutDescriptor(
                bindGroupLayouts = listOf(layout)
            )
        )

        val vertexModule = currentDevice.createShaderModule(
            ShaderModuleDescriptor(
                label = if (key.isPoints) "android-points.vert" else "android-mesh.vert",
                code = if (key.isPoints) POINTS_VERTEX_SHADER else MESH_VERTEX_SHADER
            )
        )
        val fragmentModule = currentDevice.createShaderModule(
            ShaderModuleDescriptor(
                label = if (key.isPoints) "android-points.frag" else "android-mesh.frag",
                code = if (key.isPoints) POINTS_FRAGMENT_SHADER else MESH_FRAGMENT_SHADER
            )
        )

        val pipeline = currentDevice.createRenderPipeline(
            RenderPipelineDescriptor(
                label = if (key.isPoints) "android-points-pipeline" else "android-mesh-pipeline",
                layout = pipelineLayout,
                vertex = VertexState(
                    module = vertexModule,
                    entryPoint = "main",
                    buffers = listOf(if (key.isPoints) pointsVertexLayout() else meshVertexLayout())
                ),
                fragment = FragmentState(
                    module = fragmentModule,
                    entryPoint = "main",
                    targets = listOf(
                        ColorTargetState(
                            format = SURFACE_FORMAT,
                            blend = key.blendMode.toBlendState()
                        )
                    )
                ),
                primitive = PrimitiveState(
                    topology = key.topology,
                    frontFace = GPUFrontFace.CCW,
                    cullMode = key.cullMode
                ),
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth24Plus,
                    depthWriteEnabled = key.depthTest && key.depthWrite,
                    depthCompare = if (key.depthTest) GPUCompareFunction.LessEqual else GPUCompareFunction.Always
                )
            )
        )

        return PipelineResources(pipeline)
    }

    private fun createUniformLayout(currentDevice: GPUDevice): GPUBindGroupLayout {
        return currentDevice.createBindGroupLayout(
            BindGroupLayoutDescriptor(
                label = "android-uniform-layout",
                entries = listOf(
                    BindGroupLayoutEntry(
                        binding = 0u,
                        visibility = setOf(GPUShaderStage.Vertex),
                        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform)
                    )
                )
            )
        )
    }

    private fun createUniformBuffer(id: Int): GPUBuffer {
        val currentDevice = device ?: error("Android renderer device is not available.")
        return currentDevice.createBuffer(
            BufferDescriptor(
                label = "android-uniform-$id",
                size = MATRIX_BYTE_SIZE.toULong(),
                usage = setOf(GPUBufferUsage.Uniform, GPUBufferUsage.CopyDst),
                mappedAtCreation = false
            )
        )
    }

    private fun createUniformBindGroup(id: Int, uniformBuffer: GPUBuffer): GPUBindGroup {
        val layout = uniformLayout ?: error("Android renderer uniform layout is not available.")
        val currentDevice = device ?: error("Android renderer device is not available.")
        return currentDevice.createBindGroup(
            BindGroupDescriptor(
                label = "android-bind-group-$id",
                layout = layout,
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = BufferBinding(
                            buffer = uniformBuffer,
                            offset = 0uL,
                            size = MATRIX_BYTE_SIZE.toULong()
                        )
                    )
                )
            )
        )
    }

    private fun createBuffer(size: Int, usage: Set<GPUBufferUsage>, label: String): GPUBuffer {
        val currentDevice = device ?: error("Android renderer device is not available.")
        return currentDevice.createBuffer(
            BufferDescriptor(
                label = label,
                size = size.toULong(),
                usage = usage,
                mappedAtCreation = false
            )
        )
    }

    private fun buildMeshPayload(mesh: Mesh, materialState: MaterialState): MeshPayload? {
        val geometry = mesh.geometry
        val positionAttribute = geometry.getAttribute("position") ?: return null
        if (positionAttribute.count == 0) {
            return null
        }

        val colorAttribute = geometry.getAttribute("color")
        val vertexBytes = buildMeshVertexBytes(positionAttribute, colorAttribute, materialState)
        val drawData = buildDrawData(mesh.drawMode, geometry.index, positionAttribute.count)

        return MeshPayload(
            vertexBytes = vertexBytes,
            vertexCount = positionAttribute.count,
            indexBytes = drawData.indexBytes,
            indexCount = drawData.indexCount,
            indexFormat = drawData.indexFormat,
            topology = drawData.topology,
            triangleCount = drawData.triangleCount
        )
    }

    private fun buildPointsPayload(points: Points, materialState: MaterialState): PointsPayload? {
        val geometry = points.geometry
        val instancedPosition = geometry.getInstancedAttribute(InstancedPointsGeometry.POSITION_ATTRIBUTE)
        val instancedColor = geometry.getInstancedAttribute(InstancedPointsGeometry.COLOR_ATTRIBUTE)
        val instancedSize = geometry.getInstancedAttribute(InstancedPointsGeometry.SIZE_ATTRIBUTE)
        val position = instancedPosition ?: geometry.getAttribute("position") ?: return null
        if (position.count == 0) {
            return null
        }

        val color = instancedColor ?: geometry.getAttribute("color")
        val size = instancedSize ?: geometry.getAttribute("size")
        val defaultSize = (points.material as? PointsMaterial)?.size ?: 1f

        return PointsPayload(
            instanceBytes = buildPointsInstanceBytes(
                positions = position,
                colors = color,
                sizes = size,
                materialState = materialState,
                defaultSize = defaultSize
            ),
            instanceCount = position.count,
            triangleCount = position.count * 2
        )
    }

    private fun buildMeshVertexBytes(
        positions: BufferAttribute,
        colors: BufferAttribute?,
        materialState: MaterialState
    ): ByteArray {
        val floats = FloatArray(positions.count * MESH_COMPONENTS_PER_VERTEX)
        var writeIndex = 0
        val baseAlpha = materialState.color.a * materialState.opacity
        val volumeSampler = materialState.volumeTexture?.let(VolumeTextureSampler::from)

        for (index in 0 until positions.count) {
            val positionX = positions.getX(index)
            val positionY = positions.getY(index)
            val positionZ = positions.getZ(index)

            floats[writeIndex++] = positionX
            floats[writeIndex++] = positionY
            floats[writeIndex++] = positionZ

            var red: Float
            var green: Float
            var blue: Float
            var alpha: Float

            if (colors != null && colors.itemSize >= 3) {
                red = colors.getX(index) * materialState.color.r
                green = colors.getY(index) * materialState.color.g
                blue = colors.getZ(index) * materialState.color.b
                alpha = if (colors.itemSize >= 4) colors.getW(index) * baseAlpha else baseAlpha
            } else {
                red = materialState.color.r
                green = materialState.color.g
                blue = materialState.color.b
                alpha = baseAlpha
            }

            volumeSampler?.sampleLocalPosition(positionX, positionY, positionZ)?.let { sample ->
                red *= sample.r
                green *= sample.g
                blue *= sample.b
                alpha *= sample.a
            }

            floats[writeIndex++] = red
            floats[writeIndex++] = green
            floats[writeIndex++] = blue
            floats[writeIndex++] = alpha
        }

        return floats.toByteArray()
    }

    private fun buildPointsInstanceBytes(
        positions: BufferAttribute,
        colors: BufferAttribute?,
        sizes: BufferAttribute?,
        materialState: MaterialState,
        defaultSize: Float
    ): ByteArray {
        val floats = FloatArray(positions.count * POINT_COMPONENTS_PER_INSTANCE)
        var writeIndex = 0
        val baseAlpha = materialState.color.a * materialState.opacity

        for (index in 0 until positions.count) {
            floats[writeIndex++] = positions.getX(index)
            floats[writeIndex++] = positions.getY(index)
            floats[writeIndex++] = positions.getZ(index)

            if (colors != null && colors.itemSize >= 3) {
                floats[writeIndex++] = colors.getX(index) * materialState.color.r
                floats[writeIndex++] = colors.getY(index) * materialState.color.g
                floats[writeIndex++] = colors.getZ(index) * materialState.color.b
                floats[writeIndex++] = if (colors.itemSize >= 4) colors.getW(index) * baseAlpha else baseAlpha
            } else {
                floats[writeIndex++] = materialState.color.r
                floats[writeIndex++] = materialState.color.g
                floats[writeIndex++] = materialState.color.b
                floats[writeIndex++] = baseAlpha
            }

            floats[writeIndex++] = max(0.5f, sizes?.getX(index) ?: defaultSize)
        }

        return floats.toByteArray()
    }

    private fun buildDrawData(
        drawMode: DrawMode,
        indexAttribute: BufferAttribute?,
        vertexCount: Int
    ): DrawData {
        val sourceIndices = indexAttribute?.let { attribute ->
            IntArray(attribute.count) { attribute.getX(it).roundToInt() }
        }

        return when (drawMode) {
            DrawMode.TRIANGLES -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.TriangleList,
                indices = sourceIndices,
                vertexCount = vertexCount
            )

            DrawMode.TRIANGLE_STRIP -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.TriangleStrip,
                indices = sourceIndices,
                vertexCount = vertexCount
            )

            DrawMode.TRIANGLE_FAN -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.TriangleList,
                indices = expandTriangleFan(sourceIndices ?: IntArray(vertexCount) { it }),
                vertexCount = vertexCount
            )

            DrawMode.LINES -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.LineList,
                indices = sourceIndices,
                vertexCount = vertexCount
            )

            DrawMode.LINE_STRIP -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.LineStrip,
                indices = sourceIndices,
                vertexCount = vertexCount
            )

            DrawMode.LINE_LOOP -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.LineList,
                indices = expandLineLoop(sourceIndices ?: IntArray(vertexCount) { it }),
                vertexCount = vertexCount
            )

            DrawMode.POINTS -> createIndexedDrawData(
                topology = GPUPrimitiveTopology.PointList,
                indices = sourceIndices,
                vertexCount = vertexCount
            )
        }
    }

    private fun createIndexedDrawData(
        topology: GPUPrimitiveTopology,
        indices: IntArray?,
        vertexCount: Int
    ): DrawData {
        if (indices == null || indices.isEmpty()) {
            return DrawData(
                topology = topology,
                indexBytes = null,
                indexCount = 0,
                indexFormat = null,
                triangleCount = estimateTriangleCount(topology, vertexCount, 0)
            )
        }

        val maxIndex = indices.maxOrNull() ?: 0
        val useUint32 = maxIndex > MAX_UNSIGNED_SHORT
        return DrawData(
            topology = topology,
            indexBytes = indices.toByteArray(useUint32),
            indexCount = indices.size,
            indexFormat = if (useUint32) GPUIndexFormat.Uint32 else GPUIndexFormat.Uint16,
            triangleCount = estimateTriangleCount(topology, vertexCount, indices.size)
        )
    }

    private fun expandTriangleFan(indices: IntArray): IntArray {
        if (indices.size < 3) {
            return IntArray(0)
        }

        val expanded = IntArray((indices.size - 2) * 3)
        var writeIndex = 0
        for (index in 1 until indices.lastIndex) {
            expanded[writeIndex++] = indices[0]
            expanded[writeIndex++] = indices[index]
            expanded[writeIndex++] = indices[index + 1]
        }
        return expanded
    }

    private fun expandLineLoop(indices: IntArray): IntArray {
        if (indices.size < 2) {
            return IntArray(0)
        }

        val expanded = IntArray(indices.size * 2)
        var writeIndex = 0
        for (index in indices.indices) {
            expanded[writeIndex++] = indices[index]
            expanded[writeIndex++] = indices[(index + 1) % indices.size]
        }
        return expanded
    }

    private fun resolveMaterialState(material: io.materia.core.scene.Material?): MaterialState {
        return when (material) {
            is MeshBasicMaterial -> MaterialState(
                color = material.color.clone(),
                opacity = material.opacity,
                blendMode = when {
                    material.blending == Blending.AdditiveBlending -> AndroidBlendMode.ADDITIVE
                    material.transparent || material.opacity < 1f -> AndroidBlendMode.ALPHA
                    else -> AndroidBlendMode.OPAQUE
                },
                cullMode = material.side.toGpuCullMode(),
                depthWrite = material.depthWrite,
                depthTest = material.depthTest,
                visible = material.visible,
                volumeTexture = material.map as? Data3DTexture
            )

            is MeshStandardMaterial -> MaterialState(
                color = material.color.clone(),
                opacity = material.opacity,
                blendMode = when {
                    material.blending == io.materia.material.BlendMode.ADDITIVE -> AndroidBlendMode.ADDITIVE
                    material.transparent || material.opacity < 1f -> AndroidBlendMode.ALPHA
                    else -> AndroidBlendMode.OPAQUE
                },
                cullMode = material.side.toGpuCullMode(),
                depthWrite = material.depthWrite,
                depthTest = material.depthTest,
                visible = material.visible
            )

            is PointsMaterial -> MaterialState(
                color = material.color.clone(),
                opacity = material.opacity,
                blendMode = when {
                    material.blending == Blending.AdditiveBlending -> AndroidBlendMode.ADDITIVE
                    material.transparent || material.opacity < 1f -> AndroidBlendMode.ALPHA
                    else -> AndroidBlendMode.OPAQUE
                },
                cullMode = GPUCullMode.None,
                depthWrite = material.depthWrite,
                depthTest = material.depthTest,
                visible = material.visible
            )

            is CommonMaterial -> MaterialState(
                color = Color.WHITE,
                opacity = material.opacity,
                blendMode = when {
                    material.blending == Blending.AdditiveBlending -> AndroidBlendMode.ADDITIVE
                    material.transparent || material.opacity < 1f -> AndroidBlendMode.ALPHA
                    else -> AndroidBlendMode.OPAQUE
                },
                cullMode = material.side.toGpuCullMode(),
                depthWrite = material.depthWrite,
                depthTest = material.depthTest,
                visible = material.visible
            )

            else -> MaterialState()
        }
    }

    private fun updateClearColor(scene: Scene) {
        val background = scene.background
        clearColor = when (background) {
            is Background.Color -> floatArrayOf(
                background.color.r,
                background.color.g,
                background.color.b,
                background.color.a
            )

            is Background.Gradient -> floatArrayOf(
                (background.top.r + background.bottom.r) * 0.5f,
                (background.top.g + background.bottom.g) * 0.5f,
                (background.top.b + background.bottom.b) * 0.5f,
                1f
            )

            else -> DEFAULT_CLEAR_COLOR.copyOf()
        }
    }

    private fun updateStats(drawCalls: Int, triangles: Int, frameStart: Long) {
        val frameTimeMs = (System.nanoTime() - frameStart) / 1_000_000.0
        val fps = if (frameTimeMs > 0.0) 1000.0 / frameTimeMs else renderStats.fps
        val bufferMemory = renderables.values.sumOf { it.bufferBytes }

        renderStats = RenderStats(
            fps = fps,
            frameTime = frameTimeMs,
            triangles = triangles,
            drawCalls = drawCalls,
            bufferMemory = bufferMemory,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun releaseUnusedRenderables() {
        val iterator = renderables.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!visitedIds.contains(entry.key)) {
                entry.value.dispose()
                iterator.remove()
            }
        }
    }

    private fun queryCapabilities(context: WGPUContext): RendererCapabilities {
        val adapter = context.adapter
        val info = adapter.info
        val limits = adapter.limits

        return RendererCapabilities(
            backend = backend,
            deviceName = info.device,
            driverVersion = info.description ?: "Vulkan via wgpu4k",
            supportsCompute = true,
            supportsRayTracing = false,
            supportsMultisampling = false,
            maxTextureSize = limits.maxTextureDimension2D.toInt(),
            maxCubeMapSize = limits.maxTextureDimension2D.toInt(),
            maxVertexAttributes = limits.maxVertexAttributes.toInt(),
            maxVertexUniforms = limits.maxUniformBuffersPerShaderStage.toInt(),
            maxFragmentUniforms = limits.maxUniformBuffersPerShaderStage.toInt(),
            maxVertexTextures = limits.maxSampledTexturesPerShaderStage.toInt(),
            maxFragmentTextures = limits.maxSamplersPerShaderStage.toInt(),
            maxCombinedTextures = max(
                limits.maxSampledTexturesPerShaderStage.toInt(),
                limits.maxSamplersPerShaderStage.toInt()
            ),
            maxTextureSize3D = limits.maxTextureDimension3D.toInt(),
            maxTextureArrayLayers = limits.maxTextureArrayLayers.toInt(),
            maxColorAttachments = 1,
            maxSamples = 1,
            maxUniformBufferSize = limits.maxUniformBufferBindingSize.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            maxUniformBufferBindings = limits.maxBindGroups.toInt(),
            maxAnisotropy = 1f,
            textureFormats = setOf(
                TextureFormat.RGBA8,
                TextureFormat.RGB8,
                TextureFormat.RGBA16F,
                TextureFormat.RGBA32F
            ),
            depthFormats = setOf(DepthFormat.DEPTH24_STENCIL8),
            extensions = setOf("wgpu4k", "android-surface"),
            vendor = info.vendor ?: (Build.MANUFACTURER ?: "Unknown"),
            renderer = info.device,
            version = "Vulkan via wgpu4k",
            shadingLanguageVersion = "WGSL",
            instancedRendering = true,
            multipleRenderTargets = true,
            depthTextures = true,
            floatTextures = true,
            halfFloatTextures = true,
            floatTextureLinear = true,
            standardDerivatives = true,
            vertexArrayObjects = true,
            computeShaders = true,
            shadowMaps = true,
            shadowMapComparison = true,
            shadowMapPCF = true,
            asyncOperations = true
        )
    }

    private fun estimateTriangleCount(topology: GPUPrimitiveTopology, vertexCount: Int, indexCount: Int): Int {
        val effectiveCount = if (indexCount > 0) indexCount else vertexCount
        return when (topology) {
            GPUPrimitiveTopology.TriangleList -> effectiveCount / 3
            GPUPrimitiveTopology.TriangleStrip -> max(0, effectiveCount - 2)
            GPUPrimitiveTopology.PointList -> effectiveCount
            else -> 0
        }
    }

    private fun meshVertexLayout(): VertexBufferLayout {
        return VertexBufferLayout(
            arrayStride = (MESH_COMPONENTS_PER_VERTEX * Float.SIZE_BYTES).toULong(),
            stepMode = GPUVertexStepMode.Vertex,
            attributes = listOf(
                VertexAttribute(
                    format = GPUVertexFormat.Float32x3,
                    offset = 0uL,
                    shaderLocation = 0u
                ),
                VertexAttribute(
                    format = GPUVertexFormat.Float32x4,
                    offset = (3 * Float.SIZE_BYTES).toULong(),
                    shaderLocation = 1u
                )
            )
        )
    }

    private fun pointsVertexLayout(): VertexBufferLayout {
        return VertexBufferLayout(
            arrayStride = (POINT_COMPONENTS_PER_INSTANCE * Float.SIZE_BYTES).toULong(),
            stepMode = GPUVertexStepMode.Instance,
            attributes = listOf(
                VertexAttribute(
                    format = GPUVertexFormat.Float32x3,
                    offset = 0uL,
                    shaderLocation = 0u
                ),
                VertexAttribute(
                    format = GPUVertexFormat.Float32x4,
                    offset = (3 * Float.SIZE_BYTES).toULong(),
                    shaderLocation = 1u
                ),
                VertexAttribute(
                    format = GPUVertexFormat.Float32,
                    offset = (7 * Float.SIZE_BYTES).toULong(),
                    shaderLocation = 2u
                )
            )
        )
    }

    private fun ensureInitialised() {
        check(initialised) { "Android renderer has not been initialised. Call initialize() first." }
    }

    private fun Side.toGpuCullMode(): GPUCullMode = when (this) {
        Side.FrontSide -> GPUCullMode.Back
        Side.BackSide -> GPUCullMode.Front
        Side.DoubleSide -> GPUCullMode.None
    }

    private fun MaterialSide.toGpuCullMode(): GPUCullMode = when (this) {
        MaterialSide.FRONT -> GPUCullMode.Back
        MaterialSide.BACK -> GPUCullMode.Front
        MaterialSide.DOUBLE -> GPUCullMode.None
    }

    private fun AndroidBlendMode.toBlendState(): BlendState? = when (this) {
        AndroidBlendMode.OPAQUE -> null
        AndroidBlendMode.ALPHA -> BlendState(
            color = BlendComponent(
                srcFactor = GPUBlendFactor.SrcAlpha,
                dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                operation = GPUBlendOperation.Add
            ),
            alpha = BlendComponent(
                srcFactor = GPUBlendFactor.One,
                dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                operation = GPUBlendOperation.Add
            )
        )

        AndroidBlendMode.ADDITIVE -> BlendState(
            color = BlendComponent(
                srcFactor = GPUBlendFactor.One,
                dstFactor = GPUBlendFactor.One,
                operation = GPUBlendOperation.Add
            ),
            alpha = BlendComponent(
                srcFactor = GPUBlendFactor.One,
                dstFactor = GPUBlendFactor.One,
                operation = GPUBlendOperation.Add
            )
        )
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(size * Float.SIZE_BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun IntArray.toByteArray(useUint32: Boolean): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(size * if (useUint32) Int.SIZE_BYTES else Short.SIZE_BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        forEach { value ->
            if (useUint32) {
                buffer.putInt(value)
            } else {
                buffer.putShort(value.toShort())
            }
        }
        return buffer.array()
    }

    private data class MaterialState(
        val color: Color = Color.WHITE,
        val opacity: Float = 1f,
        val blendMode: AndroidBlendMode = AndroidBlendMode.OPAQUE,
        val cullMode: GPUCullMode = GPUCullMode.Back,
        val depthWrite: Boolean = true,
        val depthTest: Boolean = true,
        val visible: Boolean = true,
        val volumeTexture: Data3DTexture? = null
    )

    private data class DrawData(
        val topology: GPUPrimitiveTopology,
        val indexBytes: ByteArray?,
        val indexCount: Int,
        val indexFormat: GPUIndexFormat?,
        val triangleCount: Int
    )

    private data class MeshPayload(
        val vertexBytes: ByteArray,
        val vertexCount: Int,
        val indexBytes: ByteArray?,
        val indexCount: Int,
        val indexFormat: GPUIndexFormat?,
        val topology: GPUPrimitiveTopology,
        val triangleCount: Int
    )

    private data class PointsPayload(
        val instanceBytes: ByteArray,
        val instanceCount: Int,
        val triangleCount: Int
    )

    private data class PipelineKey(
        val isPoints: Boolean,
        val topology: GPUPrimitiveTopology,
        val blendMode: AndroidBlendMode,
        val cullMode: GPUCullMode,
        val depthWrite: Boolean,
        val depthTest: Boolean
    )

    private data class PipelineResources(
        val pipeline: GPURenderPipeline
    )

    private sealed class CachedRenderable {
        abstract val bufferBytes: Long
        abstract fun dispose()
    }

    private class CachedMesh(
        val vertexBuffer: GPUBuffer,
        val vertexByteSize: Int,
        var indexBuffer: GPUBuffer?,
        val indexByteSize: Int,
        val uniformBuffer: GPUBuffer,
        var bindGroup: GPUBindGroup,
        var pipelineKey: PipelineKey,
        var vertexCount: Int,
        var indexCount: Int,
        var indexFormat: GPUIndexFormat?,
        var triangles: Int
    ) : CachedRenderable() {
        override val bufferBytes: Long
            get() = vertexByteSize.toLong() + indexByteSize.toLong() + MATRIX_BYTE_SIZE.toLong()

        override fun dispose() {
            vertexBuffer.close()
            indexBuffer?.close()
            uniformBuffer.close()
        }
    }

    private class CachedPoints(
        val instanceBuffer: GPUBuffer,
        val instanceByteSize: Int,
        val uniformBuffer: GPUBuffer,
        var bindGroup: GPUBindGroup,
        var pipelineKey: PipelineKey,
        var instanceCount: Int,
        var triangles: Int
    ) : CachedRenderable() {
        override val bufferBytes: Long
            get() = instanceByteSize.toLong() + MATRIX_BYTE_SIZE.toLong()

        override fun dispose() {
            instanceBuffer.close()
            uniformBuffer.close()
        }
    }

    private enum class AndroidBlendMode {
        OPAQUE,
        ALPHA,
        ADDITIVE
    }

    private companion object {
        private const val MAX_UNSIGNED_SHORT = 65_535
        private const val MATRIX_BYTE_SIZE = 16 * Float.SIZE_BYTES
        private const val MESH_COMPONENTS_PER_VERTEX = 7
        private const val POINT_COMPONENTS_PER_INSTANCE = 8
        private const val POINT_QUAD_VERTICES = 6

        private val SURFACE_FORMAT = GPUTextureFormat.BGRA8Unorm
        private val DEFAULT_CLEAR_COLOR = floatArrayOf(0f, 0f, 0f, 1f)

        private const val MESH_VERTEX_SHADER = """
            struct VertexInput {
                @location(0) position : vec3<f32>,
                @location(1) color : vec4<f32>,
            };

            struct VertexOutput {
                @builtin(position) position : vec4<f32>,
                @location(0) color : vec4<f32>,
            };

            @group(0) @binding(0)
            var<uniform> uModelViewProjection : mat4x4<f32>;

            @vertex
            fn main(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = uModelViewProjection * vec4<f32>(input.position, 1.0);
                output.color = input.color;
                return output;
            }
        """

        private const val MESH_FRAGMENT_SHADER = """
            @fragment
            fn main(@location(0) color : vec4<f32>) -> @location(0) vec4<f32> {
                return color;
            }
        """

        private const val POINTS_VERTEX_SHADER = """
            struct VertexInput {
                @location(0) instancePosition : vec3<f32>,
                @location(1) instanceColor : vec4<f32>,
                @location(2) instanceSize : f32,
            };

            struct VertexOutput {
                @builtin(position) position : vec4<f32>,
                @location(0) color : vec4<f32>,
            };

            @group(0) @binding(0)
            var<uniform> uModelViewProjection : mat4x4<f32>;

            @vertex
            fn main(@builtin(vertex_index) vertexIndex : u32, input : VertexInput) -> VertexOutput {
                var output : VertexOutput;

                var quadOffsets = array<vec2<f32>, 6>(
                    vec2<f32>(-1.0, -1.0),
                    vec2<f32>( 1.0, -1.0),
                    vec2<f32>(-1.0,  1.0),
                    vec2<f32>(-1.0,  1.0),
                    vec2<f32>( 1.0, -1.0),
                    vec2<f32>( 1.0,  1.0)
                );

                let clipPosition = uModelViewProjection * vec4<f32>(input.instancePosition, 1.0);
                let offset = quadOffsets[vertexIndex];
                let pointScale = max(input.instanceSize * 0.012, 0.004);

                var finalPosition = clipPosition;
                finalPosition.x = finalPosition.x + offset.x * pointScale * clipPosition.w;
                finalPosition.y = finalPosition.y + offset.y * pointScale * clipPosition.w;

                output.position = finalPosition;
                output.color = input.instanceColor;
                return output;
            }
        """

        private const val POINTS_FRAGMENT_SHADER = """
            @fragment
            fn main(@location(0) color : vec4<f32>) -> @location(0) vec4<f32> {
                return color;
            }
        """
    }

    private var depthTextureWidth: Int = 0
    private var depthTextureHeight: Int = 0
}