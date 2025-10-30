package io.materia.examples.voxelcraft

import io.materia.camera.PerspectiveCamera
import io.materia.controls.PointerLock
import io.materia.core.scene.Scene
import io.materia.lighting.IBLConfig
import io.materia.lighting.IBLProcessorImpl
import io.materia.lighting.ibl.IBLResult
import io.materia.lighting.processEnvironmentForScene
import io.materia.renderer.FPSCounter
import io.materia.renderer.RendererFactory
import io.materia.renderer.RendererInitializationException
import io.materia.renderer.SurfaceFactory
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.w3c.dom.HTMLCanvasElement

private val gameScope = MainScope()
private var initJob: Job? = null
private val iblProcessor = IBLProcessorImpl()
private val iblConfig = IBLConfig(
    irradianceSize = 32,
    prefilterSize = 128,
    brdfLutSize = 256,
    roughnessLevels = 5
)

/**
 * VoxelCraft entry point
 *
 * Demonstrates:
 * - World generation with Simplex noise (1,024 chunks)
 * - Player controls (WASD movement, mouse camera)
 * - Flight mode (F key toggle)
 * - WebGPU rendering with Materia (Feature 019/020)
 * - Persistence (save/load to localStorage)
 * - Game loop with delta time
 */

fun main() {
    // Expose startGameFromButton to JavaScript immediately (don't wait for load event)
    window.asDynamic().startGameFromButton = ::startGameFromButton

    window.addEventListener("beforeunload", {
        gameScope.cancel()
    })
}

fun startGameFromButton() {
    initJob?.cancel()
    initJob = gameScope.launch {
        initGame()
    }
}

suspend fun initGame() = coroutineScope {
    logInfo("Initializing VoxelCraft...")

    val canvas = document.getElementById("materia-canvas") as? HTMLCanvasElement
    if (canvas == null) {
        logError("Canvas element not found!")
        return@coroutineScope
    }

    canvas.width = 800
    canvas.height = 600

    val storage = WorldStorage()
    val savedState = storage.load()
    val startTime = js("Date.now()") as Double

    val world = savedState?.restore(this) ?: VoxelWorld(seed = 12345L, parentScope = this)

    // Set temporary spawn position (terrain not generated yet!)
    // Will find actual ground level after terrain generation completes
    val spawnX = -17  // User-specified spawn location
    val spawnZ = 2
    val tempSpawnY = 150 // High above terrain, safe temporary position

    world.player.position.set(spawnX.toFloat(), tempSpawnY.toFloat(), spawnZ.toFloat())
    world.player.rotation.set(-0.3f, 0.0f, 0.0f)
    world.player.isFlying = true // Temporary, will disable after finding ground

    logInfo("Temporary spawn: ($spawnX, $tempSpawnY, $spawnZ) - flight ON")
    logInfo("Will find ground after terrain generation completes...")

    generateTerrainAsync(
        scope = this,
        world = world,
        startTime = startTime,
        canvas = canvas,
        savedState = savedState
    )
}


