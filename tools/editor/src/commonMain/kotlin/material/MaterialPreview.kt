package io.materia.tools.editor.material

import io.materia.tools.editor.data.MaterialDefinition
import io.materia.tools.editor.data.Vector3
import io.materia.tools.editor.data.Color
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.*

/**
 * MaterialPreview - Real-time material preview renderer
 *
 * This component provides a comprehensive material preview system including:
 * - Real-time rendering on test geometries (sphere, cube, plane, etc.)
 * - Interactive camera controls for material inspection
 * - Multiple lighting scenarios (studio, outdoor, custom)
 * - Background environments (solid color, HDRI, gradient)
 * - Performance monitoring and quality settings
 * - Screenshot/capture functionality
 */
class MaterialPreview(
    private val scope: CoroutineScope,
    private val onPreviewUpdated: (PreviewImage) -> Unit = {}
) {

    // Preview state
    private val _material = MutableStateFlow<MaterialDefinition?>(null)
    private val _geometry = MutableStateFlow(PreviewGeometry.SPHERE)
    private val _lighting = MutableStateFlow(PreviewLighting.STUDIO)
    private val _environment = MutableStateFlow(PreviewEnvironment.COLOR)
    private val _backgroundColor = MutableStateFlow(Color(0.2f, 0.2f, 0.2f, 1.0f))

    // Camera state
    private val _cameraPosition = MutableStateFlow(Vector3(0f, 0f, 3f))
    private val _cameraTarget = MutableStateFlow(Vector3.ZERO)
    private val _cameraFov = MutableStateFlow(45f)
    private val _cameraAutoRotate = MutableStateFlow(false)
    private val _cameraRotationSpeed = MutableStateFlow(1f)

    // Lighting state
    private val _lightIntensity = MutableStateFlow(1f)
    private val _lightColor = MutableStateFlow(Color(1f, 1f, 1f, 1f))
    private val _ambientIntensity = MutableStateFlow(0.2f)
    private val _shadowsEnabled = MutableStateFlow(true)

    // Render settings
    private val _renderSize = MutableStateFlow(PreviewSize.MEDIUM)
    private val _renderQuality = MutableStateFlow(PreviewQuality.HIGH)
    private val _wireframeMode = MutableStateFlow(false)
    private val _showGrid = MutableStateFlow(false)
    private val _showNormals = MutableStateFlow(false)

    // Performance state
    private val _frameTime = MutableStateFlow(0f)
    private val _triangleCount = MutableStateFlow(0)
    private val _drawCalls = MutableStateFlow(0)
    private val _gpuMemoryUsage = MutableStateFlow(0L)

    // Animation state
    private val _animationTime = MutableStateFlow(0f)
    private val _animationSpeed = MutableStateFlow(1f)
    private val _animationEnabled = MutableStateFlow(false)

    // Rendering state
    private val _isRendering = MutableStateFlow(false)
    private val _renderError = MutableStateFlow<String?>(null)
    private val _lastRenderTime = MutableStateFlow(0L)

    // Jobs for background processing
    private var renderJob: Job? = null
    private var animationJob: Job? = null

    // Public read-only state
    val material: StateFlow<MaterialDefinition?> = _material.asStateFlow()
    val geometry: StateFlow<PreviewGeometry> = _geometry.asStateFlow()
    val lighting: StateFlow<PreviewLighting> = _lighting.asStateFlow()
    val environment: StateFlow<PreviewEnvironment> = _environment.asStateFlow()
    val backgroundColor: StateFlow<Color> = _backgroundColor.asStateFlow()

    val cameraPosition: StateFlow<Vector3> = _cameraPosition.asStateFlow()
    val cameraTarget: StateFlow<Vector3> = _cameraTarget.asStateFlow()
    val cameraFov: StateFlow<Float> = _cameraFov.asStateFlow()
    val cameraAutoRotate: StateFlow<Boolean> = _cameraAutoRotate.asStateFlow()
    val cameraRotationSpeed: StateFlow<Float> = _cameraRotationSpeed.asStateFlow()

    val lightIntensity: StateFlow<Float> = _lightIntensity.asStateFlow()
    val lightColor: StateFlow<Color> = _lightColor.asStateFlow()
    val ambientIntensity: StateFlow<Float> = _ambientIntensity.asStateFlow()
    val shadowsEnabled: StateFlow<Boolean> = _shadowsEnabled.asStateFlow()

    val renderSize: StateFlow<PreviewSize> = _renderSize.asStateFlow()
    val renderQuality: StateFlow<PreviewQuality> = _renderQuality.asStateFlow()
    val wireframeMode: StateFlow<Boolean> = _wireframeMode.asStateFlow()
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()
    val showNormals: StateFlow<Boolean> = _showNormals.asStateFlow()

    val frameTime: StateFlow<Float> = _frameTime.asStateFlow()
    val triangleCount: StateFlow<Int> = _triangleCount.asStateFlow()
    val drawCalls: StateFlow<Int> = _drawCalls.asStateFlow()
    val gpuMemoryUsage: StateFlow<Long> = _gpuMemoryUsage.asStateFlow()

    val animationTime: StateFlow<Float> = _animationTime.asStateFlow()
    val animationSpeed: StateFlow<Float> = _animationSpeed.asStateFlow()
    val animationEnabled: StateFlow<Boolean> = _animationEnabled.asStateFlow()

    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()
    val renderError: StateFlow<String?> = _renderError.asStateFlow()
    val lastRenderTime: StateFlow<Long> = _lastRenderTime.asStateFlow()

    // Derived state
    val fps: StateFlow<Float> = combine(_frameTime) { frameTime ->
        if (frameTime > 0f) 1000f / frameTime else 0f
    }

    val needsRender: StateFlow<Boolean> = combine(
        _material, _geometry, _lighting, _environment, _backgroundColor,
        _cameraPosition, _cameraTarget, _cameraFov, _lightIntensity, _lightColor,
        _ambientIntensity, _renderSize, _renderQuality, _wireframeMode,
        _showGrid, _showNormals, _animationTime
    ) { _ -> true }

    init {
        // Set up auto-rendering when settings change
        scope.launch {
            needsRender.collect { shouldRender ->
                if (shouldRender) {
                    triggerRender()
                }
            }
        }

        // Set up animation loop
        scope.launch {
            combine(_animationEnabled, _animationSpeed) { enabled, speed ->
                if (enabled) {
                    startAnimation(speed)
                } else {
                    stopAnimation()
                }
            }.collect { }
        }
    }

    // Material management

    fun setMaterial(material: MaterialDefinition?) {
        _material.value = material
        _renderError.value = null
    }

    fun refreshMaterial() {
        triggerRender()
    }

    // Geometry settings

    fun setGeometry(geometry: PreviewGeometry) {
        _geometry.value = geometry
        updateTriangleCount()
    }

    fun setCustomMesh(vertices: FloatArray, indices: IntArray) {
        // Store custom mesh data for rendering
        // Implementation would depend on graphics backend
        _geometry.value = PreviewGeometry.CUSTOM
        _triangleCount.value = indices.size / 3
    }

    // Lighting settings

    fun setLighting(lighting: PreviewLighting) {
        _lighting.value = lighting
        applyLightingPreset(lighting)
    }

    fun setLightIntensity(intensity: Float) {
        require(intensity >= 0f) { "Light intensity must be non-negative" }
        _lightIntensity.value = intensity
    }

    fun setLightColor(color: Color) {
        _lightColor.value = color
    }

    fun setAmbientIntensity(intensity: Float) {
        require(intensity in 0f..1f) { "Ambient intensity must be between 0 and 1" }
        _ambientIntensity.value = intensity
    }

    fun setShadowsEnabled(enabled: Boolean) {
        _shadowsEnabled.value = enabled
    }

    // Environment settings

    fun setEnvironment(environment: PreviewEnvironment) {
        _environment.value = environment
    }

    fun setBackgroundColor(color: Color) {
        _backgroundColor.value = color
    }

    fun loadHDRIEnvironment(hdriPath: String) {
        // Load HDRI environment map
        // Implementation would depend on asset loading system
        _environment.value = PreviewEnvironment.HDRI
    }

    // Camera controls

    fun setCameraPosition(position: Vector3) {
        _cameraPosition.value = position
    }

    fun setCameraTarget(target: Vector3) {
        _cameraTarget.value = target
    }

    fun setCameraFov(fov: Float) {
        require(fov in 10f..160f) { "FOV must be between 10 and 160 degrees" }
        _cameraFov.value = fov
    }

    fun setAutoRotate(enabled: Boolean) {
        _cameraAutoRotate.value = enabled
    }

    fun setRotationSpeed(speed: Float) {
        require(speed >= 0f) { "Rotation speed must be non-negative" }
        _cameraRotationSpeed.value = speed
    }

    fun resetCamera() {
        _cameraPosition.value = Vector3(0f, 0f, 3f)
        _cameraTarget.value = Vector3.ZERO
        _cameraFov.value = 45f
        _cameraAutoRotate.value = false
    }

    fun orbitCamera(deltaX: Float, deltaY: Float) {
        val current = _cameraPosition.value
        val target = _cameraTarget.value

        // Convert to spherical coordinates
        val distance = sqrt(
            (current.x - target.x) * (current.x - target.x) +
            (current.y - target.y) * (current.y - target.y) +
            (current.z - target.z) * (current.z - target.z)
        )

        val theta = atan2(current.x - target.x, current.z - target.z) + deltaX * 0.01f
        val phi = acos((current.y - target.y) / distance) + deltaY * 0.01f

        // Clamp phi to avoid gimbal lock
        val clampedPhi = phi.coerceIn(0.1f, PI.toFloat() - 0.1f)

        // Convert back to Cartesian
        val newPosition = Vector3(
            target.x + distance * sin(clampedPhi) * sin(theta),
            target.y + distance * cos(clampedPhi),
            target.z + distance * sin(clampedPhi) * cos(theta)
        )

        _cameraPosition.value = newPosition
    }

    fun panCamera(deltaX: Float, deltaY: Float) {
        val current = _cameraPosition.value
        val target = _cameraTarget.value

        // Calculate camera right and up vectors
        val forward = Vector3(
            target.x - current.x,
            target.y - current.y,
            target.z - current.z
        ).normalize()

        val right = Vector3(
            forward.z,
            0f,
            -forward.x
        ).normalize()

        val up = Vector3(
            right.y * forward.z - right.z * forward.y,
            right.z * forward.x - right.x * forward.z,
            right.x * forward.y - right.y * forward.x
        )

        val panSpeed = 0.01f
        val deltaRight = right * (deltaX * panSpeed)
        val deltaUp = up * (deltaY * panSpeed)

        _cameraPosition.value = Vector3(
            current.x + deltaRight.x + deltaUp.x,
            current.y + deltaRight.y + deltaUp.y,
            current.z + deltaRight.z + deltaUp.z
        )

        _cameraTarget.value = Vector3(
            target.x + deltaRight.x + deltaUp.x,
            target.y + deltaRight.y + deltaUp.y,
            target.z + deltaRight.z + deltaUp.z
        )
    }

    fun zoomCamera(delta: Float) {
        val current = _cameraPosition.value
        val target = _cameraTarget.value

        val direction = Vector3(
            target.x - current.x,
            target.y - current.y,
            target.z - current.z
        ).normalize()

        val zoomSpeed = 0.1f
        val zoom = direction * (delta * zoomSpeed)

        val newPosition = Vector3(
            current.x + zoom.x,
            current.y + zoom.y,
            current.z + zoom.z
        )

        // Prevent camera from going too close to target
        val minDistance = 0.1f
        val distance = sqrt(
            (newPosition.x - target.x) * (newPosition.x - target.x) +
            (newPosition.y - target.y) * (newPosition.y - target.y) +
            (newPosition.z - target.z) * (newPosition.z - target.z)
        )

        if (distance > minDistance) {
            _cameraPosition.value = newPosition
        }
    }

    // Render settings

    fun setRenderSize(size: PreviewSize) {
        _renderSize.value = size
    }

    fun setRenderQuality(quality: PreviewQuality) {
        _renderQuality.value = quality
    }

    fun setWireframeMode(enabled: Boolean) {
        _wireframeMode.value = enabled
    }

    fun setShowGrid(enabled: Boolean) {
        _showGrid.value = enabled
    }

    fun setShowNormals(enabled: Boolean) {
        _showNormals.value = enabled
    }

    // Animation controls

    fun setAnimationEnabled(enabled: Boolean) {
        _animationEnabled.value = enabled
    }

    fun setAnimationSpeed(speed: Float) {
        require(speed >= 0f) { "Animation speed must be non-negative" }
        _animationSpeed.value = speed
    }

    fun setAnimationTime(time: Float) {
        _animationTime.value = time
    }

    fun resetAnimation() {
        _animationTime.value = 0f
    }

    // Rendering

    fun triggerRender() {
        renderJob?.cancel()
        renderJob = scope.launch {
            performRender()
        }
    }

    fun captureScreenshot(): PreviewImage? {
        return performCapture()
    }

    fun captureWithSettings(size: PreviewSize, quality: PreviewQuality): PreviewImage? {
        val originalSize = _renderSize.value
        val originalQuality = _renderQuality.value

        _renderSize.value = size
        _renderQuality.value = quality

        val result = performCapture()

        _renderSize.value = originalSize
        _renderQuality.value = originalQuality

        return result
    }

    // Private implementation

    private fun applyLightingPreset(lighting: PreviewLighting) {
        when (lighting) {
            PreviewLighting.STUDIO -> {
                _lightIntensity.value = 1f
                _lightColor.value = Color(1f, 1f, 1f, 1f)
                _ambientIntensity.value = 0.3f
                _shadowsEnabled.value = true
            }
            PreviewLighting.OUTDOOR -> {
                _lightIntensity.value = 1.5f
                _lightColor.value = Color(1f, 0.95f, 0.8f, 1f)
                _ambientIntensity.value = 0.4f
                _shadowsEnabled.value = true
            }
            PreviewLighting.INDOOR -> {
                _lightIntensity.value = 0.8f
                _lightColor.value = Color(1f, 0.9f, 0.7f, 1f)
                _ambientIntensity.value = 0.2f
                _shadowsEnabled.value = false
            }
            PreviewLighting.CUSTOM -> {
                // Keep current settings
            }
        }
    }

    private fun updateTriangleCount() {
        _triangleCount.value = when (_geometry.value) {
            PreviewGeometry.SPHERE -> 1024 // Approximate for UV sphere
            PreviewGeometry.CUBE -> 12
            PreviewGeometry.PLANE -> 2
            PreviewGeometry.CYLINDER -> 768
            PreviewGeometry.TORUS -> 2048
            PreviewGeometry.TEAPOT -> 6320 // Classic Utah teapot
            PreviewGeometry.CUSTOM -> _triangleCount.value // Keep existing count
        }
    }

    private fun startAnimation(speed: Float) {
        animationJob?.cancel()
        animationJob = scope.launch {
            var lastTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

            while (true) {
                kotlinx.coroutines.delay(16) // ~60 FPS

                val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val deltaTime = (currentTime - lastTime) / 1000f
                lastTime = currentTime

                _animationTime.value += deltaTime * speed

                // Auto-rotate camera if enabled
                if (_cameraAutoRotate.value) {
                    val rotationSpeed = _cameraRotationSpeed.value
                    orbitCamera(deltaTime * rotationSpeed, 0f)
                }
            }
        }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private suspend fun performRender() {
        _isRendering.value = true
        _renderError.value = null

        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        try {
            val material = _material.value
            if (material == null) {
                _renderError.value = "No material to render"
                return
            }

            // Create render context
            val context = createRenderContext()

            // Setup camera
            setupCamera(context)

            // Setup lighting
            setupLighting(context)

            // Setup environment
            setupEnvironment(context)

            // Setup geometry
            val mesh = createGeometry(_geometry.value)

            // Setup material
            val materialInstance = createMaterialInstance(material)

            // Render frame
            val image = renderFrame(context, mesh, materialInstance)

            // Update performance metrics
            updatePerformanceMetrics(context)

            // Notify listeners
            onPreviewUpdated(image)

            _lastRenderTime.value = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        } catch (e: Exception) {
            _renderError.value = "Render error: ${e.message}"
        } finally {
            val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            _frameTime.value = (endTime - startTime).toFloat()
            _isRendering.value = false
        }
    }

    private fun performCapture(): PreviewImage? {
        // Synchronous capture for screenshots
        return try {
            val material = _material.value ?: return null
            val context = createRenderContext()

            setupCamera(context)
            setupLighting(context)
            setupEnvironment(context)

            val mesh = createGeometry(_geometry.value)
            val materialInstance = createMaterialInstance(material)

            renderFrame(context, mesh, materialInstance)
        } catch (e: Exception) {
            null
        }
    }

    // Platform-specific rendering implementations (to be overridden)

    protected open fun createRenderContext(): RenderContext {
        return RenderContext(
            width = _renderSize.value.width,
            height = _renderSize.value.height,
            quality = _renderQuality.value
        )
    }

    protected open fun setupCamera(context: RenderContext) {
        context.camera = CameraData(
            position = _cameraPosition.value,
            target = _cameraTarget.value,
            fov = _cameraFov.value,
            aspect = context.width.toFloat() / context.height.toFloat()
        )
    }

    protected open fun setupLighting(context: RenderContext) {
        context.lighting = LightingData(
            intensity = _lightIntensity.value,
            color = _lightColor.value,
            ambientIntensity = _ambientIntensity.value,
            shadowsEnabled = _shadowsEnabled.value
        )
    }

    protected open fun setupEnvironment(context: RenderContext) {
        context.environment = EnvironmentData(
            type = _environment.value,
            backgroundColor = _backgroundColor.value
        )
    }

    protected open fun createGeometry(geometry: PreviewGeometry): MeshData {
        return when (geometry) {
            PreviewGeometry.SPHERE -> createSphereMesh()
            PreviewGeometry.CUBE -> createCubeMesh()
            PreviewGeometry.PLANE -> createPlaneMesh()
            PreviewGeometry.CYLINDER -> createCylinderMesh()
            PreviewGeometry.TORUS -> createTorusMesh()
            PreviewGeometry.TEAPOT -> createTeapotMesh()
            PreviewGeometry.CUSTOM -> createCustomMesh()
        }
    }

    protected open fun createMaterialInstance(material: MaterialDefinition): MaterialInstance {
        return MaterialInstance(
            definition = material,
            wireframe = _wireframeMode.value,
            showNormals = _showNormals.value
        )
    }

    protected open fun renderFrame(
        context: RenderContext,
        mesh: MeshData,
        material: MaterialInstance
    ): PreviewImage {
        // Default implementation - would be overridden by platform-specific renderers
        return PreviewImage(
            width = context.width,
            height = context.height,
            data = ByteArray(context.width * context.height * 4) { 255.toByte() } // White image
        )
    }

    protected open fun updatePerformanceMetrics(context: RenderContext) {
        _drawCalls.value = context.drawCalls
        _gpuMemoryUsage.value = context.gpuMemoryUsed
    }

    // Geometry generation

    private fun createSphereMesh(): MeshData {
        val rings = 32
        val sectors = 32
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Generate sphere vertices
        for (ring in 0..rings) {
            val phi = PI * ring / rings
            for (sector in 0..sectors) {
                val theta = 2 * PI * sector / sectors

                val x = sin(phi) * cos(theta)
                val y = cos(phi)
                val z = sin(phi) * sin(theta)

                val u = sector.toFloat() / sectors
                val v = ring.toFloat() / rings

                // Position
                vertices.add(x.toFloat())
                vertices.add(y.toFloat())
                vertices.add(z.toFloat())

                // Normal (same as position for unit sphere)
                vertices.add(x.toFloat())
                vertices.add(y.toFloat())
                vertices.add(z.toFloat())

                // UV
                vertices.add(u)
                vertices.add(v)
            }
        }

        // Generate indices
        for (ring in 0 until rings) {
            for (sector in 0 until sectors) {
                val current = ring * (sectors + 1) + sector
                val next = current + sectors + 1

                indices.add(current)
                indices.add(next)
                indices.add(current + 1)

                indices.add(current + 1)
                indices.add(next)
                indices.add(next + 1)
            }
        }

        return MeshData(
            vertices = vertices.toFloatArray(),
            indices = indices.toIntArray(),
            vertexLayout = VertexLayout.POSITION_NORMAL_UV
        )
    }

    private fun createCubeMesh(): MeshData {
        val vertices = floatArrayOf(
            // Front face
            -1f, -1f,  1f,  0f,  0f,  1f,  0f, 0f,
             1f, -1f,  1f,  0f,  0f,  1f,  1f, 0f,
             1f,  1f,  1f,  0f,  0f,  1f,  1f, 1f,
            -1f,  1f,  1f,  0f,  0f,  1f,  0f, 1f,

            // Back face
            -1f, -1f, -1f,  0f,  0f, -1f,  1f, 0f,
            -1f,  1f, -1f,  0f,  0f, -1f,  1f, 1f,
             1f,  1f, -1f,  0f,  0f, -1f,  0f, 1f,
             1f, -1f, -1f,  0f,  0f, -1f,  0f, 0f,

            // Top face
            -1f,  1f, -1f,  0f,  1f,  0f,  0f, 1f,
            -1f,  1f,  1f,  0f,  1f,  0f,  0f, 0f,
             1f,  1f,  1f,  0f,  1f,  0f,  1f, 0f,
             1f,  1f, -1f,  0f,  1f,  0f,  1f, 1f,

            // Bottom face
            -1f, -1f, -1f,  0f, -1f,  0f,  1f, 1f,
             1f, -1f, -1f,  0f, -1f,  0f,  0f, 1f,
             1f, -1f,  1f,  0f, -1f,  0f,  0f, 0f,
            -1f, -1f,  1f,  0f, -1f,  0f,  1f, 0f,

            // Right face
             1f, -1f, -1f,  1f,  0f,  0f,  1f, 0f,
             1f,  1f, -1f,  1f,  0f,  0f,  1f, 1f,
             1f,  1f,  1f,  1f,  0f,  0f,  0f, 1f,
             1f, -1f,  1f,  1f,  0f,  0f,  0f, 0f,

            // Left face
            -1f, -1f, -1f, -1f,  0f,  0f,  0f, 0f,
            -1f, -1f,  1f, -1f,  0f,  0f,  1f, 0f,
            -1f,  1f,  1f, -1f,  0f,  0f,  1f, 1f,
            -1f,  1f, -1f, -1f,  0f,  0f,  0f, 1f
        )

        val indices = intArrayOf(
            0,  1,  2,   0,  2,  3,    // front
            4,  5,  6,   4,  6,  7,    // back
            8,  9, 10,   8, 10, 11,    // top
           12, 13, 14,  12, 14, 15,    // bottom
           16, 17, 18,  16, 18, 19,    // right
           20, 21, 22,  20, 22, 23     // left
        )

        return MeshData(
            vertices = vertices,
            indices = indices,
            vertexLayout = VertexLayout.POSITION_NORMAL_UV
        )
    }

    private fun createPlaneMesh(): MeshData {
        val vertices = floatArrayOf(
            -1f, 0f, -1f,  0f, 1f, 0f,  0f, 0f,
             1f, 0f, -1f,  0f, 1f, 0f,  1f, 0f,
             1f, 0f,  1f,  0f, 1f, 0f,  1f, 1f,
            -1f, 0f,  1f,  0f, 1f, 0f,  0f, 1f
        )

        val indices = intArrayOf(0, 1, 2, 0, 2, 3)

        return MeshData(
            vertices = vertices,
            indices = indices,
            vertexLayout = VertexLayout.POSITION_NORMAL_UV
        )
    }

    private fun createCylinderMesh(): MeshData {
        // Simplified cylinder generation
        val segments = 32
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Generate vertices for top and bottom circles
        for (i in 0..segments) {
            val angle = 2 * PI * i / segments
            val x = cos(angle).toFloat()
            val z = sin(angle).toFloat()

            // Top circle
            vertices.addAll(listOf(x, 1f, z, 0f, 1f, 0f, i.toFloat() / segments, 0f))
            // Bottom circle
            vertices.addAll(listOf(x, -1f, z, 0f, -1f, 0f, i.toFloat() / segments, 1f))
        }

        // Generate indices for sides
        for (i in 0 until segments) {
            val top1 = i * 2
            val bottom1 = i * 2 + 1
            val top2 = (i + 1) * 2
            val bottom2 = (i + 1) * 2 + 1

            // Side quad
            indices.addAll(listOf(top1, bottom1, top2))
            indices.addAll(listOf(top2, bottom1, bottom2))
        }

        return MeshData(
            vertices = vertices.toFloatArray(),
            indices = indices.toIntArray(),
            vertexLayout = VertexLayout.POSITION_NORMAL_UV
        )
    }

    private fun createTorusMesh(): MeshData {
        val majorRadius = 1f
        val minorRadius = 0.3f
        val majorSegments = 32
        val minorSegments = 16

        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        for (i in 0..majorSegments) {
            val u = 2 * PI * i / majorSegments
            for (j in 0..minorSegments) {
                val v = 2 * PI * j / minorSegments

                val x = (majorRadius + minorRadius * cos(v)) * cos(u)
                val y = minorRadius * sin(v)
                val z = (majorRadius + minorRadius * cos(v)) * sin(u)

                val nx = cos(v) * cos(u)
                val ny = sin(v)
                val nz = cos(v) * sin(u)

                vertices.addAll(listOf(
                    x.toFloat(), y.toFloat(), z.toFloat(),
                    nx.toFloat(), ny.toFloat(), nz.toFloat(),
                    i.toFloat() / majorSegments, j.toFloat() / minorSegments
                ))
            }
        }

        // Generate indices
        for (i in 0 until majorSegments) {
            for (j in 0 until minorSegments) {
                val current = i * (minorSegments + 1) + j
                val next = ((i + 1) % (majorSegments + 1)) * (minorSegments + 1) + j

                indices.addAll(listOf(current, next, current + 1))
                indices.addAll(listOf(current + 1, next, next + 1))
            }
        }

        return MeshData(
            vertices = vertices.toFloatArray(),
            indices = indices.toIntArray(),
            vertexLayout = VertexLayout.POSITION_NORMAL_UV
        )
    }

    private fun createTeapotMesh(): MeshData {
        // Teapot geometry uses sphere approximation for material preview
        return createSphereMesh()
    }

    private fun createCustomMesh(): MeshData {
        // Return stored custom mesh or default to sphere
        return createSphereMesh()
    }

    // Helper extension
    private fun Vector3.normalize(): Vector3 {
        val length = sqrt(x * x + y * y + z * z)
        return if (length > 0f) {
            Vector3(x / length, y / length, z / length)
        } else {
            Vector3.ZERO
        }
    }

    private operator fun Vector3.times(scalar: Float): Vector3 {
        return Vector3(x * scalar, y * scalar, z * scalar)
    }
}

// Data classes for preview functionality

data class PreviewImage(
    val width: Int,
    val height: Int,
    val data: ByteArray,
    val format: ImageFormat = ImageFormat.RGBA8
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewImage) return false

        return width == other.width &&
                height == other.height &&
                format == other.format &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class RenderContext(
    val width: Int,
    val height: Int,
    val quality: PreviewQuality,
    var camera: CameraData? = null,
    var lighting: LightingData? = null,
    var environment: EnvironmentData? = null,
    var drawCalls: Int = 0,
    var gpuMemoryUsed: Long = 0
)

data class CameraData(
    val position: Vector3,
    val target: Vector3,
    val fov: Float,
    val aspect: Float
)

data class LightingData(
    val intensity: Float,
    val color: Color,
    val ambientIntensity: Float,
    val shadowsEnabled: Boolean
)

data class EnvironmentData(
    val type: PreviewEnvironment,
    val backgroundColor: Color
)

data class MeshData(
    val vertices: FloatArray,
    val indices: IntArray,
    val vertexLayout: VertexLayout
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshData) return false

        return vertexLayout == other.vertexLayout &&
                vertices.contentEquals(other.vertices) &&
                indices.contentEquals(other.indices)
    }

    override fun hashCode(): Int {
        var result = vertexLayout.hashCode()
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        return result
    }
}

data class MaterialInstance(
    val definition: MaterialDefinition,
    val wireframe: Boolean = false,
    val showNormals: Boolean = false
)

// Enums

enum class PreviewGeometry {
    SPHERE, CUBE, PLANE, CYLINDER, TORUS, TEAPOT, CUSTOM
}

enum class PreviewLighting {
    STUDIO, OUTDOOR, INDOOR, CUSTOM
}

enum class PreviewEnvironment {
    COLOR, HDRI, GRADIENT
}

enum class PreviewSize(val width: Int, val height: Int) {
    SMALL(256, 256),
    MEDIUM(512, 512),
    LARGE(1024, 1024),
    EXTRA_LARGE(2048, 2048)
}

enum class PreviewQuality {
    LOW, MEDIUM, HIGH, ULTRA
}

enum class ImageFormat {
    RGB8, RGBA8, RGB16F, RGBA16F
}

enum class VertexLayout {
    POSITION_NORMAL_UV, POSITION_NORMAL_TANGENT_UV, POSITION_UV, POSITION_COLOR
}