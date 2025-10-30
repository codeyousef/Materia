# Getting Started with Materia

> **Learn how to create your first 3D scene with Materia in minutes**

## 📋 Prerequisites

### Required

- **Kotlin**: 1.9+
- **Gradle**: 8.0+
- **JDK**: 11+ (for JVM target)

### Platform-Specific

- **JVM**: LWJGL-compatible system (Windows, Linux, macOS)
- **JavaScript**: Modern browser with WebGL2 support
- **Native**: Platform-specific compilers (GCC, Clang, or MSVC)

## 🚀 Quick Setup

### 1. Add Materia to Your Project

#### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    kotlin("multiplatform") version "1.9.20"
}

kotlin {
    // Choose your targets
    jvm()
    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.materia:materia-core:0.1.0-alpha01")
            }
        }
    }
}
```

### 2. Create Your First Scene

Create a file `src/commonMain/kotlin/FirstScene.kt`:

```kotlin
import io.materia.core.scene.*
import io.materia.core.math.*
import io.materia.geometry.primitives.*
import io.materia.material.SimpleMaterial
import io.materia.camera.PerspectiveCamera
import io.materia.renderer.Renderer

class FirstScene {
    // Scene components
    private val scene = Scene()
    private lateinit var camera: PerspectiveCamera
    private lateinit var cube: Mesh

    fun initialize(aspectRatio: Float = 16f / 9f) {
        // 1. Create camera
        camera = PerspectiveCamera(
            fov = 75f,           // Field of view
            aspect = aspectRatio, // Aspect ratio
            near = 0.1f,         // Near clipping plane
            far = 1000f          // Far clipping plane
        ).apply {
            position.set(0f, 0f, 5f) // Move camera back
        }

        // 2. Create a colored cube
        val geometry = BoxGeometry(
            width = 2f,
            height = 2f,
            depth = 2f
        )

        val material = SimpleMaterial(
            albedo = Color(0.8f, 0.3f, 0.2f), // Orange-red color
            metallic = 0.3f,
            roughness = 0.4f,
            materialName = "CubeMaterial"
        )

        cube = Mesh(geometry, material).apply {
            position.set(0f, 0f, 0f)
        }

        // 3. Add cube to scene
        scene.add(cube)
    }

    fun render(renderer: Renderer, deltaTime: Float) {
        // Rotate the cube
        cube.rotation.x += deltaTime * 0.5f
        cube.rotation.y += deltaTime * 0.3f

        // Render the scene
        renderer.render(scene, camera)
    }
}
```

### 3. Platform-Specific Entry Points

#### JVM (Desktop)

Create `src/jvmMain/kotlin/Main.kt`:

```kotlin
import io.materia.renderer.createRenderer
import io.materia.renderer.RendererResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

fun main() = runBlocking {
    println("🚀 Starting Materia First Scene")

    // Create renderer (platform-specific)
    val rendererResult = createRenderer()
    val renderer = when (rendererResult) {
        is RendererResult.Success -> rendererResult.value
        is RendererResult.Error -> {
            println("❌ Failed to create renderer: ${rendererResult.exception.message}")
            return@runBlocking
        }
    }

    // Configure renderer
    renderer.setSize(1280, 720)
    renderer.clearColor = Color(0.1f, 0.1f, 0.2f) // Dark blue background

    // Initialize scene
    val scene = FirstScene()
    scene.initialize(aspectRatio = 1280f / 720f)

    println("✅ Scene initialized")
    println("🎬 Starting render loop...")

    // Simple render loop
    var lastTime = System.currentTimeMillis()
    var running = true
    var frameCount = 0

    while (running && frameCount < 600) { // Run for ~10 seconds at 60fps
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastTime) / 1000f
        lastTime = currentTime

        scene.render(renderer, deltaTime)

        frameCount++
        delay(16) // ~60 FPS

        if (frameCount % 60 == 0) {
            println("Frame $frameCount rendered")
        }
    }

    println("🏁 Rendering complete")
    renderer.dispose()
}
```

#### JavaScript (Browser)

Create `src/jsMain/kotlin/Main.kt`:

```kotlin
import io.materia.renderer.createRenderer
import io.materia.renderer.RendererResult
import io.materia.core.math.Color
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() {
    // Wait for DOM to load
    window.onload = {
        GlobalScope.launch {
            startApp()
        }
    }
}