fun generateTerrainAsync(
    scope: CoroutineScope,
    world: VoxelWorld,
    startTime: Double,
    canvas: HTMLCanvasElement,
    savedState: WorldState?
) {
    updateLoadingProgress("Starting terrain generation...")

    scope.launch {
        continueInitialization(world, canvas)
    }

    scope.launch {
        try {
            // T021: Generate terrain data first (Phase 1)
            world.generateTerrain { current, total ->
                val percent = (current * 100) / total
                if (percent % 10 == 0) {
                    logInfo("Generating terrain... $percent% ($current/$total chunks)")
                    updateLoadingProgress("Generating terrain: $percent% ($current/$total chunks)")
                }
            }

            if (savedState != null) {
                savedState.applyModifications(world)
                logInfo("Applied ${savedState.chunks.size} saved chunk modifications")
            }

            val generationTime = js("Date.now()") as Double - startTime
            logInfo("Terrain generation complete in ${generationTime.toInt()}ms")

            // NOW find actual ground level (terrain exists!)
            // Use user-specified spawn position
            val spawnX = -17
            val spawnZ = 2
            var groundY = 255

            // Search downward for first solid block
            while (groundY > 0) {
                val block = world.getBlock(spawnX, groundY, spawnZ)
                if (block != null && block != BlockType.Air && !block.isTransparent) {
                    break
                }
                groundY--
            }

            // Move player to ground level
            val finalSpawnY = groundY + 2 // Spawn 2 blocks above ground (player height = 1.8)
            world.player.position.set(spawnX.toFloat(), finalSpawnY.toFloat(), spawnZ.toFloat())
            world.player.isFlying = false // Disable flight
            world.player.velocity.set(0f, 0f, 0f) // Reset velocity

            logInfo("Found ground at Y=$groundY, spawning player at Y=$finalSpawnY")
            logInfo("Flight mode: OFF, Gravity: ON, Jump: Space")
            logInfo("Press F to toggle flight mode")

            updateLoadingProgress("Terrain ready, generating meshes...")

            // T021: Phase 2 - Wait for initial mesh generation to complete
            val initialChunkCount = 81  // 9x9 grid (INITIAL_GENERATION_RADIUS=4)
            world.setInitialMeshTarget(initialChunkCount)

            // Poll mesh generation progress
            var lastPercent = 0
            while (!world.isInitialMeshGenerationComplete) {
                delay(100)  // Check every 100ms
                val progress = world.initialMeshGenerationProgress
                val percent = (progress * 100).toInt()
                if (percent > lastPercent && percent % 5 == 0) {
                    val meshCount = (initialChunkCount * progress).toInt()
                    updateLoadingProgress("Generating meshes: $meshCount/$initialChunkCount ($percent%)")
                    lastPercent = percent
                }
            }

            // T021: Phase 3 - All meshes ready, regenerate for correct face culling
            val totalTime = js("Date.now()") as Double - startTime

            // Regenerate all meshes to ensure correct face culling at boundaries
            // This is needed because during initial generation, neighbors might not have
            // meshes yet, so boundary faces don't get culled correctly
            world.regenerateAllMeshes()

            // T021: Wait for regeneration to complete (fixes missing faces bug)
            lastPercent = 0  // Reset for regeneration tracking
            while (!world.isRegenerationComplete) {
                delay(100)  // Check every 100ms
                val progress = world.regenerationProgress
                val percent = (progress * 100).toInt()
                if (percent > lastPercent && percent % 10 == 0) {
                    val completed = (world.regenerationProgress * initialChunkCount).toInt()
                    updateLoadingProgress("Regenerating meshes: $completed/$initialChunkCount ($percent%)")
                    lastPercent = percent
                }
            }

            updateLoadingProgress("World ready!")  // Shows "Click on canvas to start"
            setupStartOnClick(world)  // Wait for click, then hide loading screen
        } catch (e: Throwable) {
            logError("Generation failed: ${e.message}", e)
            console.error(e)
        }
    }
}

