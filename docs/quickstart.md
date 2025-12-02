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
            implementation("io.materia:materia-engine:0.1.0-alpha01")
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
    implementation("io.materia:materia-engine:0.1.0-alpha01")
}

application {
    mainClass.set("MainKt")
}
```

---

## Step 2: Create Your First Scene

The unified API works identically on JVM and JS platforms:

```kotlin
import io.materia.engine.renderer.WebGPURenderer
import io.materia.engine.renderer.WebGPURendererConfig
import io.materia.engine.scene.Scene
import io.materia.engine.scene.EngineMesh
import io.materia.engine.camera.PerspectiveCamera
import io.materia.engine.material.BasicMaterial
import io.materia.engine.material.StandardMaterial
import io.materia.engine.core.RenderLoop
import io.materia.engine.core.DisposableContainer
import io.materia.engine.window.KmpWindow
import io.materia.engine.window.KmpWindowConfig
import io.materia.geometry.BufferGeometry
import io.materia.core.math.Color
import io.materia.core.math.Vector3

class SimpleScene {
    
    private val resources = DisposableContainer()
    
    // Create the scene graph
    val scene = Scene()
    
    // Create a perspective camera
    val camera = PerspectiveCamera(
        fov = 75f,
        aspect = 16f / 9f,
        near = 0.1f,
        far = 1000f
    )
    
    // Track our animated cube
    lateinit var cube: EngineMesh
    
    fun setup() {
        // Position the camera
        camera.position.set(0f, 2f, 5f)
        camera.lookAt(Vector3.ZERO)
        
        // Create a simple box geometry
        val geometry = createBoxGeometry(1f, 1f, 1f)
        resources.track(geometry)
        
        // Create a PBR material
        val material = StandardMaterial(
            color = Color(0f, 1f, 0f),  // Green
            metalness = 0.3f,
            roughness = 0.4f
        )
        resources.track(material)
        
        // Create mesh
        cube = EngineMesh(geometry, material)
        resources.track(cube)
        scene.add(cube)
        
        // Add a ground plane
        val groundGeometry = createPlaneGeometry(10f, 10f)
        val groundMaterial = StandardMaterial(
            color = Color(0.5f, 0.5f, 0.5f),
            roughness = 0.9f
        )
        resources.track(groundGeometry)
        resources.track(groundMaterial)
        
        val ground = EngineMesh(groundGeometry, groundMaterial)
        ground.rotation.x = -Math.PI.toFloat() / 2  // Horizontal
        ground.position.y = -0.5f
        resources.track(ground)
        scene.add(ground)
    }
    
    fun update(deltaTime: Float) {
        // Rotate the cube
        cube.rotateY(deltaTime * 0.5f)
        cube.rotateX(deltaTime * 0.3f)
    }
    
    fun dispose() {
        resources.dispose()
    }
}

// Helper to create box geometry
fun createBoxGeometry(width: Float, height: Float, depth: Float): BufferGeometry {
    val hw = width / 2
    val hh = height / 2
    val hd = depth / 2
    
    return BufferGeometry().apply {
        // Positions for a cube (6 faces, 2 triangles each)
        setAttribute("position", floatArrayOf(
            // Front face
            -hw, -hh, hd,   hw, -hh, hd,   hw, hh, hd,
            -hw, -hh, hd,   hw, hh, hd,   -hw, hh, hd,
            // Back face
            hw, -hh, -hd,  -hw, -hh, -hd,  -hw, hh, -hd,
            hw, -hh, -hd,  -hw, hh, -hd,   hw, hh, -hd,
            // Top face
            -hw, hh, hd,   hw, hh, hd,   hw, hh, -hd,
            -hw, hh, hd,   hw, hh, -hd,  -hw, hh, -hd,
            // Bottom face
            -hw, -hh, -hd,  hw, -hh, -hd,  hw, -hh, hd,
            -hw, -hh, -hd,  hw, -hh, hd,  -hw, -hh, hd,
            // Right face
            hw, -hh, hd,   hw, -hh, -hd,  hw, hh, -hd,
            hw, -hh, hd,   hw, hh, -hd,   hw, hh, hd,
            // Left face
            -hw, -hh, -hd, -hw, -hh, hd,  -hw, hh, hd,
            -hw, -hh, -hd, -hw, hh, hd,  -hw, hh, -hd
        ), 3)
    }
}

fun createPlaneGeometry(width: Float, height: Float): BufferGeometry {
    val hw = width / 2
    val hh = height / 2
    
    return BufferGeometry().apply {
        setAttribute("position", floatArrayOf(
            -hw, 0f, -hh,  hw, 0f, -hh,  hw, 0f, hh,
            -hw, 0f, -hh,  hw, 0f, hh,  -hw, 0f, hh
        ), 3)
    }
}
```

---

## Step 3: Platform Entry Points

### JVM Entry Point

Create `src/jvmMain/kotlin/Main.kt`:

```kotlin
import io.materia.engine.renderer.WebGPURenderer
import io.materia.engine.renderer.WebGPURendererConfig
import io.materia.engine.core.RenderLoop
import io.materia.engine.window.KmpWindow
import io.materia.engine.window.KmpWindowConfig

