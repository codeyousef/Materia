import kotlinx.browser.window
import kotlinx.browser.document
import kotlinx.coroutines.*
import org.w3c.dom.HTMLCanvasElement

fun main() {
    println("🚀 Materia Basic Scene Example (WebGPU)")
    println("======================================")

    // Create canvas if it doesn't exist
    val canvas = document.getElementById("webgpu-canvas") as? HTMLCanvasElement
        ?: createCanvas()

    MainScope().launch {
        try {
            println("Initializing example...")
            val example = BasicSceneExample()
            example.initialize()
            example.printSceneInfo()

            println("\n🎮 Controls (not yet implemented for web):")
            println("WASD - Move camera")
            println("Mouse - Look around")
            println("\n🎬 Starting render loop...")

            // Start render loop
            var lastTime = getCurrentTimeMillis()

            fun renderFrame() {
                val currentTime = getCurrentTimeMillis()
                val deltaTime = (currentTime - lastTime) / 1000.0f
                lastTime = currentTime

                // Handle input
                val input = getCurrentInput()
                example.handleInput(input)

                // Render
                example.render(deltaTime)

                // Request next frame
                window.requestAnimationFrame { renderFrame() }
            }

            // Start the render loop
            renderFrame()

        } catch (e: Exception) {
            println("❌ Error running example: ${e.message}")
            console.error(e)
        }
    }
}

private fun createCanvas(): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.id = "webgpu-canvas"
    canvas.width = 1920
    canvas.height = 1080
    canvas.style.width = "100%"
    canvas.style.height = "100vh"
    document.body?.appendChild(canvas)
    return canvas
}