# Quickstart Guide

Get your first Materia 3D scene running in under 5 minutes.

## Prerequisites

- **Kotlin 2.1.20** or later
- **Gradle 8.x** with Kotlin DSL
- **JDK 17+** for JVM target
- A modern browser with WebGPU support (Chrome 113+, Edge 113+) for JS target

---

## Step 1: Add Dependencies

### Kotlin Multiplatform Project

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.1.20"
}

kotlin {
    jvm()
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation("io.materia:materia:0.1.0-alpha01")
        }
        jvmMain.dependencies {
            implementation("io.materia:materia-jvm:0.1.0-alpha01")
        }
        jsMain.dependencies {
            implementation("io.materia:materia-js:0.1.0-alpha01")
        }
    }
}
```

### JVM-Only Project

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.20"
    application
}

dependencies {
    implementation("io.materia:materia-jvm:0.1.0-alpha01")
}

application {
    mainClass.set("MainKt")
}
```

---

## Step 2: Create Your First Scene

Create a new file `src/commonMain/kotlin/Main.kt`:

```kotlin
import io.materia.core.*
import io.materia.geometry.*
import io.materia.material.*
import io.materia.camera.*
import io.materia.light.*
import io.materia.renderer.*

class SimpleScene(private val renderer: Renderer) {
    
    // Create the scene graph root
    val scene = Scene()
    
    // Create a perspective camera
    val camera = PerspectiveCamera(
        fov = 75f,
        aspect = 16f / 9f,
        near = 0.1f,
        far = 1000f
    )
    
    // Track our animated cube
    lateinit var cube: Mesh
    
    fun setup() {
        // Position the camera
        camera.position.set(0f, 2f, 5f)
        camera.lookAt(Vector3.ZERO)
        
        // Create a simple box mesh
        val geometry = BoxGeometry(1f, 1f, 1f)
        val material = MeshStandardMaterial().apply {
            color = Color(0x00ff00)  // Green
            metalness = 0.3f
            roughness = 0.4f
        }
        cube = Mesh(geometry, material)
        scene.add(cube)
        
        // Add a ground plane
        val ground = Mesh(
            PlaneGeometry(10f, 10f),
            MeshStandardMaterial().apply {
                color = Color(0x808080)
            }
        )
        ground.rotation.x = -PI / 2  // Rotate to be horizontal
        ground.position.y = -0.5f
        scene.add(ground)
        
        // Add lights
        val directionalLight = DirectionalLight(Color.WHITE, 1.0f)
        directionalLight.position.set(5f, 10f, 7.5f)
        scene.add(directionalLight)
        
        scene.add(AmbientLight(Color(0x404040), 0.4f))
    }
    
    fun update(deltaTime: Float) {
        // Rotate the cube
        cube.rotation.x += deltaTime * 0.5f
        cube.rotation.y += deltaTime * 0.3f
        
        // Render the frame
        renderer.render(scene, camera)
    }
}
```

---

## Step 3: Platform Entry Points

### JVM Entry Point

Create `src/jvmMain/kotlin/Main.kt`:

```kotlin
import io.materia.renderer.VulkanRenderer
import io.materia.platform.Window

fun main() {
    // Create window with Vulkan surface
    val window = Window(
        title = "Materia Demo",
        width = 1280,
        height = 720
    )
    
    // Initialize renderer
    val renderer = VulkanRenderer(window)
    
    // Create scene
    val scene = SimpleScene(renderer)
    scene.setup()
    
    // Run the game loop
    window.run { deltaTime ->
        scene.update(deltaTime)
    }
    
    // Cleanup
    renderer.dispose()
    window.dispose()
}
```

### JS Entry Point

Create `src/jsMain/kotlin/Main.kt`:

```kotlin
import io.materia.renderer.WebGPURenderer
import io.materia.platform.Canvas
import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    // Get or create canvas
    val canvas = Canvas(document.getElementById("canvas") as HTMLCanvasElement)
    
    // Initialize WebGPU renderer
    WebGPURenderer.create(canvas) { renderer ->
        val scene = SimpleScene(renderer)
        scene.setup()
        
        // Animation loop
        var lastTime = 0.0
        fun animate(timestamp: Double) {
            val deltaTime = ((timestamp - lastTime) / 1000.0).toFloat()
            lastTime = timestamp
            
            scene.update(deltaTime)
            window.requestAnimationFrame(::animate)
        }
        
        window.requestAnimationFrame(::animate)
    }
}
```