suspend fun startApp() {
    console.log("🚀 Starting Materia Web App")

    // Get canvas element
    val canvas = document.getElementById("canvas")
        ?: document.createElement("canvas").also {
            document.body?.appendChild(it)
        }

    // Create renderer
    val rendererResult = createRenderer()
    val renderer = when (rendererResult) {
        is RendererResult.Success -> rendererResult.value
        is RendererResult.Error -> {
            console.error("Failed to create renderer", rendererResult.exception)
            return
        }
    }

    // Configure renderer
    val width = window.innerWidth
    val height = window.innerHeight
    renderer.setSize(width, height)
    renderer.clearColor = Color(0.1f, 0.1f, 0.2f)

    // Initialize scene
    val scene = FirstScene()
    scene.initialize(aspectRatio = width.toFloat() / height.toFloat())

    console.log("✅ Scene initialized")

    // Animation loop
    var lastTime = 0.0

    fun animate(currentTime: Double) {
        val deltaTime = ((currentTime - lastTime) / 1000.0).toFloat()
        lastTime = currentTime

        scene.render(renderer, deltaTime)

        window.requestAnimationFrame(::animate)
    }

    window.requestAnimationFrame(::animate)
}
```

Create `src/jsMain/resources/index.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Materia First Scene</title>
    <style>
        body {
            margin: 0;
            padding: 0;
            overflow: hidden;
        }
        canvas {
            display: block;
            width: 100vw;
            height: 100vh;
        }
    </style>
</head>
<body>
    <canvas id="canvas"></canvas>
    <script src="your-app.js"></script>
</body>
</html>
```

### 4. Run Your Application

```bash
# JVM (Desktop)
./gradlew runJvm

# JavaScript (Browser)
./gradlew jsBrowserDevelopmentRun
# Opens automatically in your default browser
```

## 📚 Core Concepts

### Scene Graph

Materia uses a hierarchical scene graph where every object is an `Object3D`:

```kotlin
val scene = Scene()

// Create parent object
val parent = Mesh(geometry, material).apply {
    position.set(0f, 2f, 0f)
}

// Create child object
val child = Mesh(childGeometry, childMaterial).apply {
    position.set(1f, 0f, 0f) // Relative to parent
}

// Build hierarchy
parent.add(child)
scene.add(parent)

// Transformations cascade through hierarchy
parent.rotation.y = PI.toFloat() / 4f // Child rotates with parent
```

### Transformations

Each Object3D has position, rotation, and scale:

```kotlin
val mesh = Mesh(geometry, material)

// Position (Vector3)
mesh.position.set(x = 5f, y = 0f, z = -3f)

// Rotation (Euler angles in radians)
mesh.rotation.x = PI.toFloat() / 4f  // 45 degrees
mesh.rotation.y = PI.toFloat() / 2f  // 90 degrees

// Scale (Vector3)
mesh.scale.set(2f, 1f, 1f) // Twice as wide

// Or use helper methods
mesh.translateX(5f)
mesh.rotateY(PI.toFloat() / 4f)
mesh.scale.multiply(1.5f)
```

### Cameras

Materia provides multiple camera types:

```kotlin
// Perspective Camera (3D with depth)
val perspCamera = PerspectiveCamera(
    fov = 75f,        // Vertical field of view
    aspect = 16f/9f,  // Aspect ratio
    near = 0.1f,      // Near clipping plane
    far = 1000f       // Far clipping plane
)
perspCamera.position.z = 5f
perspCamera.lookAt(Vector3.ZERO)

