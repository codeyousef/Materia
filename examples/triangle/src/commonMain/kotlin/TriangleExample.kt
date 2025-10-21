/**
 * Triangle Example - Demonstrates KreeKt 3D Library Core Functionality
 *
 * This example shows how to create a simple 3D scene with:
 * - Basic geometries (cube, sphere, plane)
 * - PBR materials
 * - Dynamic lighting
 * - Camera controls
 * - Animation loop
 */

import io.kreekt.animation.AnimationMixer
import io.kreekt.animation.DefaultAnimationMixer
import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.Color
import io.kreekt.core.math.Vector3
import io.kreekt.core.scene.Background
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.primitives.BoxGeometry
import io.kreekt.geometry.primitives.PlaneGeometry
import io.kreekt.geometry.primitives.SphereGeometry
import io.kreekt.material.SimpleMaterial
import io.kreekt.renderer.Renderer
import io.kreekt.renderer.RenderSurface
import io.kreekt.lighting.IBLConfig
import io.kreekt.lighting.IBLProcessorImpl
import io.kreekt.lighting.IBLResult
import io.kreekt.lighting.processEnvironmentForScene
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Platform-specific declarations
expect suspend fun createPlatformSurface(): RenderSurface
expect suspend fun initializeRendererWithBackend(surface: RenderSurface): Renderer
expect class InputState() {
    fun isKeyPressed(key: String): Boolean
    val isMousePressed: Boolean
    val mouseDeltaX: Float
    val mouseDeltaY: Float
}

expect fun getCurrentTimeMillis(): Long
expect fun getCurrentInput(): InputState

class TriangleExample {
    private lateinit var scene: Scene
    private lateinit var camera: PerspectiveCamera
    private lateinit var renderer: Renderer
    private lateinit var animationMixer: AnimationMixer

    private val iblProcessor = IBLProcessorImpl()
    private val iblConfig = IBLConfig(
        irradianceSize = 32,
        prefilterSize = 128,
        brdfLutSize = 256,
        roughnessLevels = 5
    )

    // Scene objects
    private lateinit var rotatingCube: Mesh
    private lateinit var floatingSphere: Mesh
    private lateinit var ground: Mesh

    // Animation state
    private var time = 0.0f

    suspend fun initialize() {
        println("🚀 Initializing KreeKt Triangle Example...")
        println("📊 Using new WebGPU/Vulkan backend system")

        // Create scene
        scene = Scene().apply {
            background = Background.Color(Color(0.1f, 0.1f, 0.2f)) // Dark blue background
        }

        // Setup camera
        camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 16.0f / 9.0f,
            near = 0.1f,
            far = 1000.0f
        ).apply {
            position.set(5.0f, 5.0f, 5.0f)
            lookAt(Vector3.ZERO)
        }

        // Initialize renderer using new backend system
        println("🔧 Creating platform-specific render surface...")
        val surface = createPlatformSurface()

        println("🔧 Initializing backend negotiation...")

        // Initialize renderer with backend telemetry
        renderer = initializeRendererWithBackend(surface)

        renderer.setSize(1920, 1080)
        renderer.clearColor = Color(0.05f, 0.05f, 0.1f)

        // Setup scene objects
        createSceneObjects()
        setupLighting()
        setupEnvironmentLighting()
        setupAnimations()

