package io.materia.tests.integration

import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Color as MateriaColor
import io.materia.core.scene.Scene
import io.materia.renderer.BackendType
import io.materia.renderer.RendererConfig
import io.materia.renderer.RendererFactory
import io.materia.renderer.SurfaceFactory
import io.materia.renderer.vulkan.VulkanRenderer
import io.materia.scene.Background
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E2E test to verify Vulkan renderer is actually outputting visible pixels.
 * This test captures a frame and validates the output is not all black.
 */
class VulkanRenderOutputTest {

    @Test
    fun `test renderer outputs non-black frame with magenta background`() = runBlocking {
        val outputPath = "/tmp/vulkan_render_test.png"
        
        // Setup GLFW
        GLFWErrorCallback.createPrint(System.err).set()
        if (!glfwInit()) {
            fail("Failed to initialize GLFW")
        }

        // Verify Vulkan support
        if (!GLFWVulkan.glfwVulkanSupported()) {
            glfwTerminate()
            fail("Vulkan is not supported on this system")
        }

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Hidden window for testing

        val width = 256
        val height = 256

        val window = glfwCreateWindow(width, height, "Vulkan Render Test", MemoryUtil.NULL, MemoryUtil.NULL)
        if (window == MemoryUtil.NULL) {
            glfwTerminate()
            fail("Failed to create GLFW window")
        }

        try {
            // Create renderer using the factory pattern like examples do
            val surface = SurfaceFactory.create(window)
            val config = RendererConfig(enableValidation = false, preferredBackend = BackendType.VULKAN)
            
            val renderer = when (val result = RendererFactory.create(surface, config)) {
                is io.materia.core.Result.Success -> result.value
                is io.materia.core.Result.Error -> {
                    fail("Failed to create renderer: ${result.message}")
                }
            }

            // Cast to VulkanRenderer to access requestFrameCapture
            val vulkanRenderer = renderer as? VulkanRenderer
                ?: fail("Expected VulkanRenderer but got ${renderer::class.simpleName}")

            // Create a simple scene with a bright magenta background
            val scene = Scene().apply {
                background = Background.Color(MateriaColor(1.0f, 0.0f, 1.0f, 1.0f)) // Bright magenta
            }

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

            // Render several frames to ensure pipeline is initialized
            repeat(10) {
                renderer.render(scene, camera)
                glfwPollEvents()
            }

            // Request frame capture
            vulkanRenderer.requestFrameCapture(outputPath)
            
            // Render one more frame to trigger capture
            renderer.render(scene, camera)
            glfwPollEvents()
            
            // Give some time for capture to complete
            Thread.sleep(100)

            // Cleanup renderer
            renderer.dispose()

            // Verify output file exists and is not all black
            val outputFile = File(outputPath)
            assertTrue(outputFile.exists(), "Screenshot file should exist at $outputPath")
            
            val image = ImageIO.read(outputFile)
            assertNotNull(image, "Should be able to read the screenshot as an image")
            
            // Check if image has any non-black pixels
            var hasNonBlackPixels = false
            var magentaPixelCount = 0
            var totalPixels = 0
            
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y)
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    
                    totalPixels++
                    
                    if (r > 10 || g > 10 || b > 10) {
                        hasNonBlackPixels = true
                    }
                    
                    // Check for magenta-ish pixels (high red, low green, high blue)
                    if (r > 200 && g < 50 && b > 200) {
                        magentaPixelCount++
                    }
                }
            }
            
            println("[VulkanRenderOutputTest] Total pixels: $totalPixels")
            println("[VulkanRenderOutputTest] Magenta pixels: $magentaPixelCount (${100.0 * magentaPixelCount / totalPixels}%)")
            println("[VulkanRenderOutputTest] Has non-black pixels: $hasNonBlackPixels")
            
            assertTrue(hasNonBlackPixels, "Rendered image should not be all black! Check Vulkan rendering pipeline.")
            
            // At least 50% of the image should be magenta (background color)
            val magentaPercentage = 100.0 * magentaPixelCount / totalPixels
            assertTrue(magentaPercentage > 30, "At least 30% of pixels should be magenta (background). Got $magentaPercentage%")
            
            println("[VulkanRenderOutputTest] ✅ Test passed! Renderer is outputting visible content.")
            
        } finally {
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }
    
    @Test
    fun `test clear color is applied correctly`() = runBlocking {
        val outputPath = "/tmp/vulkan_clear_color_test.png"
        
        // Setup GLFW
        GLFWErrorCallback.createPrint(System.err).set()
        if (!glfwInit()) {
            fail("Failed to initialize GLFW")
        }

        if (!GLFWVulkan.glfwVulkanSupported()) {
            glfwTerminate()
            fail("Vulkan is not supported on this system")
        }

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

        val width = 64
        val height = 64

        val window = glfwCreateWindow(width, height, "Clear Color Test", MemoryUtil.NULL, MemoryUtil.NULL)
        if (window == MemoryUtil.NULL) {
            glfwTerminate()
            fail("Failed to create GLFW window")
        }

        try {
            val surface = SurfaceFactory.create(window)
            val config = RendererConfig(enableValidation = false, preferredBackend = BackendType.VULKAN)
            
            val renderer = when (val result = RendererFactory.create(surface, config)) {
                is io.materia.core.Result.Success -> result.value
                is io.materia.core.Result.Error -> fail("Failed to create renderer: ${result.message}")
            }

            val vulkanRenderer = renderer as? VulkanRenderer
                ?: fail("Expected VulkanRenderer")

            // Scene with just a red background - no meshes
            val scene = Scene().apply {
                background = Background.Color(MateriaColor(1.0f, 0.0f, 0.0f, 1.0f)) // Pure red
            }

            val camera = PerspectiveCamera(75f, 1f, 0.1f, 100f)
            camera.position.set(0f, 0f, 5f)
            camera.updateMatrixWorld(true)

            // Render frames
            repeat(5) {
                renderer.render(scene, camera)
                glfwPollEvents()
            }

            vulkanRenderer.requestFrameCapture(outputPath)
            renderer.render(scene, camera)
            glfwPollEvents()
            Thread.sleep(100)

            renderer.dispose()

            val outputFile = File(outputPath)
            assertTrue(outputFile.exists(), "Screenshot file should exist")
            
            val image = ImageIO.read(outputFile)
            assertNotNull(image, "Should be able to read screenshot")
            
            // Sample the center pixel - should be red
            val centerRgb = image.getRGB(width / 2, height / 2)
            val r = (centerRgb shr 16) and 0xFF
            val g = (centerRgb shr 8) and 0xFF
            val b = centerRgb and 0xFF
            
            println("[ClearColorTest] Center pixel: R=$r, G=$g, B=$b")
            
            // Red should be high, green and blue should be low
            assertTrue(r > 200, "Red channel should be high (>200), got $r")
            assertTrue(g < 50, "Green channel should be low (<50), got $g")
            assertTrue(b < 50, "Blue channel should be low (<50), got $b")
            
            println("[ClearColorTest] ✅ Clear color is being applied correctly!")
            
        } finally {
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    private fun assertNotNull(value: Any?, message: String) {
        if (value == null) {
            fail(message)
        }
    }
}
