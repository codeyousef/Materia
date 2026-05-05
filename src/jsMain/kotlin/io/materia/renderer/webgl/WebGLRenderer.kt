package io.materia.renderer.webgl

import io.materia.camera.Camera
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.scene.Background
import io.materia.core.scene.DrawMode
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.geometry.BufferAttribute
import io.materia.geometry.InstancedPointsGeometry
import io.materia.material.MeshBasicMaterial
import io.materia.material.MeshStandardMaterial
import io.materia.points.Points
import io.materia.points.PointsMaterial
import io.materia.renderer.*
import io.materia.texture.Data3DTexture
import io.materia.texture.Texture2D
import io.materia.texture.VolumeTextureSampler
import kotlinx.browser.window
import org.khronos.webgl.*
import org.khronos.webgl.WebGLRenderingContext.Companion.ARRAY_BUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.BLEND
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.COMPILE_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_TEST
import org.khronos.webgl.WebGLRenderingContext.Companion.ELEMENT_ARRAY_BUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FLOAT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR_MIPMAP_LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR_MIPMAP_NEAREST
import org.khronos.webgl.WebGLRenderingContext.Companion.LINES
import org.khronos.webgl.WebGLRenderingContext.Companion.LINE_LOOP
import org.khronos.webgl.WebGLRenderingContext.Companion.LINE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.LINK_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.MIRRORED_REPEAT
import org.khronos.webgl.WebGLRenderingContext.Companion.NEAREST
import org.khronos.webgl.WebGLRenderingContext.Companion.NEAREST_MIPMAP_LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.NEAREST_MIPMAP_NEAREST
import org.khronos.webgl.WebGLRenderingContext.Companion.ONE_MINUS_SRC_ALPHA
import org.khronos.webgl.WebGLRenderingContext.Companion.POINTS
import org.khronos.webgl.WebGLRenderingContext.Companion.REPEAT
import org.khronos.webgl.WebGLRenderingContext.Companion.RGBA
import org.khronos.webgl.WebGLRenderingContext.Companion.SRC_ALPHA
import org.khronos.webgl.WebGLRenderingContext.Companion.STATIC_DRAW
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE0
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLES
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_FAN
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.UNPACK_FLIP_Y_WEBGL
import org.khronos.webgl.WebGLRenderingContext.Companion.UNPACK_PREMULTIPLY_ALPHA_WEBGL
import org.khronos.webgl.WebGLRenderingContext.Companion.UNSIGNED_BYTE
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.w3c.dom.HTMLCanvasElement
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
    private var uvLocation: Int = -1
    private var sizeLocation: Int = -1
    private var mvpLocation: WebGLUniformLocation? = null
    private var textureLocation: WebGLUniformLocation? = null
    private var useTextureLocation: WebGLUniformLocation? = null
    private var opacityLocation: WebGLUniformLocation? = null
    private var alphaTestLocation: WebGLUniformLocation? = null

    private var rendererCapabilities: RendererCapabilities =
        RendererCapabilities(backend = BackendType.WEBGL)
    private var supportsUint32Indices: Boolean = false
    private var anisotropyExtension: dynamic = null

    private var initialised = false

    private val meshBuffers: MutableMap<Int, MeshBuffers> = mutableMapOf()
    private val textureCache: MutableMap<Int, CachedTexture> = mutableMapOf()
    private val visitedIds: MutableSet<Int> = mutableSetOf()
    private val visitedTextureIds: MutableSet<Int> = mutableSetOf()

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
        visitedTextureIds.clear()

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
        releaseUnusedTextures()

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
        textureCache.values.forEach { cached ->
            gl.deleteTexture(cached.texture)
        }
        textureCache.clear()

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
            attribute vec2 aUV;
            attribute float aSize;
            uniform mat4 uMVP;
            varying vec3 vColor;
            varying vec2 vUV;
            void main() {
                vColor = aColor;
                vUV = aUV;
                gl_Position = uMVP * vec4(aPosition, 1.0);
                gl_PointSize = aSize;
            }
        """.trimIndent()

        val fragmentSource = """
            precision mediump float;
            varying vec3 vColor;
            varying vec2 vUV;
            uniform sampler2D uTexture;
            uniform bool uUseTexture;
            uniform float uOpacity;
            uniform float uAlphaTest;
            void main() {
                vec4 color = vec4(vColor, uOpacity);
                if (uUseTexture) {
                    color *= texture2D(uTexture, vUV);
                }
                if (color.a <= uAlphaTest) {
                    discard;
                }
                gl_FragColor = color;
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
        uvLocation = gl.getAttribLocation(linkedProgram, "aUV")
        sizeLocation = gl.getAttribLocation(linkedProgram, "aSize")
        mvpLocation = gl.getUniformLocation(linkedProgram, "uMVP")
        textureLocation = gl.getUniformLocation(linkedProgram, "uTexture")
        useTextureLocation = gl.getUniformLocation(linkedProgram, "uUseTexture")
        opacityLocation = gl.getUniformLocation(linkedProgram, "uOpacity")
        alphaTestLocation = gl.getUniformLocation(linkedProgram, "uAlphaTest")
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
        if (uvLocation >= 0) {
            gl.enableVertexAttribArray(uvLocation)
        }
        if (sizeLocation >= 0) {
            gl.enableVertexAttribArray(sizeLocation)
        }
        textureLocation?.let { gl.uniform1i(it, 0) }
        useTextureLocation?.let { gl.uniform1i(it, 0) }
        opacityLocation?.let { gl.uniform1f(it, 1f) }
        alphaTestLocation?.let { gl.uniform1f(it, 0f) }
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
        val uvAttribute = geometry.getAttribute("uv")
        val materialInfo = resolveMaterialInfo(mesh.material)
        val volumeTexture = (mesh.material as? MeshBasicMaterial)?.map as? Data3DTexture
        val texture = resolveBaseColorTexture(mesh.material)
        val cachedTexture =
            if (texture != null && uvAttribute != null && uvAttribute.itemSize >= UV_COMPONENTS) {
                acquireTexture(texture)?.also { visitedTextureIds.add(texture.id) }
            } else {
                null
            }
        val vertexData = buildVertexData(
            position = positionAttribute,
            color = colorAttribute,
            uv = uvAttribute,
            materialColor = materialInfo.color,
            useVertexColors = materialInfo.useVertexColors,
            volumeTexture = volumeTexture
        )

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
        uvAttribute?.needsUpdate = false
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
            triangles = triangles,
            texture = cachedTexture?.texture,
            opacity = materialInfo.opacity,
            alphaTest = materialInfo.alphaTest,
            transparent = materialInfo.transparent
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
            vertexData = buildVertexData(
                position = positionAttribute,
                color = colorAttribute,
                uv = null,
                materialColor = materialColor,
                useVertexColors = true
            )
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
            triangles = vertexCount,
            texture = null,
            opacity = 1f,
            alphaTest = 0f,
            transparent = false
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
        if (uvLocation >= 0) {
            gl.vertexAttribPointer(
                uvLocation,
                UV_COMPONENTS,
                FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                UV_OFFSET_BYTES
            )
        }
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
        bindMeshMaterialState(buffers)

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
        if (uvLocation >= 0) {
            gl.vertexAttribPointer(
                uvLocation,
                UV_COMPONENTS,
                FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                UV_OFFSET_BYTES
            )
        }
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
        bindMeshMaterialState(MeshMaterialState.Default)

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

    private fun releaseUnusedTextures() {
        val iterator = textureCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!visitedTextureIds.contains(entry.key)) {
                gl.deleteTexture(entry.value.texture)
                iterator.remove()
            }
        }
    }

    private fun buildVertexData(
        position: BufferAttribute,
        color: BufferAttribute?,
        uv: BufferAttribute?,
        materialColor: Color,
        useVertexColors: Boolean,
        volumeTexture: Data3DTexture? = null
    ): VertexData {
        val vertexCount = position.count
        val vertexArray = Float32Array(vertexCount * COMPONENTS_PER_VERTEX)
        var writeIndex = 0
        val volumeSampler = volumeTexture?.let(VolumeTextureSampler::from)

        val useColorAttribute = useVertexColors && color != null && color.itemSize >= COLOR_COMPONENTS
        val useUvAttribute = uv != null && uv.itemSize >= UV_COMPONENTS
        for (i in 0 until vertexCount) {
            val positionX = position.getX(i)
            val positionY = position.getY(i)
            val positionZ = position.getZ(i)
            vertexArray.put(writeIndex++, positionX)
            vertexArray.put(writeIndex++, positionY)
            vertexArray.put(writeIndex++, positionZ)

            var red: Float
            var green: Float
            var blue: Float

            if (useColorAttribute) {
                val source = color!!
                red = source.getX(i) * materialColor.r
                green = source.getY(i) * materialColor.g
                blue = source.getZ(i) * materialColor.b
            } else {
                red = materialColor.r
                green = materialColor.g
                blue = materialColor.b
            }

            volumeSampler?.sampleLocalPosition(positionX, positionY, positionZ)?.let { sample ->
                red *= sample.r
                green *= sample.g
                blue *= sample.b
            }

            vertexArray.put(writeIndex++, red)
            vertexArray.put(writeIndex++, green)
            vertexArray.put(writeIndex++, blue)
            if (useUvAttribute && i < uv!!.count) {
                vertexArray.put(writeIndex++, uv.getX(i))
                vertexArray.put(writeIndex++, uv.getY(i))
            } else {
                vertexArray.put(writeIndex++, 0f)
                vertexArray.put(writeIndex++, 0f)
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
            vertexArray.put(writeIndex++, 0f)
            vertexArray.put(writeIndex++, 0f)
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

    private fun resolveMaterialInfo(material: io.materia.core.scene.Material?): MeshMaterialState =
        when (material) {
            is MeshBasicMaterial -> MeshMaterialState(
                color = material.color,
                useVertexColors = material.vertexColors,
                opacity = material.opacity.coerceIn(0f, 1f),
                alphaTest = material.alphaTest.coerceIn(0f, 1f),
                transparent = material.transparent || material.opacity < 1f
            )

            is MeshStandardMaterial -> MeshMaterialState(
                color = material.color,
                useVertexColors = material.vertexColors,
                opacity = material.opacity.coerceIn(0f, 1f),
                alphaTest = material.alphaTest.coerceIn(0f, 1f),
                transparent = material.transparent || material.opacity < 1f
            )

            else -> MeshMaterialState.Default
        }

    private fun resolveBaseColorTexture(material: io.materia.core.scene.Material?): Texture2D? =
        when (material) {
            is MeshBasicMaterial -> material.map as? Texture2D
            is MeshStandardMaterial -> material.map
            else -> null
        }

    private fun bindMeshMaterialState(buffers: MeshBuffers) {
        bindMeshMaterialState(
            MeshMaterialState(
                color = Color.WHITE,
                useVertexColors = false,
                opacity = buffers.opacity,
                alphaTest = buffers.alphaTest,
                transparent = buffers.transparent,
                texture = buffers.texture
            )
        )
    }

    private fun bindMeshMaterialState(state: MeshMaterialState) {
        if (state.transparent) {
            gl.enable(BLEND)
            gl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
        } else {
            gl.disable(BLEND)
        }

        opacityLocation?.let { gl.uniform1f(it, state.opacity) }
        alphaTestLocation?.let { gl.uniform1f(it, state.alphaTest) }

        val texture = state.texture
        if (texture != null) {
            gl.activeTexture(TEXTURE0)
            gl.bindTexture(TEXTURE_2D, texture)
            useTextureLocation?.let { gl.uniform1i(it, 1) }
        } else {
            gl.bindTexture(TEXTURE_2D, null)
            useTextureLocation?.let { gl.uniform1i(it, 0) }
        }
    }

    private fun acquireTexture(texture: Texture2D): CachedTexture? {
        val upload = textureRgbaData(texture) ?: return null
        val cached = textureCache[texture.id]
        if (cached != null &&
            !texture.needsUpdate &&
            cached.version == texture.version &&
            cached.width == texture.width &&
            cached.height == texture.height
        ) {
            return cached
        }

        cached?.let { gl.deleteTexture(it.texture) }

        val webglTexture = gl.createTexture() ?: return null
        gl.bindTexture(TEXTURE_2D, webglTexture)
        gl.pixelStorei(UNPACK_FLIP_Y_WEBGL, if (texture.flipY) 1 else 0)
        gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA_WEBGL, if (texture.premultiplyAlpha) 1 else 0)
        gl.texImage2D(
            TEXTURE_2D,
            0,
            RGBA,
            texture.width,
            texture.height,
            0,
            RGBA,
            UNSIGNED_BYTE,
            upload.typedArray
        )

        val isPowerOfTwoTexture = isPowerOfTwo(texture.width) && isPowerOfTwo(texture.height)
        val canUseMipmaps = texture.generateMipmaps && (isWebGL2Context || isPowerOfTwoTexture)
        val canRepeat = isWebGL2Context || isPowerOfTwoTexture
        gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, mapMinFilter(texture.minFilter, canUseMipmaps))
        gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, mapMagFilter(texture.magFilter))
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, mapWrap(texture.wrapS, canRepeat))
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, mapWrap(texture.wrapT, canRepeat))
        if (canUseMipmaps) {
            gl.generateMipmap(TEXTURE_2D)
        }
        gl.bindTexture(TEXTURE_2D, null)

        val cachedTexture = CachedTexture(
            texture = webglTexture,
            version = texture.version,
            width = texture.width,
            height = texture.height,
            byteSize = upload.byteSize.toLong()
        )
        textureCache[texture.id] = cachedTexture
        texture.needsUpdate = false
        return cachedTexture
    }

    private fun textureRgbaData(texture: Texture2D): TextureUpload? {
        val pixelCount = texture.width * texture.height
        if (pixelCount <= 0) return null

        val byteData = texture.getData()
        val floatData = texture.getFloatData()
        val rgba = when {
            byteData != null ->
                expandByteData(byteData, pixelCount, componentCountFor(texture.format))

            floatData != null ->
                expandFloatData(floatData, pixelCount, componentCountFor(texture.format))

            else -> null
        } ?: return null

        val typed = Uint8Array(rgba.size)
        val dyn = typed.asDynamic()
        for (i in rgba.indices) {
            dyn[i] = rgba[i].toInt() and 0xFF
        }
        return TextureUpload(typed, rgba.size)
    }

    private fun expandByteData(data: ByteArray, pixelCount: Int, preferredComponents: Int): ByteArray? {
        val components = componentCountFromSize(data.size, pixelCount) ?: preferredComponents
        if (data.size < pixelCount * components) return null
        if (components == 4) return data.copyOf(pixelCount * 4)

        return ByteArray(pixelCount * 4) { index ->
            val pixel = index / 4
            val channel = index % 4
            val sourceOffset = pixel * components
            when {
                channel < components -> data[sourceOffset + channel]
                channel == 3 -> 255.toByte()
                else -> 0
            }
        }
    }

    private fun expandFloatData(data: FloatArray, pixelCount: Int, preferredComponents: Int): ByteArray? {
        val components = componentCountFromSize(data.size, pixelCount) ?: preferredComponents
        if (data.size < pixelCount * components) return null

        return ByteArray(pixelCount * 4) { index ->
            val pixel = index / 4
            val channel = index % 4
            val sourceOffset = pixel * components
            val value = when {
                channel < components -> data[sourceOffset + channel]
                channel == 3 -> 1f
                else -> 0f
            }
            (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    private fun componentCountFromSize(size: Int, pixelCount: Int): Int? =
        when {
            pixelCount <= 0 -> null
            size == pixelCount * 4 -> 4
            size == pixelCount * 3 -> 3
            size == pixelCount * 2 -> 2
            size == pixelCount -> 1
            else -> null
        }

    private fun componentCountFor(format: TextureFormat): Int =
        when (format) {
            TextureFormat.RGBA8,
            TextureFormat.RGBA16F,
            TextureFormat.RGBA32F,
            TextureFormat.RGBA8UI,
            TextureFormat.RGBA16UI,
            TextureFormat.RGBA32UI,
            TextureFormat.SRGB8_ALPHA8 -> 4

            TextureFormat.RGB8,
            TextureFormat.RGB16F,
            TextureFormat.RGB32F,
            TextureFormat.RGB8UI,
            TextureFormat.RGB16UI,
            TextureFormat.RGB32UI,
            TextureFormat.SRGB8 -> 3

            TextureFormat.RG8,
            TextureFormat.RG16F,
            TextureFormat.RG32F,
            TextureFormat.RG8UI,
            TextureFormat.RG16UI,
            TextureFormat.RG32UI -> 2

            TextureFormat.R8,
            TextureFormat.R16F,
            TextureFormat.R32F,
            TextureFormat.R8UI,
            TextureFormat.R16UI,
            TextureFormat.R32UI -> 1
        }

    private fun mapMinFilter(filter: TextureFilter, allowMipmaps: Boolean): Int =
        if (!allowMipmaps) {
            when (filter) {
                TextureFilter.NEAREST,
                TextureFilter.NEAREST_MIPMAP_NEAREST,
                TextureFilter.NEAREST_MIPMAP_LINEAR -> NEAREST

                else -> LINEAR
            }
        } else {
            when (filter) {
                TextureFilter.NEAREST -> NEAREST
                TextureFilter.LINEAR -> LINEAR
                TextureFilter.NEAREST_MIPMAP_NEAREST -> NEAREST_MIPMAP_NEAREST
                TextureFilter.LINEAR_MIPMAP_NEAREST -> LINEAR_MIPMAP_NEAREST
                TextureFilter.NEAREST_MIPMAP_LINEAR -> NEAREST_MIPMAP_LINEAR
                TextureFilter.LINEAR_MIPMAP_LINEAR -> LINEAR_MIPMAP_LINEAR
            }
        }

    private fun mapMagFilter(filter: TextureFilter): Int =
        when (filter) {
            TextureFilter.NEAREST,
            TextureFilter.NEAREST_MIPMAP_NEAREST,
            TextureFilter.NEAREST_MIPMAP_LINEAR -> NEAREST

            else -> LINEAR
        }

    private fun mapWrap(wrap: TextureWrap, allowRepeat: Boolean): Int =
        if (!allowRepeat) {
            CLAMP_TO_EDGE
        } else {
            when (wrap) {
                TextureWrap.REPEAT -> REPEAT
                TextureWrap.CLAMP_TO_EDGE -> CLAMP_TO_EDGE
                TextureWrap.MIRRORED_REPEAT -> MIRRORED_REPEAT
            }
        }

    private fun isPowerOfTwo(value: Int): Boolean =
        value > 0 && (value and (value - 1)) == 0

    private fun updateStats(
        drawCalls: Int,
        triangles: Int,
        frameDurationMs: Double,
        timestamp: Double
    ) {
        val fps = if (frameDurationMs > 0.0) 1000.0 / frameDurationMs else stats.fps
        val bufferMemory =
            meshBuffers.values.sumOf { it.vertexByteSize.toLong() + it.indexByteSize.toLong() }
        val textureMemory = textureCache.values.sumOf { it.byteSize }

        stats = RenderStats(
            fps = fps,
            frameTime = frameDurationMs,
            triangles = triangles,
            drawCalls = drawCalls,
            textureMemory = textureMemory,
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

    private data class TextureUpload(
        val typedArray: Uint8Array,
        val byteSize: Int
    )

    private data class CachedTexture(
        val texture: WebGLTexture,
        val version: Int,
        val width: Int,
        val height: Int,
        val byteSize: Long
    )

    private data class MeshMaterialState(
        val color: Color,
        val useVertexColors: Boolean,
        val opacity: Float,
        val alphaTest: Float,
        val transparent: Boolean,
        val texture: WebGLTexture? = null
    ) {
        companion object {
            val Default = MeshMaterialState(
                color = Color.WHITE,
                useVertexColors = false,
                opacity = 1f,
                alphaTest = 0f,
                transparent = false
            )
        }
    }

    private data class MeshBuffers(
        val vertexBuffer: WebGLBuffer,
        var indexBuffer: WebGLBuffer?,
        var vertexByteSize: Int,
        var vertexCount: Int,
        var indexByteSize: Int,
        var indexCount: Int,
        var usesUint32: Boolean,
        var drawMode: Int,
        var triangles: Int,
        var texture: WebGLTexture?,
        var opacity: Float,
        var alphaTest: Float,
        var transparent: Boolean
    )

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 3
        private const val UV_COMPONENTS = 2
        private const val SIZE_COMPONENTS = 1
        private const val COMPONENTS_PER_VERTEX =
            POSITION_COMPONENTS + COLOR_COMPONENTS + UV_COMPONENTS + SIZE_COMPONENTS
        private const val BYTES_PER_FLOAT = 4
        private const val VERTEX_STRIDE_BYTES = COMPONENTS_PER_VERTEX * BYTES_PER_FLOAT
        private const val COLOR_OFFSET_BYTES = POSITION_COMPONENTS * BYTES_PER_FLOAT
        private const val UV_OFFSET_BYTES =
            (POSITION_COMPONENTS + COLOR_COMPONENTS) * BYTES_PER_FLOAT
        private const val SIZE_OFFSET_BYTES =
            (POSITION_COMPONENTS + COLOR_COMPONENTS + UV_COMPONENTS) * BYTES_PER_FLOAT
        private const val MAX_UNSIGNED_SHORT = 65535
        private const val UNSIGNED_INT = 0x1405
        private const val MAX_SAMPLES_CONST = 0x8D57
        private const val MAX_COLOR_ATTACHMENTS_CONST = 0x8CDF
        private const val MAX_UNIFORM_BLOCK_SIZE_CONST = 0x8A30
        private const val MAX_UNIFORM_BUFFER_BINDINGS_CONST = 0x8A2F
        private val DEFAULT_CLEAR_COLOR = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
    }
}