        println("✅ Scene initialized successfully!")
        println("📊 Scene stats: ${scene.children.size} objects")
    }

    private suspend fun createSceneObjects() {
        // Create rotating cube with PBR material
        val cubeGeometry = BoxGeometry(2.0f, 2.0f, 2.0f)
        val cubeMaterial = SimpleMaterial(
            albedo = Color(0.8f, 0.3f, 0.2f), // Red-orange
            metallic = 0.7f,
            roughness = 0.3f,
            emissive = Color(0.1f, 0.05f, 0.0f), // Slight glow
            materialName = "CubeMaterial"
        )
        rotatingCube = Mesh(cubeGeometry, cubeMaterial).apply {
            position.set(0.0f, 2.0f, 0.0f)
        }
        scene.add(rotatingCube)

        // Create floating sphere with animated material
        val sphereGeometry = SphereGeometry(1.5f, 32, 16)
        val sphereMaterial = SimpleMaterial(
            albedo = Color(0.2f, 0.6f, 0.9f), // Blue
            metallic = 0.1f,
            roughness = 0.1f,
            transparent = true,
            materialName = "SphereMaterial"
        )
        floatingSphere = Mesh(sphereGeometry, sphereMaterial).apply {
            position.set(3.0f, 3.0f, 2.0f)
        }
        scene.add(floatingSphere)

        // Create ground plane
        val groundGeometry = PlaneGeometry(20.0f, 20.0f)
        val groundMaterial = SimpleMaterial(
            albedo = Color(0.4f, 0.4f, 0.4f), // Gray
            metallic = 0.0f,
            roughness = 0.8f,
            materialName = "GroundMaterial"
        )
        ground = Mesh(groundGeometry, groundMaterial).apply {
            rotation.x = -kotlin.math.PI.toFloat() / 2 // Rotate to be horizontal
        }
        scene.add(ground)

        // Add some decorative objects
        repeat(5) { i ->
            val decorGeometry = BoxGeometry(0.5f, 0.5f, 0.5f)
            val decorMaterial = SimpleMaterial(
                albedo = Color(
                    0.3f + (i * 0.15f),
                    0.6f,
                    0.9f - (i * 0.1f)
                ),
                metallic = 0.5f,
                roughness = 0.4f,
                materialName = "DecorMaterial$i"
            )
            val decorCube = Mesh(decorGeometry, decorMaterial).apply {
                position.set(
                    (i - 2) * 2.0f,
                    0.25f,
                    -3.0f
                )
            }
            scene.add(decorCube)
        }
    }

    private fun setupLighting() {
        // Basic ambient lighting for the example scene
        // Full light object wrappers are planned for Phase 3+ (see CLAUDE.md - Lighting section)
        println("🔆 Setting up basic lighting...")
    }

    private suspend fun setupEnvironmentLighting() {
        println("🌅 Processing HDR environment for image-based lighting...")
        val hdrResult = iblProcessor.loadHDREnvironment("assets/environments/studio_small.hdr")
        when (hdrResult) {
            is IBLResult.Success -> {
                val iblResult = iblProcessor.processEnvironmentForScene(
                    hdr = hdrResult.data,
                    config = iblConfig,
                    scene = scene
                )
                when (iblResult) {
                    is IBLResult.Success -> {
                        val maps = iblResult.data
                        println(
                            "✅ Applied prefiltered environment (prefilter=${maps.prefilter.size}, " +
                                    "brdf=${maps.brdfLut.width}x${maps.brdfLut.height})"
                        )
                        if (maps.brdfLut.width < 512) {
                            println("   ↳ Vulkan path still runs with a softer BRDF fallback until GPU LUT promotion lands.")
                        }
                    }

                    is IBLResult.Error -> {
                        println("⚠️ Failed to generate environment maps: ${iblResult.message}")
                    }
                }
            }

            is IBLResult.Error -> {
                println("⚠️ Failed to load HDR environment: ${hdrResult.message}")
            }
        }
    }

    private fun setupAnimations() {
        animationMixer = DefaultAnimationMixer(scene)

        // No pre-defined animations for this basic example
        // We'll do manual animation in the render loop
    }

    /**
     * Main render loop - call this continuously for animation
     */
    fun render(deltaTime: Float) {
        time += deltaTime

        // Animate rotating cube
        rotatingCube.rotation.apply {
            y = time * 0.5f
            x = time * 0.3f
        }

        // Animate floating sphere
        floatingSphere.position.apply {
            y = 3.0f + sin(time * 2.0f) * 0.5f
            x = 3.0f + cos(time * 1.0f) * 1.0f
        }

        // Pulse the sphere's emissive intensity
        val sphereMaterial = floatingSphere.material as? SimpleMaterial
        sphereMaterial?.let { material ->
            material.emissive = Color(
                0.1f + sin(time * 3.0f) * 0.1f,
                0.2f + cos(time * 2.0f) * 0.1f,
                0.3f
            )
        }

        // Rotate camera around the scene
        val cameraRadius = 8.0f
        camera.position.apply {
            x = cos(time * 0.2f) * cameraRadius
            z = sin(time * 0.2f) * cameraRadius
            y = 5.0f + sin(time * 0.1f) * 2.0f
        }
        camera.lookAt(Vector3(0.0f, 1.0f, 0.0f))

        // Update animations
        animationMixer.update(deltaTime)

        // Render the scene
        renderer.render(scene, camera)
    }

    /**
     * Add interactive controls for the camera
     */
    fun handleInput(input: InputState) {
        val forward = camera.getWorldDirection()
        val right = Vector3(1f, 0f, 0f) // Simplified right vector

        when {
            input.isKeyPressed("W") -> camera.position.add(forward.multiplyScalar(0.1f))
            input.isKeyPressed("S") -> camera.position.sub(forward.multiplyScalar(0.1f))
            input.isKeyPressed("A") -> camera.position.add(right.multiplyScalar(-0.1f))
            input.isKeyPressed("D") -> camera.position.add(right.multiplyScalar(0.1f))
            input.isKeyPressed("Q") -> camera.position.y += 0.1f
            input.isKeyPressed("E") -> camera.position.y -= 0.1f
        }

        // Mouse look
        if (input.isMousePressed) {
            val sensitivity = 0.002f
            camera.rotation.y -= input.mouseDeltaX * sensitivity
            camera.rotation.x -= input.mouseDeltaY * sensitivity
            camera.rotation.x = camera.rotation.x.coerceIn(-1.5f, 1.5f) // Clamp vertical look
        }
    }

    fun resize(width: Int, height: Int) {
        camera.aspect = width.toFloat() / height.toFloat()
        camera.updateProjectionMatrix()
        renderer.setSize(width, height)
    }

    fun dispose() {
        println("🧹 Cleaning up KreeKt Triangle Example...")
        renderer.dispose()
        // Dispose geometries and materials
        scene.traverse { obj ->
            if (obj is Mesh) {
                obj.geometry.dispose()
                // Material disposal would depend on material implementation
            }
        }
    }

    /**
     * Print scene information for debugging
     */
    fun printSceneInfo() {
        println("\n📋 Scene Information:")
        println("Objects in scene: ${scene.children.size}")
        println("Camera position: ${camera.position}")
        println("Camera rotation: ${camera.rotation}")

        scene.children.forEachIndexed { index, obj ->
            println("  [$index] ${obj::class.simpleName} at ${obj.position}")
        }

        // Performance info
        val renderStats = renderer.getStats()
        println("\n📊 Render Statistics:")
        println("Triangles: ${renderStats.triangles}")
        println("Draw calls: ${renderStats.calls}")
        println("Frame: ${renderStats.frame}")
    }
}

// Platform-specific renderer creation and input handling are declared above

/**
 * Example main function structure
 */
suspend fun runTriangleExample() {
    val example = TriangleExample()

    try {
        example.initialize()
        example.printSceneInfo()

        println("\n🎮 Controls:")
        println("WASD - Move camera")
        println("QE - Move up/down")
        println("Mouse - Look around")
        println("\n🎬 Starting render loop...")

        // Main application loop (implement this per platform)
        var lastTime = getCurrentTimeMillis()

        while (true) {
            val currentTime = getCurrentTimeMillis()
            val deltaTime = (currentTime - lastTime) / 1000.0f
            lastTime = currentTime

            // Handle input (implement per platform)
            val input = getCurrentInput()
            example.handleInput(input)

            // Render frame
            example.render(deltaTime)

            // Platform-specific frame limiting/vsync
            delay(16) // ~60 FPS
        }

    } catch (e: Exception) {
        println("❌ Error running example: ${e.message}")
        e.printStackTrace()
    } finally {
        example.dispose()
    }
}

// getCurrentTimeMillis and getCurrentInput are declared above
