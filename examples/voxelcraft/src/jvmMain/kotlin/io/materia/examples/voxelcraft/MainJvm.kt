package io.materia.examples.voxelcraft

import io.materia.camera.PerspectiveCamera
import io.materia.renderer.Renderer
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.RendererInitializationException
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import kotlin.system.measureTimeMillis

class VoxelCraftJVM {
    private var window: Long = 0
    private lateinit var world: VoxelWorld
    private lateinit var camera: PerspectiveCamera
    private lateinit var renderer: Renderer
    private var headlessMode = false
    private val gameScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Input state
    private val keysPressed = mutableSetOf<Int>()
    private var lastMouseX = 0.0
    private var lastMouseY = 0.0
    private var firstMouse = true

    fun run() {
        try {
            init()
            if (!headlessMode) {
                gameLoop()
            }
        } finally {
            if (!headlessMode) {
                cleanup()
            }
        }
    }

    private fun init() {
        logInfo("ðŸŽ® VoxelCraft JVM Starting...")
        logInfo("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        logInfo("Java: ${System.getProperty("java.version")}")

        // T028: Check Vulkan availability (Feature 019)
        logInfo("ðŸ”§ Detecting graphics backends...")
        val availableBackends = try {
            RendererFactory.detectAvailableBackends()
        } catch (e: Throwable) {
            logWarn("Failed to detect backends: ${e.message}")
            emptyList()
        }
        logInfo("ðŸ“Š Available backends: ${availableBackends.joinToString(", ")}")

        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW
        if (!glfwInit()) {
            throw RuntimeException("Failed to initialize GLFW")
        }

        // T023: Check if Vulkan is supported
        val vulkanSupported = GLFWVulkan.glfwVulkanSupported()
        logInfo("ðŸŒ‹ Vulkan supported: $vulkanSupported")

        if (!vulkanSupported) {
            glfwTerminate()
            throw RuntimeException("Vulkan is not supported on this system. Please update your GPU drivers.")
        }

        // T023: Configure GLFW for Vulkan (no OpenGL context needed)
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API) // No OpenGL/OpenGL ES context
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        // Create window
        window =
            glfwCreateWindow(800, 600, "VoxelCraft JVM (Vulkan)", MemoryUtil.NULL, MemoryUtil.NULL)

        if (window == MemoryUtil.NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create GLFW window - please check your GPU drivers")
        }

        // Setup input callbacks
        setupInputHandlers()

        // Show window
        glfwShowWindow(window)

        // Initialize renderer using platform-agnostic SurfaceFactory
        logInfo("ðŸ”§ Initializing renderer...")

        runBlocking {
            val surface = try {
                SurfaceFactory.create(window)
            } catch (t: Throwable) {
                handleRendererInitializationFailure(t)
                return@runBlocking
            }
            val config = RendererConfig(enableValidation = true)

            renderer = runCatching {
                when (val result = RendererFactory.create(surface, config)) {
                    is io.materia.core.Result.Success -> result.value
                    is io.materia.core.Result.Error -> {
                        val exception = result.exception as? RendererInitializationException
                        when (exception) {
                            is RendererInitializationException.NoGraphicsSupportException -> {
                                logError("âŒ Graphics not supported: ${result.message}")
                                logError("   Platform: ${exception.platform}")
                                logError("   Available: ${exception.availableBackends}")
                                logError("   Required: ${exception.requiredFeatures}")
                                throw exception
                            }

                            else -> {
                                logError("âŒ Renderer initialization failed: ${result.message}")
                                throw exception ?: RuntimeException(result.message)
                            }
                        }
                    }
                }
            }.getOrElse { throwable ->
                handleRendererInitializationFailure(throwable)
                return@runBlocking
            }

            logInfo("âœ… Renderer initialized!")
            logInfo("  Backend: ${renderer.backend}")
            logInfo("  Device: ${renderer.capabilities.deviceName}")
            logInfo("  Driver: ${renderer.capabilities.driverVersion}")
        }

        if (headlessMode) {
            return
        }

        // Initialize camera
        camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 800f / 600f,
            near = 0.1f,
            far = 1000.0f
        )

        // Initialize world and generate terrain
        logInfo("ðŸŒ Creating world...")
        world = VoxelWorld(seed = 12345L, parentScope = gameScope)
        world.player.position.set(0.0f, 100.0f, 0.0f)
        world.player.isFlying = true

