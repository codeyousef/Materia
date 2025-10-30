package io.materia.renderer.webgl

import io.materia.camera.Camera
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.scene.Background
import io.materia.core.scene.DrawMode
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.material.MeshBasicMaterial
import io.materia.points.Points
import io.materia.points.PointsMaterial
import io.materia.renderer.BackendType
import io.materia.renderer.DepthFormat
import io.materia.renderer.PowerPreference
import io.materia.renderer.RenderStats
import io.materia.renderer.Renderer
import io.materia.renderer.RendererCapabilities
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererInitializationException
import io.materia.renderer.TextureFormat
import io.materia.geometry.InstancedPointsGeometry
import kotlinx.browser.window
import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.ARRAY_BUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.COMPILE_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_TEST
import org.khronos.webgl.WebGLRenderingContext.Companion.ELEMENT_ARRAY_BUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FLOAT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.LINES
import org.khronos.webgl.WebGLRenderingContext.Companion.LINE_LOOP
import org.khronos.webgl.WebGLRenderingContext.Companion.LINE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.LINK_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.POINTS
import org.khronos.webgl.WebGLRenderingContext.Companion.SHADER_TYPE
import org.khronos.webgl.WebGLRenderingContext.Companion.STATIC_DRAW
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLES
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_FAN
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.khronos.webgl.WebGLUniformLocation
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Minimal WebGL renderer used as the JavaScript fallback when WebGPU is unavailable.
 *
 * The renderer supports basic mesh rendering with per-vertex colours derived from geometry
 * attributes or MeshBasicMaterial colour settings. It deliberately focuses on predictable,
 * well-defined behaviour so that automated checks can verify visual output.
 */