fun main() {
    // Create window
    val window = KmpWindow(KmpWindowConfig(
        width = 1280,
        height = 720,
        title = "Materia Demo"
    ))
    
    // Initialize renderer with unified WebGPU API
    val renderer = WebGPURenderer(WebGPURendererConfig(
        surface = window.surface,
        width = 1280,
        height = 720,
        clearColor = floatArrayOf(0.1f, 0.1f, 0.15f, 1f)
    ))
    
    // Create scene
    val simpleScene = SimpleScene()
    simpleScene.setup()
    
    // Start render loop
    val renderLoop = RenderLoop { deltaTime ->
        window.pollEvents()
        simpleScene.update(deltaTime)
        renderer.render(simpleScene.scene, simpleScene.camera)
    }
    
    window.show()
    renderLoop.start()
    
    // Cleanup (when window closes)
    renderLoop.stop()
    simpleScene.dispose()
    renderer.dispose()
    window.dispose()
}
```

### JS Entry Point

Create `src/jsMain/kotlin/Main.kt`:

```kotlin
import io.materia.engine.renderer.WebGPURenderer
import io.materia.engine.renderer.WebGPURendererConfig
import io.materia.engine.core.RenderLoop
import io.materia.engine.window.KmpWindow
import io.materia.engine.window.KmpWindowConfig
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

fun main() {
    val canvas = document.getElementById("canvas") as HTMLCanvasElement
    
    // Create window wrapper for canvas
    val window = KmpWindow(KmpWindowConfig(
        width = canvas.width,
        height = canvas.height,
        canvas = canvas
    ))
    
    // Initialize renderer (same API as JVM!)
    val renderer = WebGPURenderer(WebGPURendererConfig(
        surface = window.surface,
        width = canvas.width,
        height = canvas.height,
        clearColor = floatArrayOf(0.1f, 0.1f, 0.15f, 1f)
    ))
    
    // Create scene
    val simpleScene = SimpleScene()
    simpleScene.setup()
    
    // Start render loop (uses requestAnimationFrame on JS)
    val renderLoop = RenderLoop { deltaTime ->
        simpleScene.update(deltaTime)
        renderer.render(simpleScene.scene, simpleScene.camera)
    }
    
    renderLoop.start()
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
    <canvas id="canvas" width="1280" height="720"></canvas>
    <script src="app.js"></script>
</body>
</html>
```

---

## Step 4: Run Your Scene

```bash
# JVM (Desktop with Vulkan/WebGPU backend)
./gradlew jvmRun

# JavaScript (Browser with WebGPU)
./gradlew jsBrowserRun
```

---

## What's Next?

Now that you have a basic scene running, explore these topics:

### Use Different Materials

```kotlin
// Unlit basic material (for UI, debugging)
val unlitMaterial = BasicMaterial(
    color = Color(1f, 0f, 0f)  // Red
)

// PBR material with metallic look
val metalMaterial = StandardMaterial(
    color = Color(0.9f, 0.9f, 0.95f),
    metalness = 1.0f,
    roughness = 0.2f
)

// Emissive glowing material
val glowMaterial = StandardMaterial(
    color = Color(0.1f, 0.1f, 0.1f),
    emissive = Color(0f, 1f, 0.5f),
    emissiveIntensity = 2.0f
)
```

### Add Multiple Objects

```kotlin
val geometry = createBoxGeometry(0.5f, 0.5f, 0.5f)

for (i in 0 until 10) {
    val material = StandardMaterial(
        color = Color(i / 10f, 0.5f, 1f - i / 10f),
        metalness = i / 10f,
        roughness = 1f - i / 10f
    )
    
    val mesh = EngineMesh(geometry, material)
    mesh.position.x = (i - 5) * 1.2f
    scene.add(mesh)
}
```

### Handle Window Resize

```kotlin
window.onResize { width, height ->
    renderer.setSize(width, height)
    camera.aspect = width.toFloat() / height
    camera.updateProjectionMatrix()
}
```

### Track Resources with DisposableContainer

```kotlin
val resources = DisposableContainer()

// Add resources as you create them
val material = StandardMaterial(color = Color.RED)
resources.track(material)

val geometry = createBoxGeometry(1f, 1f, 1f)
resources.track(geometry)

// Cleanup everything at once
resources.dispose()
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

For browser targets, append `jsBrowserRun` instead of `runJvm`:
```bash
./gradlew :examples:embedding-galaxy:jsBrowserRun
```

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
- See `AGENTS.md` for shader management details

### Objects Not Visible

Common causes:
- Camera looking the wrong direction (use `camera.lookAt()`)
- Objects outside camera frustum (check `near`/`far` planes)
- Material set to transparent without proper blending config

---

## See Also

- [API Reference](api-reference/README.md) - Complete API documentation
  - [Renderer](api-reference/renderer.md) - WebGPURenderer configuration
  - [Material](api-reference/material.md) - BasicMaterial and StandardMaterial
  - [Core](api-reference/core.md) - Disposable, RenderLoop, math classes
- [Examples](examples/basic-usage.md) - More code samples