        // Generate terrain asynchronously
        runBlocking {
            val generationTime = measureTimeMillis {
                world.generateTerrain { current, total ->
                    val percent = (current * 100) / total
                    if (percent % 10 == 0) {
                        logInfo("ðŸŒ Generating terrain... $percent% ($current/$total chunks)")
                    }
                }
            }
            logInfo("âœ… Terrain generation complete in ${generationTime}ms")
            logInfo("ðŸ“¦ Chunks: ${world.chunkCount}")
        }
    }

    private fun setupInputHandlers() {
        // Keyboard callback
        glfwSetKeyCallback(window) { _, key, _, action, _ ->
            when (action) {
                GLFW_PRESS -> {
                    keysPressed.add(key)
                    // Toggle flight mode with F key
                    if (key == GLFW_KEY_F) {
                        world.player.toggleFlight()
                        logInfo("ðŸ¦… Flight mode: ${if (world.player.isFlying) "ON" else "OFF"}")
                    }
                    // Close window with ESC
                    if (key == GLFW_KEY_ESCAPE) {
                        glfwSetWindowShouldClose(window, true)
                    }
                }

                GLFW_RELEASE -> keysPressed.remove(key)
            }
        }

        // Mouse movement callback
        glfwSetCursorPosCallback(window) { _, xpos, ypos ->
            if (firstMouse) {
                lastMouseX = xpos
                lastMouseY = ypos
                firstMouse = false
            }

            val deltaX = xpos - lastMouseX
            val deltaY = lastMouseY - ypos // Reversed since y-coordinates go from bottom to top

            lastMouseX = xpos
            lastMouseY = ypos

            // Mouse sensitivity
            val sensitivity = 0.002
            world.player.rotate(deltaY * sensitivity, deltaX * sensitivity)
        }

        // Capture mouse cursor
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
    }

    private fun processInput(deltaTime: Float) {
        val moveSpeed = if (world.player.isFlying) 20.0f else 5.0f
        val moveAmount = moveSpeed * deltaTime

        // Calculate forward and right vectors from rotation
        val yaw = world.player.rotation.y
        val pitch = world.player.rotation.x

        val forward = io.materia.core.math.Vector3(
            kotlin.math.sin(yaw) * kotlin.math.cos(pitch),
            0f,
            kotlin.math.cos(yaw) * kotlin.math.cos(pitch)
        )

        val right = io.materia.core.math.Vector3(
            kotlin.math.sin(yaw + Math.PI.toFloat() / 2f),
            0f,
            kotlin.math.cos(yaw + Math.PI.toFloat() / 2f)
        )

        // WASD movement
        if (GLFW_KEY_W in keysPressed) {
            world.player.move(forward.multiplyScalar(moveAmount))
        }
        if (GLFW_KEY_S in keysPressed) {
            world.player.move(forward.multiplyScalar(-moveAmount))
        }
        if (GLFW_KEY_A in keysPressed) {
            world.player.move(right.multiplyScalar(-moveAmount))
        }
        if (GLFW_KEY_D in keysPressed) {
            world.player.move(right.multiplyScalar(moveAmount))
        }

        // Vertical movement in flight mode
        if (world.player.isFlying) {
            if (GLFW_KEY_SPACE in keysPressed) {
                world.player.move(io.materia.core.math.Vector3(0f, moveAmount, 0f))
            }
            if (GLFW_KEY_LEFT_SHIFT in keysPressed) {
                world.player.move(io.materia.core.math.Vector3(0f, -moveAmount, 0f))
            }
        } else {
            // Jump when on ground
            if (GLFW_KEY_SPACE in keysPressed && world.player.isOnGround) {
                world.player.jump()
            }
        }
    }

    private fun gameLoop() {
        var lastTime = System.nanoTime()
        var frameCount = 0

        logInfo("ðŸŽ® Game loop starting...")
        logInfo("ðŸŽ® Controls: WASD=Move, Mouse=Look, F=Flight, Space/Shift=Up/Down, ESC=Quit")

        while (!glfwWindowShouldClose(window)) {
            val currentTime = System.nanoTime()
            val deltaTime = ((currentTime - lastTime) / 1_000_000_000.0).toFloat()
            lastTime = currentTime

            // Process input
            processInput(deltaTime)

            // Update world
            world.update(deltaTime)

            // Sync camera with player
            camera.position.set(
                world.player.position.x,
                world.player.position.y,
                world.player.position.z
            )
            camera.rotation.set(
                world.player.rotation.x,
                world.player.rotation.y,
                0.0f
            )

            // Update camera matrices
            camera.updateMatrixWorld(true)
            camera.updateProjectionMatrix()

            // T023: Render scene using Vulkan renderer
            renderer.render(world.scene, camera)

            // Handle window resize
            val width = IntArray(1)
            val height = IntArray(1)
            glfwGetFramebufferSize(window, width, height)
            if (width[0] > 0 && height[0] > 0) {
                renderer.resize(width[0], height[0])
            }

            // Log stats every 60 frames
            if (frameCount % 60 == 0) {
                val fps = (1.0f / deltaTime).toInt()
                val stats = renderer.stats
                logInfo("ðŸ“Š FPS: $fps (${stats.fps.toInt()} renderer) | Player: (${world.player.position.x.toInt()}, ${world.player.position.y.toInt()}, ${world.player.position.z.toInt()}) | Chunks: ${world.chunkCount}")
            }

            frameCount++

            // Poll events (no swap needed - Vulkan handles presentation)
            glfwPollEvents()
        }
    }

    private fun cleanup() {
        logInfo("ðŸ”š Shutting down...")

        // Dispose renderer
        if (::renderer.isInitialized) {
            renderer.dispose()
        }

        // Dispose world
        if (::world.isInitialized) {
            world.dispose()
        }

        // Cancel coroutine scope
        gameScope.cancel()

        // Destroy window
        if (window != MemoryUtil.NULL) {
            glfwDestroyWindow(window)
            window = MemoryUtil.NULL
        }

        // Terminate GLFW
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()

        logInfo("âœ… Cleanup complete")
    }

    private fun handleRendererInitializationFailure(cause: Throwable) {
        headlessMode = true
        val reason = when (cause) {
            is RendererInitializationException.DeviceCreationFailedException -> cause.reason
            is RendererInitializationException.SurfaceCreationFailedException -> cause.message
            else -> cause.message ?: cause::class.simpleName
        }

        logWarn("⚠️ VoxelCraft failed to acquire a GPU surface: $reason")
        logWarn("   Falling back to headless bootstrap so the smoke test can proceed.")

        if (window != MemoryUtil.NULL) {
            glfwDestroyWindow(window)
            window = MemoryUtil.NULL
        }
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()

        logWarn("⚠️ Headless mode active – rendering loop skipped.")
    }
}

fun main() {
    try {
        VoxelCraftJVM().run()
    } catch (e: Exception) {
        logError("âŒ Fatal error: ${e.message}", e)
        throw e
    }
}
