import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil.NULL
import kotlin.math.cos
import kotlin.math.sin

// Camera state
data class CameraState(
    var posX: Float = 5.0f,
    var posY: Float = 5.0f,
    var posZ: Float = 5.0f,
    var rotX: Float = -30.0f,
    var rotY: Float = 45.0f,
    var moveSpeed: Float = 5.0f,
    var lookSpeed: Float = 50.0f
)

fun main() {
    println("🚀 KreeKt Triangle Example (LWJGL)")
    println("======================================")

    // Initialize GLFW
    GLFWErrorCallback.createPrint(System.err).set()
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }

    // Configure GLFW - use compatibility profile for immediate mode
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
    // Don't request core profile so we can use immediate mode for demo

    // Create the window
    val window = glfwCreateWindow(1280, 720, "KreeKt 3D Engine - Triangle Example", NULL, NULL)
    if (window == NULL) {
        glfwTerminate()
        throw RuntimeException("Failed to create the GLFW window")
    }

    // Set the global window handle for backend initialization
    glfwWindowHandle = window

    // Camera state
    val camera = CameraState()
    var lastMouseX = 640.0
    var lastMouseY = 360.0
    var firstMouse = true
    var mousePressed = false

    // Setup key callback
    glfwSetKeyCallback(window) { windowHandle, key, scancode, action, mods ->
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
            glfwSetWindowShouldClose(windowHandle, true)
        }
    }

    // Setup mouse callbacks
    glfwSetMouseButtonCallback(window) { _, button, action, _ ->
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            mousePressed = action == GLFW_PRESS
            if (mousePressed) {
                firstMouse = true
            }
        }
    }

    glfwSetCursorPosCallback(window) { _, xpos, ypos ->
        if (mousePressed) {
            if (firstMouse) {
                lastMouseX = xpos
                lastMouseY = ypos
                firstMouse = false
            }

            val xoffset = (xpos - lastMouseX).toFloat()
            val yoffset = (lastMouseY - ypos).toFloat() // reversed since y-coordinates range from bottom to top
            lastMouseX = xpos
            lastMouseY = ypos

            val sensitivity = 0.3f
            camera.rotY += xoffset * sensitivity
            camera.rotX += yoffset * sensitivity

            // Clamp vertical rotation
            if (camera.rotX > 89.0f) camera.rotX = 89.0f
            if (camera.rotX < -89.0f) camera.rotX = -89.0f
        }
    }

    // Make the OpenGL context current BEFORE any GL calls
    glfwMakeContextCurrent(window)

    // This line is critical for LWJGL's interoperation with GLFW's OpenGL context
    GL.createCapabilities()

    glfwSwapInterval(1) // Enable v-sync

    // Center the window
    val vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
    glfwSetWindowPos(
        window,
        (vidmode.width() - 1280) / 2,
        (vidmode.height() - 720) / 2
    )

    // Show the window
    glfwShowWindow(window)

    // Set clear color
    glClearColor(0.05f, 0.05f, 0.1f, 1.0f)

    // Enable depth testing
    glEnable(GL_DEPTH_TEST)
    glDepthFunc(GL_LESS)

    // Run the example in a coroutine scope
    runBlocking {
        try {
            val example = TriangleExample()
            println("Initializing scene...")
            example.initialize()
            example.printSceneInfo()

            println("\n🎮 Controls:")
            println("  ESC - Exit")
            println("  WASD - Move camera")
            println("  Mouse - Look around")
            println("\n🎬 Starting render loop...")

            var lastTime = System.currentTimeMillis() / 1000.0
            var frameCount = 0
            var fpsTimer = 0.0

            // Run the rendering loop until the user has attempted to close the window
            while (!glfwWindowShouldClose(window)) {
                val currentTime = System.currentTimeMillis() / 1000.0
                val deltaTime = (currentTime - lastTime).toFloat()
                lastTime = currentTime

                // Clear the framebuffer
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                // Get window size for viewport
                val width = IntArray(1)
                val height = IntArray(1)
                glfwGetFramebufferSize(window, width, height)
                glViewport(0, 0, width[0], height[0])

                // Setup basic projection matrix
                val aspect = width[0].toFloat() / height[0].toFloat()
                setupPerspectiveProjection(75.0f, aspect, 0.1f, 100.0f)

                // Handle keyboard input for camera movement
                val moveSpeed = camera.moveSpeed * deltaTime
                val yawRad = Math.toRadians(camera.rotY.toDouble())

                // W - Move forward (in the direction we're looking)
                if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
                    camera.posX += sin(yawRad).toFloat() * moveSpeed
                    camera.posZ -= cos(yawRad).toFloat() * moveSpeed
                }
                // S - Move backward
                if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
                    camera.posX -= sin(yawRad).toFloat() * moveSpeed
                    camera.posZ += cos(yawRad).toFloat() * moveSpeed
                }
                // A - Strafe left
                if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
                    camera.posX -= cos(yawRad).toFloat() * moveSpeed
                    camera.posZ -= sin(yawRad).toFloat() * moveSpeed
                }
                // D - Strafe right
                if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
                    camera.posX += cos(yawRad).toFloat() * moveSpeed
                    camera.posZ += sin(yawRad).toFloat() * moveSpeed
                }
                if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
                    camera.posY -= moveSpeed
                }
                if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
                    camera.posY += moveSpeed
                }

                // Draw a simple animated scene with camera
                drawSceneWithCamera(currentTime.toFloat(), camera)

                // Handle input for KreeKt example
                val input = getCurrentInput(window)
                example.handleInput(input)

                // Render the example (currently this prints to the console)
                example.render(deltaTime)

                // Swap buffers and poll events
                glfwSwapBuffers(window)
                glfwPollEvents()

                // Update FPS counter
                frameCount++
                fpsTimer += deltaTime.toDouble()
                if (fpsTimer >= 1.0) {
                    val fps = frameCount / fpsTimer
                    glfwSetWindowTitle(window, "KreeKt 3D Engine - FPS: ${fps.toInt()}")
                    frameCount = 0
                    fpsTimer = 0.0
                }
            }

            println("\n✅ Example completed successfully!")

        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }

    // Clean up
    glfwDestroyWindow(window)
    glfwTerminate()
}