// Orthographic Camera (2D/isometric)
val orthoCamera = OrthographicCamera(
    left = -10f,
    right = 10f,
    top = 10f,
    bottom = -10f,
    near = 0.1f,
    far = 100f
)
```

### Geometries

Materia includes many built-in geometries:

```kotlin
// Primitives
val box = BoxGeometry(width = 1f, height = 1f, depth = 1f)
val sphere = SphereGeometry(radius = 1f, widthSegments = 32, heightSegments = 16)
val plane = PlaneGeometry(width = 10f, height = 10f)
val cylinder = CylinderGeometry(radiusTop = 1f, radiusBottom = 1f, height = 2f)
val cone = ConeGeometry(radius = 1f, height = 2f)
val torus = TorusGeometry(radius = 1f, tube = 0.4f)

// Advanced
val capsule = CapsuleGeometry(radius = 0.5f, length = 2f)
val torusKnot = TorusKnotGeometry(radius = 1f, tube = 0.3f, p = 2, q = 3)
```

### Materials

Different material types for different rendering needs:

```kotlin
// SimpleMaterial (PBR-like, good default)
val simple = SimpleMaterial(
    albedo = Color(1f, 0f, 0f),      // Red
    metallic = 0.5f,                 // Half metallic
    roughness = 0.3f,                // Fairly smooth
    emissive = Color(0.1f, 0f, 0f),  // Slight glow
    materialName = "RedMetal"
)

// MeshBasicMaterial (unlit, flat color)
val basic = MeshBasicMaterial().apply {
    color = Color(0f, 1f, 0f) // Green
}

// MeshStandardMaterial (PBR)
val standard = MeshStandardMaterial().apply {
    color = Color(0x4488ff)
    metalness = 0.7f
    roughness = 0.2f
}

// MeshPhongMaterial (classic Phong shading)
val phong = MeshPhongMaterial().apply {
    color = Color(0xff00ff)
    shininess = 30f
}
```

## 🎯 Complete Working Example

Here's a complete scene with multiple objects and animation:

```kotlin
import io.materia.core.scene.*
import io.materia.core.math.*
import io.materia.geometry.primitives.*
import io.materia.material.*
import io.materia.camera.PerspectiveCamera
import kotlin.math.*

class CompleteScene {
    private val scene = Scene()
    private lateinit var camera: PerspectiveCamera
    private val objects = mutableListOf<Mesh>()
    private var time = 0f

    fun initialize() {
        // Background color
        scene.background = Background.Color(Color(0.05f, 0.05f, 0.1f))

        // Setup camera
        camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f).apply {
            position.set(0f, 5f, 10f)
            lookAt(Vector3.ZERO)
        }

        // Create ground plane
        val ground = Mesh(
            PlaneGeometry(20f, 20f),
            SimpleMaterial(
                albedo = Color(0.3f, 0.3f, 0.3f),
                roughness = 0.9f,
                materialName = "Ground"
            )
        ).apply {
            rotation.x = -PI.toFloat() / 2f // Make horizontal
        }
        scene.add(ground)

        // Create multiple cubes in a circle
        repeat(8) { i ->
            val angle = (i / 8f) * PI.toFloat() * 2f
            val radius = 5f

            val cube = Mesh(
                BoxGeometry(1f, 1f, 1f),
                SimpleMaterial(
                    albedo = Color(
                        sin(angle) * 0.5f + 0.5f,
                        cos(angle * 1.5f) * 0.5f + 0.5f,
                        sin(angle * 0.7f) * 0.5f + 0.5f
                    ),
                    metallic = 0.3f + (i / 8f) * 0.4f,
                    roughness = 0.2f + (i / 8f) * 0.3f,
                    materialName = "Cube$i"
                )
            ).apply {
                position.set(
                    cos(angle) * radius,
                    1f,
                    sin(angle) * radius
                )
            }

            scene.add(cube)
            objects.add(cube)
        }