### HTML Template for JS

Create `src/jsMain/resources/index.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Materia Demo</title>
    <style>
        body { margin: 0; overflow: hidden; }
        canvas { display: block; width: 100vw; height: 100vh; }
    </style>
</head>
<body>
    <canvas id="canvas"></canvas>
    <script src="app.js"></script>
</body>
</html>
```

---

## Step 4: Run Your Scene

```bash
# JVM (Desktop with Vulkan)
./gradlew run

# or if using multiplatform setup:
./gradlew jvmRun

# JavaScript (Browser with WebGPU)
./gradlew jsBrowserRun
```

---

## What's Next?

Now that you have a basic scene running, explore these topics:

### Add More Shapes

```kotlin
// Sphere
val sphere = Mesh(
    SphereGeometry(radius = 0.5f, segments = 32),
    MeshStandardMaterial().apply { color = Color(0xff0000) }
)
sphere.position.set(-2f, 0.5f, 0f)
scene.add(sphere)

// Cylinder
val cylinder = Mesh(
    CylinderGeometry(radiusTop = 0.3f, radiusBottom = 0.3f, height = 1f),
    MeshStandardMaterial().apply { color = Color(0x0000ff) }
)
cylinder.position.set(2f, 0.5f, 0f)
scene.add(cylinder)
```

### Load 3D Models

```kotlin
val loader = GLTFLoader()
loader.load("models/robot.gltf") { gltf ->
    scene.add(gltf.scene)
    
    // Play animations if present
    gltf.animations.firstOrNull()?.let { clip ->
        val mixer = AnimationMixer(gltf.scene)
        mixer.clipAction(clip).play()
    }
}
```

### Add Camera Controls

```kotlin
val controls = OrbitControls(camera, renderer.domElement)
controls.enableDamping = true
controls.dampingFactor = 0.05f
controls.minDistance = 2f
controls.maxDistance = 20f

// In update loop
fun update(deltaTime: Float) {
    controls.update(deltaTime)
    renderer.render(scene, camera)
}
```

### Add Shadows

```kotlin
// Enable shadows on renderer
renderer.shadowMap.enabled = true
renderer.shadowMap.type = ShadowMapType.PCFSoft

// Configure light to cast shadows
directionalLight.castShadow = true
directionalLight.shadow.mapSize.set(2048, 2048)
directionalLight.shadow.camera.near = 0.5f
directionalLight.shadow.camera.far = 50f

// Objects cast and receive shadows
cube.castShadow = true
cube.receiveShadow = true
ground.receiveShadow = true
```

---

## Example Projects

The repository includes several example projects to learn from:

| Example | Description | Run Command |
|---------|-------------|-------------|
| **Triangle** | Basic triangle rendering | `./gradlew :examples:triangle:runJvm` |
| **Basic Scene** | Scene with multiple objects | `./gradlew :examples:basic-scene:runJvm` |
| **VoxelCraft** | Minecraft-style voxel world | `./gradlew :examples:voxelcraft:runJvm` |
| **Force Graph** | 3D force-directed graph | `./gradlew :examples:force-graph:runJvm` |
| **Embedding Galaxy** | Particle visualization | `./gradlew :examples:embedding-galaxy:runJvm` |

---

## Common Issues

### WebGPU Not Available

If you see "WebGPU not supported", ensure:
- Chrome/Edge version 113 or later
- Enable `chrome://flags/#enable-unsafe-webgpu` if needed
- Try Firefox Nightly with `dom.webgpu.enabled` flag

### Vulkan Driver Issues (JVM)

If you get Vulkan initialization errors:
- Update your graphics drivers
- Ensure Vulkan SDK is installed (optional, but helpful for debugging)
- Check `VK_ICD_FILENAMES` environment variable on Linux

### SPIR-V Shader Errors

If you see shader compilation errors on JVM:
- Ensure SPIR-V binaries are present in `resources/shaders/`
- Check that shader files were compiled for your Vulkan version

---

## See Also

- [Getting Started (Detailed)](guides/getting-started.md) - In-depth setup guide
- [API Reference](api-reference/README.md) - Complete API documentation
- [Examples](examples/basic-usage.md) - More code samples
