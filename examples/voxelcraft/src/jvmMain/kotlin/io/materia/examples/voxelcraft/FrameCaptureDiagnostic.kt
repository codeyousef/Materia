#!/usr/bin/env kotlin

/**
 * VoxelCraft Frame Capture Diagnostic
 * 
 * This script captures a frame from VoxelCraft and analyzes it to diagnose the black screen issue.
 * Run with: ./gradlew :examples:voxelcraft:runFrameCapture
 */

package io.materia.examples.voxelcraft

import io.materia.camera.PerspectiveCamera
import io.materia.core.scene.Scene
import io.materia.core.scene.Background
import io.materia.core.math.Color as MateriaColor
import io.materia.renderer.BackendType
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.SurfaceFactory
import io.materia.renderer.vulkan.VulkanRenderer
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import java.io.File
import javax.imageio.ImageIO

fun main() = runBlocking {
    println("=== VoxelCraft Frame Capture Diagnostic ===")
    
    val outputPath = "/tmp/voxelcraft_diagnostic.png"
    
    // Setup GLFW
    GLFWErrorCallback.createPrint(System.err).set()
    if (!glfwInit()) {
        println("❌ FATAL: Failed to initialize GLFW")
        return@runBlocking
    }

    // Verify Vulkan support
    if (!GLFWVulkan.glfwVulkanSupported()) {
        glfwTerminate()
        println("❌ FATAL: Vulkan is not supported on this system")
        return@runBlocking
    }
    println("✅ Vulkan is supported")

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE) // Visible so we can see it

    val width = 400
    val height = 400

    val window = glfwCreateWindow(width, height, "Frame Capture Diagnostic", MemoryUtil.NULL, MemoryUtil.NULL)
    if (window == MemoryUtil.NULL) {
        glfwTerminate()
        println("❌ FATAL: Failed to create GLFW window")
        return@runBlocking
    }
    println("✅ Window created: ${width}x${height}")

    try {
        // Create renderer
        val surface = SurfaceFactory.create(window)
        println("✅ Surface created")
        
        val config = RendererConfig(enableValidation = true, preferredBackend = BackendType.VULKAN)
        
        val renderer = when (val result = RendererFactory.create(surface, config)) {
            is io.materia.core.Result.Success -> result.value
            is io.materia.core.Result.Error -> {
                println("❌ FATAL: Failed to create renderer: ${result.message}")
                return@runBlocking
            }
        }
        println("✅ Renderer created: ${renderer.backend}")
        println("   Device: ${renderer.capabilities.deviceName}")

        val vulkanRenderer = renderer as? VulkanRenderer
        if (vulkanRenderer == null) {
            println("❌ FATAL: Expected VulkanRenderer but got ${renderer::class.simpleName}")
            return@runBlocking
        }
        
        // IMPORTANT: Resize renderer to match window size (swapchain defaults to 800x600)
        println("⏳ Resizing renderer to match window size ${width}x${height}...")
        renderer.resize(width, height)
        println("✅ Renderer resized")

        // Create a simple scene with a bright magenta background (easy to see if it works)
        val scene = Scene().apply {
            background = Background.Color(MateriaColor(1.0f, 0.0f, 1.0f, 1.0f)) // Bright magenta
        }
        println("✅ Scene created with magenta background")

        // Create camera
        val camera = PerspectiveCamera(
            fov = 75f,
            aspect = width.toFloat() / height.toFloat(),
            near = 0.1f,
            far = 1000f
        )
        camera.position.set(0f, 0f, 5f)
        camera.lookAt(0f, 0f, 0f)
        camera.updateMatrixWorld(true)
        camera.updateProjectionMatrix()
        println("✅ Camera created and configured")

        // Render several frames to ensure pipeline is initialized
        println("⏳ Rendering 30 warmup frames...")
        repeat(30) { i ->
            renderer.render(scene, camera)
            glfwPollEvents()
            if (i == 0) {
                println("   First frame rendered")
            }
        }
        println("✅ Warmup complete")

        // Request frame capture
        println("⏳ Capturing frame to $outputPath...")
        vulkanRenderer.requestFrameCapture(outputPath)
        
        // Render one more frame to trigger capture
        renderer.render(scene, camera)
        glfwPollEvents()
        
        // Give some time for capture to complete
        Thread.sleep(200)
        println("✅ Frame capture requested")

        // Cleanup renderer
        renderer.dispose()
        println("✅ Renderer disposed")

        // Analyze the output
        val outputFile = File(outputPath)
        if (!outputFile.exists()) {
            println("❌ FATAL: Screenshot file was not created at $outputPath")
            return@runBlocking
        }
        println("✅ Screenshot file exists: ${outputFile.length()} bytes")

        val image = ImageIO.read(outputFile)
        if (image == null) {
            println("❌ FATAL: Could not read screenshot as image")
            return@runBlocking
        }
        println("✅ Image loaded: ${image.width}x${image.height}")
        
        // Check if size matches what we requested
        if (image.width != width || image.height != height) {
            println("⚠️ WARNING: Captured image size ${image.width}x${image.height} doesn't match window size ${width}x${height}")
        }

        // Analyze pixels
        var blackPixels = 0
        var nonBlackPixels = 0
        var magentaPixels = 0
        var totalPixels = 0
        
        val colorHistogram = mutableMapOf<String, Int>()
        
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                
                totalPixels++
                
                if (r < 10 && g < 10 && b < 10) {
                    blackPixels++
                } else {
                    nonBlackPixels++
                }
                
                // Check for magenta-ish pixels (high red, low green, high blue)
                if (r > 200 && g < 50 && b > 200) {
                    magentaPixels++
                }
                
                // Sample some colors for histogram
                if (totalPixels % 100 == 0) {
                    val colorKey = "R=$r,G=$g,B=$b"
                    colorHistogram[colorKey] = (colorHistogram[colorKey] ?: 0) + 1
                }
            }
        }
        
        println("")
        println("=== PIXEL ANALYSIS ===")
        println("Total pixels: $totalPixels")
        println("Black pixels: $blackPixels (${100.0 * blackPixels / totalPixels}%)")
        println("Non-black pixels: $nonBlackPixels (${100.0 * nonBlackPixels / totalPixels}%)")
        println("Magenta pixels: $magentaPixels (${100.0 * magentaPixels / totalPixels}%)")
        
        println("")
        println("=== SAMPLE COLORS (every 100th pixel) ===")
        colorHistogram.entries.sortedByDescending { it.value }.take(10).forEach { (color, count) ->
            println("  $color: $count samples")
        }
        
        // Sample corners and center
        println("")
        println("=== KEY PIXEL SAMPLES ===")
        fun samplePixel(x: Int, y: Int, name: String) {
            val rgb = image.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            println("  $name ($x,$y): R=$r, G=$g, B=$b")
        }
        samplePixel(0, 0, "Top-left")
        samplePixel(image.width - 1, 0, "Top-right")
        samplePixel(0, image.height - 1, "Bottom-left")
        samplePixel(image.width - 1, image.height - 1, "Bottom-right")
        samplePixel(image.width / 2, image.height / 2, "Center")
        
        println("")
        println("=== DIAGNOSIS ===")
        if (blackPixels == totalPixels) {
            println("❌ FAILED: Image is completely black!")
            println("   This suggests the clear color is not being applied or")
            println("   the swapchain image is not being presented correctly.")
        } else if (magentaPixels > totalPixels * 0.5) {
            println("✅ PASSED: Image shows expected magenta background!")
            println("   The renderer is working correctly.")
        } else {
            println("⚠️  PARTIAL: Image has some content but not expected colors.")
            println("   Check the sample colors above for more details.")
        }
        
        println("")
        println("Screenshot saved to: $outputPath")
        println("You can open it to visually inspect the output.")
        
    } finally {
        glfwDestroyWindow(window)
        glfwTerminate()
    }
    
    println("")
    println("=== Diagnostic complete ===")
}