        // Central sphere
        val sphere = Mesh(
            SphereGeometry(1.5f, 32, 16),
            SimpleMaterial(
                albedo = Color(0.2f, 0.6f, 0.9f),
                metallic = 0.8f,
                roughness = 0.2f,
                emissive = Color(0f, 0.1f, 0.2f),
                materialName = "CenterSphere"
            )
        ).apply {
            position.set(0f, 2f, 0f)
        }
        scene.add(sphere)
        objects.add(sphere)
    }

    fun update(deltaTime: Float) {
        time += deltaTime

        // Rotate cubes
        objects.dropLast(1).forEachIndexed { index, cube ->
            cube.rotation.y = time * 0.5f + index * 0.1f
            cube.position.y = 1f + sin(time * 2f + index) * 0.3f
        }

        // Float and rotate central sphere
        val sphere = objects.last()
        sphere.rotation.x = time * 0.3f
        sphere.rotation.y = time * 0.7f
        sphere.position.y = 2f + sin(time * 1.5f) * 0.5f

        // Orbit camera
        val cameraRadius = 12f
        camera.position.x = cos(time * 0.2f) * cameraRadius
        camera.position.z = sin(time * 0.2f) * cameraRadius
        camera.position.y = 5f + sin(time * 0.3f) * 2f
        camera.lookAt(Vector3(0f, 1f, 0f))
    }

    fun render(renderer: Renderer) {
        renderer.render(scene, camera)
    }
}
```

## 🎮 Adding Interactivity

### Camera Controls

```kotlin
import io.materia.controls.OrbitControls

val controls = OrbitControls(camera).apply {
    enableDamping = true
    dampingFactor = 0.05f
    minDistance = 5f
    maxDistance = 20f
}

// In your update loop
fun update(deltaTime: Float) {
    controls.update(deltaTime)
    // ... other updates
}
```

## 🔧 Troubleshooting

### Common Issues

**Issue**: Renderer creation fails
```kotlin
// Check renderer result properly
when (val result = createRenderer()) {
    is RendererResult.Success -> {
        val renderer = result.value
        // Use renderer
    }
    is RendererResult.Error -> {
        println("Error: ${result.exception.message}")
        result.exception.printStackTrace()
    }
}
```

**Issue**: Objects not visible
```kotlin
// Ensure camera is positioned correctly
camera.position.z = 5f  // Move camera back
camera.lookAt(Vector3.ZERO)  // Point at origin

// Check object positions
println("Object position: ${mesh.position}")
println("Camera position: ${camera.position}")
```

**Issue**: Black screen
```kotlin
// Set clear color
renderer.clearColor = Color(0.1f, 0.1f, 0.2f)

// Ensure scene has objects
println("Scene children: ${scene.children.size}")

// Verify rendering
scene.traverse { obj ->
    println("Object: ${obj::class.simpleName} at ${obj.position}")
}
```

## 📚 Next Steps

- **[Platform-Specific Setup](platform-specific.md)** - Detailed platform configuration
- **[API Reference](../api-reference/README.md)** - Complete API documentation
- **[Examples](../examples/basic-usage.md)** - More code examples
- **[Architecture Overview](../architecture/overview.md)** - How Materia works

## 💡 Tips

1. **Start Simple**: Begin with basic shapes and materials
2. **Use SimpleMaterial**: Good default material for most use cases
3. **Check Positions**: Use `println()` to debug object positions
4. **Frame Rate**: Target 16ms (60 FPS) for smooth animation
5. **Object Pooling**: Reuse objects when possible for better performance

## 🤝 Need Help?

- Check the [examples/basic-scene](../../examples/basic-scene/) project
- Read the [API documentation](../api-reference/README.md)
- Open an issue on GitHub
- Join our community discussions

---

**Happy Creating! 🎨**