suspend fun continueInitialization(world: VoxelWorld, canvas: HTMLCanvasElement) {
    val storage = WorldStorage()

    updateLoadingProgress("Initializing renderer...")

    // Create render surface using platform-agnostic SurfaceFactory
    val surface = SurfaceFactory.create(canvas)

    // Initialize renderer with RendererFactory (automatic backend detection)
    val availableBackends = RendererFactory.detectAvailableBackends()

    // Check if WebGPU is available
    if (!availableBackends.contains(io.materia.renderer.BackendType.WEBGPU)) {
        val errorMsg = """
            WebGPU not available

            VoxelCraft requires WebGPU support.

            WebGPU is available in:
            - Chrome/Edge 113+ (enabled by default)
            - Firefox Nightly (enable dom.webgpu.enabled in about:config)
            - Safari Technology Preview

            Your browser detected: ${availableBackends.joinToString(", ")}

            Please update your browser or enable WebGPU.
        """.trimIndent()

        logError(errorMsg)
        updateLoadingProgress(errorMsg.replace("\n", "<br>"))
        throw RuntimeException("WebGPU not available. Please use a WebGPU-enabled browser.")
    }

    // Create renderer with automatic backend selection (WebGPU + WebGL fallback)
    val renderer = try {
        when (val result = RendererFactory.create(surface)) {
            is io.materia.core.Result.Success -> result.value
            is io.materia.core.Result.Error -> {
                val exception = result.exception as? RendererInitializationException
                when (exception) {
                    is RendererInitializationException.NoGraphicsSupportException -> {
                        logError("Graphics not supported: ${result.message}")
                        logError("   Platform: ${exception.platform}")
                        logError("   Available: ${exception.availableBackends}")
                        logError("   Required: ${exception.requiredFeatures}")
                        throw exception
                    }

                    else -> {
                        logError("Renderer initialization failed: ${result.message}")
                        throw exception ?: RuntimeException(result.message)
                    }
                }
            }
        }
    } catch (e: Throwable) {
        logError("Failed to create renderer: ${e.message}")
        updateLoadingProgress("Error: ${e.message}")
        throw e
    }

    // Log selected backend
    logInfo("Renderer initialized!")
    logInfo("  Backend: ${renderer.backend}")
    logInfo("  Device: ${renderer.capabilities.deviceName}")

    // Create camera
    val camera = PerspectiveCamera(
        fov = 75.0f,
        aspect = canvas.width.toFloat() / canvas.height.toFloat(),
        near = 0.1f,
        far = 1000.0f
    ).apply {
        name = "MainCamera"  // T021: Add name for logging
    }

    wireEnvironmentLighting(world.scene)

    updateLoadingProgress("Renderer ready, finalizing world...")

    // Initialize block interaction
    val blockInteraction = BlockInteraction(world, world.player)

    // Initialize controllers
    val playerController = PlayerController(world.player, blockInteraction)
    val cameraController = CameraController(world.player, canvas)

    // Auto-save every 30 seconds
    window.setInterval({
        val result = storage.save(world)
        if (!result.success) {
            logWarn("Auto-save failed: ${result.error}")
        }
    }, 30000)

    // Save on page close
    window.addEventListener("beforeunload", {
        storage.save(world)
    })

    // T014: FPS counter with rolling average
    val fpsCounter = FPSCounter(windowSize = 60)
    var lastTime = js("Date.now()") as Double
    var frameCount = 0

    fun gameLoop() {
        val currentTime = js("performance.now()") as Double
        val deltaTime = ((currentTime - lastTime) / 1000.0).toFloat()
        lastTime = currentTime

        // Update controllers
        playerController.update(deltaTime)

        // Update world
        world.update(deltaTime)

        // Sync camera with player
        val newPosX = world.player.position.x.toFloat()
        val newPosY = world.player.position.y.toFloat()
        val newPosZ = world.player.position.z.toFloat()
        val newRotX = world.player.rotation.x
        val newRotY = world.player.rotation.y

        // Only update if camera actually moved (performance optimization)
        val cameraMoved = (
                camera.position.x != newPosX ||
                        camera.position.y != newPosY ||
                        camera.position.z != newPosZ ||
                        camera.rotation.x != newRotX ||
                        camera.rotation.y != newRotY
                )

        if (cameraMoved) {
            // Position camera at player's eye level (not feet)
            val eyeHeight = 1.6f  // Player height is 1.8, eyes at ~90%
            camera.position.set(newPosX, newPosY + eyeHeight, newPosZ)

            // Calculate look direction from pitch (X) and yaw (Y)
            val pitch = newRotX.toDouble()
            val yaw = newRotY.toDouble()

            // Convert spherical coordinates (pitch/yaw) to Cartesian direction vector
            // Materia cameras look down -Z axis by default (yaw=0 means looking at negative Z)
            val cosP = kotlin.math.cos(pitch)
            val sinP = kotlin.math.sin(pitch)
            val cosY = kotlin.math.cos(yaw)
            val sinY = kotlin.math.sin(yaw)

            // Adjusted for Materia's coordinate system (-Z forward)
            // Both X and Z negated to match movement direction calculation
            val lookDirX = -sinY * cosP
            val lookDirY = sinP
            val lookDirZ = -cosY * cosP

            // Calculate target point to look at (1 unit away in look direction)
            val lookAtX = camera.position.x + lookDirX.toFloat()
            val lookAtY = camera.position.y + lookDirY.toFloat()
            val lookAtZ = camera.position.z + lookDirZ.toFloat()

            // Use lookAt for proper first-person rotation (avoids orbital camera effect)
            // lookAt internally uses Vector3.UP (0,1,0) which keeps horizon level
            camera.lookAt(lookAtX, lookAtY, lookAtZ)

            // Update matrices
            camera.updateMatrix()
            camera.updateMatrixWorld(false) // false = don't force child updates
        }

        camera.updateProjectionMatrix() // Cheap, only updates if aspect changed

        // Render scene
        renderer.render(world.scene, camera)

        // Update HUD (every frame)
        updateHUD(world)

        // T014: Update FPS counter with rolling average (every frame)
        val fps = fpsCounter.update(currentTime)
        val stats = renderer.stats
        updateFPS(fps.toInt(), stats.triangles, stats.drawCalls)

        // T020: Performance validation after warmup (frame 120 = ~2 seconds)
        val validationResult = PerformanceValidator.validateAfterWarmup(
            frameCount = frameCount,
            metrics = PerformanceValidator.PerformanceMetrics(
                fps = fps,
                drawCalls = stats.drawCalls,
                triangles = stats.triangles,
                backendType = renderer.backend.name,
                frameTime = fpsCounter.getAverageFrameTime()
            )
        )
        validationResult?.let { result ->
            PerformanceValidator.logResult(result)
        }

        frameCount++

        // Request next frame
        window.requestAnimationFrame { gameLoop() }
    }

    // Start game loop (camera matrices will be updated on first frame)
    gameLoop()
}