class WebGLRenderer(
    private val canvas: HTMLCanvasElement
) : Renderer {

    override val backend: BackendType = BackendType.WEBGL

    override var stats: RenderStats = RenderStats(0.0, 0.0, 0, 0)
        private set

    override val capabilities: RendererCapabilities
        get() = rendererCapabilities

    private lateinit var gl: WebGLRenderingContext
    private var isWebGL2Context: Boolean = false
    private var program: WebGLProgram? = null
    private var vertexShader: org.khronos.webgl.WebGLShader? = null
    private var fragmentShader: org.khronos.webgl.WebGLShader? = null
    private var positionLocation: Int = -1
    private var colorLocation: Int = -1
    private var sizeLocation: Int = -1
    private var mvpLocation: WebGLUniformLocation? = null

    private var rendererCapabilities: RendererCapabilities =
        RendererCapabilities(backend = BackendType.WEBGL)
    private var supportsUint32Indices: Boolean = false
    private var anisotropyExtension: dynamic = null

    private var initialised = false

    private val meshBuffers: MutableMap<Int, MeshBuffers> = mutableMapOf()
    private val visitedIds: MutableSet<Int> = mutableSetOf()

    private val viewProjectionMatrix = Matrix4()
    private val modelViewProjectionMatrix = Matrix4()
    private val matrixBuffer = Float32Array(16)

    private var clearColor = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)

    override suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit> {
        return try {
            setupContext(config)
            setupProgram()
            rendererCapabilities = queryCapabilities()
            configureDefaultState()
            resize(canvas.width.takeIf { it > 0 } ?: canvas.clientWidth,
                canvas.height.takeIf { it > 0 } ?: canvas.clientHeight)
            initialised = true
            io.materia.core.Result.Success(Unit)
        } catch (ex: RendererInitializationException) {
            io.materia.core.Result.Error(ex.message ?: "Failed to initialise WebGL renderer", ex)
        } catch (ex: Throwable) {
            val wrapped = RendererInitializationException.DeviceCreationFailedException(
                backend = BackendType.WEBGL,
                adapterInfo = "WebGL context",
                reason = ex.message ?: "Unknown error"
            )
            io.materia.core.Result.Error(
                wrapped.message ?: "Failed to initialise WebGL renderer",
                wrapped
            )
        }
    }

    override fun render(scene: Scene, camera: Camera) {
        ensureInitialised()

        updateClearColor(scene)
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clear(COLOR_BUFFER_BIT or DEPTH_BUFFER_BIT)

        scene.updateMatrixWorld(true)
        camera.updateMatrixWorld(false)
        camera.updateProjectionMatrix()

        viewProjectionMatrix.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)

        gl.useProgram(program)

        val startTime = window.performance.now()
        var drawCalls = 0
        var triangles = 0
        visitedIds.clear()

        scene.traverseVisible { node ->
            when {
                node is Mesh && node.visible -> {
                    val buffers = prepareMeshBuffers(node) ?: return@traverseVisible
                    visitedIds.add(node.id)
                    triangles += drawMesh(node, buffers, viewProjectionMatrix)
                    drawCalls++
                }

                node is Points && node.visible -> {
                    val buffers = preparePointsBuffers(node) ?: return@traverseVisible
                    visitedIds.add(node.id)
                    triangles += drawPoints(node, buffers, viewProjectionMatrix)
                    drawCalls++
                }
            }
        }

        releaseUnusedBuffers()

        val endTime = window.performance.now()
        updateStats(drawCalls, triangles, endTime - startTime, endTime)
    }

    override fun resize(width: Int, height: Int) {
        val safeWidth = max(1, width)
        val safeHeight = max(1, height)

        canvas.width = safeWidth
        canvas.height = safeHeight
        canvas.style.width = "${safeWidth}px"
        canvas.style.height = "${safeHeight}px"

        if (initialised) {
            gl.viewport(0, 0, safeWidth, safeHeight)
        }
    }

    override fun dispose() {
        if (!initialised) return

        meshBuffers.values.forEach { buffers ->
            gl.deleteBuffer(buffers.vertexBuffer)
            buffers.indexBuffer?.let { gl.deleteBuffer(it) }
        }
        meshBuffers.clear()

        program?.let { gl.deleteProgram(it) }
        vertexShader?.let { gl.deleteShader(it) }
        fragmentShader?.let { gl.deleteShader(it) }

        program = null
        vertexShader = null
        fragmentShader = null
        initialised = false
    }

    private fun setupContext(config: RendererConfig) {
        val attributes = buildContextAttributes(config)
        var candidate = tryGetContext("webgl2", attributes)
        var obtainedContext = candidate.context
        var webgl2Active = candidate.isWebGL2

        if (obtainedContext == null) {
            candidate = tryGetContext("webgl2", null)
            obtainedContext = candidate.context
            webgl2Active = candidate.isWebGL2
        }

        if (obtainedContext == null) {
            candidate = tryGetContext("webgl", attributes)
            obtainedContext = candidate.context
            webgl2Active = candidate.isWebGL2
        }

        if (obtainedContext == null) {
            candidate = tryGetContext("webgl", null)
            obtainedContext = candidate.context
            webgl2Active = candidate.isWebGL2
        }

        if (obtainedContext == null) {
            candidate = tryGetContext("experimental-webgl", attributes)
            obtainedContext = candidate.context
            webgl2Active = candidate.isWebGL2
        }

        if (obtainedContext == null) {
            candidate = tryGetContext("experimental-webgl", null)
            obtainedContext = candidate.context
            webgl2Active = candidate.isWebGL2
        }

        if (obtainedContext == null) {
            throw RendererInitializationException.SurfaceCreationFailedException(
                backend = BackendType.WEBGL,
                surfaceType = "HTMLCanvasElement"
            )
        }

        gl = obtainedContext
        isWebGL2Context = webgl2Active
        supportsUint32Indices = isWebGL2Context || gl.getExtension("OES_element_index_uint") != null
        anisotropyExtension = gl.getExtension("EXT_texture_filter_anisotropic")
    }

    private fun setupProgram() {
        val vertexSource = """
            attribute vec3 aPosition;
            attribute vec3 aColor;
            attribute float aSize;
            uniform mat4 uMVP;
            varying vec3 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uMVP * vec4(aPosition, 1.0);
                gl_PointSize = aSize;
            }
        """.trimIndent()

        val fragmentSource = """
            precision mediump float;
            varying vec3 vColor;
            void main() {
                gl_FragColor = vec4(vColor, 1.0);
            }
        """.trimIndent()

        val vertex = compileShader(VERTEX_SHADER, vertexSource, "webgl_basic.vert")
        val fragment = compileShader(FRAGMENT_SHADER, fragmentSource, "webgl_basic.frag")

        val linkedProgram = gl.createProgram() ?: error("Unable to create WebGL program")
        gl.attachShader(linkedProgram, vertex)
        gl.attachShader(linkedProgram, fragment)
        gl.linkProgram(linkedProgram)

        val linkStatus = gl.getProgramParameter(linkedProgram, LINK_STATUS) as? Boolean ?: false
        if (!linkStatus) {
            val log = gl.getProgramInfoLog(linkedProgram) ?: "Unknown error"
            gl.deleteProgram(linkedProgram)
            throw RendererInitializationException.DeviceCreationFailedException(
                backend = BackendType.WEBGL,
                adapterInfo = "WebGL shader program",
                reason = log
            )
        }

        program = linkedProgram
        vertexShader = vertex
        fragmentShader = fragment

        positionLocation = gl.getAttribLocation(linkedProgram, "aPosition")
        colorLocation = gl.getAttribLocation(linkedProgram, "aColor")
        sizeLocation = gl.getAttribLocation(linkedProgram, "aSize")
        mvpLocation = gl.getUniformLocation(linkedProgram, "uMVP")
    }

    private fun compileShader(
        type: Int,
        source: String,
        name: String
    ): org.khronos.webgl.WebGLShader {
        val shader = gl.createShader(type) ?: error("Unable to create shader")
        gl.shaderSource(shader, source)
        gl.compileShader(shader)

        val compiled = gl.getShaderParameter(shader, COMPILE_STATUS) as? Boolean ?: false
        if (!compiled) {
            val log = gl.getShaderInfoLog(shader) ?: "Unknown error"
            gl.deleteShader(shader)
            throw RendererInitializationException.ShaderCompilationException(name, listOf(log))
        }

        return shader
    }

    private fun configureDefaultState() {
        gl.enable(DEPTH_TEST)
        gl.clearDepth(1.0f)
        gl.useProgram(program)
        gl.enableVertexAttribArray(positionLocation)
        gl.enableVertexAttribArray(colorLocation)
        if (sizeLocation >= 0) {
            gl.enableVertexAttribArray(sizeLocation)
        }
    }

    private fun queryCapabilities(): RendererCapabilities {
        val maxTextureSize =
            (gl.getParameter(WebGLRenderingContext.MAX_TEXTURE_SIZE) as? Int) ?: 2048
        val maxCubeMapSize =
            (gl.getParameter(WebGLRenderingContext.MAX_CUBE_MAP_TEXTURE_SIZE) as? Int)
                ?: maxTextureSize
        val maxVertexAttribs =
            (gl.getParameter(WebGLRenderingContext.MAX_VERTEX_ATTRIBS) as? Int) ?: 16
        val maxVertexUniforms =
            (gl.getParameter(WebGLRenderingContext.MAX_VERTEX_UNIFORM_VECTORS) as? Int) ?: 256
        val maxFragmentUniforms =
            (gl.getParameter(WebGLRenderingContext.MAX_FRAGMENT_UNIFORM_VECTORS) as? Int) ?: 256
        val maxVertexTextures =
            (gl.getParameter(WebGLRenderingContext.MAX_VERTEX_TEXTURE_IMAGE_UNITS) as? Int) ?: 0
        val maxFragmentTextures =
            (gl.getParameter(WebGLRenderingContext.MAX_TEXTURE_IMAGE_UNITS) as? Int) ?: 16
        val maxCombinedTextures =
            (gl.getParameter(WebGLRenderingContext.MAX_COMBINED_TEXTURE_IMAGE_UNITS) as? Int)
                ?: (maxFragmentTextures + maxVertexTextures)
        val maxSamples = if (isWebGL2Context) {
            runCatching { gl.getParameter(MAX_SAMPLES_CONST) as? Int }.getOrNull() ?: 4
        } else 4

        val supportedTextureFormats = buildSet {
            add(TextureFormat.RGBA8)
            add(TextureFormat.RGB8)
            if (gl.getExtension("OES_texture_float") != null) {
                add(TextureFormat.RGBA32F)
            }
            if (gl.getExtension("OES_texture_half_float") != null) {
                add(TextureFormat.RGBA16F)
            }
        }

        val vendor = gl.getParameter(WebGLRenderingContext.VENDOR) as? String ?: "Unknown"
        val renderer = gl.getParameter(WebGLRenderingContext.RENDERER) as? String ?: vendor
        val version = gl.getParameter(WebGLRenderingContext.VERSION) as? String ?: "WebGL"
        val shadingLanguage =
            gl.getParameter(WebGLRenderingContext.SHADING_LANGUAGE_VERSION) as? String ?: "GLSL"

        val maxAnisotropy = if (anisotropyExtension != null) {
            val constant = anisotropyExtension?.MAX_TEXTURE_MAX_ANISOTROPY_EXT
            when (constant) {
                is Int -> (gl.getParameter(constant) as? Float) ?: 1f
                else -> 1f
            }
        } else 1f

        return RendererCapabilities(
            backend = BackendType.WEBGL,
            deviceName = renderer,
            driverVersion = version,
            supportsCompute = false,
            supportsRayTracing = false,
            supportsMultisampling = maxSamples > 1,
            maxTextureSize = maxTextureSize,
            maxCubeMapSize = maxCubeMapSize,
            maxVertexAttributes = maxVertexAttribs,
            maxVertexUniforms = maxVertexUniforms,
            maxFragmentUniforms = maxFragmentUniforms,
            maxVertexTextures = maxVertexTextures,
            maxFragmentTextures = maxFragmentTextures,
            maxCombinedTextures = maxCombinedTextures,
            maxColorAttachments = if (isWebGL2Context) {
                runCatching { gl.getParameter(MAX_COLOR_ATTACHMENTS_CONST) as? Int }.getOrNull()
                    ?: 1
            } else 1,
            maxSamples = maxSamples,
            maxAnisotropy = maxAnisotropy,
            maxUniformBufferSize = if (isWebGL2Context) {
                runCatching { gl.getParameter(MAX_UNIFORM_BLOCK_SIZE_CONST) as? Int }.getOrNull()
                    ?: 16384
            } else 16384,
            maxUniformBufferBindings = if (isWebGL2Context) {
                runCatching { gl.getParameter(MAX_UNIFORM_BUFFER_BINDINGS_CONST) as? Int }.getOrNull()
                    ?: 36
            } else 36,
            textureFormats = supportedTextureFormats,
            depthFormats = setOf(DepthFormat.DEPTH24_STENCIL8),
            extensions = gl.getSupportedExtensions()?.mapNotNull { it as? String }?.toSet()
                ?: emptySet(),
            vendor = vendor,
            renderer = renderer,
            version = version,
            shadingLanguageVersion = shadingLanguage,
            instancedRendering = isWebGL2Context || gl.getExtension("ANGLE_instanced_arrays") != null,
            multipleRenderTargets = isWebGL2Context,
            depthTextures = isWebGL2Context || gl.getExtension("WEBGL_depth_texture") != null,
            floatTextures = gl.getExtension("OES_texture_float") != null,
            halfFloatTextures = gl.getExtension("OES_texture_half_float") != null,
            floatTextureLinear = gl.getExtension("OES_texture_float_linear") != null,
            standardDerivatives = gl.getExtension("OES_standard_derivatives") != null,
            vertexArrayObjects = isWebGL2Context || gl.getExtension("OES_vertex_array_object") != null,
            computeShaders = false,
            geometryShaders = false,
            tessellation = false,
            shadowMaps = true,
            shadowMapComparison = gl.getExtension("EXT_shadow_samplers") != null,
            shadowMapPCF = true,
            parallelShaderCompile = gl.getExtension("KHR_parallel_shader_compile") != null,
            asyncOperations = false
        )
    }

    private fun prepareMeshBuffers(mesh: Mesh): MeshBuffers? {
        val geometry = mesh.geometry
        val positionAttribute = geometry.getAttribute("position") ?: return null
        val vertexCount = positionAttribute.count
        if (vertexCount == 0) return null

        val colorAttribute = geometry.getAttribute("color")
        val materialColor = (mesh.material as? MeshBasicMaterial)?.color ?: Color.WHITE
        val vertexData = buildVertexData(positionAttribute, colorAttribute, materialColor)

        val existing = meshBuffers[mesh.id]
        val vertexBuffer = existing?.vertexBuffer ?: gl.createBuffer()
        ?: throw RendererInitializationException.DeviceCreationFailedException(
            backend = BackendType.WEBGL,
            adapterInfo = "WebGL",
            reason = "gl.createBuffer returned null"
        )

        gl.bindBuffer(ARRAY_BUFFER, vertexBuffer)
        gl.bufferData(ARRAY_BUFFER, vertexData.array, STATIC_DRAW)

        val indexAttribute = geometry.index
        var indexBuffer = existing?.indexBuffer
        var indexCount = 0
        var indexByteSize = 0
        var usesUint32 = false

        if (indexAttribute != null && indexAttribute.count > 0) {
            val indexData = buildIndexData(indexAttribute)
            if (indexData != null) {
                if (indexBuffer == null) {
                    indexBuffer = gl.createBuffer()
                        ?: throw RendererInitializationException.DeviceCreationFailedException(
                            backend = BackendType.WEBGL,
                            adapterInfo = "WebGL",
                            reason = "gl.createBuffer returned null"
                        )
                }
                gl.bindBuffer(ELEMENT_ARRAY_BUFFER, indexBuffer)
                gl.bufferData(ELEMENT_ARRAY_BUFFER, indexData.array, STATIC_DRAW)
                indexCount = indexData.count
                indexByteSize = indexData.byteSize
                usesUint32 = indexData.usesUint32
            } else if (indexBuffer != null) {
                gl.deleteBuffer(indexBuffer)
                indexBuffer = null
            }
        } else if (indexBuffer != null) {
            gl.deleteBuffer(indexBuffer)
            indexBuffer = null
        }

        positionAttribute.needsUpdate = false
        colorAttribute?.needsUpdate = false
        geometry.index?.needsUpdate = false

        val drawMode = mapDrawMode(mesh.drawMode)
        val triangles = estimateTriangleCount(drawMode, vertexCount, indexCount)

        val buffers = MeshBuffers(
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            vertexByteSize = vertexData.byteSize,
            vertexCount = vertexCount,
            indexByteSize = indexByteSize,
            indexCount = indexCount,
            usesUint32 = usesUint32,
            drawMode = drawMode,
            triangles = triangles
        )
        meshBuffers[mesh.id] = buffers
        gl.bindBuffer(ARRAY_BUFFER, null)
        gl.bindBuffer(ELEMENT_ARRAY_BUFFER, null)
        return buffers
    }

    private fun preparePointsBuffers(points: Points): MeshBuffers? {
        val geometry = points.geometry
        val materialColor = (points.material as? PointsMaterial)?.color ?: Color.WHITE
        val defaultSize = (points.material as? PointsMaterial)?.size ?: 1f

        val instancedPosition =
            geometry.getInstancedAttribute(InstancedPointsGeometry.POSITION_ATTRIBUTE)
        val vertexCount: Int
        val vertexData: VertexData

        if (instancedPosition != null) {
            vertexCount = instancedPosition.count
            if (vertexCount == 0) return null
            val instancedColor =
                geometry.getInstancedAttribute(InstancedPointsGeometry.COLOR_ATTRIBUTE)
            val instancedSize =
                geometry.getInstancedAttribute(InstancedPointsGeometry.SIZE_ATTRIBUTE)
            vertexData = buildPointsVertexData(
                instancedPosition,
                instancedColor,
                instancedSize,
                materialColor,
                defaultSize
            )
        } else {
            val positionAttribute = geometry.getAttribute("position") ?: return null
            vertexCount = positionAttribute.count
            if (vertexCount == 0) return null
            val colorAttribute = geometry.getAttribute("color")
            vertexData = buildVertexData(positionAttribute, colorAttribute, materialColor)
        }

        val existing = meshBuffers[points.id]
        val vertexBuffer = existing?.vertexBuffer ?: gl.createBuffer()
        ?: throw RendererInitializationException.DeviceCreationFailedException(
            backend = BackendType.WEBGL,
            adapterInfo = "WebGL",
            reason = "gl.createBuffer returned null"
        )

        gl.bindBuffer(ARRAY_BUFFER, vertexBuffer)
        gl.bufferData(ARRAY_BUFFER, vertexData.array, STATIC_DRAW)

        existing?.indexBuffer?.let { gl.deleteBuffer(it) }

        val buffers = MeshBuffers(
            vertexBuffer = vertexBuffer,
            indexBuffer = null,
            vertexByteSize = vertexData.byteSize,
            vertexCount = vertexCount,
            indexByteSize = 0,
            indexCount = 0,
            usesUint32 = false,
            drawMode = POINTS,
            triangles = vertexCount
        )

        meshBuffers[points.id] = buffers
        gl.bindBuffer(ARRAY_BUFFER, null)
        return buffers
    }

    private fun drawMesh(mesh: Mesh, buffers: MeshBuffers, viewProjection: Matrix4): Int {
        gl.bindBuffer(ARRAY_BUFFER, buffers.vertexBuffer)
        gl.vertexAttribPointer(
            positionLocation,
            POSITION_COMPONENTS,
            FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            0
        )
        gl.vertexAttribPointer(
            colorLocation,
            COLOR_COMPONENTS,
            FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            COLOR_OFFSET_BYTES
        )
        if (sizeLocation >= 0) {
            gl.vertexAttribPointer(
                sizeLocation,
                SIZE_COMPONENTS,
                FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                SIZE_OFFSET_BYTES
            )
        }

        if (buffers.indexBuffer != null && buffers.indexCount > 0) {
            gl.bindBuffer(ELEMENT_ARRAY_BUFFER, buffers.indexBuffer)
        } else {
            gl.bindBuffer(ELEMENT_ARRAY_BUFFER, null)
        }

        modelViewProjectionMatrix.multiplyMatrices(viewProjection, mesh.matrixWorld)
        fillMatrixBuffer(modelViewProjectionMatrix.elements, matrixBuffer)
        gl.uniformMatrix4fv(mvpLocation, false, matrixBuffer)

        mesh.onBeforeRender?.invoke(mesh)

        if (buffers.indexBuffer != null && buffers.indexCount > 0) {
            val indexType = if (buffers.usesUint32) {
                require(supportsUint32Indices) { "32-bit indices not supported by this context" }
                UNSIGNED_INT
            } else {
                WebGLRenderingContext.UNSIGNED_SHORT
            }
            gl.drawElements(buffers.drawMode, buffers.indexCount, indexType, 0)
        } else {
            gl.drawArrays(buffers.drawMode, 0, buffers.vertexCount)
        }

        mesh.onAfterRender?.invoke(mesh)

        return buffers.triangles
    }

    private fun drawPoints(points: Points, buffers: MeshBuffers, viewProjection: Matrix4): Int {
        gl.bindBuffer(ARRAY_BUFFER, buffers.vertexBuffer)
        gl.vertexAttribPointer(
            positionLocation,
            POSITION_COMPONENTS,
            FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            0
        )
        gl.vertexAttribPointer(
            colorLocation,
            COLOR_COMPONENTS,
            FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            COLOR_OFFSET_BYTES
        )
        if (sizeLocation >= 0) {
            gl.vertexAttribPointer(
                sizeLocation,
                SIZE_COMPONENTS,
                FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                SIZE_OFFSET_BYTES
            )
        }

        gl.bindBuffer(ELEMENT_ARRAY_BUFFER, null)

        modelViewProjectionMatrix.multiplyMatrices(viewProjection, points.matrixWorld)
        fillMatrixBuffer(modelViewProjectionMatrix.elements, matrixBuffer)
        gl.uniformMatrix4fv(mvpLocation, false, matrixBuffer)

        points.onBeforeRender?.invoke(points)
        gl.drawArrays(POINTS, 0, buffers.vertexCount)
        points.onAfterRender?.invoke(points)

        return buffers.vertexCount
    }

    private fun releaseUnusedBuffers() {
        val iterator = meshBuffers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!visitedIds.contains(entry.key)) {
                gl.deleteBuffer(entry.value.vertexBuffer)
                entry.value.indexBuffer?.let { gl.deleteBuffer(it) }
                iterator.remove()
            }
        }
    }

    private fun buildVertexData(
        position: BufferAttribute,
        color: BufferAttribute?,
        materialColor: Color
    ): VertexData {
        val vertexCount = position.count
        val vertexArray = Float32Array(vertexCount * COMPONENTS_PER_VERTEX)
        var writeIndex = 0

        val useColorAttribute = color != null && color.itemSize >= COLOR_COMPONENTS
        for (i in 0 until vertexCount) {
            vertexArray.put(writeIndex++, position.getX(i))
            vertexArray.put(writeIndex++, position.getY(i))
            vertexArray.put(writeIndex++, position.getZ(i))

            if (useColorAttribute) {
                val source = color!!
                vertexArray.put(writeIndex++, source.getX(i))
                vertexArray.put(writeIndex++, source.getY(i))
                vertexArray.put(writeIndex++, source.getZ(i))
            } else {
                vertexArray.put(writeIndex++, materialColor.r)
                vertexArray.put(writeIndex++, materialColor.g)
                vertexArray.put(writeIndex++, materialColor.b)
            }
            vertexArray.put(writeIndex++, 1f)
        }

        return VertexData(vertexArray, vertexCount * COMPONENTS_PER_VERTEX * BYTES_PER_FLOAT)
    }

    private fun buildPointsVertexData(
        positions: BufferAttribute,
        colors: BufferAttribute?,
        sizes: BufferAttribute?,
        materialColor: Color,
        defaultSize: Float
    ): VertexData {
        val vertexCount = positions.count
        val vertexArray = Float32Array(vertexCount * COMPONENTS_PER_VERTEX)
        var writeIndex = 0

        val useColorAttribute = colors != null && colors.itemSize >= COLOR_COMPONENTS
        for (i in 0 until vertexCount) {
            vertexArray.put(writeIndex++, positions.getX(i))
            vertexArray.put(writeIndex++, positions.getY(i))
            vertexArray.put(writeIndex++, positions.getZ(i))

            if (useColorAttribute) {
                val source = colors!!
                vertexArray.put(writeIndex++, source.getX(i))
                vertexArray.put(writeIndex++, source.getY(i))
                vertexArray.put(writeIndex++, source.getZ(i))
            } else {
                vertexArray.put(writeIndex++, materialColor.r)
                vertexArray.put(writeIndex++, materialColor.g)
                vertexArray.put(writeIndex++, materialColor.b)
            }

            val sizeValue = sizes?.getX(i) ?: defaultSize
            vertexArray.put(writeIndex++, sizeValue.coerceAtLeast(1f))
        }

        return VertexData(vertexArray, vertexCount * COMPONENTS_PER_VERTEX * BYTES_PER_FLOAT)
    }

    private fun buildIndexData(attribute: BufferAttribute): IndexData? {
        val count = attribute.count
        if (count == 0) return null

        var maxIndex = 0
        for (i in 0 until count) {
            maxIndex = max(maxIndex, attribute.getX(i).roundToInt())
        }

        val useUint32 = supportsUint32Indices && maxIndex > MAX_UNSIGNED_SHORT
        if (!supportsUint32Indices && maxIndex > MAX_UNSIGNED_SHORT) {
            throw RendererInitializationException.DeviceCreationFailedException(
                backend = BackendType.WEBGL,
                adapterInfo = "WebGL context",
                reason = "Geometry requires 32-bit indices but OES_element_index_uint is unavailable"
            )
        }

        val typedArray: ArrayBufferView = if (useUint32) {
            val array = Uint32Array(count)
            for (i in 0 until count) {
                array.put(i, attribute.getX(i).roundToInt())
            }
            array
        } else {
            val array = Uint16Array(count)
            for (i in 0 until count) {
                array.put(i, attribute.getX(i).roundToInt())
            }
            array
        }

        val byteSize = typedArray.byteLength
        return IndexData(
            array = typedArray,
            count = count,
            byteSize = byteSize,
            usesUint32 = useUint32
        )
    }

    private fun updateClearColor(scene: Scene) {
        val background = scene.background
        val targetColor = when (background) {
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
                1.0f
            )

            else -> DEFAULT_CLEAR_COLOR
        }

        if (!clearColor.contentEquals(targetColor)) {
            gl.clearColor(targetColor[0], targetColor[1], targetColor[2], targetColor[3])
            clearColor = targetColor
        }
    }

    private fun estimateTriangleCount(drawMode: Int, vertexCount: Int, indexCount: Int): Int {
        val effectiveCount = if (indexCount > 0) indexCount else vertexCount
        return when (drawMode) {
            TRIANGLES -> effectiveCount / 3
            TRIANGLE_STRIP, TRIANGLE_FAN -> max(0, effectiveCount - 2)
            else -> 0
        }
    }

    private fun mapDrawMode(mode: DrawMode): Int = when (mode) {
        DrawMode.TRIANGLES -> TRIANGLES
        DrawMode.TRIANGLE_STRIP -> TRIANGLE_STRIP
        DrawMode.TRIANGLE_FAN -> TRIANGLE_FAN
        DrawMode.LINES -> LINES
        DrawMode.LINE_LOOP -> LINE_LOOP
        DrawMode.LINE_STRIP -> LINE_STRIP
        DrawMode.POINTS -> POINTS
    }

    private fun updateStats(
        drawCalls: Int,
        triangles: Int,
        frameDurationMs: Double,
        timestamp: Double
    ) {
        val fps = if (frameDurationMs > 0.0) 1000.0 / frameDurationMs else stats.fps
        val bufferMemory =
            meshBuffers.values.sumOf { it.vertexByteSize.toLong() + it.indexByteSize.toLong() }

        stats = RenderStats(
            fps = fps,
            frameTime = frameDurationMs,
            triangles = triangles,
            drawCalls = drawCalls,
            textureMemory = 0L,
            bufferMemory = bufferMemory,
            timestamp = timestamp.toLong()
        )
    }

    private fun ensureInitialised() {
        check(initialised) { "WebGLRenderer has not been initialised. Call initialize() first." }
    }

    private fun fillMatrixBuffer(source: FloatArray, target: Float32Array) {
        for (i in source.indices) {
            target.put(i, source[i])
        }
    }

    private fun buildContextAttributes(config: RendererConfig): dynamic {
        val obj = js("{}")
        obj["antialias"] = config.msaaSamples > 1
        obj["alpha"] = true
        obj["depth"] = true
        obj["stencil"] = true
        obj["preserveDrawingBuffer"] = false
        obj["powerPreference"] = when (config.powerPreference) {
            PowerPreference.HIGH_PERFORMANCE -> "high-performance"
            PowerPreference.LOW_POWER -> "low-power"
        }
        return obj
    }

    private data class ContextCandidate(val context: WebGLRenderingContext?, val isWebGL2: Boolean)

    private fun tryGetContext(type: String, attributes: dynamic): ContextCandidate {
        return try {
            val raw =
                if (attributes != null) canvas.getContext(type, attributes) else canvas.getContext(
                    type
                )
            val context = when (raw) {
                null -> null
                is WebGLRenderingContext -> raw
                else -> raw.unsafeCast<WebGLRenderingContext?>()
            }
            val isWebGL2 =
                raw != null && js("typeof WebGL2RenderingContext !== 'undefined' && raw instanceof WebGL2RenderingContext").unsafeCast<Boolean>()
            ContextCandidate(context, isWebGL2)
        } catch (_: Throwable) {
            ContextCandidate(null, false)
        }
    }

    private fun Float32Array.put(index: Int, value: Float) {
        asDynamic()[index] = value
    }

    private fun Uint32Array.put(index: Int, value: Int) {
        asDynamic()[index] = value
    }

    private fun Uint16Array.put(index: Int, value: Int) {
        asDynamic()[index] = value
    }

    private data class VertexData(
        val array: Float32Array,
        val byteSize: Int
    )

    private data class IndexData(
        val array: ArrayBufferView,
        val count: Int,
        val byteSize: Int,
        val usesUint32: Boolean
    )

    private data class MeshBuffers(
        val vertexBuffer: WebGLBuffer,
        var indexBuffer: WebGLBuffer?,
        var vertexByteSize: Int,
        var vertexCount: Int,
        var indexByteSize: Int,
        var indexCount: Int,
        var usesUint32: Boolean,
        var drawMode: Int,
        var triangles: Int
    )

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 3
        private const val SIZE_COMPONENTS = 1
        private const val COMPONENTS_PER_VERTEX =
            POSITION_COMPONENTS + COLOR_COMPONENTS + SIZE_COMPONENTS
        private const val BYTES_PER_FLOAT = 4
        private const val VERTEX_STRIDE_BYTES = COMPONENTS_PER_VERTEX * BYTES_PER_FLOAT
        private const val COLOR_OFFSET_BYTES = POSITION_COMPONENTS * BYTES_PER_FLOAT
        private const val SIZE_OFFSET_BYTES =
            (POSITION_COMPONENTS + COLOR_COMPONENTS) * BYTES_PER_FLOAT
        private const val MAX_UNSIGNED_SHORT = 65535
        private const val UNSIGNED_INT = 0x1405
        private const val MAX_SAMPLES_CONST = 0x8D57
        private const val MAX_COLOR_ATTACHMENTS_CONST = 0x8CDF
        private const val MAX_UNIFORM_BLOCK_SIZE_CONST = 0x8A30
        private const val MAX_UNIFORM_BUFFER_BINDINGS_CONST = 0x8A2F
        private val DEFAULT_CLEAR_COLOR = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
    }
}
