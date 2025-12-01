package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.scene.Scene

actual interface Renderer {
    actual val backend: BackendType
    actual val capabilities: RendererCapabilities
    actual val stats: RenderStats

    actual suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit>
    actual fun render(scene: Scene, camera: Camera)
    actual fun resize(width: Int, height: Int)
    actual fun dispose()
}

internal class AndroidStubRenderer(
    private val surface: AndroidRenderSurface
) : Renderer {

    override val backend: BackendType = BackendType.VULKAN

    override val capabilities: RendererCapabilities = RendererCapabilities(
        backend = backend,
        deviceName = android.os.Build.MODEL ?: "Android Device",
        driverVersion = "Vulkan (native)",
        supportsCompute = false,
        supportsRayTracing = false,
        supportsMultisampling = false,
        maxTextureSize = 0,
        maxCubeMapSize = 0,
        maxVertexAttributes = 0,
        maxVertexUniforms = 0,
        maxFragmentUniforms = 0,
        maxVertexTextures = 0,
        maxFragmentTextures = 0,
        maxCombinedTextures = 0,
        maxTextureSize3D = 0,
        maxTextureArrayLayers = 0,
        maxColorAttachments = 0,
        maxSamples = 0,
        maxUniformBufferSize = 0,
        maxUniformBufferBindings = 0,
        maxAnisotropy = 0f,
        vertexShaderPrecisions = ShaderPrecisions(),
        fragmentShaderPrecisions = ShaderPrecisions(),
        textureFormats = emptySet(),
        compressedTextureFormats = emptySet(),
        depthFormats = emptySet(),
        extensions = emptySet(),
        vendor = android.os.Build.MANUFACTURER ?: "Unknown",
        renderer = android.os.Build.HARDWARE ?: "Unknown",
        version = "Unavailable",
        shadingLanguageVersion = "Unavailable",
        instancedRendering = false,
        multipleRenderTargets = false,
        depthTextures = false,
        floatTextures = false,
        halfFloatTextures = false,
        floatTextureLinear = false,
        standardDerivatives = false,
        vertexArrayObjects = false,
        computeShaders = false,
        geometryShaders = false,
        tessellation = false,
        shadowMaps = false,
        shadowMapComparison = false,
        shadowMapPCF = false,
        parallelShaderCompile = false,
        asyncOperations = false
    )

    override val stats: RenderStats = RenderStats(
        fps = 0.0,
        frameTime = 0.0,
        triangles = 0,
        drawCalls = 0
    )

    override suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit> {
        return io.materia.core.Result.Error(
            "Android renderer pipeline is not yet implemented.",
            RendererInitializationException.DeviceCreationFailedException(
                backend = backend,
                adapterInfo = capabilities.deviceName,
                reason = "Android Vulkan backend integration pending"
            )
        )
    }

    override fun render(scene: Scene, camera: Camera) {
        // Rendering delegated to native Vulkan layer
    }

    override fun resize(width: Int, height: Int) {
        // Resize handled by native Vulkan layer
    }

    override fun dispose() {
        val heldSurface = surface.holder.surface
        if (heldSurface.isValid) {
            heldSurface.release()
        }
    }
}