private suspend fun wireEnvironmentLighting(scene: Scene) {
    logInfo("Processing HDR environment for scene lighting...")
    when (val hdrResult = iblProcessor.loadHDREnvironment("assets/environments/studio_small.hdr")) {
        is IBLResult.Success -> {
            val iblResult = iblProcessor.processEnvironmentForScene(
                hdr = hdrResult.data,
                config = iblConfig,
                scene = scene
            )
            when (iblResult) {
                is IBLResult.Success -> {
                    val maps = iblResult.data
                    logInfo(
                        "IBL environment applied (prefilter=${maps.prefilter.size}, " +
                                "brdf=${maps.brdfLut.width}x${maps.brdfLut.height})"
                    )
                    if (maps.brdfLut.width < 512) {
                        logWarn("BRDF LUT fallback is active; expect slightly softer specular highlights until GPU LUT generation ships.")
                    }
                }

                is IBLResult.Error -> logWarn("IBL processing failed: ${iblResult.message}")
            }
        }

        is IBLResult.Error -> logWarn("Failed to load HDR environment: ${hdrResult.message}")
    }
}

fun updateLoadingProgress(message: String) {
    val progressElement = document.getElementById("loading-progress")
    if (message == "World ready!") {
        // T021: Show click instruction after loading completes
        progressElement?.innerHTML =
            "$message<br><br><strong>Click on the canvas to start playing!</strong>"
    } else {
        progressElement?.textContent = message
    }
}

/**
 * T021: Setup click handler to start the game.
 * Loading screen stays visible until user clicks, ensuring pointer lock request
 * happens with a valid user gesture.
 */
fun setupStartOnClick(world: VoxelWorld) {
    val loading = document.getElementById("loading")
    val canvas = document.getElementById("materia-canvas") as? HTMLCanvasElement

    // Make loading screen clickable
    loading?.addEventListener("click", {
        // Hide loading screen
        loading.setAttribute("class", "loading hidden")

        // Request pointer lock (requires user gesture)
        canvas?.let { PointerLock.request(it) }
    })

    // Also allow clicking directly on canvas
    canvas?.addEventListener("click", {
        // Hide loading screen if still visible
        loading?.setAttribute("class", "loading hidden")

        // Request pointer lock
        canvas?.let { PointerLock.request(it) }
    })
}

fun hideLoadingScreen() {
    val loading = document.getElementById("loading")
    loading?.setAttribute("class", "loading hidden")
}

fun updateHUD(world: VoxelWorld) {
    // Update position
    val posElement = document.getElementById("position")
    val pos = world.player.position
    posElement?.textContent = "X: ${pos.x.toInt()}, Y: ${pos.y.toInt()}, Z: ${pos.z.toInt()}"

    // Update flight status
    val flightElement = document.getElementById("flight-status")
    flightElement?.textContent = if (world.player.isFlying) "ON" else "OFF"

    // Update chunks
    val chunksElement = document.getElementById("chunks")
    chunksElement?.textContent = "${world.chunkCount} / 1024"
}

fun updateFPS(fps: Int, triangles: Int = 0, drawCalls: Int = 0) {
    val fpsElement = document.getElementById("fps")
    fpsElement?.textContent = "$fps FPS | ${triangles} tris | ${drawCalls} DC"
}

/**
 * VoxelCraft game facade - Simplified for vertical slice
 */
class VoxelCraft(val seed: Long, private val scope: CoroutineScope = gameScope) {
    val world = VoxelWorld(seed, scope)

    fun update(deltaTime: Float) {
        world.update(deltaTime)
    }

    fun dispose() {
        world.dispose()
    }

    companion object {
        fun fromSavedState(state: Any, scope: CoroutineScope = gameScope): VoxelCraft {
            val worldState = state as WorldState
            return VoxelCraft(worldState.seed, scope)
        }
    }
}