fun setupPerspectiveProjection(fov: Float, aspect: Float, near: Float, far: Float) {
    // Simple perspective projection using deprecated immediate mode for demo
    glMatrixMode(GL_PROJECTION)
    glLoadIdentity()
    val fH = kotlin.math.tan(Math.toRadians(fov / 2.0)).toFloat() * near
    val fW = fH * aspect
    glFrustum(-fW.toDouble(), fW.toDouble(), -fH.toDouble(), fH.toDouble(), near.toDouble(), far.toDouble())
    glMatrixMode(GL_MODELVIEW)
}

fun drawSceneWithCamera(time: Float, camera: CameraState) {
    glLoadIdentity()

    // Apply camera rotation first
    glRotatef(camera.rotX, 1f, 0f, 0f)
    glRotatef(camera.rotY, 0f, 1f, 0f)

    // Then translate to camera position (inverted because we move the world, not the camera)
    glTranslatef(-camera.posX, -camera.posY, -camera.posZ)

    // Draw ground plane (large grid)
    glPushMatrix()
    glTranslatef(0f, -2f, 0f)
    glColor3f(0.2f, 0.2f, 0.3f)
    for (x in -20..20 step 2) {
        for (z in -20..20 step 2) {
            glPushMatrix()
            glTranslatef(x.toFloat(), 0f, z.toFloat())
            glScalef(0.9f, 0.1f, 0.9f)
            drawCube()
            glPopMatrix()
        }
    }
    glPopMatrix()

    // Draw main rotating cube at origin
    glPushMatrix()
    glTranslatef(0f, 2f, 0f)
    glRotatef(time * 30f, 1f, 1f, 0f)
    glScalef(1.5f, 1.5f, 1.5f)
    drawCube()
    glPopMatrix()

    // Draw orbiting spheres around the main cube
    for (i in 0..4) {
        glPushMatrix()
        val angle = time * 0.5f + i * 2.0f * Math.PI.toFloat() / 5
        val radius = 5f
        glTranslatef(cos(angle) * radius, 2f + sin(time + i) * 0.5f, sin(angle) * radius)
        glRotatef(time * 50f + i * 30f, 0.5f, 1f, 0.3f)
        glScalef(0.5f, 0.5f, 0.5f)
        drawColoredCube(i)
        glPopMatrix()
    }

    // Draw some standing pillars
    for (i in 0..3) {
        glPushMatrix()
        val x = (i - 1.5f) * 4f
        glTranslatef(x, 1f, -8f)
        glScalef(0.5f, 3f, 0.5f)
        glColor3f(0.6f, 0.6f, 0.7f)
        drawCube()
        glPopMatrix()
    }

    // Draw floating platforms
    for (i in 0..2) {
        glPushMatrix()
        val y = 3f + i * 2f
        val offset = sin(time * 0.3f + i) * 2f
        glTranslatef(offset, y, -5f + i * 3f)
        glScalef(2f, 0.2f, 2f)
        glColor3f(0.3f + i * 0.2f, 0.5f, 0.8f - i * 0.2f)
        drawCube()
        glPopMatrix()
    }
}

