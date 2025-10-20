package io.kreekt.examples.common

import io.kreekt.renderer.Renderer
import io.kreekt.renderer.vulkan.VulkanRenderer
import org.lwjgl.glfw.GLFW
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

object WindowCapture {
    fun capture(
        window: Long,
        width: Int,
        height: Int,
        outputPath: String,
        renderer: Renderer?
    ): Boolean {
        if (GraphicsEnvironment.isHeadless()) {
            val vulkanRenderer = renderer as? VulkanRenderer
            return if (vulkanRenderer != null) {
                vulkanRenderer.requestFrameCapture(outputPath)
                true
            } else {
                System.err.println("[WindowCapture] Skipping capture; headless environment and renderer not supported")
                false
            }
        }

        return try {
            val xBuf = IntArray(1)
            val yBuf = IntArray(1)
            GLFW.glfwGetWindowPos(window, xBuf, yBuf)
            val rect = Rectangle(xBuf[0], yBuf[0], width, height)
            val robot = Robot()
            val image = robot.createScreenCapture(rect)
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            ImageIO.write(image, "png", file)
            true
        } catch (e: Exception) {
            System.err.println("[WindowCapture] Failed to capture window: ${e.message}")
            false
        }
    }
}