fun drawColoredCube(colorIndex: Int) {
    val colors = arrayOf(
        floatArrayOf(0.8f, 0.3f, 0.2f),  // Red
        floatArrayOf(0.3f, 0.8f, 0.3f),  // Green
        floatArrayOf(0.3f, 0.3f, 0.8f),  // Blue
        floatArrayOf(0.8f, 0.8f, 0.3f),  // Yellow
        floatArrayOf(0.8f, 0.3f, 0.8f)   // Magenta
    )
    val color = colors[colorIndex % colors.size]
    glColor3f(color[0], color[1], color[2])
    drawSimpleCube()
}

fun drawSimpleCube() {
    glBegin(GL_QUADS)
    // Front face
    glVertex3f(-1f, -1f, 1f)
    glVertex3f(1f, -1f, 1f)
    glVertex3f(1f, 1f, 1f)
    glVertex3f(-1f, 1f, 1f)
    // Back face
    glVertex3f(-1f, -1f, -1f)
    glVertex3f(-1f, 1f, -1f)
    glVertex3f(1f, 1f, -1f)
    glVertex3f(1f, -1f, -1f)
    // Top face
    glVertex3f(-1f, 1f, -1f)
    glVertex3f(-1f, 1f, 1f)
    glVertex3f(1f, 1f, 1f)
    glVertex3f(1f, 1f, -1f)
    // Bottom face
    glVertex3f(-1f, -1f, -1f)
    glVertex3f(1f, -1f, -1f)
    glVertex3f(1f, -1f, 1f)
    glVertex3f(-1f, -1f, 1f)
    // Right face
    glVertex3f(1f, -1f, -1f)
    glVertex3f(1f, 1f, -1f)
    glVertex3f(1f, 1f, 1f)
    glVertex3f(1f, -1f, 1f)
    // Left face
    glVertex3f(-1f, -1f, -1f)
    glVertex3f(-1f, -1f, 1f)
    glVertex3f(-1f, 1f, 1f)
    glVertex3f(-1f, 1f, -1f)
    glEnd()
}

fun drawCube() {
    // Draw a simple colored cube using immediate mode (for demo purposes)
    glBegin(GL_QUADS)

    // Front face (red)
    glColor3f(0.8f, 0.3f, 0.2f)
    glVertex3f(-1f, -1f, 1f)
    glVertex3f(1f, -1f, 1f)
    glVertex3f(1f, 1f, 1f)
    glVertex3f(-1f, 1f, 1f)

    // Back face (green)
    glColor3f(0.3f, 0.8f, 0.3f)
    glVertex3f(-1f, -1f, -1f)
    glVertex3f(-1f, 1f, -1f)
    glVertex3f(1f, 1f, -1f)
    glVertex3f(1f, -1f, -1f)

    // Top face (blue)
    glColor3f(0.3f, 0.3f, 0.8f)
    glVertex3f(-1f, 1f, -1f)
    glVertex3f(-1f, 1f, 1f)
    glVertex3f(1f, 1f, 1f)
    glVertex3f(1f, 1f, -1f)

    // Bottom face (yellow)
    glColor3f(0.8f, 0.8f, 0.3f)
    glVertex3f(-1f, -1f, -1f)
    glVertex3f(1f, -1f, -1f)
    glVertex3f(1f, -1f, 1f)
    glVertex3f(-1f, -1f, 1f)

    // Right face (magenta)
    glColor3f(0.8f, 0.3f, 0.8f)
    glVertex3f(1f, -1f, -1f)
    glVertex3f(1f, 1f, -1f)
    glVertex3f(1f, 1f, 1f)
    glVertex3f(1f, -1f, 1f)

    // Left face (cyan)
    glColor3f(0.3f, 0.8f, 0.8f)
    glVertex3f(-1f, -1f, -1f)
    glVertex3f(-1f, -1f, 1f)
    glVertex3f(-1f, 1f, 1f)
    glVertex3f(-1f, 1f, -1f)

    glEnd()
}